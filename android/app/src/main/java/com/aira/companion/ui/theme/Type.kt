package com.aira.companion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Editorial = FontFamily.Serif
private val Interface = FontFamily.SansSerif

val AiraTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                letterSpacing = (-1.2).sp,
            ),
        displayMedium =
            // Dawn move 5: the welcome headline grows a step; italics are
            // applied per call site (greetings, emphasis words), not here.
            TextStyle(
                fontFamily = Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.8).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.45).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 27.sp,
                lineHeight = 32.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 23.sp,
                lineHeight = 28.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Interface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.4.sp,
            ),
    )
