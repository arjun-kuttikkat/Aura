---
description: Debug the Aura Android app, capture logs, and write debug report
---

# Debug App Workflow

This workflow connects to the running device or emulator via ADB to read the Logcat output and capture fatal runtime exceptions.

1. Fetch the latest crash logs from the device.
// turbo
```bash
adb logcat -d -v threadtime -e "FATAL EXCEPTION|AndroidRuntime|aura"
```

2. Read the stacktrace from the terminal output.
3. Identify the Kotlin or XML file causing the crash during runtime and apply the necessary fix.
