import contextlib
import importlib.util
import io
import json
import math
import pathlib
import queue
import types
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("pose_service", pathlib.Path(__file__).parents[1] / "pose_service.py")
service = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(service)


class PoseServiceTests(unittest.TestCase):
    def test_import_does_not_load_camera_or_model(self):
        self.assertNotIn("cv2", service.__dict__)
        self.assertNotIn("mediapipe", service.__dict__)

    def test_commands_ignore_invalid_input_and_stop_on_parent_disconnect(self):
        commands = queue.Queue()
        text = 'invalid\n[]\n{"type":"pose","enabled":true}\n{"type":"unknown"}\n'
        with patch.object(service.sys, "stdin", io.StringIO(text)):
            service.read_commands(commands)
        self.assertEqual(commands.get_nowait(), {"type": "pose", "enabled": True})
        self.assertEqual(commands.get_nowait(), {"type": "stop"})
        self.assertTrue(commands.empty())

    def test_landmarks_preserve_visibility_and_presence(self):
        points = [types.SimpleNamespace(x=.2, y=.3, z=-.1, visibility=.9, presence=.8) for _ in range(33)]
        packed = service.pack_landmarks(points)
        self.assertEqual(len(packed), 33)
        self.assertEqual(packed[0]["presence"], .8)
        points[0].x = math.nan
        self.assertEqual(service.pack_landmarks(points), [])

    def test_partial_skeleton_is_invalid(self):
        self.assertEqual(service.pack_landmarks([]), [])
        point = types.SimpleNamespace(x=0, y=0, z=0, visibility=1, presence=1)
        self.assertEqual(service.pack_landmarks([point]), [])

    def test_emitter_uses_versioned_single_line_json(self):
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            service.emit({"type": "status", "message": "camera\nready"})
        self.assertEqual(len(stream.getvalue().splitlines()), 1)
        self.assertEqual(json.loads(stream.getvalue())["version"], 1)

    def test_model_missing_fails_before_mediapipe_import(self):
        with self.assertRaisesRegex(FileNotFoundError, "setup_model"):
            service.create_landmarker(pathlib.Path("/missing/nova-test-pose-model.task"))

    def test_camera_is_released_when_open_fails(self):
        capture = types.SimpleNamespace(isOpened=lambda: False, release=lambda: released.append(True))
        cv2 = types.SimpleNamespace(VideoCapture=lambda index: capture)
        released = []
        with patch.dict("sys.modules", {"cv2": cv2}), patch.object(service, "read_commands", lambda q: None):
            with self.assertRaisesRegex(RuntimeError, "Cannot open camera"):
                service.run(0, pathlib.Path("missing"))
        self.assertEqual(released, [True])

    def test_camera_only_emits_matched_frame_and_stops_cleanly(self):
        released, output = [], []
        controls = queue.Queue()
        frame = types.SimpleNamespace(shape=(720, 960, 3))
        capture = types.SimpleNamespace(isOpened=lambda: True, set=lambda *args: None,
                                        read=lambda: (True, frame), release=lambda: released.append(True))
        cv2 = types.SimpleNamespace(VideoCapture=lambda index: capture, CAP_PROP_FRAME_WIDTH=1,
                                    CAP_PROP_FRAME_HEIGHT=2, CAP_PROP_FPS=3, CAP_PROP_BUFFERSIZE=4,
                                    IMWRITE_JPEG_QUALITY=5, imencode=lambda *args: (True, b"\xff\xd8\xff\xd9"))

        def receive(packet):
            output.append(packet)
            if packet["type"] == "frame":
                controls.put({"type": "stop"})

        with patch.dict("sys.modules", {"cv2": cv2}), patch.object(service.queue, "Queue", return_value=controls), \
                patch.object(service, "read_commands", lambda q: None), patch.object(service, "emit", receive):
            service.run(0, pathlib.Path("missing"))
        packet = output[-1]
        self.assertEqual(packet["type"], "frame")
        self.assertFalse(packet["poseEnabled"])
        self.assertEqual(packet["landmarks"], [])
        self.assertEqual((packet["width"], packet["height"]), (960, 720))
        self.assertEqual(packet["frameId"], 0)
        self.assertGreater(packet["timestampMs"], 0)
        self.assertEqual(released, [True])

    def test_missing_model_keeps_camera_preview_available(self):
        released, output = [], []
        controls = queue.Queue()
        controls.put({"type": "pose", "enabled": True})
        frame = types.SimpleNamespace(shape=(720, 960, 3))
        capture = types.SimpleNamespace(isOpened=lambda: True, set=lambda *args: None,
                                        read=lambda: (True, frame), release=lambda: released.append(True))
        cv2 = types.SimpleNamespace(VideoCapture=lambda index: capture, CAP_PROP_FRAME_WIDTH=1,
                                    CAP_PROP_FRAME_HEIGHT=2, CAP_PROP_FPS=3, CAP_PROP_BUFFERSIZE=4,
                                    IMWRITE_JPEG_QUALITY=5, imencode=lambda *args: (True, b"\xff\xd8\xff\xd9"))

        def receive(packet):
            output.append(packet)
            if packet["type"] == "frame":
                controls.put({"type": "stop"})

        with patch.dict("sys.modules", {"cv2": cv2}), patch.object(service.queue, "Queue", return_value=controls), \
                patch.object(service, "read_commands", lambda q: None), patch.object(service, "emit", receive):
            service.run(0, pathlib.Path("/missing/nova-test-pose-model.task"))
        self.assertTrue(any(packet.get("state") == "pose_error" for packet in output))
        self.assertEqual(output[-1]["type"], "frame")
        self.assertFalse(output[-1]["poseEnabled"])
        self.assertEqual(released, [True])


if __name__ == "__main__":
    unittest.main()
