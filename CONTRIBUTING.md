# Contributing

Thanks for contributing.

## Development Setup

1. Install Android Studio and SDK components:
   - Android SDK Platform 35
   - NDK (Side by side)
   - CMake
2. Open project and run Gradle sync.
3. Build once before making changes:
   ```bash
   ./gradlew :app:assembleDebug
   ```

## Scope for This Repository

- Java + XML + ViewBinding app code
- JNI interface definitions in Java
- Native inference integration in `app/src/main/cpp`
- Model catalog and download flow

## Pull Request Guidelines

1. Keep changes focused and minimal.
2. Do not add unrelated dependencies.
3. Keep model paths under:
   - `getExternalFilesDir(null)/models/<filename>.gguf`
4. Preserve existing JNI APIs when possible.
5. Include logs/screenshots for UI or runtime behavior changes.
6. Update `README.md` when user-facing behavior changes.

## Coding Guidelines

- Prefer clear, maintainable Java code.
- Avoid app-wide refactors unless necessary.
- Keep native changes aligned with upstream `llama.cpp` style.
- Add concise logs for critical state transitions (download/load/infer).

## Testing Checklist

Before opening a PR, verify:

1. `./gradlew :app:assembleDebug` succeeds.
2. App launches on an `arm64-v8a` emulator/device.
3. Model catalog loads in Model Store.
4. Download, cancel, select, delete flows work.
5. Chat can load selected model and return output.
6. No obvious regressions in logs.

## Reporting Issues

When filing issues, include:

- Device/emulator + ABI
- Android version / API level
- App version/commit hash
- Exact logs (`ModelStore`, `ModelDownload`, `ModelChat`, `llama-io-prompt`)
- Reproduction steps
