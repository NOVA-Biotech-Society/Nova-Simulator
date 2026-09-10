"""Explicit, app-invoked setup. Never opens a camera or modifies a global Python install."""
import pathlib
import subprocess
import sys
import sysconfig
import venv
import capture_runtime


def main():
    root = pathlib.Path(__file__).resolve().parent
    environment = root.parent / ".venv"
    python = environment / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    if not (3, 9) <= sys.version_info[:2] <= (3, 13):
        raise RuntimeError("Capture requires standard 64-bit Python 3.9–3.13.")
    if sysconfig.get_config_var("Py_GIL_DISABLED"):
        raise RuntimeError("Use standard CPython; experimental free-threaded Python is not supported by the capture wheels.")
    compatible = capture_runtime.windows_arm()
    target_options = []
    if compatible:
        python = capture_runtime.prepare_compatibility_runtime(root)
        (python.parent / "ready.sha256").unlink(missing_ok=True)
        target_options = ["--target", str(python.parent / "Lib/site-packages"), "--upgrade"]
    elif not python.is_file():
        print("Creating the project's Python environment…", flush=True)
        venv.EnvBuilder(with_pip=True).create(environment)
    print("Installing camera and MediaPipe packages. This can take several minutes…", flush=True)
    subprocess.run([str(python), "-m", "pip", "install", "--disable-pip-version-check",
                    "--only-binary=:all:", *target_options, "-r", str(root / "requirements.txt")], check=True)
    print("Downloading and checking the pose model…", flush=True)
    subprocess.run([str(python), str(root / "setup_model.py")], check=True)
    subprocess.run([str(python), str(root / "smoke_capture.py"), "--inside"], check=True)
    if compatible:
        (python.parent / "ready.sha256").write_text(capture_runtime.requirements_digest(root), encoding="ascii")
    print("Capture setup complete. Enable the camera to begin.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Setup failed: {exc}", flush=True)
        raise SystemExit(1)
