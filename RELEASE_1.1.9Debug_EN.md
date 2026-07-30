# doroUbuntu 1.1.9Debug

This public debug build has passed clean-install and offline-initialization acceptance.

## Highlights

- Ubuntu 24.04 offline environment
- XFCE and embedded Termux:X11
- PulseAudio sound bridge
- Landscape desktop by default
- Ubuntu icon on the startup screen
- Welcome page in the built-in terminal
- Clean desktop terminal startup
- Six repaired desktop launchers

## GPU Strategy

- Software rendering for the desktop shell
- Zink for application launchers
- Turnip and Mesa remain unchanged
- No global Zink override
- Wrapper: `/usr/local/bin/doro-gpu-run`

## Known Limitations

- This is still a Debug build
- Other GPUs are not fully verified
- Firefox uses software video decoding
- Experimental hardware decoding is not included
- APK and offline assets are large