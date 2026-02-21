---
description: Build the Aura Android app and check for compilation errors
---

# Build App Workflow

This workflow compiles the Android application using Gradle to catch syntax errors, missing dependencies, or build failures early.

1. Run the Gradle build command.
// turbo
```bash
./gradlew assembleDebug
```

2. If the command fails, analyze the terminal output to pinpoint the exact file and line number causing the error, and formulate a fix.
