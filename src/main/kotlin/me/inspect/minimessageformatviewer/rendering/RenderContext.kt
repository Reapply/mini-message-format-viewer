package me.inspect.minimessageformatviewer.rendering

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Color

/** Finds the colors a project's `<palette:name>` tag stands for. */
interface PaletteProvider {

    /** The palette by lowercase name, or empty if the project has none this provider understands. */
    fun palette(project: Project): Map<String, Color>

    companion object {
        val EP_NAME: ExtensionPointName<PaletteProvider> =
            ExtensionPointName.create("me.inspect.mini-message-format-viewer.paletteProvider")
    }
}

/** What reading MiniMessage text depends on beyond the text itself. */
data class RenderContext(
    val palette: Map<String, Color> = emptyMap(),
    val legacyAmpersand: Boolean = true
) {
    companion object {
        fun of(project: Project): RenderContext {
            val palette = try {
                PaletteProvider.EP_NAME.extensionList.fold(emptyMap<String, Color>()) { all, provider ->
                    all + provider.palette(project)
                }
            } catch (_: IndexNotReadyException) {
                emptyMap()
            }

            val legacyAmpersand = runCatching { MiniMessageSettings.getInstance().legacyAmpersand }.getOrDefault(true)
            return RenderContext(palette, legacyAmpersand)
        }
    }
}
