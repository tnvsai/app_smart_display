#include <Arduino.h>
#include <stdio.h>
#include "ui_missed_call_screen.h"
#include <string.h>

// UI element references
static lv_obj_t *img_icon = nullptr;  // Missed call icon
static lv_obj_t *label_name = nullptr;
static lv_obj_t *label_number = nullptr;
static lv_obj_t *label_timestamp = nullptr;
static lv_obj_t *badge_count = nullptr;  // Count badge (if multiple)
static lv_obj_t *btn_dismiss = nullptr;
static lv_obj_t *card = nullptr;  // Main notification card

// Animation
static lv_anim_t slide_anim;

// Callback
static dismiss_callback_t dismiss_cb = nullptr;

// Styles
static lv_style_t style_card;
static lv_style_t style_icon;
static lv_style_t style_name;
static lv_style_t style_number;
static lv_style_t style_timestamp;
static lv_style_t style_badge;
static lv_style_t style_btn_ok;

// Style initialization guard
static bool missed_styles_initialized = false;

// Button event callback
static void btn_dismiss_event_cb(lv_event_t *e) {
    // Immediately hide the missed call screen first
    ui_missed_call_screen_hide();
    
    // Then call the dismiss callback to switch screens
    if (dismiss_cb) {
        dismiss_cb();
    }
}

// Screen tap callback (tap anywhere to dismiss)
static void screen_tap_event_cb(lv_event_t *e) {
    lv_event_code_t code = lv_event_get_code(e);
    if (code == LV_EVENT_CLICKED) {
        // Immediately hide the missed call screen first
        ui_missed_call_screen_hide();
        
        // Then call the dismiss callback to switch screens
        if (dismiss_cb) {
            dismiss_cb();
        }
    }
}

// Slide animation callback
static void slide_anim_cb(void *var, int32_t value) {
    lv_obj_t *obj = (lv_obj_t *)var;
    lv_obj_set_y(obj, value);
}

void ui_missed_call_screen_create(lv_obj_t *parent) {
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_missed_call_screen_create");
        return;
    }
    
    // Set screen background to semi-transparent black (overlay effect)
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_80, LV_PART_MAIN);  // 80% opacity for overlay
    
    // Add tap event to screen (tap anywhere to dismiss)
    lv_obj_add_event_cb(parent, screen_tap_event_cb, LV_EVENT_CLICKED, nullptr);
    
    // Initialize styles only once (critical - re-initializing causes crash)
    if (!missed_styles_initialized) {
        Serial.println("[UI] Initializing missed call screen styles...");
        
        lv_style_init(&style_card);
        lv_style_set_bg_color(&style_card, lv_color_hex(0x1a1a1a));
        lv_style_set_bg_opa(&style_card, LV_OPA_COVER);
        lv_style_set_radius(&style_card, 12);
        lv_style_set_pad_all(&style_card, 16);
        lv_style_set_border_width(&style_card, 2);
        lv_style_set_border_color(&style_card, lv_color_hex(0xFF0000));
        
        lv_style_init(&style_icon);
        lv_style_set_text_color(&style_icon, lv_color_hex(0xFF0000));
        lv_style_set_text_font(&style_icon, lv_font_default());
        
        lv_style_init(&style_name);
        lv_style_set_text_color(&style_name, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_name, lv_font_default());
        lv_style_set_text_align(&style_name, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_number);
        lv_style_set_text_color(&style_number, lv_color_hex(0x808080));
        lv_style_set_text_font(&style_number, lv_font_default());
        lv_style_set_text_align(&style_number, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_timestamp);
        lv_style_set_text_color(&style_timestamp, lv_color_hex(0x666666));
        lv_style_set_text_font(&style_timestamp, lv_font_default());
        lv_style_set_text_align(&style_timestamp, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_badge);
        lv_style_set_bg_color(&style_badge, lv_color_hex(0xFF0000));
        lv_style_set_bg_opa(&style_badge, LV_OPA_COVER);
        lv_style_set_text_color(&style_badge, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_badge, lv_font_default());
        lv_style_set_radius(&style_badge, LV_RADIUS_CIRCLE);
        lv_style_set_pad_all(&style_badge, 4);
        
        lv_style_init(&style_btn_ok);
        lv_style_set_bg_color(&style_btn_ok, lv_color_hex(0x00FFFF));
        lv_style_set_bg_opa(&style_btn_ok, LV_OPA_COVER);
        lv_style_set_radius(&style_btn_ok, 20);
        lv_style_set_border_width(&style_btn_ok, 0);
        
        missed_styles_initialized = true;
        Serial.println("[UI] Missed call styles initialized");
    }
    
    // Create notification card (centered, slides from top)
    card = lv_obj_create(parent);
    lv_obj_add_style(card, &style_card, 0);
    lv_obj_set_size(card, 150, 200);
    lv_obj_align(card, LV_ALIGN_TOP_MID, 0, -220);  // Start off-screen
    lv_obj_clear_flag(card, LV_OBJ_FLAG_SCROLLABLE);
    
    // Create missed call icon (top center of card)
    img_icon = lv_label_create(card);
    lv_obj_add_style(img_icon, &style_icon, 0);
    lv_label_set_text(img_icon, LV_SYMBOL_WARNING);  // Use warning symbol as missed call icon
    lv_obj_align(img_icon, LV_ALIGN_TOP_MID, 0, 10);
    
    // Create count badge (top right of card, hidden initially)
    badge_count = lv_obj_create(card);
    lv_obj_add_style(badge_count, &style_badge, 0);
    lv_obj_set_size(badge_count, 30, 30);
    lv_obj_align(badge_count, LV_ALIGN_TOP_RIGHT, -10, 10);
    lv_obj_add_flag(badge_count, LV_OBJ_FLAG_HIDDEN);
    
    lv_obj_t *label_badge_text = lv_label_create(badge_count);
    lv_label_set_text(label_badge_text, "1");
    lv_obj_center(label_badge_text);
    
    // Create name label
    label_name = lv_label_create(card);
    lv_obj_add_style(label_name, &style_name, 0);
    lv_label_set_text(label_name, "Missed Call");
    lv_obj_align(label_name, LV_ALIGN_CENTER, 0, -30);
    
    // Create phone number label
    label_number = lv_label_create(card);
    lv_obj_add_style(label_number, &style_number, 0);
    lv_label_set_text(label_number, "");
    lv_obj_align(label_number, LV_ALIGN_CENTER, 0, 0);
    
    // Create timestamp label
    label_timestamp = lv_label_create(card);
    lv_obj_add_style(label_timestamp, &style_timestamp, 0);
    lv_label_set_text(label_timestamp, "");
    lv_obj_align(label_timestamp, LV_ALIGN_CENTER, 0, 20);
    
    // Create dismiss button (bottom of card)
    btn_dismiss = lv_btn_create(card);
    lv_obj_add_style(btn_dismiss, &style_btn_ok, 0);
    lv_obj_set_size(btn_dismiss, 120, 35);
    lv_obj_align(btn_dismiss, LV_ALIGN_BOTTOM_MID, 0, -10);
    lv_obj_add_event_cb(btn_dismiss, btn_dismiss_event_cb, LV_EVENT_CLICKED, nullptr);
    
    lv_obj_t *label_ok = lv_label_create(btn_dismiss);
    lv_label_set_text(label_ok, "OK");
    lv_obj_set_style_text_color(label_ok, lv_color_hex(0x000000), 0);
    lv_obj_center(label_ok);
    
    Serial.println("[UI] Missed call screen created");
}

