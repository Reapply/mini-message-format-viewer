package me.inspect.minimessageformatviewer.rendering.model

object MessageRenderModelFactory {
    fun create(mode: RenderMode): MessageRenderModel {
        return when (mode) {
            RenderMode.ADVENTURE_API -> AdventureApiRenderModel
            RenderMode.LEGACY -> LegacyRenderModel
        }
    }
}
