---
description: Run unit tests for the Aura Android app
---

# Test App Workflow

This workflow executes all local JVM unit tests to verify that the business logic (e.g., Solana RPC client logic, cryptography) functions correctly without needing a full device emulator.

1. Run the test suite.
// turbo
```bash
./gradlew testDebugUnitTest
```

2. Analyze the output to see which specific tests failed and inspect the relevant source code.
