#include <Arduino.h>
#include "ui_incoming_call_screen.h"
#include "ui_theme.h"
#include <string.h>
#define COLOR_TEXT_PRIMARY 0xFFFF  // Ensure theme constants available

// UI element references
static lv_obj_t *label_header = nullptr;
// REMOVED: arc_pulse (causing crashes) - static avatar only
static lv_obj_t *label_name = nullptr;
static lv_obj_t *label_number = nullptr;
static lv_obj_t *btn_dismiss = nullptr;
static lv_obj_t *img_avatar = nullptr;  // Avatar circle with initial

// Animation objects
static lv_anim_t pulse_anim;
static lv_anim_t vibrate_anim;

// Callbacks
static decline_callback_t dismiss_cb = nullptr;  // Use decline callback as dismiss

// Styles
static lv_style_t style_bg_red;
static lv_style_t style_btn_green;
static lv_style_t style_btn_red;
static lv_style_t style_title;
static lv_style_t style_name;
static lv_style_t style_number;

// Style initialization guard
static bool incoming_styles_initialized = false;

// Button event callbacks
static void btn_dismiss_event_cb(lv_event_t *e) {
    if (dismiss_cb) {
        dismiss_cb();
    }
}

// Pulse animation callback - DISABLED for stability
static void pulse_anim_cb(void *var, int32_t value) {
    // DISABLED - causing crashes
    return;
    
    if (!var) return;
    lv_obj_t *arc = (lv_obj_t *)var;
    if (!lv_obj_is_valid(arc)) return;
    
    lv_obj_set_style_arc_width(arc, value, LV_PART_MAIN);
    
    // Fade opacity from 255 to 100
    uint8_t opacity = 255 - (value * 155 / 30);  // 30 is max width
    lv_obj_set_style_arc_opa(arc, opacity, LV_PART_MAIN);
}

static void pulse_anim_ready_cb(lv_anim_t *a) {
    // DISABLED - causing crashes
    return;
}

