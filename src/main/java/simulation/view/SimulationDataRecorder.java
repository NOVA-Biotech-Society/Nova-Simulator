package simulation.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import simulation.model.SimulationState;

/**
 * Data Recorder
 */
public class SimulationDataRecorder {

    private PrintWriter writer;
    private boolean isRecording = false;

    /**
     * Starts a new recording session. Creates the file and writes the CSV headers.
     * @param filePath The destination path (e.g., "simulation_data.csv")
     */
    public void startRecording(String filePath) {
        try {


            writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, false)));

            //CSV Headers
            writer.println("time,hip_angle,knee_angle,ankle_angle,hip_torque,knee_torque,ankle_torque");

            isRecording = true;
            System.out.println("Data recording started: " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to initialize the data recorder: " + e.getMessage());
        }
    }

    public void logFrame(SimulationState state) {
        if (!isRecording || writer == null) return;

        double time = state.getTime();

        // Extract angles
        double hipAngle  = Math.toDegrees(state.getHumanModel().getThigh().getAngle());
        double kneeAngle = Math.toDegrees(state.getHumanModel().getKneeJoint().getAngle());
        double ankleAngle = Math.toDegrees(state.getHumanModel().getAnkleJoint().getAngle());

        // Extract torques
        double hipTorque  = state.getExoskeletonModel().getHipMotor().getOutputTorque();
        double kneeTorque = state.getExoskeletonModel().getKneeMotor().getOutputTorque();
        double ankleTorque = state.getExoskeletonModel().getAnkleMotor().getOutputTorque();

        // Write row format: time, hip_ang, knee_ang, ankle_ang, hip_trq, knee_trq, ankle_trq
        writer.printf("%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                time, hipAngle, kneeAngle, ankleAngle, hipTorque, kneeTorque, ankleTorque);
    }

    /**
     * Flushes remaining data and closes the file .
     */
    public void stopRecording() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
        isRecording = false;
        System.out.println("Data recording stopped and saved.");
    }

    public boolean isRecording() {
        return isRecording;
    }
}