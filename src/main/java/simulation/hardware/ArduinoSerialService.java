package simulation.hardware;

import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Owns serial port lifecycle and streams Arduino lines on a background thread.
 */
public class ArduinoSerialService {

    public interface Listener {
        void onConnected(String portName);
        void onDisconnected();
        void onPotValue(int value);
        void onButton1Pressed();
        void onButton2Pressed();
        void onInfo(String message);
        void onError(String message, Exception exception);
    }

    private SerialPort serialPort;
    private ExecutorService ioExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public List<String> listAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .collect(Collectors.toList());
    }

    public synchronized void connect(String systemPortName, int baudRate, Listener listener) {
        if (running.get()) {
            disconnect();
        }

        SerialPort selectedPort = Arrays.stream(SerialPort.getCommPorts())
                .filter(port -> port.getSystemPortName().equals(systemPortName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Serial port not found: " + systemPortName));

        selectedPort.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        selectedPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1500, 0);

        if (!selectedPort.openPort()) {
            throw new IllegalStateException("Failed to open serial port: " + systemPortName);
        }

        serialPort = selectedPort;
        running.set(true);
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ArduinoSerialReader");
            t.setDaemon(true);
            return t;
        });

        listener.onConnected(systemPortName);
        ioExecutor.submit(() -> readLoop(listener));
    }

    public synchronized void disconnect() {
        if (!running.get()) {
            return;
        }
        running.set(false);

        if (serialPort != null) {
            try {
                serialPort.closePort();
            } catch (Exception ignored) {
            }
            serialPort = null;
        }

        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
    }

    public boolean isConnected() {
        return running.get();
    }

    private void readLoop(Listener listener) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serialPort.getInputStream(), StandardCharsets.US_ASCII))) {
            while (running.get()) {
                String line = reader.readLine();
                if (line == null) {
                    if (running.get() && serialPort != null && serialPort.isOpen()) {
                        continue;
                    }
                    break;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if ("BUTTON_1".equals(trimmed)) {
                    listener.onButton1Pressed();
                    continue;
                }
                if ("BUTTON_2".equals(trimmed)) {
                    listener.onButton2Pressed();
                    continue;
                }
                if ("START".equals(trimmed)) {
                    listener.onInfo("Arduino startup banner received");
                    continue;
                }

                if (trimmed.startsWith("POT:")) {
                    parsePotValue(trimmed.substring(4), listener);
                    continue;
                }

                // Backward compatible path for older sketches that emit plain numbers.
                parsePotValue(trimmed, listener);
            }
        } catch (IOException e) {
            if (running.get()) {
                listener.onError("Serial read failed (device disconnected or stream closed).", e);
            }
        } finally {
            boolean wasRunning = running.get();
            disconnect();
            if (wasRunning) {
                listener.onDisconnected();
            }
        }
    }

    private void parsePotValue(String raw, Listener listener) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= 0 && value <= 1023) {
                listener.onPotValue(value);
            } else {
                listener.onInfo("Ignoring out-of-range POT value: " + value);
            }
        } catch (NumberFormatException ignored) {
            listener.onInfo("Ignoring unrecognized serial line: " + raw);
        }
    }
}
