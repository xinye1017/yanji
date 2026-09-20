package com.example.yanji.ui.components

import androidx.compose.ui.unit.dp

/**
 * Standardized insets for Yanji screens.
 *
 * Ensures all primary tab pages (Home, Focus, Note, Stats, Profile)
 * leave breathing room above the floating bottom dock:
 * 52dp dock + 8dp float gap + system navigation area + 20dp breathing.
 * 112dp keeps the final interactive row clear on gesture and three-button navigation.
 */
object AppContentInsets {
    val BottomBarPadding = 112.dp
}
