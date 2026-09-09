package simulation.vision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.StringReader;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PoseSessionTest {
    @TempDir Path directory;

    @Test void recordsLabelsRawLandmarksAndReplaysWithoutImages() throws Exception {
        Path file=directory.resolve("session.jsonl");
        PoseSessionRecorder recorder=new PoseSessionRecorder(file);
        PoseFrame first=PoseMathChecks.sample(1000,0), second=PoseMathChecks.sample(1040,1);
        recorder.record(first,"Standing"); recorder.record(second,"Descending");
        assertEquals(file,recorder.stop().get(5,TimeUnit.SECONDS));
        var lines=Files.readAllLines(file);
        assertEquals(3,lines.size()); assertFalse(Files.readString(file).contains("jpeg"));
        PoseSession.Sample saved=PoseProtocol.JSON.fromJson(lines.get(2),PoseSession.Sample.class);
        assertEquals("Descending",saved.keyframeLabel()); assertEquals(second,saved.pose());
        ReplayPoseSource replay=new ReplayPoseSource(file); replay.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(replay.isRunning() && System.nanoTime()<deadline) Thread.sleep(10);
        assertEquals("Replay complete",replay.status()); assertEquals(second,replay.latest().orElseThrow());
        assertFalse(replay.isHealthy(),"finished replay must not remain live");
        replay.close(); assertTrue(replay.latest().isEmpty());
    }

    @Test void rejectsBackwardsReplayAndClearsLastPose() throws Exception {
        Path file=directory.resolve("bad.jsonl");
        var header=new PoseSession.Header(1,"session","test",1000,"radians","test");
        String text=PoseProtocol.JSON.toJson(header)+"\n"
                +PoseProtocol.JSON.toJson(new PoseSession.Sample(1,"pose","test","Standing",2000,PoseMathChecks.sample(1040,1)))+"\n"
                +PoseProtocol.JSON.toJson(new PoseSession.Sample(1,"pose","test","Standing",2000,PoseMathChecks.sample(1000,0)))+"\n";
        Files.writeString(file,text);
        ReplayPoseSource replay=new ReplayPoseSource(file); replay.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(replay.isRunning() && System.nanoTime()<deadline) Thread.sleep(10);
        assertTrue(replay.status().contains("chronologically")); assertTrue(replay.latest().isEmpty());
        replay.close();
    }

    @Test void boundsInputAndRejectsUnsupportedProtocol() {
        assertThrows(java.io.IOException.class,()->PoseProtocol.readLine(new StringReader("x".repeat(PoseProtocol.MAX_LINE_LENGTH+1))));
        assertThrows(IllegalArgumentException.class,()->PoseProtocol.decode("{\"version\":2,\"type\":\"frame\"}"));
        assertThrows(RuntimeException.class,()->PoseProtocol.decode("{\"version\":1,\"type\":\"frame\",\"inferenceMs\":NaN}"));
        assertThrows(IllegalArgumentException.class,()->PoseProtocol.decode("{\"version\":1,\"type\":\"frame\"}"));
    }

    @Test void reportsFailedSaveWithoutOverwritingExistingTarget() throws Exception {
        Path missing=directory.resolve("missing/session.jsonl");
        PoseSessionRecorder recorder=new PoseSessionRecorder(missing);
        recorder.record(PoseMathChecks.sample(1000,0),"Standing");
        assertThrows(java.util.concurrent.ExecutionException.class,()->recorder.stop().get(5,TimeUnit.SECONDS));
        assertFalse(Files.exists(missing)); assertNotNull(recorder.failure());
    }
}
