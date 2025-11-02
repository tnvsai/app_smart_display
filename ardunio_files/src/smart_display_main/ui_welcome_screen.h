#ifndef UI_WELCOME_SCREEN_H
#define UI_WELCOME_SCREEN_H

#include <lvgl.h>

/**
 * Create welcome screen UI
 * @param parent Screen object to attach UI elements to
 */
void ui_welcome_screen_create(lv_obj_t *parent);

/**
 * Update welcome screen with BLE connection status
 * @param connected True if BLE is connected
 */
void ui_welcome_screen_update_ble_status(bool connected);

/**
 * Show/hide welcome screen
 * Called automatically based on navigation/call state
 */
void ui_welcome_screen_show(void);
void ui_welcome_screen_hide(void);

#endif // UI_WELCOME_SCREEN_H

