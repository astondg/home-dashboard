package com.homedashboard.app.settings

import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Utility for detecting display characteristics, particularly for e-ink devices.
 *
 * This detects whether the device is likely a monochrome e-ink display, a color e-ink display,
 * or a standard LCD/OLED display. This information is used to optimize the UI rendering.
 */
object DisplayDetection {

    /**
     * Known Boox e-ink device models and their color capabilities
     */
    private val BOOX_COLOR_MODELS = setOf(
        "Tab Ultra C",
        "Tab Ultra C Pro",
        "Note Air3 C",
        "Note Air4 C", // hypothetical
        "Tab Mini C",
        "Page Color",
        "Poke5 Color"
    )

    private val BOOX_MONOCHROME_MODELS = setOf(
        "Note Air",
        "Note Air2",
        "Note Air2 Plus",
        "Note Air3",
        "Note Air4",
        "Note Air5",
        "Note Max",
        "Max Lumi",
        "Max Lumi2",
        "Note5",
        "Note X",
        "Tab X",
        "Tab Ultra",
        "Poke5",
        "Page",
        "Leaf",
        "Leaf2",
        "Go 10.3"
    )

    /**
     * E-ink device manufacturers
     */
    private val EINK_MANUFACTURERS = setOf(
        "onyx", "boox",           // Boox/Onyx
        "remarkable",             // reMarkable
        "supernote",              // Supernote
        "kobo", "rakuten",        // Kobo
        "kindle", "amazon",       // Kindle
        "bigme",                  // Bigme
        "meebook",                // Meebook
        "pocketbook"              // PocketBook
    )

    /**
     * Detected display type
     */
    enum class DisplayType {
        /** Standard color LCD/OLED display */
        COLOR_LCD,

        /** Monochrome e-ink display (black & white, possibly with grayscale) */
        MONOCHROME_EINK,

        /** Color e-ink display (Kaleido, Gallery, etc.) */
        COLOR_EINK,

        /** Unknown display type */
        UNKNOWN
    }

    /**
     * Detect the display type based on device properties
     */
    fun detectDisplayType(context: Context): DisplayType {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val model = Build.MODEL ?: ""
        val device = Build.DEVICE?.lowercase() ?: ""
        val product = Build.PRODUCT?.lowercase() ?: ""

        // Check if this is a known e-ink device
        val isEinkManufacturer = EINK_MANUFACTURERS.any {
            manufacturer.contains(it) || product.contains(it) || device.contains(it)
        }

        if (!isEinkManufacturer) {
            return DisplayType.COLOR_LCD
        }

        // Check for known Boox models
        val isBoox = manufacturer.contains("onyx") ||
                     manufacturer.contains("boox") ||
                     product.contains("boox")

        if (isBoox) {
            // Check if it's a known color model
            val isColorModel = BOOX_COLOR_MODELS.any {
                model.contains(it, ignoreCase = true)
            }

            if (isColorModel) {
                return DisplayType.COLOR_EINK
            }

            // Check if it's a known monochrome model
            val isMonochromeModel = BOOX_MONOCHROME_MODELS.any {
                model.contains(it, ignoreCase = true)
            }

            if (isMonochromeModel) {
                return DisplayType.MONOCHROME_EINK
            }

            // Default Boox devices to monochrome (most common)
            return DisplayType.MONOCHROME_EINK
        }

        // For other e-ink manufacturers, default to monochrome
        // as color e-ink is still relatively rare
        return DisplayType.MONOCHROME_EINK
    }

    /**
     * Check if the display appears to be an e-ink display
     */
    fun isEinkDisplay(context: Context): Boolean {
        return when (detectDisplayType(context)) {
            DisplayType.MONOCHROME_EINK, DisplayType.COLOR_EINK -> true
            else -> false
        }
    }

    /**
     * Check if the display supports color
     */
    fun supportsColor(context: Context): Boolean {
        return when (detectDisplayType(context)) {
            DisplayType.COLOR_LCD, DisplayType.COLOR_EINK -> true
            DisplayType.MONOCHROME_EINK -> false
            DisplayType.UNKNOWN -> true // Default to assuming color support
        }
    }

    /**
     * Get the effective display mode based on detection and user preference
     */
    fun getEffectiveDisplayMode(context: Context, userPreference: DisplayMode): Boolean {
        return when (userPreference) {
            DisplayMode.AUTO -> !supportsColor(context) // Use monochrome if no color support
            DisplayMode.COLOR -> false // User wants color mode
            DisplayMode.MONOCHROME -> true // User wants monochrome mode
        }
    }

    /**
     * Get display characteristics for UI optimization
     */
    data class DisplayCharacteristics(
        val displayType: DisplayType,
        val isEink: Boolean,
        val supportsColor: Boolean,
        val recommendedRefreshStrategy: RefreshStrategy,
        val estimatedGrayscaleLevels: Int
    )

    enum class RefreshStrategy {
        /** Normal refresh, animations OK */
        NORMAL,
        /** Reduce animations, prefer partial refresh */
        REDUCED_MOTION,
        /** Minimize refreshes, static UI preferred */
        MINIMAL_REFRESH
    }

    /**
     * Get comprehensive display characteristics
     */
    fun getDisplayCharacteristics(context: Context): DisplayCharacteristics {
        val displayType = detectDisplayType(context)

        return DisplayCharacteristics(
            displayType = displayType,
            isEink = displayType == DisplayType.MONOCHROME_EINK || displayType == DisplayType.COLOR_EINK,
            supportsColor = displayType != DisplayType.MONOCHROME_EINK,
            recommendedRefreshStrategy = when (displayType) {
                DisplayType.COLOR_LCD -> RefreshStrategy.NORMAL
                DisplayType.COLOR_EINK -> RefreshStrategy.REDUCED_MOTION
                DisplayType.MONOCHROME_EINK -> RefreshStrategy.MINIMAL_REFRESH
                DisplayType.UNKNOWN -> RefreshStrategy.NORMAL
            },
            estimatedGrayscaleLevels = when (displayType) {
                DisplayType.COLOR_LCD -> 256
                DisplayType.COLOR_EINK -> 16 // Kaleido typically has limited grayscale
                DisplayType.MONOCHROME_EINK -> 16 // Standard e-ink has 16 levels
                DisplayType.UNKNOWN -> 256
            }
        )
    }
}
