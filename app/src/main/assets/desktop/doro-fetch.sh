#!/bin/sh
structure='Title:Separator:OS:Kernel:Uptime:Packages:Shell:Terminal:CPU:GPU:Memory:Colors'
columns=${COLUMNS:-$(tput cols 2>/dev/null || echo 80)}
case "$columns" in *[!0-9]*|'') columns=80 ;; esac
screen_width=$(xdpyinfo 2>/dev/null | sed -n 's/.*dimensions:[[:space:]]*\([0-9][0-9]*\)x.*/\1/p' | head -n 1)
case "$screen_width" in *[!0-9]*|'') screen_width=0 ;; esac

if [ "$screen_width" -ge 1200 ] || [ "$columns" -ge 100 ]; then
    exec fastfetch --logo ubuntu --structure "$structure"
fi

fastfetch --logo ubuntu --structure Logo
printf '\033[38;5;196m------------------------------------------\033[0m\n'
exec fastfetch --logo none --structure "$structure"
