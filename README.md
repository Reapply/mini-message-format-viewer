# MiniMessage Format Viewer

An IntelliJ IDEA plugin for viewing and highlighting MiniMessage formatted text.

## Features

- Syntax highlighting for MiniMessage tags in Kotlin, Java and YAML
- Color preview for color codes, gradients and rainbows
- Bold, italic, underlined and strikethrough shown in the editor
- Kotlin constants and helpers: `"${Messages.ACCENT}text"` and `${num("<amount>")}` show in the colors they hold
- Java concatenations: `Messages.ACCENT + "text"`
- `<palette:name>` tags, read from a `val PALETTE = mapOf("accent" to ACCENT, ...)` in your code
- Gutter color swatches with a color picker for strings like `"<#FCA821>"`
- Hover a message to preview it the way it looks in chat
- Completion for every standard MiniMessage tag and its arguments (click and hover actions, colors, keybinds, pride flags, fonts, selectors), closing tags and placeholders
- Warnings for bad hex colors, mistyped tags, unknown palette names and stray closing tags
- An inspection for `send(player, message, "name" to value)` calls whose placeholders don't match the message
- Legacy `§a` and `&a` codes (`&a` can be turned off in settings)

## Installation

1. Download the plugin JAR from the releases page.
2. In IntelliJ IDEA, go to Settings > Plugins > Install Plugin from Disk.
3. Select the downloaded JAR file and restart IDEA.

## Usage

Open a file containing MiniMessage formatted text. The plugin will automatically highlight tags and colors.

## Development

This plugin is built with Kotlin and Gradle, and builds on Java 25.

To build:
```
./gradlew build
```

To run in development:
```
./gradlew runIde
```