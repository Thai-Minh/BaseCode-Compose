package com.looper.base.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.looper.base.R

val CustomFont = FontFamily(
    Font(resId = R.font.poppins_extra_bold, weight = FontWeight.W800),
    Font(resId = R.font.poppins_bold, weight = FontWeight.W700),
    Font(resId = R.font.poppins_semibold, weight = FontWeight.W600),
    Font(resId = R.font.poppins_light, weight = FontWeight.W300),
    Font(resId = R.font.poppins_medium, weight = FontWeight.W500),
    Font(resId = R.font.poppins_regular, weight = FontWeight.W400),
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    ),
    titleLarge = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    ),
    titleMedium = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    ),
)


val Typography.titleSemiBold: TextStyle
    get() = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    )

val Typography.titleExtraBold: TextStyle
    get() = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    )

val Typography.titleLight: TextStyle
    get() = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    )

val Typography.titleRegular: TextStyle
    get() = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    )

val Typography.titleMedium: TextStyle
    get() = TextStyle(
        fontFamily = CustomFont,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(
            includeFontPadding = false
        )
    )