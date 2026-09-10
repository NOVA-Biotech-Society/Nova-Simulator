"""Download the versioned Google Pose Landmarker Lite model once, before capture."""
import pathlib
import urllib.request
import zipfile

MODEL_URL = ("https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
             "pose_landmarker_lite/float16/1/pose_landmarker_lite.task")


def main():
    target = pathlib.Path(__file__).parent / "models" / "pose_landmarker_lite.task"
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".download")
    try:
        with urllib.request.urlopen(MODEL_URL, timeout=60) as response:
            with temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
        if not zipfile.is_zipfile(temporary):
            raise ValueError("The download is not a MediaPipe task archive")
        with zipfile.ZipFile(temporary) as archive:
            if not {"pose_detector.tflite", "pose_landmarks_detector.tflite"} <= set(archive.namelist()):
                raise ValueError("The task archive is missing the pose models")
            if archive.testzip() is not None:
                raise ValueError("The task archive is corrupt")
        temporary.replace(target)
        print(f"Model ready: {target}")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
