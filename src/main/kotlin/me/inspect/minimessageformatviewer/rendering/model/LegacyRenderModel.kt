package me.inspect.minimessageformatviewer.rendering.model

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object LegacyRenderModel : MessageRenderModel {
    private val legacyDeserializer = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .build()

    private val legacySerializer = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    override fun render(source: String, customColors: Map<String, String>): RenderOutput {
        val sourceMapping = LegacyTextParsing.mapSource(source)
        if (sourceMapping.sourceIndices.isEmpty()) {
            return RenderOutput(emptyList(), sourceMapping.highlights)
        }

        val normalized = LegacyTextParsing.normalizeToSection(source)
        val component = runCatching {
            legacyDeserializer.deserialize(normalized)
        }.getOrNull() ?: return RenderOutput(emptyList(), sourceMapping.highlights)

        val serializedLegacy = legacySerializer.serialize(component)
        val styled = LegacyTextParsing.parseStyledCharacters(serializedLegacy)
        val styledCharacters = RenderOutputMapper.mapStyledCharacters(sourceMapping.sourceIndices, styled)

        return RenderOutput(
            styledCharacters = styledCharacters,
            tagHighlights = sourceMapping.highlights
        )
    }
}
