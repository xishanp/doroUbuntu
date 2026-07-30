# doroUbuntu

English | [简体中文](README.md)

## Overview

doroUbuntu is an offline Ubuntu desktop environment for Android. It integrates Ubuntu 24.04, XFCE, Termux:X11, and PulseAudio.

## Current Version

- Version: `1.1.9Debug`
- Status: clean-install acceptance completed
- Architecture: ARM64

## Features

- Offline Ubuntu 24.04 initialization
- XFCE desktop environment
- Embedded Termux:X11
- PulseAudio sound bridge
- Turnip and Zink graphics stack
- fastfetch terminal welcome page
- Landscape desktop by default
- 2.5-second startup screen

## GPU Strategy

The desktop shell uses software rendering. Application launchers use Zink through `/usr/local/bin/doro-gpu-run`. Turnip and Mesa remain unchanged. Zink is not forced globally because that causes a black screen.

```bash
doro-gpu-run glxinfo -B
```

Zink Vulkan, Turnip on Adreno 740, and hardware acceleration have been verified.

## Desktop Launchers

Firefox, Spark Store, VLC, LibreOffice, GIMP, and Thunar support launcher recovery and GPU-wrapper startup.

## Installation

1. Keep at least 10 GB of free storage.
2. Install the APK from the Releases page.
3. Open doroUbuntu and start initialization.
4. Wait until all offline tasks finish.
5. Follow the prompt to enter XFCE.

Do not force-stop the app or clear it from the background during deployment.

## Compatibility

Qualcomm Adreno 740 is currently verified. Other GPUs and Android systems are not fully tested. Firefox keeps software video decoding. Experimental MediaCodec decoding is not included.

## License

License information is being整理ed. Follow the respective licenses of the code, themes, icons, drivers, and bundled software before use or redistribution.