@file:Suppress("unused")

package samples

/**
 * Open this file in IntelliJ and inspect each string literal to preview effects.
 *
 * Renderer mode recommendation:
 * - Settings > Tools > MiniMessage Colors > Rendering Model = Built-in
 *
 * Optional global force toggles:
 * - me.inspect.minimessageformatviewer.rendering.TestStyleOverrides
 */
object EffectGallery {

    // Single effects
    val underline = "<underlined>Underline</underlined>"
    val strike = "<strikethrough>Strikethrough</strikethrough>"
    val boxedObfuscated = "<obfuscated>Obfuscated (BOXED)</obfuscated>"

    // Short aliases
    val underlineShort = "<u>Underline Short</u>"
    val strikeShort = "<st>Strike Short</st>"
    val obfuscatedShort = "<obf>Obfuscated Short</obf>"

    // Pair combinations
    val underlineStrike = "<underlined><strikethrough>Underline + Strike</strikethrough></underlined>"
    val underlineObfuscated = "<underlined><obfuscated>Underline + Obfuscated</obfuscated></underlined>"
    val strikeObfuscated = "<strikethrough><obfuscated>Strike + Obfuscated</obfuscated></strikethrough>"

    // Triple combination
    val allEffects = "<underlined><strikethrough><obfuscated>All Effects</obfuscated></strikethrough></underlined>"

    // Color + effects
    val colorUnderline = "<red><underlined>Red + Underline</underlined></red>"
    val colorStrike = "<#55AAFF><strikethrough>Hex + Strike</strikethrough></#55AAFF>"
    val colorObfuscated = "<green><obfuscated>Green + Obfuscated</obfuscated></green>"

    // Gradient + effects
    val gradientUnderline = "<gradient:#ff0000:#00ff00><underlined>Gradient + Underline</underlined></gradient>"
    val gradientStrike = "<gradient:#ff00ff:#00ffff><strikethrough>Gradient + Strike</strikethrough></gradient>"
    val gradientObfuscated = "<gradient:#ffaa00:#0055ff><obfuscated>Gradient + Obfuscated</obfuscated></gradient>"

    // Scope/reset checks
    val closeTagScope = "<underlined>ON</underlined> OFF"
    val resetScope = "<underlined>ON<reset>OFF"
    val mixedScope = "<underlined>A <strikethrough>B</strikethrough> C</underlined> D"
}
