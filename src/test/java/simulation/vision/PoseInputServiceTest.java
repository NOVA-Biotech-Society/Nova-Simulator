package simulation.vision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PoseInputServiceTest {
    @TempDir Path directory;

    @Test void findsWorkerFromCompiledClassesWhenIdeUsesAnotherWorkingDirectory() throws Exception {
        Path root = directory.resolve("Project with spaces");
        Path vision = Files.createDirectories(root.resolve("vision"));
        Files.writeString(vision.resolve("pose_service.py"), "# worker");
        Path classes = Files.createDirectories(root.resolve("target/classes"));
        assertEquals(vision, PoseRuntime.findDirectory(directory.resolve("unrelated"), classes));
        assertEquals(vision, PoseRuntime.findDirectory(root.resolve("src/main"), null));
    }

    @Test void reportsNativeStartupDiagnosticsInsteadOfGenericDisconnect() throws Exception {
        withWorker("import sys\nprint('CAMERA_TEST_IMPORT_FAILURE: missing native library', file=sys.stderr)\nsys.exit(1)\n", source -> {
            source.start();
            await(() -> source.status().state() == PoseInputService.State.ERROR);
            assertTrue(source.status().message().contains("CAMERA_TEST_IMPORT_FAILURE"), source.status().message());
        });
    }

    @Test void setupCanBeCancelledWithoutWaitingForTheBlockingInstaller() throws Exception {
        Files.writeString(directory.resolve("setup_capture.py"),
                "import os,time\nprint('TEST_INSTALLER_READY:' + str(os.getpid()), flush=True)\ntime.sleep(60)\n");
        withWorker("# no camera opened\n", source -> {
            source.setup();
            await(() -> source.status().message().contains("TEST_INSTALLER_READY"));
            assertEquals(PoseInputService.State.SETUP, source.status().state());
            long pid = Long.parseLong(source.status().message().split(":")[1]);
            long before = System.nanoTime();
            source.stop();
            assertTrue(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1));
            await(() -> ProcessHandle.of(pid).map(handle -> !handle.isAlive()).orElse(true));
            assertEquals(PoseInputService.State.OFF, source.status().state());
            assertTrue(source.latest().isEmpty());
        });
    }

    private interface CameraCheck { void run(PoseInputService source) throws Exception; }
    @Test void stoppingCameraAlsoTerminatesTheCompatibilityWorker() throws Exception {
        String script = "import json,subprocess,sys\n"
                + "child=subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])\n"
                + "print(json.dumps({'version':1,'type':'status','state':'camera','message':'CHILD_PID:'+str(child.pid)}), flush=True)\n"
                + "child.wait()\n";
        withWorker(script, source -> {
            source.start();
            await(() -> source.status().message().startsWith("CHILD_PID:"));
            long pid = Long.parseLong(source.status().message().split(":")[1]);
            source.stop();
            await(() -> ProcessHandle.of(pid).map(handle -> !handle.isAlive()).orElse(true));
            assertEquals(PoseInputService.State.OFF, source.status().state());
        });
    }

    private void withWorker(String script, CameraCheck check) throws Exception {
        String python = System.getProperty("os.name").startsWith("Windows") ? "python" : "python3";
        try { assumeTrue(new ProcessBuilder(python,"--version").start().waitFor() == 0); }
        catch (java.io.IOException e) { assumeTrue(false,"Python interpreter unavailable"); }
        Files.writeString(directory.resolve("pose_service.py"), script);
        String oldDir = System.getProperty("nova.vision.dir"), oldPython = System.getProperty("nova.vision.python");
        System.setProperty("nova.vision.dir", directory.toString()); System.setProperty("nova.vision.python", python);
        try (PoseInputService source = new PoseInputService()) { check.run(source); }
        finally { restore("nova.vision.dir", oldDir); restore("nova.vision.python", oldPython); }
    }

    @Test void stopAndRestartCannotPublishFramesFromOldWorker() throws Exception {
        String python=System.getProperty("os.name").startsWith("Windows") ? "python" : "python3";
        try { assumeTrue(new ProcessBuilder(python,"--version").start().waitFor()==0); }
        catch(java.io.IOException e) { assumeTrue(false,"Python interpreter unavailable"); }
        var sample=PoseMathChecks.sample(1000,0);
        String packet=PoseProtocol.JSON.toJson(new PoseProtocol.Packet(1,"frame",null,null,1000,0,
                1280,720,true,5,"/9j/2Q==",sample.landmarks(),List.of()));
        String script="import json,time,sys\npacket=json.loads("+PoseProtocol.JSON.toJson(packet)+")\n"
                +"for i in range(300):\n packet['frameId']=i\n packet['timestampMs']=int(time.time()*1000)\n"
                +" print(json.dumps(packet),flush=True)\n time.sleep(0.02)\n";
        Files.writeString(directory.resolve("pose_service.py"),script);
        String oldDir=System.getProperty("nova.vision.dir"), oldPython=System.getProperty("nova.vision.python");
        System.setProperty("nova.vision.dir",directory.toString()); System.setProperty("nova.vision.python",python);
        try(PoseInputService source=new PoseInputService()) {
            source.start(); await(()->source.latest().isPresent()); assertTrue(source.isHealthy());
            source.stop(); Thread.sleep(100);
            assertTrue(source.latest().isEmpty()); assertEquals(PoseInputService.State.OFF,source.status().state());
            source.start(); source.stop(); source.start();
            await(()->source.latest().isPresent()); assertTrue(source.isHealthy());
            source.stop(); Thread.sleep(100);
            assertTrue(source.latest().isEmpty()); assertEquals(PoseInputService.State.OFF,source.status().state());
        } finally {
            restore("nova.vision.dir",oldDir); restore("nova.vision.python",oldPython);
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(),"Camera worker did not publish within five seconds");
    }
    private static void restore(String key,String value) {
        if(value==null) System.clearProperty(key); else System.setProperty(key,value);
    }
}
