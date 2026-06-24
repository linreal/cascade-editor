package io.github.linreal.cascade.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_up_right
import cascadeeditor.sample.generated.resources.ic_brackets_angle
import cascadeeditor.sample.generated.resources.ic_caret_right
import cascadeeditor.sample.generated.resources.ic_code
import cascadeeditor.sample.generated.resources.ic_editor
import cascadeeditor.sample.generated.resources.ic_palette
import cascadeeditor.sample.generated.resources.ic_puzzle
import io.github.linreal.cascade.navigation.AppScreen
import io.github.linreal.cascade.theme.CascadeSampleColors
import io.github.linreal.cascade.theme.LocalCascadeSampleColors
import io.github.linreal.cascade.theme.geistFontFamily
import io.github.linreal.cascade.ui.PageScaffold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun LandingScreen(
    onNavigate: (AppScreen) -> Unit,
) {
    val colors = LocalCascadeSampleColors.current
    val geist = geistFontFamily()
    PageScaffold(
        pageColor = colors.background,
        canvasColor = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Branding
            KmpBadge(colors)
            Spacer(modifier = Modifier.height(22.dp))
            BrandTitle(colors)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A block-based document editor, reimagined for Compose Multiplatform.",
                style = TextStyle(
                    fontFamily = geist,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.subtitle,
                ),
                modifier = Modifier.widthIn(max = 300.dp),
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Hero card — Editor Demo
            HeroCard(colors = colors, onClick = { onNavigate(AppScreen.EditorDemo) })

            Spacer(modifier = Modifier.height(34.dp))

            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "EXPLORE",
                    style = TextStyle(
                        fontFamily = geist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.16.em,
                        color = colors.exploreHeader,
                    ),
                )
                Text(
                    text = "4 modules",
                    style = TextStyle(
                        fontFamily = geist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.modulesCount,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Module cards
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleCard(
                    colors = colors,
                    icon = Res.drawable.ic_puzzle,
                    tile = colors.tileBlocks,
                    title = "Custom Blocks & Commands",
                    description = "Extend the editor with custom block types",
                    onClick = { onNavigate(AppScreen.CustomBlocks) },
                )
                ModuleCard(
                    colors = colors,
                    icon = Res.drawable.ic_code,
                    tile = colors.tileToolbar,
                    title = "Custom Toolbar",
                    description = "Build your own formatting toolbar",
                    onClick = { onNavigate(AppScreen.CustomToolbar) },
                )
                ModuleCard(
                    colors = colors,
                    icon = Res.drawable.ic_palette,
                    tile = colors.tileExternal,
                    title = "External Toolbar",
                    description = "Render toolbar controls outside the editor",
                    onClick = { onNavigate(AppScreen.ExternalToolbar) },
                )
                ModuleCard(
                    colors = colors,
                    icon = Res.drawable.ic_brackets_angle,
                    tile = colors.tileHtml,
                    title = "Custom HTML Profile",
                    description = "Import and export a custom HTML profile",
                    onClick = { onNavigate(AppScreen.CustomHtmlProfile) },
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun KmpBadge(colors: CascadeSampleColors) {
    val geist = geistFontFamily()
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.badgeBackground)
            .border(1.dp, colors.badgeBorder, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.badgeDot),
        )
        Text(
            text = "KOTLIN MULTIPLATFORM",
            style = TextStyle(
                fontFamily = geist,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                color = colors.badgeText,
            ),
        )
    }
}

@Composable
private fun BrandTitle(colors: CascadeSampleColors) {
    val geist = geistFontFamily()
    Text(
        text = "Cascade",
        style = TextStyle(
            fontFamily = geist,
            fontSize = 56.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            color = colors.titleInk,
        ),
    )
    Text(
        text = "Editor",
        style = TextStyle(
            fontFamily = geist,
            fontSize = 56.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            color = colors.titleAccent,
        ),
    )
}

@Composable
private fun HeroCard(
    colors: CascadeSampleColors,
    onClick: () -> Unit,
) {
    val geist = geistFontFamily()
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 22.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.heroShadow,
                spotColor = colors.heroShadow,
            )
            .clip(shape)
            .background(Brush.linearGradient(colors.heroGradient))
            .clickable(onClick = onClick),
    ) {
        // Decorative corner glow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Column(modifier = Modifier.padding(26.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FEATURED",
                    style = TextStyle(
                        fontFamily = geist,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.18.em,
                        color = Color.White.copy(alpha = 0.8f),
                    ),
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_arrow_up_right),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_editor),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Editor Demo",
                        style = TextStyle(
                            fontFamily = geist,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Slash commands · rich text · drag-to-reorder",
                        style = TextStyle(
                            fontFamily = geist,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = Color.White.copy(alpha = 0.82f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    colors: CascadeSampleColors,
    icon: DrawableResource,
    tile: CascadeSampleColors.TileColors,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val geist = geistFontFamily()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (colors.isDark) 0.dp else 4.dp,
                shape = shape,
                clip = false,
                spotColor = colors.heroShadow,
                ambientColor = colors.heroShadow,
            )
            .clip(shape)
            .background(colors.cardBackground)
            .border(1.dp, colors.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tile.background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tile.icon),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = geist,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cardTitle,
                ),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = TextStyle(
                    fontFamily = geist,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.cardDescription,
                ),
            )
        }
        Image(
            painter = painterResource(Res.drawable.ic_caret_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.caret),
            modifier = Modifier.size(18.dp),
        )
    }
}
