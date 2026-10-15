package me.inspect.minimessageformatviewer

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.ColorIcon
import me.inspect.minimessageformatviewer.kotlin.KotlinPlaceholderInspection
import me.inspect.minimessageformatviewer.preview.MiniMessageDocumentationTargetProvider
import me.inspect.minimessageformatviewer.settings.MiniMessageSettings
import java.awt.Color
import java.awt.Font

class MiniMessageFeaturesTest : BasePlatformTestCase() {

    private val messages = """
        package me.inspect.util

        object Messages {
            const val TEXT = "<#FDE7A8>"
            const val ACCENT = "<#FCA821>"
            const val VALUE = "<#FFC23C>"
            const val ERROR = "<#FF5555>"

            val PALETTE: Map<String, String> = mapOf(
                "text" to TEXT,
                "accent" to ACCENT
            )

            private fun paint(color: String, text: String) =
                "${'$'}color${'$'}text</${'$'}{color.substring(1)}"

            fun num(text: String) = paint(VALUE, text)

            fun send(target: Any, message: String, vararg placeholders: Pair<String, String>) {}
        }
    """.trimIndent()

    private val accent = Color(0xFC, 0xA8, 0x21)
    private val text = Color(0xFD, 0xE7, 0xA8)
    private val value = Color(0xFF, 0xC2, 0x3C)
    private val red = Color(0xFF, 0x55, 0x55)

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("me/inspect/util/Messages.kt", messages)
    }

    fun testConstantsColorTextAfterThem() {
        kotlin("val m = \"\${Messages.ACCENT}accented \${Messages.TEXT}plain\"")

        assertEquals(accent, colorOf("accented"))
        assertEquals(text, colorOf("plain"))
        assertEquals(accent, colorOf("{Messages.ACCENT}"))
    }

    fun testHelperFunctionsAreFollowed() {
        kotlin("val m = \"\${Messages.TEXT}You have \${Messages.num(\"<amount>\")} stars\"")

        assertEquals(value, colorOf("<amount>"))
        assertEquals(value, colorOf("num"))
        assertEquals(text, colorOf("stars"))
    }

    fun testPaletteTag() {
        kotlin("val m = \"<palette:accent>accented <palette:acent>broken\"")

        assertEquals(accent, colorOf("accented"))
        assertTrue(warnings().toString(), warnings().any { "Unknown palette color 'acent'" in it })
    }

    fun testPaletteTagInYaml() {
        myFixture.configureByText("messages.yml", "denied: <palette:accent>Not allowed\nquoted: \"<red>Red\"\n")

        assertEquals(accent, colorOf("Not allowed"))
        assertEquals(red, colorOf("Red"))
    }

    fun testDecorationsAndClosingTags() {
        kotlin("val m = \"<red>a<bold>bolded</bold>after\"")

        val bolded = infoAt("bolded")!!.forcedTextAttributes
        assertEquals(red, bolded.foregroundColor)
        assertTrue(bolded.fontType and Font.BOLD != 0)

        // Closing </bold> only ends the bold, not the red around it.
        val after = infoAt("after")!!.forcedTextAttributes
        assertEquals(red, after.foregroundColor)
        assertEquals(Font.PLAIN, after.fontType)
    }

    fun testTagProblems() {
        kotlin("val m = \"<#FFF>short <gradeint:red:blue>typo </blue>stray\"")

        val found = warnings()
        assertTrue(found.toString(), found.any { "hex colors are written #RRGGBB" in it })
        assertTrue(found.toString(), found.any { "did you mean <gradient>" in it })
        assertTrue(found.toString(), found.any { "</blue> closes a tag that is not open" in it })
    }

    fun testPlaceholdersNearRareTagsAreNotTypos() {
        kotlin("val m = \"<red>Costs <price>\"")
        assertTrue(warnings().toString(), warnings().none { "did you mean" in it })
    }

    fun testPlaceholderInspection() {
        myFixture.enableInspections(KotlinPlaceholderInspection::class.java)
        kotlin(
            """
            fun test() {
                Messages.send(Unit, "${'$'}{Messages.ACCENT}<player> earned <amount>", "amount" to "5", "extra" to "x")
                Messages.send(Unit, "<player> joined", "player" to "Steve")
            }
            """.trimIndent()
        )

        val found = warnings()
        assertTrue(found.toString(), found.any { "uses <player> but no \"player\" placeholder" in it })
        assertTrue(found.toString(), found.any { "no <extra>" in it })
        assertEquals(found.toString(), 2, found.count { "placeholder" in it })
    }

    fun testJavaConcatenation() {
        myFixture.configureByText(
            "Test.java",
            """
            class Test {
                static final String ACCENT = "<#FCA821>";
                String m = ACCENT + "accented " + "<red>reddish";
            }
            """.trimIndent()
        )

        assertEquals(accent, colorOf("accented"))
        assertEquals(red, colorOf("reddish"))
    }

    fun testLegacyCodes() {
        kotlin("val m = \"§cred &aamp\"")
        assertEquals(red, colorOf("red"))
        assertEquals(Color(0x55, 0xFF, 0x55), colorOf("amp"))

        MiniMessageSettings.getInstance().legacyAmpersand = false
        try {
            kotlin("val m = \"§cred &aamp\"")
            assertEquals(red, colorOf("amp"))
        } finally {
            MiniMessageSettings.getInstance().legacyAmpersand = true
        }
    }

    fun testColorSwatch() {
        myFixture.configureFromTempProjectFile("me/inspect/util/Messages.kt")
        val offset = myFixture.editor.document.text.indexOf("<#FCA821>")
        val leaf = myFixture.file.findElementAt(offset)!!

        val color = ElementColorProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getColorFrom(leaf) }
        assertEquals(accent, color)
        val gutters = myFixture.findAllGutters()
        assertTrue(gutters.map { it.icon.javaClass }.toString(), gutters.any { it.icon is ColorIcon })
    }

    fun testHoverPreview() {
        kotlin("val m = \"\${Messages.ACCENT}<bold>Hi</bold> <player>\"")
        val offset = myFixture.editor.document.text.indexOf("Hi")

        val target = MiniMessageDocumentationTargetProvider().documentationTargets(myFixture.file, offset).single()
        val html = target.computeDocumentation().toString()

        assertTrue(html, "fca821" in html.lowercase())
        assertTrue(html, "<b>Hi</b>" in html)
        assertTrue(html, "‹player›" in html)

        val onConstant = myFixture.editor.document.text.indexOf("ACCENT}")
        assertTrue(MiniMessageDocumentationTargetProvider().documentationTargets(myFixture.file, onConstant).isEmpty())
    }

    fun testCompletion() {
        myFixture.configureByText("Test.kt", "val m = \"<pal<caret>\"")
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertTrue(lookups.toString(), "palette:accent" in lookups)

        myFixture.configureByText("Test2.kt", "val m = \"<bol<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<bold><caret>\"")
    }

    fun testCompletesArguments() {
        myFixture.configureByText("Test.kt", "val m = \"<click:<caret>\"")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "run_command", "open_url", "copy_to_clipboard")

        myFixture.configureByText("Test2.kt", "val m = \"<key:key.ju<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<key:key.jump><caret>\"")

        myFixture.configureByText("Test3.kt", "val m = \"<pride:tra<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<pride:trans><caret>\"")

        // Palette colors are offered as hex where a tag takes a color.
        myFixture.configureByText("Test4.kt", "val m = \"<gradient:<caret>\"")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "#FCA821", "red")
    }

    fun testCompletesHoverTextInQuotes() {
        myFixture.configureByText("Test.kt", "val m = \"<hover:show_te<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<hover:show_text:'<caret>'>\"")

        // Inside the hover text, tags complete again.
        myFixture.configureByText("Test2.kt", "val m = \"<hover:show_text:'<bol<caret>'>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<hover:show_text:'<bold><caret>'>\"")
    }

    fun testClosingTagSuggestsInnermostOpenTag() {
        myFixture.configureByText("Test.kt", "val m = \"<red><bold>x</<caret>\"")
        myFixture.completeBasic()
        assertEquals(listOf("bold", "red"), myFixture.lookupElementStrings)
    }

    fun testCompletesPlaceholdersFromTheString() {
        myFixture.configureByText("Test.kt", "val m = \"<player> earned <amount>, <pla<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<player> earned <amount>, <player><caret>\"")
    }

    fun testCompletesNegatedDecorations() {
        myFixture.configureByText("Test.kt", "val m = \"<!ita<caret>\"")
        myFixture.completeBasic()
        myFixture.checkResult("val m = \"<!italic><caret>\"")
    }

    fun testNegatedShadowIsValid() {
        kotlin("val m = \"<!shadow>No shadow <shadow:red:0.5>red shadow\"")
        assertTrue(warnings().toString(), warnings().isEmpty())
    }

    private fun kotlin(code: String) {
        myFixture.configureByText("Test.kt", "import me.inspect.util.Messages\n\n$code")
    }

    private fun warnings(): List<String> =
        myFixture.doHighlighting()
            .filter { it.severity >= HighlightSeverity.WEAK_WARNING }
            .mapNotNull { it.description }

    private fun infoAt(word: String): HighlightInfo? {
        val start = myFixture.editor.document.text.indexOf(word)
        assertTrue("'$word' not in file", start >= 0)
        return myFixture.doHighlighting()
            .filter { it.forcedTextAttributes?.foregroundColor != null }
            .firstOrNull { it.startOffset <= start && start + word.length <= it.endOffset }
    }

    private fun colorOf(word: String): Color? = infoAt(word)?.forcedTextAttributes?.foregroundColor
}
