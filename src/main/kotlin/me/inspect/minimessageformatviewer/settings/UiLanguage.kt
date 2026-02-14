package me.inspect.minimessageformatviewer.settings

enum class UiLanguage(val displayName: String) {
    ENGLISH("English"),
    KOREAN("한국어");

    companion object {
        fun fromRaw(raw: String?): UiLanguage {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ENGLISH
        }
    }
}
