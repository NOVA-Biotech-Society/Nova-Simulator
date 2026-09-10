"""Managed camera worker. NDJSON over private stdin/stdout; never opens a network port.

Frames contain the original JPEG and matching landmarks from that exact image.
All dependency/model loading is lazy. Camera-only mode needs OpenCV alone.
"""
import argparse
import base64
import json
import math
import pathlib
import queue
import sys
import threading
import time

PROTOCOL_VERSION = 1


def emit(message):
    print(json.dumps({"version": PROTOCOL_VERSION, **message}, allow_nan=False,
                     separators=(",", ":")), flush=True)


def read_commands(commands):
    try:
        for line in sys.stdin:
            if len(line) > 4096:
                continue
            try:
                command = json.loads(line)
                if isinstance(command, dict) and command.get("type") in {"pose", "stop"}:
                    commands.put(command)
            except (ValueError, TypeError):
                continue
    finally:
        commands.put({"type": "stop"})


def pack_landmarks(landmarks):
    result = []
    for point in landmarks:
        values = [point.x, point.y, point.z, point.visibility, point.presence]
        if not all(value is not None and math.isfinite(value) for value in values):
            return []
        result.append(dict(zip(("x", "y", "z", "visibility", "presence"), values)))
    return result if len(result) == 33 else []


def create_landmarker(model):
    if not model.is_file():
        raise FileNotFoundError("Pose model missing. Run python vision/setup_model.py first.")
    import mediapipe as mp
    options = mp.tasks.vision.PoseLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=str(model)),
        running_mode=mp.tasks.vision.RunningMode.VIDEO,
        num_poses=1, min_pose_detection_confidence=0.6,
        min_pose_presence_confidence=0.6, min_tracking_confidence=0.6)
    return mp, mp.tasks.vision.PoseLandmarker.create_from_options(options)


def open_camera(cv2, camera_index):
    # Windows' default MSMF backend can open a device without delivering images.
    # Validate an actual frame and release each failed backend before retrying.
    backends = [(cv2.CAP_DSHOW, "DirectShow"), (cv2.CAP_MSMF, "Media Foundation")] \
        if sys.platform == "win32" else [(cv2.CAP_ANY, "system")]
    attempts = []
    for backend, name in backends:
        capture = None
        emit({"type": "status", "state": "opening", "message": f"Opening camera {camera_index} ({name})"})
        try:
            capture = cv2.VideoCapture(camera_index, backend)
            if not capture.isOpened():
                attempts.append(f"{name}: device unavailable")
                continue
            # Keep the device's native format: forcing 720p/30 can break working cameras.
            deadline = time.monotonic() + 4
            while time.monotonic() < deadline:
                ok, frame = capture.read()
                if ok and frame is not None and frame.size > 0:
                    result = capture, frame
                    capture = None  # Ownership passes to run(), including cleanup.
                    return result
                time.sleep(0.05)
            attempts.append(f"{name}: no frames received")
        except Exception as exc:
            attempts.append(f"{name}: {exc}")
        finally:
            if capture is not None:
                capture.release()
    raise RuntimeError(f"Cannot open camera {camera_index}. {'; '.join(attempts)}. "
                       "Close other camera apps, allow camera access for desktop apps in OS settings, "
                       "and try another device index in Capture setup.")


def run(camera_index, model):
    try:
        import cv2
    except ImportError as exc:
        raise RuntimeError("OpenCV is unavailable in this Python environment. "
                           "Click Set up capture in the app, then enable the camera again. "
                           f"({exc})") from exc
    commands = queue.Queue(maxsize=32)
    threading.Thread(target=read_commands, args=(commands,), daemon=True).start()
    capture = None
    landmarker = None
    mp = None
    try:
        capture, first_frame = open_camera(cv2, camera_index)
        first_captured_ms = time.time_ns() // 1_000_000
        emit({"type": "status", "state": "camera", "message": "Camera ready"})
        frame_id = 0
        previous_timestamp = 0
        failures = 0
        while True:
            while not commands.empty():
                command = commands.get_nowait()
                if command["type"] == "stop":
                    return
                if command.get("enabled") is True and landmarker is None:
                    emit({"type": "status", "state": "loading", "message": "Loading MediaPipe"})
                    try:
                        mp, landmarker = create_landmarker(model)
                    except Exception as exc:
                        emit({"type": "status", "state": "pose_error", "message": str(exc)})
                elif command.get("enabled") is False and landmarker is not None:
                    landmarker.close()
                    landmarker = None
                    emit({"type": "status", "state": "camera", "message": "Camera ready"})
            started = time.monotonic()
            if first_frame is not None:
                ok, frame = True, first_frame
                first_frame = None
                captured_ms = first_captured_ms
            else:
                ok, frame = capture.read()
                captured_ms = time.time_ns() // 1_000_000
            if not ok:
                failures += 1
                if failures >= 10:
                    raise RuntimeError("Camera disconnected or stopped delivering frames.")
                time.sleep(0.03)
                continue
            failures = 0
            # Limit transport size while retaining the camera's actual aspect ratio.
            height, width = frame.shape[:2]
            scale = min(1.0, 960 / width, 720 / height)
            if scale < 1:
                frame = cv2.resize(frame, (round(width * scale), round(height * scale)))
            height, width = frame.shape[:2]
            landmarks, world_landmarks = [], []
            inference_ms = 0.0
            if landmarker is not None:
                inference_start = time.monotonic()
                timestamp = max(previous_timestamp + 1, time.monotonic_ns() // 1_000_000)
                previous_timestamp = timestamp
                try:
                    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    result = landmarker.detect_for_video(
                        mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), timestamp)
                    if result.pose_landmarks:
                        landmarks = pack_landmarks(result.pose_landmarks[0])
                        world_landmarks = pack_landmarks(result.pose_world_landmarks[0])
                except Exception as exc:
                    landmarker.close()
                    landmarker = None
                    emit({"type": "status", "state": "pose_error", "message": str(exc)})
                inference_ms = (time.monotonic() - inference_start) * 1000
            ok, jpeg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
            if not ok:
                continue
            emit({"type": "frame", "frameId": frame_id, "timestampMs": captured_ms,
                  "width": width, "height": height, "poseEnabled": landmarker is not None,
                  "inferenceMs": inference_ms, "landmarks": landmarks,
                  "worldLandmarks": world_landmarks,
                  "jpeg": base64.b64encode(jpeg).decode("ascii")})
            frame_id += 1
            time.sleep(max(0, 1 / 30 - (time.monotonic() - started)))
    finally:
        if landmarker is not None:
            landmarker.close()
        if capture is not None:
            capture.release()


def main():
    import capture_runtime
    if capture_runtime.windows_arm():
        try:
            return capture_runtime.forward_to_compatible_worker(pathlib.Path(__file__).parent,
                                                               "pose_service.py", sys.argv[1:])
        except Exception as exc:
            emit({"type": "error", "message": str(exc)})
            return 1
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--model", type=pathlib.Path,
                        default=pathlib.Path(__file__).parent / "models/pose_landmarker_lite.task")
    args = parser.parse_args()
    try:
        run(args.camera, args.model)
    except (BrokenPipeError, KeyboardInterrupt):
        pass
    except Exception as exc:
        emit({"type": "error", "message": f"{type(exc).__name__}: {exc}"})
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
