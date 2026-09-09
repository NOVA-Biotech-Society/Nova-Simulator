package simulation.vision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Locates the optional worker and its interpreter independently of the IDE working directory. */
final class PoseRuntime {
    private PoseRuntime() { }

    static Path directory() throws IOException {
        String configured = System.getProperty("nova.vision.dir");
        if (configured != null && !configured.isBlank()) {
            Path root = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(root.resolve("pose_service.py"))) return root;
            throw new IOException("No pose_service.py in nova.vision.dir: " + root);
        }
        Path location = null;
        try {
            location = Path.of(PoseRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception ignored) { /* Working directory remains a valid fallback. */ }
        return findDirectory(Path.of(System.getProperty("user.dir")), location);
    }

    static Path findDirectory(Path workingDirectory, Path codeLocation) throws IOException {
        // Prefer the project that supplied this class, then the launch location.
        for (Path start : new Path[]{codeLocation, workingDirectory}) {
            if (start == null) continue;
            Path path = start.toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) path = path.getParent();
            for (int level = 0; path != null && level < 8; level++, path = path.getParent()) {
                if (Files.isRegularFile(path.resolve("vision/pose_service.py"))) return path.resolve("vision");
                if (Files.isRegularFile(path.resolve("pose_service.py"))) return path;
            }
        }
        throw new IOException("Camera files were not found beside the app. Keep the vision folder in the project "
                + "or set -Dnova.vision.dir to its full path.");
    }

    static List<String> python(Path root) throws IOException, InterruptedException {
        String configured = System.getProperty("nova.vision.python");
        if (configured != null && !configured.isBlank()) return List.of(configured);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        String executable = windows ? "Scripts/python.exe" : "bin/python";
        Path local = root.getParent().resolve(".venv").resolve(executable);
        // An existing project environment is authoritative; do not silently switch environments.
        if (Files.isRegularFile(local)) return List.of(local.toString());
        List<List<String>> candidates = new ArrayList<>();
        String active = System.getenv("VIRTUAL_ENV");
        if (active != null && Files.isRegularFile(Path.of(active).resolve(executable)))
            candidates.add(List.of(Path.of(active).resolve(executable).toString()));
        if (windows) {
            candidates.add(List.of("py", "-3.11"));
            candidates.add(List.of("py", "-3.12"));
        }
        candidates.add(List.of("python3")); candidates.add(List.of("python"));
        List<String> available = null;
        for (List<String> candidate : candidates) {
            List<String> command = new ArrayList<>(candidate);
            command.addAll(List.of("-c", "import sys,importlib.util; "
                    + "print('NOVA_PYTHON', int(importlib.util.find_spec('cv2') is not None)); "
                    + "sys.exit(0 if (3,9) <= sys.version_info[:2] <= (3,12) else 2)"));
            Process probe = null;
            try {
                probe = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!probe.waitFor(3, TimeUnit.SECONDS) || probe.exitValue() != 0) continue;
                String output = new String(probe.getInputStream().readNBytes(256), StandardCharsets.UTF_8);
                if (!output.contains("NOVA_PYTHON")) continue;
                if (output.contains("NOVA_PYTHON 1")) return candidate;
                if (available == null) available = candidate;
            } catch (IOException ignored) { /* Try the next launcher. */ }
            finally { if (probe != null && probe.isAlive()) probe.destroyForcibly(); }
        }
        if (available != null) return available;
        throw new IOException("Python 3.9–3.12 was not found. Install 64-bit Python 3.11, restart the app, "
                + "then click Set up capture. You can also set -Dnova.vision.python to your interpreter.");
    }

    static ProcessBuilder worker(Path root, String script, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(python(root));
        command.addAll(List.of("-u", root.resolve(script).toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.getParent().toFile());
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        return builder;
    }
}
