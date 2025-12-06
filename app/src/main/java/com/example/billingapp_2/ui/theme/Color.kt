package com.example.billingapp_2.ui.theme

import androidx.compose.ui.graphics.Color

// Define a vibrant and accessible color palette for your application.
// These colors are chosen to provide good contrast and a modern feel.

// Primary colors (used for main UI elements like buttons, active states)
// Using a warm, inviting primary color
val PrimaryLight = Color(0xFF6F00FF) // A deep, rich purple
val OnPrimaryLight = Color(0xFFFFFFFF) // White text on primary
val PrimaryContainerLight = Color(0xFFEADDFF) // Lighter shade for containers
val OnPrimaryContainerLight = Color(0xFF21005D) // Dark text on primary container

// Secondary colors (for accents and less prominent actions)
// A vibrant teal for secondary actions
val SecondaryLight = Color(0xFF00BFA5) // Teal Green
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFACE1AF) // Lighter teal for containers
val OnSecondaryContainerLight = Color(0xFF00211A)

// Tertiary colors (for complementary accents or specific emphasis)
// A warm orange for tertiary accents
val TertiaryLight = Color(0xFFFFA000) // Amber Orange
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDEB0) // Lighter amber for containers
val OnTertiaryContainerLight = Color(0xFF2F1100)

// Error colors (for indicating errors or warnings)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Background and surface colors (for screen background and component surfaces)
val BackgroundLight = Color(0xFFFDF7FF) // Very light purple background
val OnBackgroundLight = Color(0xFF1E1A20) // Dark text on background
val SurfaceLight = Color(0xFFFDF7FF) // Same as background for consistency
val OnSurfaceLight = Color(0xFF1E1A20) // Dark text on surface
val SurfaceVariantLight = Color(0xFFE7E0EB) // Slightly darker surface for cards/containers
val OnSurfaceVariantLight = Color(0xFF49454E) // Text on surface variant

// Outline color (for borders and dividers)
val OutlineLight = Color(0xFF7A757F)
val OutlineVariantLight = Color(0xFFC9C4D0)

// --- Dark Theme Colors ---

// Primary colors for dark theme (inverted or darker shades of light primary)
val PrimaryDark = Color(0xFFB570FF) // Lighter purple for dark theme
val OnPrimaryDark = Color(0xFF370068)
val PrimaryContainerDark = Color(0xFF530097)
val OnPrimaryContainerDark = Color(0xFFEADDFF)

// Secondary colors for dark theme
val SecondaryDark = Color(0xFF66FFDA) // Brighter teal for dark theme
val OnSecondaryDark = Color(0xFF00372F)
val SecondaryContainerDark = Color(0xFF005044)
val OnSecondaryContainerDark = Color(0xFF66FFDA)

// Tertiary colors for dark theme
val TertiaryDark = Color(0xFFFFA000) // Lighter amber for dark theme
val OnTertiaryDark = Color(0xFF4C2700)
val TertiaryContainerDark = Color(0xFF6E3C00)
val OnTertiaryContainerDark = Color(0xFFFFA000) // Changed to match the TertiaryLight's feel

// Error colors for dark theme
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Background and surface colors for dark theme
val BackgroundDark = Color(0xFF131215) // Dark background
val OnBackgroundDark = Color(0xFFE7E0EB)
val SurfaceDark = Color(0xFF131215) // Same as background
val OnSurfaceDark = Color(0xFFE7E0EB)
val SurfaceVariantDark = Color(0xFF49454E)
val OnSurfaceVariantDark = Color(0xFFC9C4D0)

// Outline color for dark theme
val OutlineDark = Color(0xFF948F99)
val OutlineVariantDark = Color(0xFF49454E)
val ScreenBackground = Color(0xFFF3F4F6) // Light gray for the main screen background
val CardBackground = Color.White

// Text
object AppColors {
    val ScreenBackground = Color(0xFFF3F4F6) // Light gray for the main screen background
    val CardBackground = Color.White
    val PrimaryText = Color(0xFF1F2937)      // Dark gray for headings and important text
    val SecondaryText = Color(0xFF6B7280)    // Medium gray for labels and secondary info
    val BalanceText = Color(0xFFEF4444)      // Red for highlighting the balance amount
    val WhiteText = Color.White              // For text on colored buttons
    val Accent = Color(0xFF0E7490)           // A teal/cyan color for accents like the STB number
    val ButtonCyan = Color(0xFF06B6D4)       // Cyan for the Edit Hardware button

    // Buttons
    val ButtonGreen = Color(0xFF22C55E)
    val ButtonBlue = Color(0xFF3B82F6)
    val ButtonOrange = Color(0xFFF97316)
    val ButtonPurple = Color(0xFFA855F7)
    val ButtonBorder = Color(0xFFD1D5DB)      // Light gray for the outlined button border


        // Primary Colors
        val PrimaryGreen = Color(0xFF54D22D)


        // Background Colors

        val SurfaceColor = Color(0xFFFFFFFF)
        val LightGreenBackground = Color(0xFFEEFCE9)
        val SurfaceVariant = Color(0xFFF1F5F1)

        // Text Colors

        val DarkTextColor = Color(0xFF323431)
        val MediumTextColor = Color(0xFF494D48)
        val IdStbTextColor = Color(0xFFCC5500)

        // Border Colors
        val OutlineColor = Color(0xFFD0D7D1)

        // Status Colors
        val SuccessGreen = Color(0xFF10B981)
        val ErrorRed = Color(0xFFEF4444)
        val WarningOrange = Color(0xFFF59E0B)

    // Bottom Navigation
    val BottomNavActive = Color(0xFF15803D)         // Dark green for the active nav item
    val BottomNavActiveBackground = Color(0xFFDCFCE7) // Light green for the active nav item background
    val BottomNavInactive = Color(0xFF6B7280)     // Gray for inactive nav items
}