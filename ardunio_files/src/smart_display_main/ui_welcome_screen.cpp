#include <Arduino.h>
#include "ui_welcome_screen.h"

// UI element references
static lv_obj_t *label_title = nullptr;
static lv_obj_t *label_subtitle = nullptr;
static lv_obj_t *arc_loading = nullptr;
static lv_obj_t *label_status = nullptr;

// Style objects
static lv_style_t style_title;
static lv_style_t style_subtitle;
static lv_style_t style_status;

// Style initialization guard
static bool welcome_styles_initialized = false;

void ui_welcome_screen_create(lv_obj_t *parent) {
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_welcome_screen_create");
        return;
    }

    // Background
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, LV_PART_MAIN);

    if (!welcome_styles_initialized) {
        // Title style
        lv_style_init(&style_title);
        lv_style_set_text_color(&style_title, lv_color_hex(0xFFFFFF));
        lv_style_set_text_font(&style_title, &lv_font_montserrat_28);
        lv_style_set_text_align(&style_title, LV_TEXT_ALIGN_CENTER);

        // Subtitle style
        lv_style_init(&style_subtitle);
        lv_style_set_text_color(&style_subtitle, lv_color_hex(0x808080));
        lv_style_set_text_font(&style_subtitle, &lv_font_montserrat_20);
        lv_style_set_text_align(&style_subtitle, LV_TEXT_ALIGN_CENTER);

        // Status style
        lv_style_init(&style_status);
        lv_style_set_text_color(&style_status, lv_color_hex(0x00FFFF));
        lv_style_set_text_font(&style_status, &lv_font_montserrat_20);
        lv_style_set_text_align(&style_status, LV_TEXT_ALIGN_CENTER);

        welcome_styles_initialized = true;
    }

    // Title: YatraMate
    label_title = lv_label_create(parent);
    lv_obj_add_style(label_title, &style_title, 0);
    lv_label_set_text(label_title, "YatraMate");
    lv_obj_set_width(label_title, lv_pct(100));
    lv_label_set_long_mode(label_title, LV_LABEL_LONG_CLIP);
    lv_obj_align(label_title, LV_ALIGN_TOP_MID, 0, 44);

    // Tagline: by tnvsai
    label_subtitle = lv_label_create(parent);
    lv_obj_add_style(label_subtitle, &style_subtitle, 0);
    lv_label_set_text(label_subtitle, "by tnvsai");
    lv_obj_set_width(label_subtitle, lv_pct(100));
    lv_label_set_long_mode(label_subtitle, LV_LABEL_LONG_CLIP);
    lv_obj_align(label_subtitle, LV_ALIGN_TOP_MID, 0, 78);

    // Loading arc (spinner)
    arc_loading = lv_arc_create(parent);
    lv_obj_set_size(arc_loading, 60, 60);
    lv_arc_set_range(arc_loading, 0, 360);
    lv_arc_set_value(arc_loading, 270);
    lv_arc_set_bg_angles(arc_loading, 0, 360);
    lv_obj_set_style_arc_color(arc_loading, lv_color_hex(0x00FFFF), LV_PART_MAIN);
    lv_obj_set_style_arc_color(arc_loading, lv_color_hex(0x1a1a1a), LV_PART_INDICATOR);
    lv_obj_set_style_arc_width(arc_loading, 4, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc_loading, 4, LV_PART_INDICATOR);
    lv_obj_set_style_arc_opa(arc_loading, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_arc_opa(arc_loading, LV_OPA_30, LV_PART_INDICATOR);
    lv_obj_remove_style(arc_loading, nullptr, LV_PART_KNOB);
    lv_obj_clear_flag(arc_loading, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_align(arc_loading, LV_ALIGN_CENTER, 0, 20);

    // Animate arc rotation
    lv_anim_t arc_anim; lv_anim_init(&arc_anim);
    lv_anim_set_var(&arc_anim, arc_loading);
    lv_anim_set_values(&arc_anim, 0, 360);
    lv_anim_set_time(&arc_anim, 2000);
    lv_anim_set_repeat_count(&arc_anim, LV_ANIM_REPEAT_INFINITE);
    lv_anim_set_exec_cb(&arc_anim, [](void *var, int32_t value) {
        lv_arc_set_value((lv_obj_t*)var, value);
    });
    lv_anim_start(&arc_anim);

    // Status label
    label_status = lv_label_create(parent);
    lv_obj_add_style(label_status, &style_status, 0);
    lv_label_set_text(label_status, "Connecting...");
    lv_obj_align(label_status, LV_ALIGN_BOTTOM_MID, 0, -50);

    Serial.println("[UI] Welcome screen created (title, subtitle, spinner, status)");
}

void ui_welcome_screen_update_ble_status(bool connected) {
    if (label_status == nullptr) return;

    if (connected) {
        lv_label_set_text(label_status, "Connected");
        lv_obj_set_style_text_color(label_status, lv_color_hex(0x00FF00), 0);
        if (arc_loading && lv_obj_is_valid(arc_loading)) {
            lv_obj_add_flag(arc_loading, LV_OBJ_FLAG_HIDDEN);
        }
    } else {
        lv_label_set_text(label_status, "Connecting...");
        lv_obj_set_style_text_color(label_status, lv_color_hex(0x00FFFF), 0);
        if (arc_loading && lv_obj_is_valid(arc_loading)) {
            lv_obj_clear_flag(arc_loading, LV_OBJ_FLAG_HIDDEN);
        }
    }
}

void ui_welcome_screen_show(void) {}
void ui_welcome_screen_hide(void) {}

