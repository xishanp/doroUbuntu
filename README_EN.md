# doroUbuntu

English | [简体中文](README.md)

## Overview

doroUbuntu is a complete Linux environment for Android devices. It runs Ubuntu 24.04 and provides an XFCE desktop through Termux:X11.

The project integrates the Turnip and Zink graphics stack. Applications inherit GPU acceleration by default, while the desktop core uses a stable rendering path. Adreno 740 has been verified, and Firefox keeps graphics acceleration enabled.

## Features

- Complete Ubuntu 24.04 user space
- XFCE desktop environment
- Termux:X11 graphics output
- Turnip graphics driver
- Zink OpenGL acceleration
- GPU acceleration for applications
- Graphics acceleration in Firefox
- One-click offline deployment
- Visible installation tasks
- Installation watchdog protection
- fastfetch terminal welcome screen

## Desktop Experience

- macOS-inspired layout
- WhiteSur dark theme
- WhiteSur icon pack
- Red, yellow, and green window buttons
- Window buttons on the left
- Plank dock
- Dock icon zoom on hover
- Apple-style menu icon

The default dock includes:

- Firefox
- Spark App Store


## Development Environment

Common development tools are preinstalled:

- Python
- GCC
- CMake
- Java
- Rust
- Go
- Jupyter

## Requirements

- An Android device
- At least 10 GB of free storage
- A reasonably powerful device
- A compatible GPU for acceleration
- About 30 minutes for first deployment

## Installation

1. Download and install the APK.
2. Open doroUbuntu.
3. Start one-click deployment.
4. Wait for all active tasks to finish.
5. Follow the in-app prompt to launch the desktop.

The installer does not display fake progress. Active tasks remain visible and are protected by a watchdog. Do not force-stop the app or clear it from the background during deployment.

## Device Compatibility

Currently verified:

- Qualcomm Adreno 740
- Turnip graphics driver
- Zink graphics acceleration

Other GPUs and Android versions have not been fully tested. Mali GPUs, older Adreno GPUs, customized Android systems, and strict background limits may affect installation, startup, or rendering.

Device reports are welcome. Please include the device model, SoC, Android version, results, and logs.

## Known Issues and Risks

- First deployment takes time
- The APK and offline assets are large
- GPU compatibility varies by device
- Background restrictions may interrupt deployment
- Insufficient storage may cause failure
- Some Android systems may restrict file access
- Debug builds are not stable releases

Back up important data before testing. Keep error messages and logs when reporting a problem.

## Current Version

Version: `1.1.9Debug`

APK: `doroUbuntu-1.1.9Debug.apk`

## Bug Reports

Please include:

- Device model
- SoC and GPU
- Android version
- System ROM
- Reproduction steps
- Screenshots or logs

## License

License information is pending. Before public distribution, verify the licenses of the source code, themes, icons, drivers, and bundled software.
