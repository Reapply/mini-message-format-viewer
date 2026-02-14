package me.inspect.minimessageformatviewer.rendering.model

enum class RenderMode(val displayName: String) {
    ADVENTURE_API("Adventure API (MiniMessage)"),
    LEGACY("Legacy Codes (&/§)");

    companion object {
        fun fromRaw(raw: String?): RenderMode {
            return entries.firstOrNull { it.name == raw } ?: ADVENTURE_API
        }
    }
}
