package org.opensources.courses.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** System font only (no bundled font to license); slightly larger body text for readability. */
internal val CoursesTypography =
    Typography(
        headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
