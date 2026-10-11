# Offline speech models

These six unmodified, upstream pretrained Vosk archives are application assets, not build
outputs. They are shipped in the APK and are never downloaded by the application or Gradle.
The application extracts only the selected language into its private, non-backed-up storage.

All six models are published under **Apache License 2.0** by Alpha Cephei.
See `LICENSE.txt`, the upstream [model catalogue](https://alphacephei.com/vosk/models), and
`manifest.json` for the exact names, origins and SHA-256 checksums.

To acquire the pinned archives outside the build: `python scripts/prepare-voice-models.py`.
Review any manifest change and ship the archives alongside it. No signing keys or personal
audio are included. Combined compressed size: about 240 MiB. The largest uncompressed model
is about 92 MiB; only the selected model is loaded into memory.
