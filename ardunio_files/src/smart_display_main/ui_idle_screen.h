#ifndef UI_IDLE_SCREEN_H
#define UI_IDLE_SCREEN_H

#include <lvgl.h>

/**
 * Create idle screen UI (MCU connected, maps off)
 * @param parent Screen object to attach UI elements to
 */
void ui_idle_screen_create(lv_obj_t *parent);

/**
 * Update idle screen with BLE connection status
 * @param connected True if BLE is connected
 */
void ui_idle_screen_update_ble_status(bool connected);

/**
 * Start pulsing animation for "ready" indicator
 */
void ui_idle_screen_start_pulse(void);

/**
 * Stop all animations
 */
void ui_idle_screen_stop_animations(void);

void ui_idle_screen_set_no_nav_msg(bool show);

#endif // UI_IDLE_SCREEN_H

