# Antigravity Agent Studio (Android `arm64-v8a`)

Production-ready Android implementation (Kotlin, Jetpack Compose Material 3, Coroutines/Flow) for an on-device **Antigravity Agent Studio** APK.

## Architectural Layers

1. **Module 1 — Binary Asset Manager (`RuntimeInstaller.kt`)**:
   - Extracts bundled Node.js runtime (`node`), CLI binary (`antigravity`), and `busybox` from APK `assets/runtime/` into `context.filesDir/runtime/`.
   - Strict **SELinux & W^X (Write XOR Execute)** compliance (`context.filesDir` execution only).
   - Idempotent `.runtime_version` stamp checking + explicit POSIX `0755` (`setExecutable(true, false)` + `Os.chmod`) permissions.
2. **Module 2 — CLI Process Supervisor (`AntigravityProcessManager.kt`)**:
   - Spawns headless Node.js daemon (`antigravity serve --port 8765 --headless`) via `ProcessBuilder`.
   - Enforces memory containment (`--max-old-space-size=256`) for 6GB RAM Android devices.
   - Injects `HOME`, `TMPDIR`, `PATH` (`filesDir/runtime/bin`), `ANTIGRAVITY_PORT=8765`, and `NODE_ENV=production`.
   - Streams `stdout`/`stderr` asynchronously on `Dispatchers.IO` into a hot `SharedFlow<String>`.
   - Graceful shutdown via `SIGTERM` with timeout fallback to `SIGKILL`.
3. **Module 3 — Persistent Background Service (`AgentDaemonService.kt`)**:
   - Android `ForegroundService` configured with `foregroundServiceType="dataSync"` and `START_STICKY`.
   - Live ongoing notification reflecting engine state (`Starting`, `Listening on port 8765`, `Executing task`, `Error`).
   - Dual IPC bridge: Local Android `Binder` + localhost `127.0.0.1:8765` WebSocket/HTTP client.
4. **Module 4 — Adaptive Antigravity UI (`AntigravityStudioScreen.kt`)**:
   - Pure OLED Dark Theme (`#111215` Background, `#1A1B1E` Surfaces, `#2A2B2F` Borders).
   - Responsive `WindowWidthSizeClass` switching between **Phone Compact** (Navigation Drawer + Chat Console + ModalBottomSheet Terminal) and **Tablet Expanded** (3-Pane Desktop IDE layout).
5. **Module 5 — Gradle & Manifest Configurations**:
   - Strictly compiled for `arm64-v8a`, `minSdk = 29`, `targetSdk = 35`.
   - Uncompressed binary assets (`noCompress`) and `useLegacyPackaging = true`.

## Building the APK

```bash
./gradlew :app:assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`
