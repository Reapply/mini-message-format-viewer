package me.inspect.minimessageformatviewer.rendering.model

import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object AdventureApiRenderModel : MessageRenderModel {
    private val legacySerializer = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    override fun render(source: String, customColors: Map<String, String>): RenderOutput {
        val sourceMapping = AdventureTextMapping.map(source, customColors)
        if (sourceMapping.sourceIndices.isEmpty()) {
            return RenderOutput(emptyList(), sourceMapping.highlights)
        }

        val component = runCatching {
            buildMiniMessage(customColors).deserialize(source)
        }.getOrNull() ?: return RenderOutput(emptyList(), sourceMapping.highlights)

        val serializedLegacy = legacySerializer.serialize(component)
        val styled = LegacyTextParsing.parseStyledCharacters(serializedLegacy)
        val styledCharacters = RenderOutputMapper.mapStyledCharacters(sourceMapping.sourceIndices, styled)

        return RenderOutput(
            styledCharacters = styledCharacters,
            tagHighlights = sourceMapping.highlights
        )
    }

    private fun buildMiniMessage(customColors: Map<String, String>): MiniMessage {
        if (customColors.isEmpty()) return MiniMessage.miniMessage()

        val resolverBuilder = TagResolver.builder()
            .resolver(StandardTags.defaults())

        customColors.forEach { (name, hex) ->
            val normalizedName = name.trim().lowercase()
            if (normalizedName.isEmpty()) return@forEach

            val textColor = parseTextColor(hex) ?: return@forEach
            resolverBuilder.tag(normalizedName, Tag.styling(textColor))
        }

        return MiniMessage.builder()
            .tags(resolverBuilder.build())
            .build()
    }

    private fun parseTextColor(raw: String): TextColor? {
        val hex = raw.trim().removePrefix("#")
        if (hex.length != 6 || !hex.all { it.isHexDigit() }) return null
        return TextColor.fromHexString("#$hex")
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
