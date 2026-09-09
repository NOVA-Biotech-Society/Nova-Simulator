"""Explicit, app-invoked setup. Never opens a camera or modifies a global Python install."""
import pathlib
import subprocess
import sys
import venv


def main():
    root = pathlib.Path(__file__).resolve().parent
    environment = root.parent / ".venv"
    python = environment / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    if not (3, 9) <= sys.version_info[:2] <= (3, 12):
        raise RuntimeError("Capture requires Python 3.9–3.12. Use 64-bit Python 3.11 for this project.")
    if not python.is_file():
        print("Creating the project's Python environment…", flush=True)
        venv.EnvBuilder(with_pip=True).create(environment)
    print("Installing camera and MediaPipe packages. This can take several minutes…", flush=True)
    subprocess.run([str(python), "-m", "pip", "install", "--disable-pip-version-check",
                    "-r", str(root / "requirements.txt")], check=True)
    print("Downloading and checking the pose model…", flush=True)
    subprocess.run([str(python), str(root / "setup_model.py")], check=True)
    subprocess.run([str(python), "-c", "import cv2, mediapipe; "
                    "from mediapipe.tasks.python.vision import PoseLandmarker"], check=True)
    print("Capture setup complete. Enable the camera to begin.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Setup failed: {exc}", flush=True)
        raise SystemExit(1)
