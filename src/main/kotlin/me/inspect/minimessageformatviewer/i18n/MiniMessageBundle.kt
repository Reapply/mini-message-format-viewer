package me.inspect.minimessageformatviewer.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

object MiniMessageBundle : DynamicBundle("messages.MiniMessageBundle") {
    const val BUNDLE = "messages.MiniMessageBundle"

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
