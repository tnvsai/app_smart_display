#ifndef UI_MISSED_CALL_SCREEN_H
#define UI_MISSED_CALL_SCREEN_H

#include <lvgl.h>

/**
 * Create missed call screen UI
 * @param parent Screen object to attach UI elements to
 */
void ui_missed_call_screen_create(lv_obj_t *parent);

/**
 * Update missed call screen with caller information
 * @param name Caller name
 * @param number Caller phone number
 * @param count Number of missed calls from this caller
 * @param timestamp Timestamp string (formatted time)
 */
void ui_missed_call_screen_update(const char *name, const char *number, int count, const char *timestamp);

/**
 * Show missed call screen with slide animation
 */
void ui_missed_call_screen_show(void);

/**
 * Hide missed call screen
 */
void ui_missed_call_screen_hide(void);

/**
 * Dismiss callback (called when user dismisses)
 */
typedef void (*dismiss_callback_t)(void);

void ui_missed_call_screen_set_dismiss_callback(dismiss_callback_t dismiss_cb);

#endif // UI_MISSED_CALL_SCREEN_H

