package me.inspect.minimessageformatviewer.parser

enum class TagType {
    OPEN,
    CLOSE,
    RESET,
    SELF_CLOSING,

    /** A name MiniMessage does not know, filled in by a placeholder such as `<player>`. */
    PLACEHOLDER,

    /** Looks like a tag MiniMessage knows but cannot be parsed, such as `<#FFF>`. */
    INVALID
}
