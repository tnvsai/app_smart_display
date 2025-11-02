#ifndef UI_THEME_H
#define UI_THEME_H

#include <lvgl.h>

// ============================================================================
// Color Definitions (RGB565 format)
// ============================================================================

// Background Colors (BRIGHT for daylight visibility)
#define COLOR_BG_PRIMARY      0x0000      // Pure black
#define COLOR_BG_SECONDARY    0x3186      // Brighter gray (#1a1a1a -> #313131 for better contrast)

// Text Colors (BRIGHT for daylight)
#define COLOR_TEXT_PRIMARY    0xFFFF      // Bright white
#define COLOR_TEXT_SECONDARY  0xC618      // Bright gray (#C0C0C0 for better visibility)
#define COLOR_TEXT_ACCENT    0x07FF      // Bright cyan (#00FFFF)

// Status Colors (BRIGHT)
#define COLOR_STATUS_ACTIVE   0x07E0      // Bright green (#00FF00)
#define COLOR_STATUS_WARNING  0xFE00     // Bright orange (#FFAA00 -> #FFAA00)
#define COLOR_STATUS_ERROR    0xF800     // Bright red (#FF0000)

// Navigation Arrow Colors (VERY BRIGHT for daylight visibility)
#define COLOR_ARROW_STRAIGHT  0x07E0     // BRIGHT Green (#00FF00) - maximum visibility
#define COLOR_ARROW_LEFT      0x07E0     // BRIGHT Green (#00FF00) - LEFT turn
#define COLOR_ARROW_RIGHT     0x07E0     // BRIGHT Green (#00FF00) - RIGHT turn
#define COLOR_ARROW_SHARP     0xFE20     // BRIGHT Orange (#FF9900) - SHARP turns
#define COLOR_ARROW_SLIGHT    0x07FF     // BRIGHT Cyan (#00FFFF) - SLIGHT turns
#define COLOR_ARROW_UTURN     0x07E0     // BRIGHT Green (#00FF00) - U-TURN (green like standard turns)
#define COLOR_ARROW_MERGE     0xF81F     // BRIGHT Purple/Magenta (#FF00FF) - MERGE/SPLIT
#define COLOR_ARROW_KEEP      0xF81F     // BRIGHT Purple/Magenta (#FF00FF) - KEEP LEFT/RIGHT
#define COLOR_ARROW_DEST      0xF800     // BRIGHT Red (#FF0000) - DESTINATION

// Accent Colors (BRIGHT)
#define COLOR_ACCENT_CYAN     0x07FF     // BRIGHT Cyan (#00FFFF)
#define COLOR_ACCENT_GREEN    0x07E0     // BRIGHT Green (#00FF00)
#define COLOR_ACCENT_YELLOW   0xFFE0     // BRIGHT Yellow (#FFFF00)

// ============================================================================
// Animation Timings (milliseconds)
// ============================================================================

#define ANIM_TIME_FAST        150         // Fast transitions
#define ANIM_TIME_NORMAL      300         // Normal transitions
#define ANIM_TIME_SLOW        500         // Slow transitions
#define ANIM_TIME_SCREEN      300         // Screen transitions

// Pulse Animation
#define ANIM_PULSE_PERIOD     2000        // 2 seconds
#define ANIM_PULSE_MIN_SCALE  100        // 100% (1.0)
#define ANIM_PULSE_MAX_SCALE  110        // 110% (1.1)

// Arrow Animation
#define ANIM_ARROW_ROTATE     300         // Arrow rotation time
#define ANIM_COLOR_FADE       200         // Color transition time

// Text Animation
#define ANIM_TEXT_FADE        250         // Text fade in/out

// ============================================================================
// Font Sizes and Typography
// ============================================================================

// Note: LVGL font sizes are specified in pixel height
// These are approximate sizes for reference

#define FONT_SIZE_LARGE       48          // Distance display (40-48px)
#define FONT_SIZE_MEDIUM      20          // Maneuver text (18-20px)
#define FONT_SIZE_NORMAL      16          // Regular text (14-16px)
#define FONT_SIZE_SMALL       14          // Status text (12-14px)

// ============================================================================
// Layout Constants (pixels)
// ============================================================================

#define PADDING_EDGE          10          // Edge padding
#define PADDING_ELEMENT       8           // Element spacing
#define GAP_SMALL             5           // Small gap
#define GAP_MEDIUM            10          // Medium gap
#define GAP_LARGE             20          // Large gap

// Button Sizes
#define BUTTON_DISMISS_WIDTH  120         // Dismiss button width
#define BUTTON_DISMISS_HEIGHT 50          // Dismiss button height
#define BUTTON_TOUCH_MIN      44          // Minimum touch target

// Avatar/Icon Sizes
#define AVATAR_SIZE           100         // Avatar size (call screens)
#define ICON_SIZE_LARGE       120         // Large icon (navigation arrow)
#define ICON_SIZE_MEDIUM      70          // Medium icon (compass)
#define ICON_SIZE_SMALL       40          // Small icon

// ============================================================================
// Screen Dimensions
// ============================================================================

#define SCREEN_WIDTH          172         // Display width
#define SCREEN_HEIGHT         320         // Display height

// ============================================================================
// Navigation Screen Layout Zones
// ============================================================================

#define NAV_STATUS_Y          5           // Status badge Y position
#define NAV_COMPASS_X         142         // Compass X position (right-aligned)
#define NAV_COMPASS_Y         5           // Compass Y position
#define NAV_ARROW_Y           40          // Arrow Y position (higher up for big arrow)
#define NAV_DISTANCE_Y        200         // Distance Y position (moved down for bigger display)
#define NAV_MANEUVER_Y        270         // Maneuver text Y position (moved down)
#define NAV_ETA_Y             300         // ETA banner Y position

// ============================================================================
// Function Declarations - Style Initialization
// ============================================================================

/**
 * Initialize all global UI styles
 * Should be called once during setup
 */
void ui_theme_init(void);

/**
 * Get style for large text (distance display)
 */
lv_style_t* ui_theme_get_large_text_style(void);

/**
 * Get style for medium text (maneuver)
 */
lv_style_t* ui_theme_get_medium_text_style(void);

/**
 * Get style for normal text
 */
lv_style_t* ui_theme_get_normal_text_style(void);

/**
 * Get style for small text (status)
 */
lv_style_t* ui_theme_get_small_text_style(void);

/**
 * Get style for button (dismiss button)
 */
lv_style_t* ui_theme_get_button_style(void);

/**
 * Get style for status badge
 */
lv_style_t* ui_theme_get_status_badge_style(void);

/**
 * Get navigation arrow color for direction
 * @param direction Direction string ("straight", "left", "right", etc.)
 * @return RGB565 color value
 */
uint16_t ui_theme_get_arrow_color(const char* direction);

#endif // UI_THEME_H

