"""Project-owned Windows ARM compatibility runtime; all downloads require explicit setup."""
import hashlib
import pathlib
import subprocess
import sys
import sysconfig
import tempfile
import urllib.request
import zipfile

# Official CPython embeddable distribution and PyPA wheel, verified before extraction.
# Sources and upgrade procedure are documented in vision/README.md.
PYTHON_VERSION = "3.13.15"
PYTHON_URL = f"https://www.python.org/ftp/python/{PYTHON_VERSION}/python-{PYTHON_VERSION}-embed-amd64.zip"
PYTHON_SHA256 = "d1f04d990aee1253d8569e8e5104e30fa9f5fa830899f14843448872d936a2cf"
PIP_URL = "https://files.pythonhosted.org/packages/b7/3f/945ef7ab14dc4f9d7f40288d2df998d1837ee0888ec3659c813487572faa/pip-25.2-py3-none-any.whl"
PIP_SHA256 = "6d67a2b4e7f14d8b31b8b52648866fa717f45a1eb70e83002f4331d07e953717"


def windows_arm():
    # sysconfig describes this interpreter; platform.machine may describe the ARM
    # host even while an x64 interpreter is running under Windows emulation.
    return sys.platform == "win32" and sysconfig.get_platform().replace("_", "-").lower() == "win-arm64"


def compatibility_directory(vision):
    return pathlib.Path(vision).resolve().parent / ".nova-capture" / f"python-{PYTHON_VERSION}-x64"


def requirements_digest(vision):
    return hashlib.sha256((pathlib.Path(vision) / "requirements.txt").read_bytes()).hexdigest()


def compatible_python(vision):
    directory = compatibility_directory(vision)
    ready = directory / "ready.sha256"
    python = directory / "python.exe"
    if not python.is_file() or not ready.is_file() or ready.read_text().strip() != requirements_digest(vision):
        raise RuntimeError("Windows ARM capture needs its compatible runtime. Click Set up capture first. "
                           "Your installed ARM Python does not need to be removed.")
    return python


def download_verified(url, target, expected_digest, maximum_bytes=32 * 1024 * 1024):
    digest = hashlib.sha256()
    total = 0
    try:
        with urllib.request.urlopen(url, timeout=60) as response, pathlib.Path(target).open("wb") as output:
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > maximum_bytes:
                    raise ValueError("Runtime download exceeded the expected size")
                digest.update(chunk)
                output.write(chunk)
        if digest.hexdigest() != expected_digest:
            raise ValueError("Runtime download failed its SHA-256 integrity check")
    except Exception:
        pathlib.Path(target).unlink(missing_ok=True)
        raise


def extract_archive(archive_path, target):
    target = pathlib.Path(target).resolve()
    with zipfile.ZipFile(archive_path) as archive:
        for entry in archive.infolist():
            path = pathlib.PurePosixPath(entry.filename.replace("\\", "/"))
            if path.is_absolute() or ".." in path.parts or ":" in entry.filename:
                raise ValueError("Invalid runtime archive path")
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Runtime archive may not contain symbolic links")
        if archive.testzip() is not None:
            raise ValueError("Runtime archive is corrupt")
        archive.extractall(target)


def prepare_compatibility_runtime(vision):
    directory = compatibility_directory(vision)
    python = directory / "python.exe"
    if python.is_file() and (directory / "archive.sha256").is_file():
        if (directory / "archive.sha256").read_text().strip() == PYTHON_SHA256:
            return python
        raise RuntimeError("The existing compatibility runtime has a different version. See vision/README.md.")
    if directory.exists():
        raise RuntimeError("An incomplete compatibility runtime exists. Rename that folder and run setup again: " + str(directory))
    directory.parent.mkdir(parents=True, exist_ok=True)
    print("Preparing a separate Python 3.13 x64 runtime for Windows ARM emulation…", flush=True)
    with tempfile.TemporaryDirectory(prefix="prepare-", dir=directory.parent) as temporary:
        temporary = pathlib.Path(temporary)
        payload = temporary / "runtime"
        payload.mkdir()
        download_verified(PYTHON_URL, temporary / "python.zip", PYTHON_SHA256)
        extract_archive(temporary / "python.zip", payload)
        download_verified(PIP_URL, temporary / "pip.whl", PIP_SHA256)
        extract_archive(temporary / "pip.whl", payload / "Lib/site-packages")
        # Isolated search path: only bundled stdlib, project packages and vision scripts.
        (payload / "python313._pth").write_text(
            "python313.zip\n.\nLib/site-packages\n../../vision\nimport site\n", encoding="utf-8")
        if not (payload / "python.exe").is_file() or not (payload / "python313.dll").is_file():
            raise ValueError("The runtime archive is missing Python")
        (payload / "archive.sha256").write_text(PYTHON_SHA256, encoding="ascii")
        payload.rename(directory)
    return python


def forward_to_compatible_worker(vision, script, arguments):
    """Keep the same private pipes; Java owns and terminates this process tree."""
    python = compatible_python(vision)
    return subprocess.call([str(python), "-X", "utf8", "-u", str(pathlib.Path(vision) / script), *arguments])
