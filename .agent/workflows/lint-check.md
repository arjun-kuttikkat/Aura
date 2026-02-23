---
description: Check Gradle dependencies and lint issues for the Aura app
---

# Lint Check Workflow

This workflow runs the Android Lint tool via Gradle to identify deprecated code, potential bugs, or XML layout issues.

1. Run the Gradle lint command.
// turbo
```bash
./gradlew lintDebug
```

2. Analyze the output for any severe warnings or errors that need to be addressed before continuing development.
