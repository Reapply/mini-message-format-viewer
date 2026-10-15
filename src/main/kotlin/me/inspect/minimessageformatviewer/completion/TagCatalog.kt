package me.inspect.minimessageformatviewer.completion

import com.intellij.ui.ColorUtil
import me.inspect.minimessageformatviewer.color.ColorResolver
import me.inspect.minimessageformatviewer.parser.Decoration
import java.awt.Color

/** What happens after a suggestion is picked. */
enum class After {
    NOTHING,

    /** Adds `>`, finishing the tag. */
    CLOSE,

    /** Adds `:` for the next argument. */
    COLON,

    /** Adds `:` and opens completion for the next argument. */
    COLON_AND_COMPLETE,

    /** Adds `:''>` with the caret between the quotes, for MiniMessage text such as hover text. */
    QUOTED_TEXT
}

data class Suggestion(
    val value: String,
    val typeText: String,
    val after: After,
    val color: Color? = null,
    val aliases: List<String> = emptyList(),
    val priority: Double = 0.0
)

/** The standard MiniMessage tags and their arguments, as listed in the MiniMessage format docs. */
object TagCatalog {

    private class TagInfo(
        val name: String,
        val description: String,
        val aliases: List<String> = emptyList(),
        val after: After = After.CLOSE
    )

    private val TAGS = Decoration.entries.map { TagInfo(it.tagName, "decoration", it.aliases.toList()) } + listOf(
        TagInfo("color", "color", listOf("colour", "c"), After.COLON_AND_COMPLETE),
        TagInfo("shadow", "shadow color", after = After.COLON_AND_COMPLETE),
        TagInfo("reset", "clears all styles"),
        TagInfo("click", "click action", after = After.COLON_AND_COMPLETE),
        TagInfo("hover", "hover action", after = After.COLON_AND_COMPLETE),
        TagInfo("key", "keybind", after = After.COLON_AND_COMPLETE),
        TagInfo("lang", "translatable", listOf("tr", "translate"), After.COLON),
        TagInfo("lang_or", "translatable with fallback", listOf("tr_or", "translate_or"), After.COLON),
        TagInfo("insert", "shift-click to insert", after = After.COLON),
        TagInfo("rainbow", "rainbow"),
        TagInfo("gradient", "gradient", after = After.COLON_AND_COMPLETE),
        TagInfo("transition", "color transition", after = After.COLON_AND_COMPLETE),
        TagInfo("font", "font", after = After.COLON_AND_COMPLETE),
        TagInfo("newline", "line break", listOf("br")),
        TagInfo("selector", "entity selector", listOf("sel"), After.COLON_AND_COMPLETE),
        TagInfo("score", "scoreboard score", after = After.COLON),
        TagInfo("nbt", "NBT value", listOf("data"), After.COLON_AND_COMPLETE),
        TagInfo("pride", "pride flag gradient"),
        TagInfo("sprite", "sprite", after = After.COLON_AND_COMPLETE),
        TagInfo("head", "player head", after = After.COLON)
    )

    private val ALIASES = TAGS.flatMap { tag -> tag.aliases.map { it to tag.name } }.toMap()

    private val CLICK_ACTIONS = listOf(
        "run_command" to "run a command",
        "suggest_command" to "put text in the chat box",
        "open_url" to "open a link",
        "copy_to_clipboard" to "copy text",
        "change_page" to "turn a book page",
        "open_file" to "open a file",
        "show_dialog" to "show a dialog",
        "custom" to "custom payload"
    )

    private val KEYBINDS = listOf(
        "key.jump", "key.sneak", "key.sprint", "key.forward", "key.back", "key.left", "key.right",
        "key.attack", "key.use", "key.pickItem", "key.drop", "key.inventory", "key.swapOffhand",
        "key.chat", "key.command", "key.playerlist", "key.screenshot", "key.togglePerspective", "key.advancements"
    )

    private val PRIDE_FLAGS = listOf(
        "pride", "progress", "trans", "bi", "pan", "nb", "lesbian", "ace", "agender", "demisexual", "genderqueer",
        "genderfluid", "intersex", "aro", "baker", "philly", "queer", "gay", "bigender", "demigender", "femboy"
    )

