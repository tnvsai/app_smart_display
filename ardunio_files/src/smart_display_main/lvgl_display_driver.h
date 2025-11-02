#ifndef LVGL_DISPLAY_DRIVER_H
#define LVGL_DISPLAY_DRIVER_H

#include <lvgl.h>
#include <Arduino_GFX_Library.h>
#include "esp_lcd_touch_axs5106l.h"

// Display configuration
#define DISPLAY_WIDTH 172
#define DISPLAY_HEIGHT 320

// Forward declarations
extern Arduino_GFX *gfx;
extern bool touchEnabled;

// LVGL display buffer (dynamically allocated)
extern lv_disp_draw_buf_t draw_buf;
extern lv_color_t *disp_draw_buf;
extern uint32_t screenWidth;
extern uint32_t screenHeight;
extern uint32_t bufSize;

// LVGL display and input device
extern lv_disp_drv_t disp_drv;
extern lv_indev_drv_t indev_drv;
extern lv_disp_t *disp;
extern lv_indev_t *indev;

/**
 * LVGL display flush callback
 * Called by LVGL when a display area needs to be refreshed
 */
void lvgl_display_flush(lv_disp_drv_t *disp_drv, const lv_area_t *area, lv_color_t *color_p);

/**
 * LVGL input device read callback
 * Called by LVGL to get touch input
 */
void lvgl_touchpad_read(lv_indev_drv_t *indev_drv, lv_indev_data_t *data);

/**
 * Initialize LVGL display driver
 * Sets up display buffers, flush callback, and touch input
 */
void lvgl_display_init(Arduino_GFX *display);

/**
 * Initialize LVGL library
 * Must be called before using any LVGL functions
 */
void lvgl_init(void);

#endif // LVGL_DISPLAY_DRIVER_H

