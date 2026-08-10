package com.zknw.unoduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.profile.Profile
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
import com.zknw.unoduo.progress.Levels
import com.zknw.unoduo.ui.components.AvatarFrame
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.LevelBar
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.UnoCardBack
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette

/**
 * The wardrobe.
 *
 * One tab per family, every item on show whether or not it has been earned — a locked
 * row that tells you the level it costs is the whole reason to keep playing, and hiding
 * it would leave the screen looking empty for the first fifty levels.
 */
@Composable
fun CosmeticsScreen(
    profile: Profile,
    onWear: (String) -> Unit,
    onBack: () -> Unit
) {
    var tab by remember { mutableStateOf(CosmeticKind.FRAME) }
    val level = profile.level

    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader("Cosmétiques", "Débloqués en montant de niveau.", onBack)
            Spacer(Modifier.height(16.dp))

            Panel(Modifier.fillMaxWidth()) {
                LevelBar(profile.xp, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            KindTabs(tab) { tab = it }
            Spacer(Modifier.height(12.dp))

            val items = Cosmetics.of(tab)
            val owned = items.count { it.level <= level }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tab.blurb,
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                InkChip("$owned / ${items.size}", color = Palette.SlateHigh)
            }
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Two to a row: wide enough for a readable name under every preview,
                // narrow enough that the whole family is a short scroll.
                items.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { item ->
                            CosmeticTile(
                                item = item,
                                profile = profile,
                                worn = profile.worn(item.kind).id == item.id,
                                modifier = Modifier.weight(1f),
                                onWear = onWear
                            )
                        }
                        // Keeps the last odd tile at half width instead of stretching it.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun KindTabs(selected: CosmeticKind, onSelect: (CosmeticKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CosmeticKind.entries.forEach { kind ->
            val on = kind == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) Palette.Gold else Palette.Slate)
                    .border(
                        2.5.dp,
                        Palette.Outline,
                        RoundedCornerShape(13.dp)
                    )
                    .clickableNoRipple { onSelect(kind) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    text = kind.label,
                    color = if (on) Palette.Outline else Palette.Text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun CosmeticTile(
    item: Cosmetic,
    profile: Profile,
    worn: Boolean,
    modifier: Modifier,
    onWear: (String) -> Unit
) {
    val locked = item.level > profile.level
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier
            .clip(shape)
            .background(Palette.Slate)
            .border(if (worn) 4.dp else 3.dp, if (worn) Palette.Gold else Palette.Outline, shape)
            .clickableNoRipple(enabled = !locked) { onWear(item.id) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp),
            contentAlignment = Alignment.Center
        ) {
            // Locked items are still drawn, just dimmed: you have to be able to see
            // what you are climbing towards.
            Box(
                if (locked) Modifier.graphicsLayer { alpha = 0.38f } else Modifier,
                contentAlignment = Alignment.Center
            ) {
                Preview(item, profile)
            }
        }
        // A title's preview *is* its name, so printing it again underneath would just
        // be the same words twice.
        if (item.kind != CosmeticKind.TITLE) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.name,
                color = if (locked) Palette.TextDim else Palette.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            worn -> InkChip("Porté", color = Palette.Gold, textColor = Palette.Outline)
            locked -> InkChip("Niveau ${item.level}", color = Palette.Ink)
            else -> InkChip("Débloqué", color = Palette.SlateHigh)
        }
    }
}

@Composable
private fun Preview(item: Cosmetic, profile: Profile) {
    when (item.kind) {
        CosmeticKind.FRAME -> AvatarFrame(frame = item, size = 46.dp) {
            PlayerAvatar(
                name = profile.name,
                look = AvatarLook(profile.avatarColor, profile.photoUri),
                size = 46.dp
            )
        }

        CosmeticKind.BACK -> UnoCardBack(width = 46.dp, skin = item, elevation = 3.dp)

        CosmeticKind.FELT -> Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Color(item.a), Color(item.b), Color(item.c)),
                        radius = 140f
                    )
                )
                .border(3.dp, Palette.Outline, RoundedCornerShape(12.dp))
        )

        CosmeticKind.TITLE -> Text(
            text = item.worn.ifEmpty { "—" },
            color = Palette.Gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        CosmeticKind.NAME -> PseudoText(profile.name.ifBlank { "Joueur" }, item, 18.sp)

        CosmeticKind.STICKER -> Text(item.text, fontSize = 40.sp)
    }
}

/**
 * A pseudo, painted.
 *
 * Drawn twice: an ink outline underneath, the colour on top. That is how every glyph on
 * every card in this game is drawn, and it is what a flat coloured word was missing —
 * a plain tint reads as a label, an outlined one reads as part of the deck. Two colours
 * make a gradient, three make a proper sweep across the word.
 */
@Composable
fun PseudoText(
    name: String,
    item: Cosmetic,
    size: TextUnit,
    modifier: Modifier = Modifier
) {
    val stroke = with(LocalDensity.current) { size.toPx() * 0.17f }
    val stops = listOfNotNull(
        item.a.takeIf { it != 0L },
        item.b.takeIf { it != 0L },
        item.c.takeIf { it != 0L }
    ).map { Color(it) }

    val fill = when {
        stops.size >= 2 -> TextStyle(
            brush = Brush.horizontalGradient(stops),
            fontSize = size,
            fontWeight = FontWeight.Black
        )
        else -> TextStyle(
            color = stops.firstOrNull() ?: Palette.Text,
            fontSize = size,
            fontWeight = FontWeight.Black
        )
    }

    Box(modifier) {
        Text(
            text = name,
            style = TextStyle(
                color = Palette.Outline,
                fontSize = size,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = name, style = fill, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Everything the next levels hand over, newest first. Used by the profile screen. */
@Composable
fun NextRewards(level: Int, modifier: Modifier = Modifier) {
    val upcoming = ((level + 1)..Levels.MAX)
        .flatMap { Cosmetics.rewardsAt(it) }
        .take(4)
    if (upcoming.isEmpty()) return

    Column(modifier) {
        upcoming.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InkChip("Niv. ${item.level}", color = Palette.Ink)
                Spacer(Modifier.size(10.dp))
                Text(
                    text = item.name,
                    color = Palette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.kind.label,
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