void ui_incoming_call_screen_create(lv_obj_t *parent) {
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_incoming_call_screen_create");
        return;
    }
    
    // Set screen background to black
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, LV_PART_MAIN);
    
    // Initialize styles only once (critical - re-initializing causes crash)
    if (!incoming_styles_initialized) {
        Serial.println("[UI] Initializing incoming call screen styles...");
        
        lv_style_init(&style_bg_red);
        lv_style_set_bg_color(&style_bg_red, lv_color_hex(0xFF0000));
        lv_style_set_bg_opa(&style_bg_red, LV_OPA_COVER);
        lv_style_set_pad_all(&style_bg_red, 8);
        
        // Dismiss button style (cyan, like theme)
        lv_style_init(&style_btn_green);  // Reuse variable name for dismiss button
        lv_style_set_bg_color(&style_btn_green, lv_color_hex(0x00FFFF));  // Cyan
        lv_style_set_bg_opa(&style_btn_green, LV_OPA_COVER);
        lv_style_set_radius(&style_btn_green, 25);  // Rounded corners
        lv_style_set_border_width(&style_btn_green, 0);
        lv_style_set_pad_all(&style_btn_green, 10);
        
        lv_style_init(&style_title);
        lv_style_set_text_color(&style_title, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_title, lv_font_default());
        lv_style_set_text_align(&style_title, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_name);
        lv_style_set_text_color(&style_name, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_name, lv_font_default());
        lv_style_set_text_align(&style_name, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_number);
        lv_style_set_text_color(&style_number, lv_color_hex(0x808080));
        lv_style_set_text_font(&style_number, lv_font_default());
        lv_style_set_text_align(&style_number, LV_TEXT_ALIGN_CENTER);
        
        incoming_styles_initialized = true;
        Serial.println("[UI] Incoming call styles initialized");
    }
    
    // Create header bar (subtle, top)
    label_header = lv_label_create(parent);
    lv_obj_add_style(label_header, &style_title, 0);
    lv_label_set_text(label_header, "Incoming Call");
    lv_obj_align(label_header, LV_ALIGN_TOP_MID, 0, 10);
    
    // REMOVED: Pulsing arc (causing crashes) - use simple static avatar instead
    
    // Create simple avatar circle (80px, centered) - ULTRA SIMPLE
    img_avatar = lv_obj_create(parent);
    lv_obj_set_size(img_avatar, 80, 80);
    lv_obj_set_style_radius(img_avatar, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(img_avatar, lv_color_hex(0x3186), 0);  // Use theme color
    lv_obj_set_style_border_width(img_avatar, 3, 0);
    lv_obj_set_style_border_color(img_avatar, lv_color_hex(0x07FF), 0);  // Bright cyan border
    lv_obj_align(img_avatar, LV_ALIGN_CENTER, 0, -40);
    lv_obj_clear_flag(img_avatar, LV_OBJ_FLAG_CLICKABLE);
    
    // Create initial label inside avatar (required for update function)
    lv_obj_t *avatar_label = lv_label_create(img_avatar);
    lv_obj_set_style_text_color(avatar_label, lv_color_hex(COLOR_TEXT_PRIMARY), LV_PART_MAIN);
    lv_obj_set_style_text_font(avatar_label, lv_font_default(), LV_PART_MAIN);
    lv_obj_set_style_text_align(avatar_label, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_text(avatar_label, "?");
    lv_obj_center(avatar_label);
    
    // Create name label (below avatar, large and bright)
    label_name = lv_label_create(parent);
    lv_obj_add_style(label_name, &style_name, 0);
    lv_label_set_text(label_name, "Caller");
    lv_obj_set_style_text_color(label_name, lv_color_hex(COLOR_TEXT_PRIMARY), LV_PART_MAIN);  // Bright white
    lv_obj_align(label_name, LV_ALIGN_CENTER, 0, 20);
    
    // Create phone number label
    label_number = lv_label_create(parent);
    lv_obj_add_style(label_number, &style_number, 0);
    lv_label_set_text(label_number, "");
    lv_obj_align(label_number, LV_ALIGN_CENTER, 0, 50);
    
    // Create single dismiss button (center bottom, cyan)
    btn_dismiss = lv_btn_create(parent);
    lv_obj_set_size(btn_dismiss, BUTTON_DISMISS_WIDTH, BUTTON_DISMISS_HEIGHT);
    lv_obj_add_style(btn_dismiss, &style_btn_green, 0);  // Uses cyan color from style
    lv_obj_align(btn_dismiss, LV_ALIGN_BOTTOM_MID, 0, -30);
    lv_obj_add_event_cb(btn_dismiss, btn_dismiss_event_cb, LV_EVENT_CLICKED, nullptr);
    
    // Add "Dismiss" text label to button
    lv_obj_t *label_dismiss_text = lv_label_create(btn_dismiss);
    lv_label_set_text(label_dismiss_text, "Dismiss");
    lv_obj_set_style_text_color(label_dismiss_text, lv_color_hex(0x000000), 0);  // Black text on cyan
    lv_obj_set_style_text_font(label_dismiss_text, lv_font_default(), 0);
    lv_obj_center(label_dismiss_text);
    
    // Add instruction text (above button) - reuse existing style_number
    lv_obj_t *label_instruction = lv_label_create(parent);
    lv_obj_add_style(label_instruction, &style_number, 0);  // Reuse existing style
    lv_label_set_text(label_instruction, "Tap to dismiss");
    lv_obj_align(label_instruction, LV_ALIGN_BOTTOM_MID, 0, -90);
    
    // DISABLED: Pulse animation initialization (causing crashes)
    // Animation will remain disabled until stability is confirmed
    
    Serial.println("[UI] Incoming call screen created");
}

void ui_incoming_call_screen_update(const char *name, const char *number) {
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
    
    if (label_number && lv_obj_is_valid(label_number)) {
        if (number && strlen(number) > 0 && strcmp(number, "Unknown") != 0) {
            lv_label_set_text(label_number, number);
        } else {
            lv_label_set_text(label_number, "");
        }
    }
}

void ui_incoming_call_screen_start_ringing(void) {
    // DISABLED - animation causing crashes, just show the arc
    // REMOVED: arc_pulse animation (disabled for stability)
    if (false) {  // Never execute - arc_pulse removed
        Serial.println("[UI] Ringing indicator shown (animation disabled)");
    } else {
        Serial.println("[UI] Warning: arc_pulse invalid");
    }
}

void ui_incoming_call_screen_stop_animations(void) {
    // REMOVED: arc_pulse (no longer exists)
    if (false) {  // Never execute - arc_pulse removed
        Serial.println("[UI] Stopped ringing animation");
    }
    // Note: Screen-level animations are managed by ui_screens
}

void ui_incoming_call_screen_set_callbacks(accept_callback_t accept_cb_fn, decline_callback_t decline_cb_fn) {
    // Accept callback ignored (MCU doesn't control mobile)
    // Only dismiss callback is used
    dismiss_cb = decline_cb_fn;  // Use decline callback as dismiss
}

// Screen reference (declared in ui_screens.cpp)
// Note: This is only used in stop_animations, and ui_screens.cpp exports it

