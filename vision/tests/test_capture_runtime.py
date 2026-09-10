import io
import pathlib
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
import capture_runtime as runtime
import setup_capture as setup


class CaptureRuntimeTests(unittest.TestCase):
    def test_detects_interpreter_architecture_instead_of_host_architecture(self):
        with patch.object(runtime.sys, "platform", "win32"):
            for platform, expected in [("win-arm64", True), ("win-amd64", False), ("win32", False)]:
                with patch.object(runtime.sysconfig, "get_platform", return_value=platform):
                    self.assertEqual(runtime.windows_arm(), expected)

    def test_bad_download_is_removed_before_it_can_be_extracted(self):
        with tempfile.TemporaryDirectory() as folder:
            target = pathlib.Path(folder) / "runtime.zip"
            with patch.object(runtime.urllib.request, "urlopen", return_value=io.BytesIO(b"wrong bytes")):
                with self.assertRaisesRegex(ValueError, "integrity"):
                    runtime.download_verified("https://example.invalid/archive", target, "0" * 64)
            self.assertFalse(target.exists())

    def test_archive_cannot_write_outside_the_runtime(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            archive = root / "bad.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("../outside.py", "untrusted")
            with self.assertRaisesRegex(ValueError, "archive path"):
                runtime.extract_archive(archive, root / "runtime")
            self.assertFalse((root / "outside.py").exists())

    def test_windows_arm_setup_keeps_arm_environment_and_marks_only_success_ready(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder).resolve()
            vision = root / "vision"
            vision.mkdir()
            (vision / "requirements.txt").write_text("test requirements")
            directory = runtime.compatibility_directory(vision)
            directory.mkdir(parents=True)
            python = directory / "python.exe"
            python.write_text("test executable")
            original = sys.executable
            with patch.object(setup, "__file__", str(vision / "setup_capture.py")), \
                    patch.object(setup.sys, "version_info", (3, 13, 0)), \
                    patch.object(runtime, "windows_arm", return_value=True), \
                    patch.object(runtime, "prepare_compatibility_runtime", return_value=python), \
                    patch.object(setup.venv, "EnvBuilder") as builder, \
                    patch.object(setup.subprocess, "run") as run:
                setup.main()
            builder.assert_not_called()
            self.assertEqual(sys.executable, original)
            command = run.call_args_list[0].args[0]
            self.assertEqual(command[0], str(python))
            self.assertEqual(command[command.index("--target") + 1], str(directory / "Lib/site-packages"))
            self.assertIn("--only-binary=:all:", command)
            self.assertEqual(runtime.compatible_python(vision), python)
            (vision / "requirements.txt").write_text("new requirements")
            with self.assertRaisesRegex(RuntimeError, "Set up capture"):
                runtime.compatible_python(vision)

    def test_python_314_is_rejected(self):
        with patch.object(setup.sys, "version_info", (3, 14, 0)):
            with self.assertRaisesRegex(RuntimeError, "3.9–3.13"):
                setup.main()


if __name__ == "__main__":
    unittest.main()
