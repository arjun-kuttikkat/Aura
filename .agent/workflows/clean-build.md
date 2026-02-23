---
description: Clean build cache and rebuild the Aura Android app from scratch
---

# Clean Build Workflow

This workflow is used when Gradle caches get corrupted, or when weird compilation errors occur that aren't related to recent code changes.

1. Clean the project and then build it.
// turbo
```bash
./gradlew clean assembleDebug
```

2. If the build succeeds, the cache was the issue. If it fails, analyze the new output to locate the actual error.
