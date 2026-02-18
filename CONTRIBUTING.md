# Contributing to Aura

Thank you for your interest in contributing to Aura! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/yourusername/aura-android/issues)
2. If not, create a new issue with:
   - A clear, descriptive title
   - Steps to reproduce the bug
   - Expected vs actual behavior
   - Screenshots (if applicable)
   - Device/OS information
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
   - Follow the code style guidelines
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
- Extract reusable components
- Follow Material Design 3 guidelines

### File Naming

- Use PascalCase for classes and composables: `HomeScreen.kt`
- Use camelCase for functions and variables: `onItemClick`
- Use descriptive names that indicate purpose

### Architecture

- Follow MVVM pattern
- Keep business logic separate from UI
- Use dependency injection where appropriate
- Write unit tests for business logic

## Development Setup

1. Clone your fork:
   ```bash
   git clone https://github.com/yourusername/aura-android.git
   ```

2. Open in Android Studio

3. Sync Gradle dependencies

4. Run the app to verify setup

## Testing

- Write unit tests for new features
- Test on multiple Android versions (API 26+)
- Test on different screen sizes
- Ensure no memory leaks

## Review Process

1. All PRs require at least one approval
2. CI checks must pass
3. Code review feedback will be addressed
4. Maintainers will merge approved PRs

## Questions?

Feel free to open an issue for questions or reach out to maintainers.

Thank you for contributing to Aura! 🎉
