#ifndef UI_SCREENS_H
#define UI_SCREENS_H

#include <lvgl.h>

/**
 * Screen identifiers
 */
enum UIScreen {
    UI_SCREEN_NONE = 0,
    UI_SCREEN_WELCOME,           // Initial boot, connecting
    UI_SCREEN_IDLE,              // MCU connected, maps off (NEW)
    UI_SCREEN_NAVIGATION,        // Active navigation (LVGL version)
    UI_SCREEN_INCOMING_CALL,     // Incoming call
    UI_SCREEN_OUTGOING_CALL,     // Outgoing/ongoing call
    UI_SCREEN_MISSED_CALL        // Missed call notification
};

/**
 * Screen objects (created by individual screen modules)
 */
extern lv_obj_t *screen_welcome;
extern lv_obj_t *screen_idle;
extern lv_obj_t *screen_navigation;
extern lv_obj_t *screen_incoming_call;
extern lv_obj_t *screen_outgoing_call;
extern lv_obj_t *screen_missed_call;

/**
 * Current active screen
 */
extern UIScreen current_screen;

/**
 * Initialize all UI screens
 * Creates all screen objects but doesn't show them
 */
void ui_screens_init(void);

/**
 * Show a specific screen
 * @param screen Screen identifier to show
 * @param anim_time Animation duration in milliseconds (0 = no animation)
 */
void ui_show_screen(UIScreen screen, uint32_t anim_time = 300);

/**
 * Get current active screen
 */
UIScreen ui_get_current_screen(void);

/**
 * Hide current screen and show navigation
 */
void ui_show_navigation(void);

/**
 * Transition to screen with fade animation
 */
void ui_transition_fade(UIScreen from, UIScreen to, uint32_t time);

/**
 * Cleanup screens (free memory if needed)
 */
void ui_screens_cleanup(void);

#endif // UI_SCREENS_H

