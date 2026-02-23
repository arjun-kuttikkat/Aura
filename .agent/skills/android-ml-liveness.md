---
name: Android NPU Face Liveness & Mirror Ritual
description: Integrating Google ML Kit or local TFLite models for facial detection and liveness confirmation.
---

# The Mirror Ritual (Liveness Check)

## 1. CameraX Integration
Use AndroidX CameraX to capture frames in real-time smoothly without blocking the UI.
- Use the `ImageAnalysis` use case to process frames.

## 2. Face Detection & Liveness
Integrate machine learning (e.g., `com.google.mlkit:face-detection`).
- Look for face presence, eye open probability, and head euler angles (movement) to confirm liveness—ensuring it is a real person and not a printed photo.

## 3. Secure Hardware Execution
Ensure inference models run locally on the device for privacy and speed, leveraging the Seeker's NPU capabilities where possible.
- Upon successful validation, trigger the Seed Vault to sign a "Proof of Life" message.
