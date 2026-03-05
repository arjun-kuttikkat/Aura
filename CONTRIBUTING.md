# Contributing to Aura

Thank you for your interest in contributing to Aura! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/arjun-kuttikkat/Aura/issues)
2. If not, create a new issue with:
   - A clear, descriptive title
   - Steps to reproduce the bug
   - Expected vs actual behavior
   - Screenshots (if applicable)
   - Device/OS information and wallet app version
   - App version

### Suggesting Features

1. Check existing feature requests
2. Create a new issue with:
   - Clear description of the feature
   - Use case and benefits
   - Mockups or examples (if applicable)

### Pull Requests

1. **Fork the repository**
2. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**:
   - Follow the code style guidelines below
   - Write or update tests
   - Update documentation if needed
4. **Commit your changes**:
   ```bash
   git commit -m "Add: description of your changes"
   ```
   Use conventional commit messages:
   - `Add:` for new features
   - `Fix:` for bug fixes
   - `Update:` for updates to existing features
   - `Refactor:` for code refactoring
   - `Docs:` for documentation changes
5. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```
6. **Create a Pull Request**:
   - Provide a clear description
   - Reference related issues
   - Add screenshots for UI changes

## Code Style Guidelines

### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful variable and function names

### Compose Guidelines

- Keep composables small and focused
- Use `@Preview` annotations for preview functions
- Extract reusable components into `ui/components/`
- Follow Material Design 3 guidelines
- Use `remember` and `derivedStateOf` to avoid recomposition

### File Naming

- Use PascalCase for classes and composables: `HomeScreen.kt`
- Use camelCase for functions and variables: `onItemClick`
- Use descriptive names that indicate purpose

### Architecture

- **Single-Activity** with Jetpack Navigation Compose (17 destinations)
- **Repository pattern** — all data access through `AuraRepository`
- **StateFlow** for reactive state management
- Business logic in Repository / Manager classes, not in composables
- Edge Functions handle all server-side validation (NFC, AI, minting)

## Development Setup

1. Clone your fork:
   ```bash
   git clone https://github.com/<your-username>/Aura.git
   ```

2. Create `local.properties` with required keys (see README)

3. Open in Android Studio Ladybug+

4. Sync Gradle dependencies

5. Connect an NFC-capable Android device or emulator (API 26+)

6. Run the app to verify setup

## Edge Function Development

Edge Functions live in `supabase/functions/`. To develop locally:

```bash
supabase start                     # Start local Supabase
supabase functions serve <name>    # Hot-reload a single function
```

Each function is a standalone Deno module. Keep them stateless and idempotent.

## Testing

- Write unit tests for new features
- Test on multiple Android versions (API 26+)
- Test on different screen sizes
- Test wallet flows with both Phantom and Solflare
- Verify NFC flows on a physical device (emulators lack NFC)

## Review Process

1. All PRs require at least one approval
2. CI checks must pass
3. Code review feedback will be addressed
4. Maintainers will merge approved PRs

## Questions?

Feel free to open an issue for questions or reach out to maintainers.

Thank you for contributing to Aura!
