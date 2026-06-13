# Contributing to YtReader

Thank you for your interest in contributing to YtReader! This document explains how to set up your development environment, run the project, and submit changes.

## Development setup

### Prerequisites

- JDK 11 (for the app module) and JDK 21 (for the extractor module, auto-installed by Gradle)
- Android Studio Ladybug or newer
- Android SDK with API 36 installed
- Git

### Clone and build

```bash
git clone https://github.com/deedeedev/YtReader.git
cd YtReader
./gradlew :app:assembleDebug
```

The first build downloads dependencies and may take a few minutes.

### Run the app

Open the project in Android Studio and run the `app` configuration on a device or emulator (API 26+).

### Run tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :extractor:test
```

## Commit style

This project follows [Conventional Commits](https://www.conventionalcommits.org/). The commit message format is:

```
<type>(<scope>): <short summary>

<optional body>

<optional footer>
```

Common types:
- `feat`: new user-facing feature
- `fix`: bug fix
- `refactor`: code change that neither fixes a bug nor adds a feature
- `test`: adding or updating tests
- `docs`: documentation only
- `chore`: tooling, build configuration, dependencies
- `security`: security-related change

Examples:
- `feat: add support for dark theme scheduling`
- `fix(reader): prevent crash when subtitle is empty`
- `security: mask API key in settings UI`

## Pull request process

1. Fork the repository and create a branch from `main`.
2. Make your changes. Add tests for new functionality.
3. Ensure all tests pass: `./gradlew :app:testDebugUnitTest`
4. Ensure the debug build compiles: `./gradlew :app:assembleDebug`
5. Update the README or relevant docs if your change affects user-facing behavior.
6. Open a pull request against `main`.
7. Fill in the PR template (it includes a checklist).
8. Wait for review. A maintainer will approve or request changes.

## Code style

- Kotlin: follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). 4-space indentation.
- Compose: use Material 3 components. Follow the patterns in existing screens.
- Repository pattern: data layer uses repositories that expose `Flow`/`StateFlow`.
- ViewModels: expose immutable `StateFlow<UiState>` only.

## Reporting issues

Use the GitHub issue tracker. Choose the appropriate template (bug report or feature request) and fill in all the requested information.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you agree to abide by its terms.

## License

By contributing, you agree that your contributions will be licensed under the GNU General Public License v3.0 — the same license as the project.

## Questions?

Open a GitHub Discussion (once enabled) or open an issue with the `question` label.
