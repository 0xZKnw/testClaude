package com.zknw.unoduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Rules
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.SectionLabel
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun RulesScreen(onBack: () -> Unit) {
    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader("Règles", "La version maison, celle qu'on joue vraiment.", onBack)
            Spacer(Modifier.height(18.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
                items(Rules.sections) { section ->
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            SectionLabel(section.title.uppercase())
                            Spacer(Modifier.height(12.dp))
                            section.lines.forEach { line ->
                                Row(Modifier.padding(bottom = 10.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 7.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Palette.TextDim)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        line,
                                        color = Palette.Text.copy(alpha = 0.88f),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
