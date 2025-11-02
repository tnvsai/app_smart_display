#ifndef UI_NAVIGATION_SCREEN_H
#define UI_NAVIGATION_SCREEN_H

#include <lvgl.h>

/**
 * Create navigation screen UI (LVGL version)
 * @param parent Screen object to attach UI elements to
 */
void ui_navigation_screen_create(lv_obj_t *parent);

/**
 * Update navigation direction arrow
 * @param direction Direction string ("straight", "left", "right", etc.)
 * @param animated True to animate the change (default: true)
 */
void ui_navigation_screen_update_direction(const char* direction, bool animated = true);

/**
 * Update distance display
 * @param distance Distance in meters
 * @param animated True to animate the countdown (default: true)
 */
void ui_navigation_screen_update_distance(int distance, bool animated = true);

/**
 * Update maneuver instruction text
 * @param maneuver Maneuver text (auto-wraps)
 */
void ui_navigation_screen_update_maneuver(const char* maneuver);

/**
 * Update ETA display
 * @param eta ETA string (e.g., "5 min", "Arriving in 2 min")
 */
void ui_navigation_screen_update_eta(const char* eta);

/**
 * Show critical navigation alert (for < 100m distance)
 * @param show True to show pulsing alert, false to hide
 */
void ui_navigation_screen_show_critical_alert(bool show);

/**
 * Update compass direction (if available)
 * @param heading Compass heading in degrees (0-360)
 */
void ui_navigation_screen_update_compass(int heading);

/**
 * Clear/reset navigation screen
 */
void ui_navigation_screen_clear(void);

/** Hide all navigation line/arrow/flag objects (safe when switching screens) */
void ui_navigation_hide_all_objects(void);

/**
 * Update status bar (optional helpers)
 */
void ui_navigation_screen_set_ble(bool connected);
void ui_navigation_screen_set_signal(int bars);

#endif // UI_NAVIGATION_SCREEN_H

