package me.inspect.minimessageformatviewer.rendering.model

interface MessageRenderModel {
    fun render(source: String, customColors: Map<String, String>): RenderOutput
}
