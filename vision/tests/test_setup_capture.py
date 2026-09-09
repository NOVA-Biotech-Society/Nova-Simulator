import contextlib
import importlib.util
import io
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("setup_capture", pathlib.Path(__file__).parents[1] / "setup_capture.py")
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)


class CaptureSetupTests(unittest.TestCase):
    def test_installs_only_into_the_project_environment(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            script = root / "vision/setup_capture.py"
            with patch.object(setup, "__file__", str(script)), \
                    patch.object(setup.sys, "version_info", (3, 11, 0)), \
                    patch.object(setup.venv, "EnvBuilder") as builder, \
                    patch.object(setup.subprocess, "run") as run, contextlib.redirect_stdout(io.StringIO()):
                setup.main()
            builder.return_value.create.assert_called_once_with(root / ".venv")
            commands = [call.args[0] for call in run.call_args_list]
            self.assertEqual(len(commands), 3)
            self.assertTrue(all(pathlib.Path(command[0]).is_relative_to(root / ".venv") for command in commands))
            self.assertEqual(commands[0][1:4], ["-m", "pip", "install"])
            self.assertTrue(all(call.kwargs["check"] for call in run.call_args_list))

    def test_existing_environment_is_not_recreated(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            python = root / ".venv" / ("Scripts/python.exe" if setup.sys.platform == "win32" else "bin/python")
            python.parent.mkdir(parents=True)
            python.write_text("existing environment")
            with patch.object(setup, "__file__", str(root / "vision/setup_capture.py")), \
                    patch.object(setup.sys, "version_info", (3, 11, 0)), \
                    patch.object(setup.venv, "EnvBuilder") as builder, \
                    patch.object(setup.subprocess, "run"), contextlib.redirect_stdout(io.StringIO()):
                setup.main()
            builder.assert_not_called()
            self.assertEqual(python.read_text(), "existing environment")

    def test_failed_install_does_not_claim_success_or_download_model(self):
        output = io.StringIO()
        with patch.object(setup.sys, "version_info", (3, 11, 0)), \
                patch.object(setup.venv, "EnvBuilder"), \
                patch.object(setup.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "pip")) as run, \
                contextlib.redirect_stdout(output):
            with self.assertRaises(subprocess.CalledProcessError):
                setup.main()
        self.assertEqual(run.call_count, 1)
        self.assertNotIn("setup complete", output.getvalue())


if __name__ == "__main__":
    unittest.main()
