# MiniMessage Format Viewer

An IntelliJ IDEA plugin for viewing and highlighting MiniMessage formatted text.

## Features

- Syntax highlighting for MiniMessage tags
- Color preview for color codes
- Gradient support

## Installation

1. Download the plugin JAR from the releases page.
2. In IntelliJ IDEA, go to Settings > Plugins > Install Plugin from Disk.
3. Select the downloaded JAR file and restart IDEA.

## Usage

Open a file containing MiniMessage formatted text. The plugin will automatically highlight tags and colors.

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