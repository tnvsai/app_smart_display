#include <Arduino.h>
#include <stdio.h>
#include "ui_outgoing_call_screen.h"
#include <string.h>

// UI element references
static lv_obj_t *label_name = nullptr;
static lv_obj_t *label_status = nullptr;
static lv_obj_t *spinner = nullptr;
static lv_obj_t *label_duration = nullptr;
static lv_obj_t *btn_hangup = nullptr;
static lv_obj_t *img_avatar = nullptr;

// Callback
static hangup_callback_t hangup_cb = nullptr;

// Styles
static lv_style_t style_name;
static lv_style_t style_status;
static lv_style_t style_duration;
static lv_style_t style_btn_red;

// Style initialization guard
static bool outgoing_styles_initialized = false;

// Button event callback
static void btn_hangup_event_cb(lv_event_t *e) {
    if (hangup_cb) {
        hangup_cb();
    }
}

// Spinner animation callback (removed - spinner handles rotation automatically)
// LVGL spinner widget rotates on its own, no manual animation needed

// Spinner animation ready callback (not needed - spinner is self-animating)

void ui_outgoing_call_screen_create(lv_obj_t *parent) {
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_outgoing_call_screen_create");
        return;
    }
    
    // Set screen background to black
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, LV_PART_MAIN);
    
    // Initialize styles only once (critical - re-initializing causes crash)
    if (!outgoing_styles_initialized) {
        Serial.println("[UI] Initializing outgoing call screen styles...");
        
        lv_style_init(&style_name);
        lv_style_set_text_color(&style_name, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_name, lv_font_default());
        lv_style_set_text_align(&style_name, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_status);
        lv_style_set_text_color(&style_status, lv_color_hex(0x00FFFF));
        lv_style_set_text_font(&style_status, lv_font_default());
        lv_style_set_text_align(&style_status, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_duration);
        lv_style_set_text_color(&style_duration, lv_color_hex(0x808080));
        lv_style_set_text_font(&style_duration, lv_font_default());
        lv_style_set_text_align(&style_duration, LV_TEXT_ALIGN_RIGHT);
        
        lv_style_init(&style_btn_red);
        lv_style_set_bg_color(&style_btn_red, lv_color_hex(0xFF0000));
        lv_style_set_bg_opa(&style_btn_red, LV_OPA_COVER);
        lv_style_set_radius(&style_btn_red, LV_RADIUS_CIRCLE);
        lv_style_set_border_width(&style_btn_red, 0);
        
        outgoing_styles_initialized = true;
        Serial.println("[UI] Outgoing call styles initialized");
    }
    
    // Create avatar circle (center top)
    img_avatar = lv_obj_create(parent);
    lv_obj_set_size(img_avatar, 80, 80);
    lv_obj_set_style_radius(img_avatar, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(img_avatar, lv_color_hex(0x2a2a2a), 0);
    lv_obj_set_style_border_width(img_avatar, 2, 0);
    lv_obj_set_style_border_color(img_avatar, lv_color_hex(0x00FFFF), 0);
    lv_obj_align(img_avatar, LV_ALIGN_TOP_MID, 0, 50);
    lv_obj_clear_flag(img_avatar, LV_OBJ_FLAG_CLICKABLE);
    
    // Create initial label inside avatar (required for update function)
    lv_obj_t *avatar_label = lv_label_create(img_avatar);
    lv_obj_set_style_text_color(avatar_label, lv_color_hex(0xFFFFFF), LV_PART_MAIN);
    lv_obj_set_style_text_font(avatar_label, lv_font_default(), LV_PART_MAIN);
    lv_obj_set_style_text_align(avatar_label, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_text(avatar_label, "?");
    lv_obj_center(avatar_label);
    
    // Create name label (below avatar)
    label_name = lv_label_create(parent);
    lv_obj_add_style(label_name, &style_name, 0);
    lv_label_set_text(label_name, "Calling...");
    lv_obj_align(label_name, LV_ALIGN_CENTER, 0, 20);
    
    // Create status label ("Calling..." with animated dots)
    label_status = lv_label_create(parent);
    lv_obj_add_style(label_status, &style_status, 0);
    lv_label_set_text(label_status, "Calling");
    lv_obj_align(label_status, LV_ALIGN_CENTER, 0, 50);
    
    // Create spinner (rotating dial animation, centered)
    spinner = lv_spinner_create(parent, 2000, 60);  // 2 second period, 60 degree arc
    lv_obj_set_size(spinner, 100, 100);
    lv_obj_set_style_arc_color(spinner, lv_color_hex(0x00FFFF), LV_PART_MAIN);
    lv_obj_set_style_arc_color(spinner, lv_color_hex(0x00FFFF), LV_PART_INDICATOR);
    lv_obj_set_style_arc_width(spinner, 6, LV_PART_MAIN);
    lv_obj_set_style_arc_width(spinner, 6, LV_PART_INDICATOR);
    lv_obj_align(spinner, LV_ALIGN_CENTER, 0, -10);
    
    // Create duration label (top right, hidden initially)
    label_duration = lv_label_create(parent);
    lv_obj_add_style(label_duration, &style_duration, 0);
    lv_label_set_text(label_duration, "");
    lv_obj_align(label_duration, LV_ALIGN_TOP_RIGHT, -10, 10);
    lv_obj_add_flag(label_duration, LV_OBJ_FLAG_HIDDEN);
    
    // Create hang-up button (red, bottom center, circular)
    btn_hangup = lv_btn_create(parent);
    lv_obj_set_size(btn_hangup, 70, 70);
    lv_obj_add_style(btn_hangup, &style_btn_red, 0);
    lv_obj_align(btn_hangup, LV_ALIGN_BOTTOM_MID, 0, -30);
    lv_obj_add_event_cb(btn_hangup, btn_hangup_event_cb, LV_EVENT_CLICKED, nullptr);
    
    // Add phone-off icon to hang-up button
    lv_obj_t *label_hangup_icon = lv_label_create(btn_hangup);
    lv_label_set_text(label_hangup_icon, LV_SYMBOL_CLOSE);
    lv_obj_set_style_text_color(label_hangup_icon, lv_color_hex(0xFFFFFF), 0);
    lv_obj_set_style_text_font(label_hangup_icon, lv_font_default(), 0);
    lv_obj_center(label_hangup_icon);
    
    Serial.println("[UI] Outgoing call screen created");
}

void ui_outgoing_call_screen_update(const char *name) {
    if (label_name && lv_obj_is_valid(label_name)) {
        if (name && strlen(name) > 0) {
            lv_label_set_text(label_name, name);
            
            // Update avatar with first letter (safe check for name length)
            if (img_avatar && lv_obj_is_valid(img_avatar) && name && strlen(name) > 0) {
                lv_obj_t *avatar_label = lv_obj_get_child(img_avatar, 0);
                if (avatar_label && lv_obj_is_valid(avatar_label)) {
                    char initial[2] = {name[0], '\0'};
                    lv_label_set_text(avatar_label, initial);
                }
            }
        } else {
            lv_label_set_text(label_name, "Unknown");
        }
    }
}

void ui_outgoing_call_screen_set_connecting(bool connecting) {
    if (label_status && lv_obj_is_valid(label_status)) {
        if (connecting) {
            lv_label_set_text(label_status, "Calling");
            // Show spinner
            if (spinner && lv_obj_is_valid(spinner)) {
                lv_obj_clear_flag(spinner, LV_OBJ_FLAG_HIDDEN);
            }
            // Hide duration
            if (label_duration && lv_obj_is_valid(label_duration)) {
                lv_obj_add_flag(label_duration, LV_OBJ_FLAG_HIDDEN);
            }
        } else {
            lv_label_set_text(label_status, "Connected");
            // Hide spinner
            if (spinner && lv_obj_is_valid(spinner)) {
                lv_obj_add_flag(spinner, LV_OBJ_FLAG_HIDDEN);
            }
            // Show duration
            if (label_duration && lv_obj_is_valid(label_duration)) {
                lv_obj_clear_flag(label_duration, LV_OBJ_FLAG_HIDDEN);
            }
        }
    }
}

void ui_outgoing_call_screen_update_duration(int duration_seconds) {
    if (label_duration && lv_obj_is_valid(label_duration)) {
        int minutes = duration_seconds / 60;
        int seconds = duration_seconds % 60;
        char duration_str[16];
        snprintf(duration_str, sizeof(duration_str), "%02d:%02d", minutes, seconds);
        lv_label_set_text(label_duration, duration_str);
    }
}

void ui_outgoing_call_screen_set_hangup_callback(hangup_callback_t hangup_cb_fn) {
    hangup_cb = hangup_cb_fn;
    Serial.println("[UI] Outgoing call hangup callback registered");
}

