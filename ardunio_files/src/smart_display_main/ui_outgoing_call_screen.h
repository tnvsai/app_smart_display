#ifndef UI_OUTGOING_CALL_SCREEN_H
#define UI_OUTGOING_CALL_SCREEN_H

#include <lvgl.h>

/**
 * Create outgoing call screen UI
 * @param parent Screen object to attach UI elements to
 */
void ui_outgoing_call_screen_create(lv_obj_t *parent);

/**
 * Update outgoing call screen with caller information
 * @param name Caller/contact name
 */
void ui_outgoing_call_screen_update(const char *name);

/**
 * Update call status
 * @param connecting True if still connecting, false if connected
 */
void ui_outgoing_call_screen_set_connecting(bool connecting);

/**
 * Update call duration (only shown after connected)
 * @param duration_seconds Call duration in seconds
 */
void ui_outgoing_call_screen_update_duration(int duration_seconds);

/**
 * Hang-up/Dismiss button callback
 * Note: MCU doesn't control mobile, so this is just for dismissing the screen
 */
typedef void (*hangup_callback_t)(void);

void ui_outgoing_call_screen_set_hangup_callback(hangup_callback_t hangup_cb);

#endif // UI_OUTGOING_CALL_SCREEN_H

