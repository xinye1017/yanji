package com.example.yanji.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens (companion to DESIGN.md `spacing` block).
 *
 * Use these instead of raw `Modifier.height(N.dp)` / `padding(N.dp)` to keep
 * vertical rhythm consistent across screens. Three roles only — don't invent
 * new sizes without updating DESIGN.md.
 *
 *   - [PageTopGap]    page top → first content card
 *   - [CardGap]       between sibling cards on the same screen
 *   - [SectionGap]    between a group title (e.g. SettingsGroupTitle) and its card
 *   - [InnerGap]      inside a card, between stacked text rows
 *   - [InlineGap]     between a leading icon and label inside a row
 *   - [TightGap]      very small visual gap (between a primary label and its value)
 */
object YanjiSpacing {
    val PageTopGap = 24.dp       // was 16/20 — pick one
    val CardGap = 20.dp          // was 20/24/32 — pick one
    val SectionGap = 12.dp       // group title → card
    val InnerGap = 12.dp         // inside a card, between text rows
    val InlineGap = 16.dp        // leading icon → label
    val TightGap = 4.dp          // primary label → its value
}
