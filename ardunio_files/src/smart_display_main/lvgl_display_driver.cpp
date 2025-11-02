#include <Arduino.h>
#include "lvgl_display_driver.h"
#include "esp_lcd_touch_axs5106l.h"  // For touch_data_t, bsp_touch_read, bsp_touch_get_coordinates

#ifdef ESP32
#include "esp_heap_caps.h"
#endif

// LVGL display draw buffer - use dynamic allocation like the working example
lv_disp_draw_buf_t draw_buf;
lv_color_t *disp_draw_buf = nullptr;  // Will be allocated dynamically

// LVGL display and input device drivers
lv_disp_drv_t disp_drv;
lv_indev_drv_t indev_drv;

// LVGL display and input device instances
lv_disp_t *disp;
lv_indev_t *indev;

// External display reference
extern Arduino_GFX *gfx;
extern bool touchEnabled;

// Buffer size will be calculated dynamically
uint32_t screenWidth = DISPLAY_WIDTH;
uint32_t screenHeight = DISPLAY_HEIGHT;
uint32_t bufSize;

/**
 * Display flush callback - transfers pixel data to display
 * IMPORTANT: Use Arduino_GFX bitmap drawing methods for MUCH better performance!
 */
void lvgl_display_flush(lv_disp_drv_t *disp_drv, const lv_area_t *area, lv_color_t *color_p) {
    uint32_t w = (area->x2 - area->x1 + 1);
    uint32_t h = (area->y2 - area->y1 + 1);

    if (w == 0 || h == 0) {
        lv_disp_flush_ready(disp_drv);
        return;
    }

    // Use Arduino_GFX bitmap drawing - MUCH faster than pixel-by-pixel!
    // Check if we need byte swap (LV_COLOR_16_SWAP)
#if (LV_COLOR_16_SWAP != 0)
    gfx->draw16bitBeRGBBitmap(area->x1, area->y1, (uint16_t *)&color_p->full, w, h);
#else
    gfx->draw16bitRGBBitmap(area->x1, area->y1, (uint16_t *)&color_p->full, w, h);
#endif

    // Inform LVGL that flushing is done
    lv_disp_flush_ready(disp_drv);
}

/**
 * Touch input read callback
 * Based on working example implementation
 */
void lvgl_touchpad_read(lv_indev_drv_t *indev_drv, lv_indev_data_t *data) {
    touch_data_t touch_data;
    uint8_t touchpad_cnt = 0;

    /* Read touch controller data */
    bsp_touch_read();
    /* Get coordinates */
    bool touchpad_pressed = bsp_touch_get_coordinates(&touch_data);

    if (touchpad_pressed && touch_data.touch_num > 0) {
        data->point.x = touch_data.coords[0].x;
        data->point.y = touch_data.coords[0].y;
        data->state = LV_INDEV_STATE_PRESSED;
    } else {
        data->state = LV_INDEV_STATE_RELEASED;
    }
}

/**
 * Initialize LVGL display driver
 * Based on working example - uses proper ESP32 memory allocation
 */
void lvgl_display_init(Arduino_GFX *display) {
    // Get actual screen dimensions
    screenWidth = display->width();
    screenHeight = display->height();
    
    // Calculate buffer size (use partial buffer for efficiency)
    // Working example uses screenWidth * 40 for partial rendering
    bufSize = screenWidth * 40;
    
    Serial.printf("[LVGL] Screen: %dx%d, Buffer size: %d pixels\n", screenWidth, screenHeight, bufSize);
    
    // Allocate display buffer dynamically (like working example)
#ifdef ESP32
    // Try to allocate in internal memory first
    disp_draw_buf = (lv_color_t *)heap_caps_malloc(bufSize * 2 * sizeof(lv_color_t), MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT);
    if (!disp_draw_buf) {
        // If that fails, try without internal flag
        disp_draw_buf = (lv_color_t *)heap_caps_malloc(bufSize * 2 * sizeof(lv_color_t), MALLOC_CAP_8BIT);
    }
#else
    // For non-ESP32 platforms
    disp_draw_buf = (lv_color_t *)malloc(bufSize * 2 * sizeof(lv_color_t));
#endif
    
    if (!disp_draw_buf) {
        Serial.println("[LVGL] ERROR: Failed to allocate display buffer!");
        Serial.printf("[LVGL] Tried to allocate: %d bytes\n", bufSize * 2 * sizeof(lv_color_t));
        return;
    }
    
    Serial.printf("[LVGL] Display buffer allocated: %d bytes (%d KB)\n", 
                  bufSize * 2 * sizeof(lv_color_t), 
                  (bufSize * 2 * sizeof(lv_color_t)) / 1024);
    
    // Initialize display buffer with double buffering
    // Working example: allocate bufSize * 2, pass first half and NULL (LVGL manages second half)
    // Actually, we should pass both halves explicitly for better control
    lv_color_t *buf1 = disp_draw_buf;
    lv_color_t *buf2 = disp_draw_buf + bufSize;
    lv_disp_draw_buf_init(&draw_buf, buf1, buf2, bufSize);
    
    // Initialize display driver
    lv_disp_drv_init(&disp_drv);
    disp_drv.hor_res = screenWidth;
    disp_drv.ver_res = screenHeight;
    disp_drv.flush_cb = lvgl_display_flush;
    disp_drv.draw_buf = &draw_buf;
    
    // Register display driver
    disp = lv_disp_drv_register(&disp_drv);
    
    Serial.println("[LVGL] Display driver initialized successfully");
}

/**
 * Initialize LVGL library and display
 * Based on working example - MUST call lv_init() first!
 */
void lvgl_init(void) {
    // Initialize LVGL FIRST (before anything else)
    lv_init();
    
    Serial.println("[LVGL] LVGL library initialized");
    String LVGL_Arduino = String('V') + lv_version_major() + "." + lv_version_minor() + "." + lv_version_patch();
    Serial.println("[LVGL] LVGL Version: " + LVGL_Arduino);
    
    // Display will be initialized after Arduino_GFX is ready
}

