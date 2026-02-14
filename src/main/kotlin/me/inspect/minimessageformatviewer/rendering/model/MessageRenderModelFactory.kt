package me.inspect.minimessageformatviewer.rendering.model

object MessageRenderModelFactory {
    fun create(mode: RenderMode): MessageRenderModel {
        return when (mode) {
            RenderMode.BUILT_IN -> BuiltInRenderModel
            RenderMode.ADVENTURE_API -> AdventureApiRenderModel
        }
    }
}
