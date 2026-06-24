package io.github.linreal.cascade.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.geist_bold
import cascadeeditor.sample.generated.resources.geist_bold_italic
import cascadeeditor.sample.generated.resources.geist_italic
import cascadeeditor.sample.generated.resources.geist_regular
import org.jetbrains.compose.resources.Font

/**
 * Geist — the OFL-licensed display/text family used for the sample's editor content
 *
 * Their SIL Open Font
 * License is recorded in `THIRD_PARTY_NOTICES/Geist-OFL.txt`.
 */
@Composable
internal fun geistFontFamily(): FontFamily = FontFamily(
    Font(Res.font.geist_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(Res.font.geist_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(Res.font.geist_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.geist_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
)


@Composable
internal fun sampleTypography(): Typography {
    val geist = geistFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = geist),
        displayMedium = base.displayMedium.copy(fontFamily = geist),
        displaySmall = base.displaySmall.copy(fontFamily = geist),
        headlineLarge = base.headlineLarge.copy(fontFamily = geist),
        headlineMedium = base.headlineMedium.copy(fontFamily = geist),
        headlineSmall = base.headlineSmall.copy(fontFamily = geist),
        titleLarge = base.titleLarge.copy(fontFamily = geist),
        titleMedium = base.titleMedium.copy(fontFamily = geist),
        titleSmall = base.titleSmall.copy(fontFamily = geist),
        bodyLarge = base.bodyLarge.copy(fontFamily = geist),
        bodyMedium = base.bodyMedium.copy(fontFamily = geist),
        bodySmall = base.bodySmall.copy(fontFamily = geist),
        labelLarge = base.labelLarge.copy(fontFamily = geist),
        labelMedium = base.labelMedium.copy(fontFamily = geist),
        labelSmall = base.labelSmall.copy(fontFamily = geist),
    )
}
