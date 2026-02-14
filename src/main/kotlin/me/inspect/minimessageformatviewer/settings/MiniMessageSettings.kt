package me.inspect.minimessageformatviewer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import me.inspect.minimessageformatviewer.rendering.model.RenderMode

@State(
    name = "MiniMessageSettings",
    storages = [Storage("MiniMessageSettings.xml")]
)
class MiniMessageSettings : PersistentStateComponent<MiniMessageSettings> {

    var customColors: MutableMap<String, String> = mutableMapOf()
    var renderMode: String = RenderMode.BUILT_IN.name

    override fun getState() = this

    override fun loadState(state: MiniMessageSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): MiniMessageSettings =
            ApplicationManager.getApplication().getService(MiniMessageSettings::class.java)
    }
}
