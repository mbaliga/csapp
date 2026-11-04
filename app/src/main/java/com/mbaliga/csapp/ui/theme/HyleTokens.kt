package com.mbaliga.csapp.ui.theme

import androidx.compose.ui.graphics.Color

/** Hyle token values copied here (no submodule dependency; see BUILD-SPEC §6). */
object HyleTokens {
    val Ground = Color(0xFF07070A)
    val Surface1 = Color(0xFF101016)
    val Surface2 = Color(0xFF181821)
    val Violet = Color(0xFF8E7BFF)          // primary
    val Radium = Color(0xFFCCEC75)          // on-device / local provenance
    val Alien = Color(0xFF3ED4FF)           // cloud provenance
    val TextHigh = Color(0xFFEDEDF3)
    val TextMuted = Color(0xFF9D9DAE)
    val Outline = Color(0xFF2B2B38)

    // Light theme (must pass AA against its ground).
    val LightGround = Color(0xFFFBFBFD)
    val LightSurface1 = Color(0xFFF1F1F6)
    val LightSurface2 = Color(0xFFE7E7EF)
    val LightViolet = Color(0xFF4B36C9)
    val LightText = Color(0xFF14141C)
    val LightMuted = Color(0xFF55556A)
    val LightOutline = Color(0xFFC9C9D6)
    val LightRadium = Color(0xFF3F5A00)
    val LightAlien = Color(0xFF005F7A)
}
