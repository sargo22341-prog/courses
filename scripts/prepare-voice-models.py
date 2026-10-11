"""Acquire the licensed Vosk assets outside Gradle; never run during an app build."""

import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import time
import urllib.request
import zipfile


MODELS = {
    "fr": "vosk-model-small-fr-0.22",
    "en": "vosk-model-small-en-us-0.15",
    "de": "vosk-model-small-de-0.15",
    "es": "vosk-model-small-es-0.42",
    "it": "vosk-model-small-it-0.22",
    "pt": "vosk-model-small-pt-0.3",
}
ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets/voice-models"
MAX_ARCHIVE = 80 * 1024 * 1024


def download(url: str, target: Path) -> None:
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=60) as response, target.open("wb") as output:
                count = 0
                deadline = time.monotonic() + 180
                while count <= MAX_ARCHIVE and time.monotonic() < deadline:
                    chunk = response.read(64 * 1024)
                    if not chunk:
                        return
                    count += len(chunk)
                    output.write(chunk)
                raise ValueError("Model download exceeded its size or time limit")
        except (OSError, ValueError):
            if attempt == 2:
                raise


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    manifest = []
    with tempfile.TemporaryDirectory(prefix="courses-voice-") as temporary:
        for language, name in MODELS.items():
            url = f"https://alphacephei.com/vosk/models/{name}.zip"
            destination = ASSETS / f"{language}.zip"
            if not destination.exists():
                staged = Path(temporary) / f"{language}.zip"
                download(url, staged)
                with zipfile.ZipFile(staged) as archive:
                    if len(archive.infolist()) > 128 or archive.testzip() is not None:
                        raise ValueError(f"Invalid model archive: {name}")
                shutil.copyfile(staged, destination)
            manifest.append({"language": language, "name": name, "sha256": digest(destination), "url": url})
            print(f"{language}: {destination.stat().st_size / 1024 / 1024:.1f} MiB", flush=True)
    (ASSETS / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
