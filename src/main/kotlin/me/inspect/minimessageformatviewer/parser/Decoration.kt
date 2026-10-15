package me.inspect.minimessageformatviewer.parser

enum class Decoration(val tagName: String, vararg val aliases: String) {
    BOLD("bold", "b"),
    ITALIC("italic", "i", "em"),
    UNDERLINED("underlined", "u"),
    STRIKETHROUGH("strikethrough", "st"),
    OBFUSCATED("obfuscated", "obf");

    companion object {
        fun byName(name: String): Decoration? =
            entries.firstOrNull { it.tagName == name || name in it.aliases }
    }
}
