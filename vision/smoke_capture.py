"""Verify camera codecs and actual pose inference without accessing a camera."""
import argparse
import pathlib
import subprocess
import sys
import sysconfig

import capture_runtime


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inside", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--image", type=pathlib.Path, help="Optional known pose image for landmark validation")
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent
    if not args.inside:
        if capture_runtime.windows_arm():
            python = capture_runtime.compatible_python(root)
        else:
            python = root.parent / ".venv" / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
            if not python.is_file():
                python = pathlib.Path(sys.executable)
        return subprocess.call([str(python), "-X", "utf8", str(__file__), "--inside", *sys.argv[1:]])
    import cv2
    import numpy as np
    from pose_service import create_landmarker, pack_landmarks
    frame = cv2.imread(str(args.image)) if args.image else np.zeros((256, 256, 3), dtype=np.uint8)
    if frame is None:
        raise RuntimeError("Smoke-test image could not be decoded")
    ok, encoded = cv2.imencode(".jpg", frame)
    if not ok or cv2.imdecode(encoded, cv2.IMREAD_COLOR) is None:
        raise RuntimeError("Camera JPEG round-trip failed")
    mp, landmarker = create_landmarker(root / "models/pose_landmarker_lite.task")
    try:
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        result = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), 1)
        if args.image:
            if not result.pose_landmarks or len(pack_landmarks(result.pose_landmarks[0])) != 33:
                raise RuntimeError("Pose image did not produce 33 valid landmarks")
            if len(pack_landmarks(result.pose_world_landmarks[0])) != 33:
                raise RuntimeError("Pose image did not produce 33 valid world landmarks")
    finally:
        landmarker.close()
    print(f"Capture verified: Python {sys.version.split()[0]} / {sysconfig.get_platform()} / "
          f"MediaPipe {mp.__version__}; JPEG codec and VIDEO inference passed", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
