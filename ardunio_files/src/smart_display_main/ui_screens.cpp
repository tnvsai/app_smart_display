#include <Arduino.h>
#include "ui_screens.h"
#include "ui_welcome_screen.h"
#include "ui_idle_screen.h"
#include "ui_navigation_screen.h"
#include "ui_incoming_call_screen.h"
#include "ui_outgoing_call_screen.h"
#include "ui_missed_call_screen.h"

// Forward declarations - screen objects need to be accessible
extern lv_obj_t *screen_incoming_call;

// Screen objects
lv_obj_t *screen_welcome = nullptr;
lv_obj_t *screen_idle = nullptr;
lv_obj_t *screen_navigation = nullptr;
lv_obj_t *screen_incoming_call = nullptr;
lv_obj_t *screen_outgoing_call = nullptr;
lv_obj_t *screen_missed_call = nullptr;

// Current active screen
UIScreen current_screen = UI_SCREEN_NONE;

void ui_screens_init(void) {
    Serial.println("[UI] Initializing screens...");
    
    // Create all screen objects
    screen_welcome = lv_obj_create(nullptr);
    screen_idle = lv_obj_create(nullptr);
    screen_navigation = lv_obj_create(nullptr);
    screen_incoming_call = lv_obj_create(nullptr);
    screen_outgoing_call = lv_obj_create(nullptr);
    screen_missed_call = lv_obj_create(nullptr);
    
    // Verify all screens were created
    if (!screen_welcome || !screen_idle || !screen_navigation || 
        !screen_incoming_call || !screen_outgoing_call || !screen_missed_call) {
        Serial.println("[UI] ERROR: Failed to create screen objects!");
        return;
    }
    
    Serial.println("[UI] Screen objects created, initializing UI elements...");
    
    // Initialize individual screens (setup their UI elements)
    ui_welcome_screen_create(screen_welcome);
    ui_idle_screen_create(screen_idle);
    ui_navigation_screen_create(screen_navigation);
    ui_incoming_call_screen_create(screen_incoming_call);
    ui_outgoing_call_screen_create(screen_outgoing_call);
    ui_missed_call_screen_create(screen_missed_call);
    
    Serial.println("[UI] All screens initialized successfully");
}

void ui_show_screen(UIScreen screen, uint32_t anim_time) {
    lv_obj_t *target_screen = nullptr;
    
    // Get target screen object
    switch (screen) {
        case UI_SCREEN_WELCOME:
            target_screen = screen_welcome;
            break;
        case UI_SCREEN_IDLE:
            target_screen = screen_idle;
            break;
        case UI_SCREEN_NAVIGATION:
            target_screen = screen_navigation;
            break;
        case UI_SCREEN_INCOMING_CALL:
            target_screen = screen_incoming_call;
            break;
        case UI_SCREEN_OUTGOING_CALL:
            target_screen = screen_outgoing_call;
            break;
        case UI_SCREEN_MISSED_CALL:
            target_screen = screen_missed_call;
            break;
        default:
            Serial.printf("[UI] Error: Invalid screen ID %d\n", screen);
            return;
    }
    
    if (target_screen == nullptr) {
        Serial.printf("[UI] Error: Screen %d not initialized (null pointer)\n", screen);
        return;
    }
    
    // Verify screen object is valid before loading
    if (!lv_obj_is_valid(target_screen)) {
        Serial.printf("[UI] Error: Screen %d object is invalid\n", screen);
        return;
    }
    
    // Process LVGL before screen change
    lv_timer_handler();
    
    Serial.printf("[UI] Loading screen %d (immediate, no animation)\n", screen);
    
    // ULTRA SIMPLIFIED: Always use immediate load, no animations EVER
    // This prevents all animation-related crashes
    lv_scr_load(target_screen);
    
    // Process LVGL after screen change (multiple times for stability)
    for (int i = 0; i < 2; i++) {
        lv_timer_handler();
    }
    
    current_screen = screen;
    
    Serial.printf("[UI] Switched to screen %d successfully\n", screen);
}

UIScreen ui_get_current_screen(void) {
    return current_screen;
}

void ui_show_navigation(void) {
    // Navigation uses Arduino_GFX, so we need to hide LVGL
    // Create a minimal black screen to "hide" LVGL content so Arduino_GFX can draw
    if (screen_navigation == nullptr) {
        screen_navigation = lv_obj_create(nullptr);
        lv_obj_set_style_bg_color(screen_navigation, lv_color_hex(0x000000), LV_PART_MAIN);
        lv_obj_set_style_bg_opa(screen_navigation, LV_OPA_COVER, LV_PART_MAIN);
    }
    
    // Switch to navigation screen (black, so Arduino_GFX can draw on top)
    lv_scr_load(screen_navigation);
    current_screen = UI_SCREEN_NAVIGATION;
    
    // IMPORTANT: Arduino_GFX will draw navigation on top of this black LVGL screen
    // No overlap because LVGL screen is solid black
}

void ui_transition_fade(UIScreen from, UIScreen to, uint32_t time) {
    lv_obj_t *from_screen = nullptr;
    lv_obj_t *to_screen = nullptr;
    
    // Get screen objects (simplified - would need full switch for all screens)
    // For now, just transition to target
    ui_show_screen(to, time);
}

void ui_screens_cleanup(void) {
    // Cleanup if needed (LVGL handles most cleanup automatically)
    current_screen = UI_SCREEN_NONE;
}