    private val FONTS = listOf("default", "uniform", "alt", "illageralt")

    private val SELECTORS = listOf(
        "@p" to "nearest player", "@a" to "all players", "@r" to "random player",
        "@s" to "the viewer", "@e" to "all entities", "@n" to "nearest entity"
    )

    /** Tag names, colors, palette names and negated decorations for the start of a tag. */
    fun tags(palette: Map<String, Color>): List<Suggestion> = buildList {
        TAGS.forEach { add(Suggestion(it.name, it.description, it.after, aliases = it.aliases)) }
        if (palette.isNotEmpty()) add(Suggestion("palette", "palette color", After.COLON_AND_COMPLETE))

        palette.forEach { (name, color) -> add(Suggestion("palette:$name", "palette", After.CLOSE, color)) }
        addAll(colors(After.CLOSE))

        Decoration.entries.forEach { add(Suggestion("!${it.tagName}", "turn off ${it.tagName}", After.CLOSE)) }
        add(Suggestion("!shadow", "turn off shadow", After.CLOSE))
    }

    /** Suggestions for argument [index] of [tag]. */
    fun arguments(tag: String, index: Int, palette: Map<String, Color>): List<Suggestion> {
        val name = ALIASES[tag] ?: Decoration.byName(tag)?.tagName ?: tag
        if (Decoration.byName(name) != null) {
            return if (index == 1) listOf("false", "true").map { Suggestion(it, "decoration on or off", After.CLOSE) } else emptyList()
        }

        return when (name) {
            "color", "shadow" -> if (index == 1) colors(After.CLOSE) + paletteHex(palette, After.CLOSE) else emptyList()

            "gradient", "transition" -> colors(After.NOTHING) + paletteHex(palette, After.NOTHING)

            "palette" -> if (index == 1) palette.map { (key, color) -> Suggestion(key, "palette", After.CLOSE, color) } else emptyList()

            "click" -> if (index == 1) CLICK_ACTIONS.map { (action, what) -> Suggestion(action, what, After.COLON) } else emptyList()

            "hover" -> if (index == 1) {
                listOf(
                    Suggestion("show_text", "MiniMessage text", After.QUOTED_TEXT),
                    Suggestion("show_item", "item key[:count]", After.COLON),
                    Suggestion("show_entity", "type:uuid[:name]", After.COLON)
                )
            } else {
                emptyList()
            }

            "key" -> if (index == 1) KEYBINDS.map { Suggestion(it, "keybind", After.CLOSE) } else emptyList()

            "rainbow" -> if (index == 1) listOf(Suggestion("!", "reversed", After.CLOSE)) else emptyList()

            "pride" -> if (index == 1) PRIDE_FLAGS.map { Suggestion(it, "flag", After.CLOSE) } else emptyList()

            "font" -> if (index == 1) FONTS.map { Suggestion(it, "font", After.CLOSE) } else emptyList()

            "selector" -> if (index == 1) SELECTORS.map { (selector, what) -> Suggestion(selector, what, After.CLOSE) } else emptyList()

            "nbt" -> if (index == 1) listOf("block", "entity", "storage").map { Suggestion(it, "data source", After.COLON) } else emptyList()

            "sprite" -> if (index == 1) listOf("blocks", "items").map { Suggestion(it, "atlas", After.COLON) } else emptyList()

            "head" -> if (index == 2) listOf("true", "false").map { Suggestion(it, "draw the hat layer", After.CLOSE) } else emptyList()

            else -> emptyList()
        }
    }

    private fun colors(after: After): List<Suggestion> =
        ColorResolver.names().sorted().map { Suggestion(it, "color", after, ColorResolver.resolve(it)) }

    // The palette's own colors as hex, for arguments that take a color but not a palette name.
    private fun paletteHex(palette: Map<String, Color>, after: After): List<Suggestion> =
        palette.map { (name, color) -> Suggestion("#${ColorUtil.toHex(color).uppercase()}", "palette: $name", after, color) }
}
