<h1 align="center">CameraReset</h1>

<p align="center">
  A lightweight, headless Android system utility designed to evict hung camera sessions, release leaked HAL locks, and trace offending processes via Quick Settings.
</p>

<p align="center">
  <a href="https://github.com/WhyICantCreateMyName/CameraServer-reset-for-android/releases/latest"><img src="https://img.shields.io/badge/version-1.0.0-blue" alt="Version"></a>
  <a href="#compatibility"><img src="https://img.shields.io/badge/Platform-Android%208.0%20--%2015%2B-green?logo=android" alt="Platform"></a>
  <img src="https://img.shields.io/badge/Interface-Headless%20%2F%20TileService-purple" alt="Interface">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue" alt="License"></a>
</p>

---

## Overview

On several Android OEM skins—particularly Samsung One UI running on modern Qualcomm SoCs—the camera subsystem periodically freezes, displaying errors such as **"Cannot connect to camera"** or `CAMERA_IN_USE` across stock camera applications, third-party QR scanners, and banking verification modules.

### Underlying Mechanism
* **Leaked Binder Tokens:** Applications interacting with the Android Camera2 API (`CameraManager`) that terminate abnormally or fail to invoke `CameraDevice.close()` often leave lingering Binder references inside `cameraserver`.
* **HAL Deadlock:** Under specific timing conditions, driver implementations (such as Qualcomm's CamX HAL `vendor.samsung.hardware.camera.provider`) fail stream allocation and abort, leaving hardware pipelines unresponsive to lower-priority clients.
* **Eviction Behavior:** AOSP `CameraService` implements client priority scheduling. Initiating a video call (e.g., Messenger or Zalo) forces higher-priority client binding, triggering internal `connectHelper` eviction logic that sweeps stale tokens and reinitializes the HAL stream.

**CameraReset** automates this eviction process directly on the device with a single tap, eliminating the need for USB-tethered ADB terminal commands or device reboots.

---

## Architecture & Features

| Component | Implementation | Function |
| --- | --- | --- |
| **System Interface** | `TileService` | Headless execution triggered directly from the Quick Settings / Quick Panel. Zero persistent background memory overhead. |
| **Session Diagnostics** | `dumpsys media.camera` | Identifies active clients, listener PIDs, and the latest 20 service connect/disconnect/reject events. |
| **Process Termination** | Shizuku IPC (`am force-stop`) | Terminates identified rogue background packages holding the camera lock. |
| **Hardware Eviction Pulse** | `Camera2 API` | Acquires a momentary high-priority camera session and closes cleanly to recycle the underlying HAL stream. |
| **Stream Cycle** | `cmd media.camera set-camera-mute` | Toggles camera stream muting to flush pending hardware requests. |
| **Local Audit Trail** | Direct storage logging | Persists diagnostic dumps and summary lines to `/sdcard/CameraLogs/` for post-incident debugging. |

---

## Compatibility

| System | Supported Versions | Notes |
| --- | --- | --- |
| **Android Core** | Android 8.0 (Oreo) – Android 15 / 16 / 17 | Standard AOSP `TileService` and `CameraManager` implementation. |
| **Samsung One UI** | One UI 1.0 – One UI 9.0+ | Fully compatible with both Exynos and Qualcomm HAL providers. |
| **Other OEMs** | Google Pixel, Xiaomi (HyperOS/MIUI), OnePlus/Oppo (ColorOS), Vivo | Universal compatibility using standard AOSP subsystems. |
| **Privilege Backend** | Shizuku v13+ | Required for `dumpsys` client analysis and `am force-stop` operations. Hardware pulse operates independently without Shizuku. |

---

## Installation

1. Download `CameraReset-v1.0.0.apk` from [Releases](https://github.com/WhyICantCreateMyName/CameraServer-reset-for-android/releases/latest).
2. Install the APK on your device:
   ```bash
   adb install -r CameraReset-v1.0.0.apk
   ```
3. *(Recommended for rootless diagnostics)* Install [Shizuku](https://shizuku.rikka.app/) and enable **CameraReset** under **Authorized applications**.

> **Note:** CameraReset is built as a **100% UI-less / headless** utility. It intentionally does not register an `android.intent.category.LAUNCHER` activity and will not produce an icon in your app drawer. Management is handled via Quick Settings and the system App Management settings.

---

## Usage

### 1. Adding the Quick Settings Tile
1. Expand the Quick Panel / Notification Shade by swiping down twice.
2. Tap the **Edit (Pencil)** icon to modify available tiles.
3. Locate **Reset Camera** and drag it to your active panel.

### 2. Handling a Camera Lockup
When an application reports a camera connection failure:
1. **Do not reboot the phone.** Rebooting flushes the current process state and destroys diagnostic evidence.
2. Swipe down the Quick Panel and tap **Reset Camera**.
3. The device will provide haptic confirmation while the utility:
   - Dumps active camera client states.
   - Force-terminates hung packages (if authorized via Shizuku).
   - Pulses the hardware driver pipeline.
   - Writes diagnostic logs to internal storage.
4. Reopen your target camera application.

---

## Diagnostic Logs

Logs are written directly to internal storage at:
```text
/sdcard/CameraLogs/
├── history.txt                         # Append-only summary log
└── camera_reset_YYYYMMDD_HHmmss.log   # Detailed raw dumpsys and service events
```

### Log Structure (`camera_reset_*.log`):
* **Section 1: Active Camera Clients:** Lists the package name and PID holding the device lock.
* **Section 2: Service Events Log:** Recent chronological connection, disconnection, and rejection status codes.
* **Section 3: CameraService Worker Trace:** Worker thread lifecycle states.
* **Section 4: Raw Hardware State:** Detailed dumpsys capture of sensor IDs and stream configurations.

---

## Building from Source

### Prerequisites
* JDK 17
* Android SDK (Platform 35 / 36, Build-Tools 34.0.0+)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/WhyICantCreateMyName/CameraServer-reset-for-android.git
cd CameraServer-reset-for-android

# Build Debug APK
./gradlew assembleDebug

# Output artifact location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Disclaimer

This utility performs low-level camera hardware session cycling and process terminations. While it relies entirely on documented AOSP APIs and standard system commands, users are advised that force-stopping background applications may cause unsaved data in those specific applications to be discarded.
