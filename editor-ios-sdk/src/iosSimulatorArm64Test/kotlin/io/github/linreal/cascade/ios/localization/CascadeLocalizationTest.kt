package io.github.linreal.cascade.ios.localization

import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.ios.controller.CascadeEditorController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The resolved-strings states asserted here are exactly what `makeViewController`
 * passes to the hosted editor's `strings`/`blockStrings` parameters, so these
 * tests pin the full Swift-facing localization path headlessly.
 */
class CascadeLocalizationTest {

    @Test
    fun controllerDefaultsToBuiltInEnglishStrings() {
        val controller = CascadeEditorController()

        assertEquals(CascadeEditorStrings.default(), controller.resolvedStrings.value)
        assertEquals(CascadeEditorBlockStrings.default(), controller.resolvedBlockStrings.value)
    }

    @Test
    fun emptyLocalizationResolvesToDefaults() {
        val controller = CascadeEditorController()

        controller.setLocalization(CascadeEditorLocalization())

        assertEquals(CascadeEditorStrings.default(), controller.resolvedStrings.value)
        assertEquals(CascadeEditorBlockStrings.default(), controller.resolvedBlockStrings.value)
    }

    @Test
    fun localizedChromeStringsReachTheResolvedSet() {
        val controller = CascadeEditorController()
        val strings = CascadeLocalizedStrings().apply {
            back = "‹ Zurück"
            bold = "Fett"
            italic = "Kursiv"
            linkApply = "Link anwenden"
            linkValidationBlankUrl = "Bitte URL eingeben."
            unsupportedBlock = { typeId -> "Nicht unterstützter Block: $typeId" }
        }

        controller.setLocalization(CascadeEditorLocalization(strings))

        val resolved = controller.resolvedStrings.value
        assertEquals("‹ Zurück", resolved.back)
        assertEquals("Fett", resolved.bold)
        assertEquals("Kursiv", resolved.italic)
        assertEquals("Link anwenden", resolved.linkApply)
        assertEquals("Bitte URL eingeben.", resolved.linkValidationError(LinkValidationError.Blank))
        assertEquals("Nicht unterstützter Block: table", resolved.unsupportedBlock("table"))
        // Unset fields keep the built-in English defaults.
        val defaults = CascadeEditorStrings.default()
        assertEquals(defaults.underline, resolved.underline)
        assertEquals(defaults.hideKeyboard, resolved.hideKeyboard)
        assertEquals(defaults.linkCancel, resolved.linkCancel)
    }

    @Test
    fun localizedBlockStringsMergeOverDefaultsAndCarryCustomTypes() {
        val controller = CascadeEditorController()

        controller.setLocalization(
            CascadeEditorLocalization(
                blockStrings = listOf(
                    CascadeLocalizedBlockStrings(
                        typeId = "paragraph",
                        displayName = "Absatz",
                        description = "Einfacher Textabsatz",
                        keywords = listOf("text", "absatz"),
                    ),
                    CascadeLocalizedBlockStrings(
                        typeId = "table",
                        displayName = "Tabelle",
                        description = "Interaktive Tabelle",
                    ),
                ),
            ),
        )

        val resolved = controller.resolvedBlockStrings.value
        val paragraph = assertNotNull(resolved.forType("paragraph"))
        assertEquals("Absatz", paragraph.displayName)
        assertEquals("Einfacher Textabsatz", paragraph.description)
        assertEquals(listOf("text", "absatz"), paragraph.keywords)
        // A natively registered custom type id localizes through the same surface.
        val table = assertNotNull(resolved.forType("table"))
        assertEquals("Tabelle", table.displayName)
        // Types without an override keep the built-in entry.
        assertEquals(
            CascadeEditorBlockStrings.default().forType("todo"),
            resolved.forType("todo"),
        )
    }

    @Test
    fun localizationIsSnapshottedWhenApplied() {
        val controller = CascadeEditorController()
        val strings = CascadeLocalizedStrings().apply { bold = "Fett" }
        controller.setLocalization(CascadeEditorLocalization(strings))

        // Mutating the bag after applying must not leak into the resolved set...
        strings.bold = "Gras"
        assertEquals("Fett", controller.resolvedStrings.value.bold)

        // ...until it is applied again.
        controller.setLocalization(CascadeEditorLocalization(strings))
        assertEquals("Gras", controller.resolvedStrings.value.bold)
    }
}
