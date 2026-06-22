# YtReader

[![License: GPL-3.0](https://img.shields.io/github/license/deedeedev/YtReader)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/deedeedev/YtReader)](https://github.com/deedeedev/YtReader/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/deedeedev/YtReader/release.yml)](https://github.com/deedeedev/YtReader/actions)

An Android app for reading YouTube video subtitles with AI-powered text cleaning.

## Features

- Browse and search YouTube videos
- Read and navigate subtitles with highlighting
- AI-powered subtitle text cleaning (OpenAI compatible API)
- Local text cleaning and formatting
- Organize videos into collections
- Highlight and annotate text
- Home screen widget for quick access

## Tech Stack

- Kotlin + Jetpack Compose
- Room database
- OkHttp + Jsoup
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) — core video and subtitle fetching
- WorkManager for background tasks

## Building

1. Clone the repository
2. Open in Android Studio
3. Build and run

Requirements:
- Android SDK 36 (compile)
- Min SDK 26
- JDK 11 (app module)

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, test commands, and contribution guidelines.

## Releases

Tagged releases (`v*.*.*`) automatically produce signed APKs via GitHub Actions. See the [Releases page](https://github.com/deedeedev/YtReader/releases) for downloads.

## Community

- Read the [Code of Conduct](CODE_OF_CONDUCT.md) before participating.
- Use [GitHub Issues](https://github.com/deedeedev/YtReader/issues) for bug reports and feature requests.
- Use [GitHub Discussions](https://github.com/deedeedev/YtReader/discussions) for questions and community chat.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.

## Credits

This app uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) by the [TeamNewPipe](https://github.com/TeamNewPipe) project for video and subtitle data extraction. NewPipe Extractor is licensed under the GNU General Public License v3.0.
