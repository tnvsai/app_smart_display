#include "ui_theme.h"
#include <Arduino.h>
#include <string.h>

// Global style objects
static lv_style_t style_large_text;
static lv_style_t style_medium_text;
static lv_style_t style_normal_text;
static lv_style_t style_small_text;
static lv_style_t style_button;
static lv_style_t style_status_badge;

static bool styles_initialized = false;

void ui_theme_init(void) {
    if (styles_initialized) return;
    
    // Large text style (distance display)
    lv_style_init(&style_large_text);
    lv_style_set_text_color(&style_large_text, lv_color_hex(COLOR_TEXT_PRIMARY));
    lv_style_set_text_font(&style_large_text, &lv_font_montserrat_28);
    lv_style_set_text_align(&style_large_text, LV_TEXT_ALIGN_CENTER);
    
    // Medium text style (maneuver)
    lv_style_init(&style_medium_text);
    lv_style_set_text_color(&style_medium_text, lv_color_hex(COLOR_TEXT_PRIMARY));
    lv_style_set_text_font(&style_medium_text, &lv_font_montserrat_20);
    lv_style_set_text_align(&style_medium_text, LV_TEXT_ALIGN_CENTER);
    
    // Normal text style
    lv_style_init(&style_normal_text);
    lv_style_set_text_color(&style_normal_text, lv_color_hex(COLOR_TEXT_PRIMARY));
    lv_style_set_text_font(&style_normal_text, lv_font_default());
    lv_style_set_text_align(&style_normal_text, LV_TEXT_ALIGN_CENTER);
    
    // Small text style (status)
    lv_style_init(&style_small_text);
    lv_style_set_text_color(&style_small_text, lv_color_hex(COLOR_TEXT_SECONDARY));
    lv_style_set_text_font(&style_small_text, lv_font_default());
    lv_style_set_text_align(&style_small_text, LV_TEXT_ALIGN_CENTER);
    
    // Button style
    lv_style_init(&style_button);
    lv_style_set_bg_color(&style_button, lv_color_hex(COLOR_ACCENT_CYAN));
    lv_style_set_bg_opa(&style_button, LV_OPA_COVER);
    lv_style_set_radius(&style_button, 25);
    lv_style_set_border_width(&style_button, 0);
    lv_style_set_text_color(&style_button, lv_color_hex(COLOR_TEXT_PRIMARY));
    lv_style_set_text_font(&style_button, lv_font_default());
    lv_style_set_pad_all(&style_button, 10);
    
    // Status badge style
    lv_style_init(&style_status_badge);
    lv_style_set_bg_color(&style_status_badge, lv_color_hex(COLOR_BG_SECONDARY));
    lv_style_set_bg_opa(&style_status_badge, LV_OPA_COVER);
    lv_style_set_radius(&style_status_badge, 8);
    lv_style_set_pad_all(&style_status_badge, 5);
    lv_style_set_text_color(&style_status_badge, lv_color_hex(COLOR_STATUS_ACTIVE));
    lv_style_set_text_font(&style_status_badge, lv_font_default());
    
    styles_initialized = true;
}

lv_style_t* ui_theme_get_large_text_style(void) {
    return &style_large_text;
}

lv_style_t* ui_theme_get_medium_text_style(void) {
    return &style_medium_text;
}

lv_style_t* ui_theme_get_normal_text_style(void) {
    return &style_normal_text;
}

lv_style_t* ui_theme_get_small_text_style(void) {
    return &style_small_text;
}

lv_style_t* ui_theme_get_button_style(void) {
    return &style_button;
}

lv_style_t* ui_theme_get_status_badge_style(void) {
    return &style_status_badge;
}

uint16_t ui_theme_get_arrow_color(const char* direction) {
    if (!direction) return COLOR_ARROW_STRAIGHT;
    
    // Convert to lowercase for comparison
    char dir_lower[16];
    strncpy(dir_lower, direction, sizeof(dir_lower) - 1);
    dir_lower[sizeof(dir_lower) - 1] = '\0';
    for (int i = 0; dir_lower[i]; i++) {
        if (dir_lower[i] >= 'A' && dir_lower[i] <= 'Z') {
            dir_lower[i] = dir_lower[i] - 'A' + 'a';
        }
    }
    
    // Determine color based on direction (check more specific first)
    if (strstr(dir_lower, "destination") || strstr(dir_lower, "arrived") || strstr(dir_lower, "destination_reached")) {
        return COLOR_ARROW_DEST;  // Red
    } else if (strstr(dir_lower, "sharp_left") || strstr(dir_lower, "sharp_right") || strstr(dir_lower, "sharp-left") || strstr(dir_lower, "sharp-right")) {
        return COLOR_ARROW_SHARP;  // Orange
    } else if (strstr(dir_lower, "slight_left") || strstr(dir_lower, "slight_right") || strstr(dir_lower, "slight-left") || strstr(dir_lower, "slight-right")) {
        return COLOR_ARROW_SLIGHT;  // Cyan
    } else if (strstr(dir_lower, "merge_left") || strstr(dir_lower, "merge-right") || 
               strstr(dir_lower, "merge_right") || strstr(dir_lower, "merge")) {
        return COLOR_ARROW_MERGE;  // Purple
    } else if (strstr(dir_lower, "keep_left") || strstr(dir_lower, "keep-right") || 
               strstr(dir_lower, "keep_right") || strstr(dir_lower, "keep")) {
        return COLOR_ARROW_KEEP;  // Purple
    } else if (strstr(dir_lower, "uturn") || strstr(dir_lower, "u-turn") || strstr(dir_lower, "u_turn")) {
        return COLOR_ARROW_UTURN;  // Green
    } else if (strstr(dir_lower, "straight") || strstr(dir_lower, "continue")) {
        return COLOR_ARROW_STRAIGHT;  // Green
    } else if (strstr(dir_lower, "left")) {
        return COLOR_ARROW_LEFT;  // Green
    } else if (strstr(dir_lower, "right")) {
        return COLOR_ARROW_RIGHT;  // Green
    } else {
        return COLOR_ARROW_STRAIGHT;  // Default to green/straight
    }
}