void ui_missed_call_screen_update(const char *name, const char *number, int count, const char *timestamp) {
    // Update name
    if (label_name && lv_obj_is_valid(label_name)) {
        if (name && strlen(name) > 0) {
            lv_label_set_text(label_name, name);
        } else {
            lv_label_set_text(label_name, "Unknown Caller");
        }
    }
    
    // Update number
    if (label_number && lv_obj_is_valid(label_number)) {
        if (number && strlen(number) > 0 && strcmp(number, "Unknown") != 0) {
            lv_label_set_text(label_number, number);
        } else {
            lv_label_set_text(label_number, "");
        }
    }
    
    // Update count badge
    if (badge_count && lv_obj_is_valid(badge_count)) {
        if (count > 1) {
            lv_obj_clear_flag(badge_count, LV_OBJ_FLAG_HIDDEN);
            lv_obj_t *label_badge = lv_obj_get_child(badge_count, 0);
            if (label_badge && lv_obj_is_valid(label_badge)) {
                char count_str[8];
                snprintf(count_str, sizeof(count_str), "%d", count);
                lv_label_set_text(label_badge, count_str);
            }
        } else {
            lv_obj_add_flag(badge_count, LV_OBJ_FLAG_HIDDEN);
        }
    }
    
    // Update timestamp
    if (label_timestamp && lv_obj_is_valid(label_timestamp)) {
        if (timestamp && strlen(timestamp) > 0) {
            lv_label_set_text(label_timestamp, timestamp);
        } else {
            lv_label_set_text(label_timestamp, "");
        }
    }
}

void ui_missed_call_screen_show(void) {
    if (card && lv_obj_is_valid(card)) {
        // Reset position (off-screen)
        lv_obj_set_y(card, -220);
        
        // Slide in animation
        lv_anim_init(&slide_anim);
        lv_anim_set_var(&slide_anim, card);
        lv_anim_set_values(&slide_anim, -220, 40);  // Slide from top to center
        lv_anim_set_time(&slide_anim, 400);  // 400ms
        lv_anim_set_exec_cb(&slide_anim, slide_anim_cb);
        lv_anim_start(&slide_anim);
        Serial.println("[UI] Started missed call slide-in animation");
    } else {
        Serial.println("[UI] Warning: card invalid, cannot show");
    }
}

void ui_missed_call_screen_hide(void) {
    if (card && lv_obj_is_valid(card)) {
        // Slide out animation
        lv_anim_init(&slide_anim);
        lv_anim_set_var(&slide_anim, card);
        lv_anim_set_values(&slide_anim, lv_obj_get_y(card), -220);
        lv_anim_set_time(&slide_anim, 300);
        lv_anim_set_exec_cb(&slide_anim, slide_anim_cb);
        lv_anim_start(&slide_anim);
        Serial.println("[UI] Started missed call slide-out animation");
    } else {
        Serial.println("[UI] Warning: card invalid, cannot hide");
    }
}

void ui_missed_call_screen_set_dismiss_callback(dismiss_callback_t dismiss_cb_fn) {
    dismiss_cb = dismiss_cb_fn;
}

