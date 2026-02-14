# MiniMessage Format Viewer

An IntelliJ IDEA plugin for viewing and highlighting MiniMessage formatted text.

## Features

- Adventure API model (`MiniMessage`) rendering
- Legacy model (`&`/`§` color codes) rendering
- Settings-based renderer switch (Adventure API / Legacy)
- Color + bold + italic preview in string literals
- Tag/code highlighting with readable contrast adjustment

## Installation

1. Download the plugin JAR from the releases page.
2. In IntelliJ IDEA, go to Settings > Plugins > Install Plugin from Disk.
3. Select the downloaded JAR file and restart IDEA.

## Usage

Open a file containing MiniMessage formatted text. The plugin will automatically highlight tags and colors.

Renderer model can be changed in:
`Settings > Tools > MiniMessage Colors > Rendering Model`

## Development

This plugin is built with Kotlin and Gradle.

To build:
```
./gradlew build
```

To run in development:
```
./gradlew runIde
```

## Third-party notices

See `THIRD_PARTY_NOTICES.md` for license notices.
