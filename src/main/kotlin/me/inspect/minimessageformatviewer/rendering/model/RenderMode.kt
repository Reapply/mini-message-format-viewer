package me.inspect.minimessageformatviewer.rendering.model

enum class RenderMode(val displayName: String) {
    BUILT_IN("Built-in MiniMessage Parser"),
    ADVENTURE_API("Adventure API (MiniMessage)"),
    ;

    companion object {
        fun fromRaw(raw: String?): RenderMode {
            if (raw == "LEGACY") return BUILT_IN
            return entries.firstOrNull { it.name == raw } ?: BUILT_IN
        }
    }
}
