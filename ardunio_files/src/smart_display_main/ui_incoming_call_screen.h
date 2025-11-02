#ifndef UI_INCOMING_CALL_SCREEN_H
#define UI_INCOMING_CALL_SCREEN_H

#include <lvgl.h>

/**
 * Create incoming call screen UI
 * @param parent Screen object to attach UI elements to
 */
void ui_incoming_call_screen_create(lv_obj_t *parent);

/**
 * Update incoming call screen with caller information
 * @param name Caller name
 * @param number Caller phone number
 */
void ui_incoming_call_screen_update(const char *name, const char *number);

/**
 * Start pulsing animation (ringing effect)
 */
void ui_incoming_call_screen_start_ringing(void);

/**
 * Stop animations
 */
void ui_incoming_call_screen_stop_animations(void);

/**
 * Dismiss button callback (MCU doesn't control mobile, so only dismiss is available)
 */
typedef void (*accept_callback_t)(void);  // Kept for compatibility but unused
typedef void (*decline_callback_t)(void);  // Used as dismiss callback

void ui_incoming_call_screen_set_callbacks(accept_callback_t accept_cb, decline_callback_t decline_cb);

#endif // UI_INCOMING_CALL_SCREEN_H

