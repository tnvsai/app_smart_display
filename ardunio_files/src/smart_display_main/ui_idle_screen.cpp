#include <Arduino.h>
#include "ui_idle_screen.h"
#include "ui_theme.h"

// UI element references
static lv_obj_t *label_title = nullptr;
static lv_obj_t *label_subtitle = nullptr;
static lv_obj_t *label_no_nav = nullptr;
// New: keep a reference to parent to lazily create indicators
static lv_obj_t* idle_root = nullptr;
// Optional small indicators
static lv_obj_t* indicator_ble = nullptr;
static lv_obj_t* indicator_ready = nullptr;

// New: status bar and label
static lv_obj_t* status_bar = nullptr;
static lv_obj_t* label_status = nullptr;

// Styles
static lv_style_t style_title;
static lv_style_t style_status;
static lv_style_t style_instruction;
static lv_style_t style_ready_dot;

// Animation for pulsing indicator
static lv_anim_t pulse_anim;

// Animation callback for pulse
static void pulse_anim_cb(void *var, int32_t value) {
    if (var == nullptr) return;
    lv_obj_set_style_opa((lv_obj_t*)var, value, LV_PART_MAIN);
}

// Track if styles are initialized
static bool styles_initialized = false;

void ui_idle_screen_create(lv_obj_t *parent) {
    idle_root = parent;
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_idle_screen_create");
        return;
    }
    
    Serial.println("[UI] Creating idle screen elements...");
    
    // Initialize theme if not already done
    ui_theme_init();
    
    // Initialize styles only once (critical - re-initializing causes crash)
    if (!styles_initialized) {
        Serial.println("[UI] Initializing idle screen styles...");
        
        lv_style_init(&style_title);
        lv_style_set_text_color(&style_title, lv_color_hex(COLOR_TEXT_PRIMARY));
        lv_style_set_text_font(&style_title, lv_font_default());
        lv_style_set_text_align(&style_title, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_status);
        lv_style_set_text_color(&style_status, lv_color_hex(COLOR_STATUS_ACTIVE));
        lv_style_set_text_font(&style_status, lv_font_default());
        lv_style_set_text_align(&style_status, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_instruction);
        lv_style_set_text_color(&style_instruction, lv_color_hex(COLOR_TEXT_SECONDARY));
        lv_style_set_text_font(&style_instruction, lv_font_default());
        lv_style_set_text_align(&style_instruction, LV_TEXT_ALIGN_CENTER);
        
        lv_style_init(&style_ready_dot);
        lv_style_set_bg_color(&style_ready_dot, lv_color_hex(COLOR_ACCENT_CYAN));
        lv_style_set_bg_opa(&style_ready_dot, LV_OPA_COVER);
        lv_style_set_radius(&style_ready_dot, LV_RADIUS_CIRCLE);
        lv_style_set_border_width(&style_ready_dot, 0);
        
        styles_initialized = true;
        Serial.println("[UI] Styles initialized");
    }
    
    // Base background: pure black
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, LV_PART_MAIN);

    // Status bar at top
    status_bar = lv_obj_create(parent);
    lv_obj_set_size(status_bar, lv_pct(100), 28);
    lv_obj_set_style_bg_color(status_bar, lv_color_hex(0x30343A), 0);
    lv_obj_set_style_bg_opa(status_bar, LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(status_bar, 0, 0);
    lv_obj_align(status_bar, LV_ALIGN_TOP_MID, 0, 0);

    label_status = lv_label_create(status_bar);
    lv_label_set_text(label_status, "");
    lv_obj_set_style_text_color(label_status, lv_color_hex(0xFFFFFF), 0);
    lv_obj_set_style_text_font(label_status, &lv_font_montserrat_20, 0);
    lv_obj_set_style_text_align(label_status, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_align(label_status, LV_ALIGN_CENTER, 0, 0);

    // Hide old title/subtitle to keep layout clean
    label_title = lv_label_create(parent);
    lv_label_set_text(label_title, "");
    lv_obj_add_flag(label_title, LV_OBJ_FLAG_HIDDEN);
    label_subtitle = lv_label_create(parent);
    lv_label_set_text(label_subtitle, "");
    lv_obj_add_flag(label_subtitle, LV_OBJ_FLAG_HIDDEN);

    // Main hint message (wrapping, fits screen)
    label_no_nav = lv_label_create(parent);
    lv_label_set_text(label_no_nav, "");
    lv_obj_set_style_text_color(label_no_nav, lv_color_hex(0x9EC1FF), 0);
    lv_obj_set_style_text_font(label_no_nav, &lv_font_montserrat_28, 0);
    lv_label_set_long_mode(label_no_nav, LV_LABEL_LONG_WRAP);
    lv_obj_set_width(label_no_nav, lv_pct(96));
    lv_obj_set_style_text_align(label_no_nav, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_align(label_no_nav, LV_ALIGN_TOP_MID, 0, 44); // below status bar
    
    Serial.println("[UI] Idle screen created successfully");
}

void ui_idle_screen_update_ble_status(bool connected) {
    if (!idle_root) return;
    // Only show connected state here; disconnected state is handled by welcome screen
    if (!connected) {
        return;
    }
    if (label_status && status_bar) {
        lv_label_set_text(label_status, "Connected");
        lv_obj_set_style_bg_color(status_bar, lv_color_hex(0x1E824C), 0);
    }
    // Optional indicator dot
    if (indicator_ble == nullptr) {
        indicator_ble = lv_obj_create(idle_root);
        lv_obj_set_size(indicator_ble, 8, 8);
        lv_obj_set_style_radius(indicator_ble, LV_RADIUS_CIRCLE, 0);
        lv_obj_align(indicator_ble, LV_ALIGN_TOP_RIGHT, -10, 10);
        lv_obj_set_style_bg_opa(indicator_ble, LV_OPA_COVER, 0);
    }
    lv_obj_set_style_bg_color(indicator_ble, lv_color_hex(0x9AF59A), LV_PART_MAIN);
}

void ui_idle_screen_start_pulse() {
    if (!idle_root) return;
    if (indicator_ready == nullptr) {
        indicator_ready = lv_obj_create(idle_root);
        lv_obj_set_size(indicator_ready, 14, 14);
        lv_obj_set_style_radius(indicator_ready, LV_RADIUS_CIRCLE, 0);
        lv_obj_align(indicator_ready, LV_ALIGN_BOTTOM_MID, 0, -14);
        lv_obj_set_style_bg_color(indicator_ready, lv_color_hex(0x9EC1FF), 0);
        lv_obj_set_style_bg_opa(indicator_ready, LV_OPA_50, 0);
    }
    if (!lv_obj_is_valid(indicator_ready)) return;
    lv_anim_t a; lv_anim_init(&a);
    lv_anim_set_var(&a, indicator_ready);
    lv_anim_set_values(&a, LV_OPA_30, LV_OPA_100);
    lv_anim_set_time(&a, 800);
    lv_anim_set_playback_time(&a, 800);
    lv_anim_set_repeat_count(&a, LV_ANIM_REPEAT_INFINITE);
    lv_anim_set_exec_cb(&a, [](void* obj, int32_t v){ lv_obj_set_style_bg_opa((lv_obj_t*)obj, (lv_opa_t)v, 0); });
    lv_anim_start(&a);
}

void ui_idle_screen_stop_animations() {
    if (indicator_ready) {
        lv_anim_del(indicator_ready, nullptr);
    }
}

void ui_idle_screen_set_no_nav_msg(bool show) {
    if (!label_no_nav) return;
    if (show) {
        lv_label_set_text(label_no_nav, "Connect to Google Maps to start navigation.");
        lv_obj_set_style_text_color(label_no_nav, lv_color_hex(0x9EC1FF), 0);
    } else {
        lv_label_set_text(label_no_nav, "");
    }
}

