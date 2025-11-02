#include <Arduino_GFX_Library.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include "esp_lcd_touch_axs5106l.h"

// LVGL includes
#include <lvgl.h>
#include "lvgl_display_driver.h"
#include "ui_screens.h"
#include "ui_theme.h"
#include "ui_welcome_screen.h"
#include "ui_idle_screen.h"
#include "ui_navigation_screen.h"
#include "ui_incoming_call_screen.h"
#include "ui_outgoing_call_screen.h"
#include "ui_missed_call_screen.h"

// Touch variables
bool touchEnabled = true;
int touchX = 0;
int touchY = 0;
unsigned long lastTouchTime = 0;

// Touch functions are provided by the library

// Touch configuration (matching the working example)
#define Touch_I2C_SDA 18  // I2C SDA pin for touch
#define Touch_I2C_SCL 19  // I2C SCL pin for touch
#define Touch_RST     20  // Touch reset pin
#define Touch_INT     21  // Touch interrupt pin

// ==== BLE Configuration ====
#define SERVICE_UUID "12345678-1234-1234-1234-1234567890ab"
#define CHARACTERISTIC_UUID "abcd1234-5678-90ab-cdef-1234567890ab"
BLECharacteristic *pCharacteristic;
bool deviceConnected = false;

// ==== Display Configuration ====
#define GFX_BL 23
#define ROTATION 0
Arduino_DataBus *bus = new Arduino_HWSPI(15 /* DC */, 14 /* CS */, 1 /* SCK */, 2 /* MOSI */);
Arduino_GFX *gfx = new Arduino_ST7789(bus, 22 /* RST */, 0, false, 172, 320, 34, 0, 34, 0);

// ==== Screen Layout Zones (172x320 display) ====
#define ZONE_STATUS_Y 0
#define ZONE_STATUS_H 30
#define ZONE_SPEED_Y 30
#define ZONE_SPEED_H 30
#define ZONE_ARROW_Y 60
#define ZONE_ARROW_H 100      // Made arrow zone much smaller
#define ZONE_DISTANCE_Y 160    // Moved up significantly to reduce gap
#define ZONE_DISTANCE_H 50
#define ZONE_MANEUVER_Y 210    // Moved up significantly to reduce gap
#define ZONE_MANEUVER_H 50     // Increased from 40 for longer text
#define SCREEN_WIDTH 172
#define SCREEN_HEIGHT 320

// ==== Enhanced Colors (20% more saturated) ====
#define COLOR_GREEN 0x07E0      // Basic directions
#define COLOR_ORANGE 0xFD20     // Sharp turns (enhanced)
#define COLOR_CYAN 0x07FF       // Slight turns
#define COLOR_YELLOW 0xFFE0     // Roundabouts/Merge
#define COLOR_MAGENTA 0xF81F    // U-turn
#define COLOR_RED 0xF800        // Destination
#define COLOR_WHITE 0xFFFF      // Outlines
#define COLOR_BLACK 0x0000      // Background
#define COLOR_GRAY 0x8410       // Inactive elements

// ==== DEBUG FLAGS ====
#define DEBUG_BLE true
#define DEBUG_TOUCH true
#define DEBUG_CALLS true
#define DEBUG_NAVIGATION true

// ==== Global State ====
String currentETA = "";
String currentManeuver = "";
String currentDirection = "";
int currentDistance = 0;
int scrollOffset = 0;
unsigned long lastScrollTime = 0;
unsigned long lastNavUpdate = 0; // Track last time we received nav data

// Phone call state
bool isPhoneCallActive = false;
bool isMissedCallShowing = false;
int missedCallCount = 0;
String currentCallerName = "";
String currentCallerNumber = "";
unsigned long callStartTime = 0;
unsigned long missedCallTime = 0;
String currentCallState = "";
unsigned long phoneCallDisplayStartTime = 0;  // Track when call display started
#define MIN_PHONE_CALL_DISPLAY_TIME 5000  // Minimum 5 seconds for phone call display

// Navigation state before phone call
bool wasNavigationActive = false;
String savedDirection = "";
int savedDistance = 0;
String savedManeuver = "";
String savedETA = "";

// Persistent missed call tracking
struct MissedCallInfo {
    String callerName;
    String callerNumber;
    int count;
    unsigned long firstMissedTime;
    bool acknowledged;  // User tapped to dismiss
};
MissedCallInfo persistentMissedCall = {"", "", 0, 0, false};
unsigned long lastMissedCallReminderTime = 0;
#define MISSED_CALL_REMINDER_INTERVAL 60000  // Show reminder every 60 seconds
#define INITIAL_MISSED_CALL_DISPLAY_TIME 10000  // Show missed call for 10 seconds initially
#define REMINDER_DISPLAY_TIME 5000  // Show reminder for 5 seconds

// ==== LCD Register Init ====
void lcd_reg_init(void) {
    static const uint8_t init_operations[] = {
        BEGIN_WRITE, WRITE_COMMAND_8, 0x11, END_WRITE, DELAY, 120,
        BEGIN_WRITE, WRITE_C8_D16, 0xDF, 0x98, 0x53, WRITE_C8_D8, 0xB2, 0x23, WRITE_COMMAND_8, 0xB7,
        WRITE_BYTES, 4, 0x00, 0x47, 0x00, 0x6F, WRITE_COMMAND_8, 0xBB, WRITE_BYTES, 6, 0x1C, 0x1A, 0x55, 0x73, 0x63, 0xF0,
        WRITE_C8_D16, 0xC0, 0x44, 0xA4, WRITE_C8_D8, 0xC1, 0x16, WRITE_COMMAND_8, 0xC3,
        WRITE_BYTES, 8, 0x7D, 0x07, 0x14, 0x06, 0xCF, 0x71, 0x72, 0x77, WRITE_COMMAND_8, 0xC4,
        WRITE_BYTES, 12, 0x00, 0x00, 0xA0, 0x79, 0x0B, 0x0A, 0x16, 0x79, 0x0B, 0x0A, 0x16, 0x82,
        WRITE_COMMAND_8, 0xC8, WRITE_BYTES, 32,
        0x3F, 0x32, 0x29, 0x29, 0x27, 0x2B, 0x27, 0x28, 0x28, 0x26, 0x25, 0x17, 0x12, 0x0D, 0x04, 0x00,
        0x3F, 0x32, 0x29, 0x29, 0x27, 0x2B, 0x27, 0x28, 0x28, 0x26, 0x25, 0x17, 0x12, 0x0D, 0x04, 0x00,
        WRITE_COMMAND_8, 0xD0, WRITE_BYTES, 5, 0x04, 0x06, 0x6B, 0x0F, 0x00,
        WRITE_C8_D16, 0xD7, 0x00, 0x30, WRITE_C8_D8, 0xE6, 0x14, WRITE_C8_D8, 0xDE, 0x01,
        WRITE_COMMAND_8, 0xB7, WRITE_BYTES, 5, 0x03, 0x13, 0xEF, 0x35, 0x35,
        WRITE_COMMAND_8, 0xC1, WRITE_BYTES, 3, 0x14, 0x15, 0xC0,
        WRITE_C8_D16, 0xC2, 0x06, 0x3A, WRITE_C8_D16, 0xC4, 0x72, 0x12,
        WRITE_C8_D8, 0xBE, 0x00, WRITE_C8_D8, 0xDE, 0x02,
        WRITE_COMMAND_8, 0xE5, WRITE_BYTES, 3, 0x00, 0x02, 0x00,
        WRITE_COMMAND_8, 0xE5, WRITE_BYTES, 3, 0x01, 0x02, 0x00,
        // Set pixel format: 16-bit RGB565 (0x55 is standard for ST7789)
        WRITE_C8_D8, 0xDE, 0x00, WRITE_C8_D8, 0x35, 0x00, WRITE_C8_D8, 0x3A, 0x55,
        WRITE_COMMAND_8, 0x2A, WRITE_BYTES, 4, 0x00, 0x22, 0x00, 0xCD,
        WRITE_COMMAND_8, 0x2B, WRITE_BYTES, 4, 0x00, 0x00, 0x01, 0x3F,
        WRITE_C8_D8, 0xDE, 0x02, WRITE_COMMAND_8, 0xE5, WRITE_BYTES, 3, 0x00, 0x02, 0x00,
        WRITE_C8_D8, 0xDE, 0x00, WRITE_C8_D8, 0x36, 0x08, WRITE_COMMAND_8, 0x21, END_WRITE,
        DELAY, 10, BEGIN_WRITE, WRITE_COMMAND_8, 0x29, END_WRITE
    };
    bus->batchOperation(init_operations, sizeof(init_operations));
}

// ===== ENHANCED ARROW DRAWING FUNCTIONS (40% LARGER + OUTLINES) =====
// LEFT arrow - Pointing LEFT (Enhanced)
void drawLeftArrow(int x, int y) {
    // Draw white outline first
    for (int offset = -4; offset <= 4; offset++) {
        gfx->drawLine(x, y+offset, x-42, y+offset, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x-63, y, x-42, y-17, x-42, y+17, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int offset = -3; offset <= 3; offset++) {
        gfx->drawLine(x, y+offset, x-40, y+offset, COLOR_GREEN); // Green shaft (thicker)
    }
    gfx->fillTriangle(x-60, y, x-40, y-15, x-40, y+15, COLOR_GREEN); // Green arrow head
}

// RIGHT arrow - Pointing RIGHT (Enhanced)
void drawRightArrow(int x, int y) {
    // Draw white outline first
    for (int offset = -4; offset <= 4; offset++) {
        gfx->drawLine(x, y+offset, x+42, y+offset, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x+63, y, x+42, y-17, x+42, y+17, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int offset = -3; offset <= 3; offset++) {
        gfx->drawLine(x, y+offset, x+40, y+offset, COLOR_GREEN); // Green shaft (thicker)
    }
    gfx->fillTriangle(x+60, y, x+40, y-15, x+40, y+15, COLOR_GREEN); // Green arrow head
}

// STRAIGHT arrow - Pointing UP (Enhanced, but shorter to avoid ETA overlap)
void drawStraightArrow(int x, int y) {
    // Draw white outline first
    for (int offset = -4; offset <= 4; offset++) {
        gfx->drawLine(x+offset, y, x+offset, y-30, COLOR_WHITE); // White outline (shorter)
    }
    gfx->fillTriangle(x, y-50, x-17, y-30, x+17, y-30, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int offset = -3; offset <= 3; offset++) {
        gfx->drawLine(x+offset, y, x+offset, y-28, COLOR_GREEN); // Green shaft (thicker, but shorter)
    }
    gfx->fillTriangle(x, y-48, x-15, y-28, x+15, y-28, COLOR_GREEN); // Green arrow head
}

// SHARP LEFT - Orange diagonal (Enhanced)
void drawSharpLeftArrow(int x, int y) {
    // Draw white outline first
    for (int i = -3; i <= 3; i++) {
        gfx->drawLine(x, y+i, x-56, y-35+i, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x-70, y-42, x-56, y-28, x-56, y-49, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int i = -2; i <= 2; i++) {
        gfx->drawLine(x, y+i, x-54, y-33+i, COLOR_ORANGE); // Orange shaft (thicker)
    }
    gfx->fillTriangle(x-68, y-40, x-54, y-26, x-54, y-47, COLOR_ORANGE); // Orange arrow head
}

// SHARP RIGHT - Orange diagonal (Enhanced)
void drawSharpRightArrow(int x, int y) {
    // Draw white outline first
    for (int i = -3; i <= 3; i++) {
        gfx->drawLine(x, y+i, x+56, y-35+i, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x+70, y-42, x+56, y-28, x+56, y-49, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int i = -2; i <= 2; i++) {
        gfx->drawLine(x, y+i, x+54, y-33+i, COLOR_ORANGE); // Orange shaft (thicker)
    }
    gfx->fillTriangle(x+68, y-40, x+54, y-26, x+54, y-47, COLOR_ORANGE); // Orange arrow head
}

// SLIGHT LEFT - Cyan gentle curve (Enhanced with smooth arc)
void drawSlightLeftArrow(int x, int y) {
    // Draw smooth curved path using multiple line segments
    int curvePoints = 8;
    for (int i = -3; i <= 3; i++) {
        for (int j = 0; j < curvePoints; j++) {
            float t = (float)j / (curvePoints - 1);
            int curveX = x - (int)(28 * t);
            int curveY = y - (int)(14 * t * t) + i; // Quadratic curve
            int nextX = x - (int)(28 * (t + 1.0/curvePoints));
            int nextY = y - (int)(14 * (t + 1.0/curvePoints) * (t + 1.0/curvePoints)) + i;
            gfx->drawLine(curveX, curveY, nextX, nextY, COLOR_WHITE); // White outline
        }
    }
    
    // Draw colored curve on top
    for (int i = -2; i <= 2; i++) {
        for (int j = 0; j < curvePoints; j++) {
            float t = (float)j / (curvePoints - 1);
            int curveX = x - (int)(26 * t);
            int curveY = y - (int)(12 * t * t) + i;
            int nextX = x - (int)(26 * (t + 1.0/curvePoints));
            int nextY = y - (int)(12 * (t + 1.0/curvePoints) * (t + 1.0/curvePoints)) + i;
            gfx->drawLine(curveX, curveY, nextX, nextY, COLOR_CYAN); // Cyan curve
        }
    }
    
    // Enhanced arrow head
    gfx->fillTriangle(x-42, y-14, x-28, y-20, x-28, y-8, COLOR_CYAN);
}

// SLIGHT RIGHT - Cyan gentle curve (Enhanced with smooth arc)
void drawSlightRightArrow(int x, int y) {
    // Draw smooth curved path using multiple line segments
    int curvePoints = 8;
    for (int i = -3; i <= 3; i++) {
        for (int j = 0; j < curvePoints; j++) {
            float t = (float)j / (curvePoints - 1);
            int curveX = x + (int)(28 * t);
            int curveY = y - (int)(14 * t * t) + i; // Quadratic curve
            int nextX = x + (int)(28 * (t + 1.0/curvePoints));
            int nextY = y - (int)(14 * (t + 1.0/curvePoints) * (t + 1.0/curvePoints)) + i;
            gfx->drawLine(curveX, curveY, nextX, nextY, COLOR_WHITE); // White outline
        }
    }
    
    // Draw colored curve on top
    for (int i = -2; i <= 2; i++) {
        for (int j = 0; j < curvePoints; j++) {
            float t = (float)j / (curvePoints - 1);
            int curveX = x + (int)(26 * t);
            int curveY = y - (int)(12 * t * t) + i;
            int nextX = x + (int)(26 * (t + 1.0/curvePoints));
            int nextY = y - (int)(12 * (t + 1.0/curvePoints) * (t + 1.0/curvePoints)) + i;
            gfx->drawLine(curveX, curveY, nextX, nextY, COLOR_CYAN); // Cyan curve
        }
    }
    
    // Enhanced arrow head
    gfx->fillTriangle(x+42, y-14, x+28, y-20, x+28, y-8, COLOR_CYAN);
}

// U-TURN - Magenta (Enhanced)
void drawUTurn(int x, int y) {
    // Draw white outline first
    gfx->drawArc(x, y, 28, 42, 180, 270, COLOR_WHITE); // White outline
    gfx->fillTriangle(x-35, y-49, x-28, y-35, x-42, y-35, COLOR_WHITE); // White outline
    
    // Draw colored U-turn on top
    gfx->drawArc(x, y, 26, 40, 180, 270, COLOR_MAGENTA); // Magenta U-shape
    gfx->fillTriangle(x-33, y-47, x-26, y-33, x-40, y-33, COLOR_MAGENTA); // Magenta arrow head
}

// ROUNDABOUT - Yellow circle (Enhanced)
void drawRoundabout(int x, int y, String dir) {
    // Draw white outline first
    gfx->drawCircle(x, y, 35, COLOR_WHITE); // White outline
    
    // Draw colored roundabout on top
    gfx->drawCircle(x, y, 33, COLOR_YELLOW); // Yellow circle
    
    if (dir == "roundabout_left") {
        // Exit left with outline
        gfx->drawLine(x-35, y, x-49, y-28, COLOR_WHITE); // White outline
        gfx->drawLine(x-33, y, x-47, y-26, COLOR_YELLOW); // Yellow line
        gfx->fillTriangle(x-70, y-35, x-47, y-21, x-47, y-35, COLOR_YELLOW);
    } else if (dir == "roundabout_right") {
        // Exit right with outline
        gfx->drawLine(x+35, y, x+49, y-28, COLOR_WHITE); // White outline
        gfx->drawLine(x+33, y, x+47, y-26, COLOR_YELLOW); // Yellow line
        gfx->fillTriangle(x+70, y-35, x+47, y-21, x+47, y-35, COLOR_YELLOW);
    } else {
        // Exit straight with outline
        gfx->drawLine(x, y-35, x, y-49, COLOR_WHITE); // White outline
        gfx->drawLine(x, y-33, x, y-47, COLOR_YELLOW); // Yellow line
        gfx->fillTriangle(x, y-70, x-11, y-47, x+11, y-47, COLOR_YELLOW);
    }
}

// DESTINATION - Red flag (Enhanced)
void drawDestination(int x, int y) {
    // Draw white outline first
    for (int i = -2; i <= 2; i++) {
        gfx->drawLine(x+i, y-28, x+i, y+28, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x+1, y-28, x+1, y-7, x+28, y-17, COLOR_WHITE); // White outline
    
    // Draw colored flag on top
    for (int i = -1; i <= 1; i++) {
        gfx->drawLine(x+i, y-26, x+i, y+26, COLOR_RED); // Red pole
    }
    gfx->fillTriangle(x+1, y-26, x+1, y-5, x+26, y-15, COLOR_RED); // Red flag
}

// KEEP LEFT/RIGHT - Vertical lines (Enhanced)
void drawKeepLeft(int x, int y) {
    gfx->fillRect(x-35, y-42, 5, 84, COLOR_CYAN); // Cyan bar (thicker)
}

void drawKeepRight(int x, int y) {
    gfx->fillRect(x+30, y-42, 5, 84, COLOR_CYAN); // Cyan bar (thicker)
}

// MAIN ARROW FUNCTION
void drawArrow(String dir) {
    // DISABLED - LVGL handles all navigation drawing now
    // Using Arduino_GFX causes overlap with LVGL screens
    return;
    if (DEBUG_NAVIGATION) {
        Serial.printf("[NAV] Drawing arrow: %s at zone (Y:%d, H:%d)\n", dir.c_str(), ZONE_ARROW_Y, ZONE_ARROW_H);
    }
    
    gfx->fillRect(0, ZONE_ARROW_Y, SCREEN_WIDTH, ZONE_ARROW_H, COLOR_BLACK); // Black background
    int midX = SCREEN_WIDTH / 2;
    int midY = ZONE_ARROW_Y + ZONE_ARROW_H / 2; // Center of expanded arrow area
    
    if (dir == "left" || dir == "sharp_left" || dir == "slight_left") {
        if (dir == "sharp_left") drawSharpLeftArrow(midX, midY);
        else if (dir == "slight_left") drawSlightLeftArrow(midX, midY);
        else drawLeftArrow(midX, midY);
    } else if (dir == "right" || dir == "sharp_right" || dir == "slight_right") {
        if (dir == "sharp_right") drawSharpRightArrow(midX, midY);
        else if (dir == "slight_right") drawSlightRightArrow(midX, midY);
        else drawRightArrow(midX, midY);
    } else if (dir == "straight" || dir == "forward") {
        drawStraightArrow(midX, midY);
    } else if (dir == "uturn" || dir == "u_turn" || dir == "turn_around") {
        drawUTurn(midX, midY);
    } else if (dir == "destination" || dir == "arrived" || dir == "end") {
        drawDestination(midX, midY);
    } else if (dir.indexOf("roundabout") >= 0) {
        drawRoundabout(midX, midY, dir);
    } else if (dir == "keep_left") {
        drawKeepLeft(midX, midY);
    } else if (dir == "keep_right") {
        drawKeepRight(midX, midY);
    }
}

// STATUS BAR (Enhanced with icons)
void displayStatus(String text, uint16_t color) {
    // DISABLED - LVGL handles all status display now
    return;
    gfx->fillRect(0, ZONE_STATUS_Y, SCREEN_WIDTH, ZONE_STATUS_H, COLOR_BLACK);
    
    // Draw Bluetooth icon
    drawBluetoothIcon(10, ZONE_STATUS_Y + 5, deviceConnected);
    
    // Draw signal strength bars
    drawSignalBars(SCREEN_WIDTH - 40, ZONE_STATUS_Y + 5, deviceConnected ? 3 : 0);
    
    // Status text (smaller, right-aligned)
    gfx->setCursor(SCREEN_WIDTH/2 - 20, ZONE_STATUS_Y + 8);
    gfx->setTextColor(color);
    gfx->setTextSize(1);
    gfx->println(text);
}

// Bluetooth Icon Drawing
void drawBluetoothIcon(int x, int y, bool connected) {
    uint16_t iconColor = connected ? COLOR_GREEN : COLOR_GRAY;
    
    // Bluetooth symbol (simplified)
    gfx->drawLine(x, y+2, x+2, y+4, iconColor);
    gfx->drawLine(x+2, y+4, x, y+6, iconColor);
    gfx->drawLine(x, y+6, x+2, y+8, iconColor);
    gfx->drawLine(x+2, y+8, x, y+10, iconColor);
    gfx->drawLine(x+2, y+4, x+4, y+4, iconColor);
    gfx->drawLine(x+2, y+8, x+4, y+8, iconColor);
}

// Signal Strength Bars
void drawSignalBars(int x, int y, int strength) {
    // Draw 3 signal bars
    for (int i = 0; i < 3; i++) {
        uint16_t barColor = (i < strength) ? COLOR_GREEN : COLOR_GRAY;
        int barHeight = 4 + (i * 2);
        gfx->fillRect(x + (i * 4), y + (8 - barHeight), 2, barHeight, barColor);
    }
}

// DISTANCE (Enhanced with color coding and larger font)
void displayDistance(int dist) {
    // DISABLED - LVGL handles all distance display now
    return;
    gfx->fillRect(0, ZONE_DISTANCE_Y, SCREEN_WIDTH, ZONE_DISTANCE_H, COLOR_BLACK);
    
    // Color coding based on distance
    uint16_t distanceColor;
    if (dist < 100) {
        distanceColor = COLOR_GREEN; // Green for close (<100m)
    } else if (dist < 500) {
        distanceColor = COLOR_YELLOW; // Yellow for medium (100-500m)
    } else {
        distanceColor = COLOR_WHITE; // White for far (>500m)
    }
    
    // Center the distance text
    gfx->setCursor(SCREEN_WIDTH/2 - 40, ZONE_DISTANCE_Y + 15);
    gfx->setTextColor(distanceColor);
    gfx->setTextSize(4); // Larger font (was 3)
    
    if (dist >= 1000) {
        gfx->printf("%.1fkm", dist/1000.0);
    } else {
        gfx->printf("%dm", dist);
    }
    
    // Add distance unit badge
    gfx->setCursor(SCREEN_WIDTH/2 + 20, ZONE_DISTANCE_Y + 20);
    gfx->setTextColor(COLOR_GRAY);
    gfx->setTextSize(1);
    gfx->print(dist >= 1000 ? "km" : "m");
}

// ETA DISPLAY (ETA-focused approach)
void displayETA(String eta) {
    // DISABLED - LVGL handles all ETA display now
    return;
    gfx->fillRect(0, ZONE_SPEED_Y, SCREEN_WIDTH, ZONE_SPEED_H, COLOR_BLACK);
    
    // Center the ETA text
    gfx->setCursor(SCREEN_WIDTH/2 - 30, ZONE_SPEED_Y + 8);
    gfx->setTextColor(COLOR_WHITE);
    gfx->setTextSize(2);
    gfx->print(eta);
    
    // Add "ETA" label
    gfx->setCursor(SCREEN_WIDTH/2 + 20, ZONE_SPEED_Y + 8);
    gfx->setTextColor(COLOR_GRAY);
    gfx->setTextSize(1);
    gfx->print("ETA");
}

// MANEUVER
void displayManeuver(String text, bool immediateRender = false) {
    // DISABLED - LVGL handles all maneuver display now
    // Keep currentManeuver update for state tracking
    currentManeuver = text;
    return;
    gfx->fillRect(0, ZONE_MANEUVER_Y, SCREEN_WIDTH, ZONE_MANEUVER_H, COLOR_BLACK);
    
    // Reset scroll offset when text changes or when called with immediateRender
    static String lastText = "";
    if (lastText != text || immediateRender) {
        scrollOffset = 0;
        lastText = text;
    }
    
    // Only update scrolling if not immediately rendering
    if (!immediateRender) {
        unsigned long currentTime = millis();
        if (currentTime - lastScrollTime > 100) { // Scroll every 100ms
            scrollOffset++;
            if (scrollOffset > text.length() * 6) { // Approximate character width
                scrollOffset = -SCREEN_WIDTH;
            }
            lastScrollTime = currentTime;
        }
    }
    
    // Adjusted cursor position for better text visibility
    gfx->setCursor(10 + scrollOffset, ZONE_MANEUVER_Y + 15);
    gfx->setTextColor(COLOR_YELLOW);
    gfx->setTextSize(2);  // Keep readable size
    gfx->println(text);
}

// PHONE CALL DISPLAY FUNCTIONS
void displayIncomingCall(String name, String number) {
    // DISABLED - LVGL handles all incoming call screens now
    // This function is kept for compatibility but does nothing to prevent overlap
    Serial.println("[CALL] displayIncomingCall() disabled - LVGL handles this");
    return;
    // Save navigation state before showing call
    if (!isPhoneCallActive && !isMissedCallShowing) {
        wasNavigationActive = true;
        savedDirection = currentDirection;
        savedDistance = currentDistance;
        savedManeuver = currentManeuver;
        savedETA = currentETA;
        
        if (DEBUG_CALLS) {
            Serial.printf("[CALL] Saving navigation state: dir=%s, dist=%d\n", 
                          savedDirection.c_str(), savedDistance);
        }
    }
    
    isPhoneCallActive = true;
    currentCallerName = name;
    currentCallerNumber = number;
    currentCallState = "INCOMING";
    callStartTime = millis();
    
    // Clear screen completely
    gfx->fillScreen(COLOR_BLACK);
    
    Serial.println("[CALL] Drawing incoming call screen...");
    
    // Header - RED background box for visibility
    gfx->fillRect(0, 0, SCREEN_WIDTH, 35, COLOR_RED);
    gfx->setCursor(10, 12);
    gfx->setTextColor(COLOR_WHITE);
    gfx->setTextSize(1);
    gfx->print("INCOMING CALL");
    
    // Caller name (large, centered)
    gfx->setCursor(10, 60);
    gfx->setTextColor(COLOR_GREEN);
    gfx->setTextSize(2);
    gfx->print(name);
    
    // Phone number (medium) - only show if not "Unknown"
    if (number != "Unknown" && number.length() > 0) {
        gfx->setCursor(10, 100);
        gfx->setTextColor(COLOR_WHITE);
        gfx->setTextSize(1);
        gfx->print(number);
    }
    
    // Ringing animation dots (centered)
    drawRingingAnimation();
    
    // Dismiss instruction (at bottom)
    gfx->fillRect(0, 285, SCREEN_WIDTH, 35, COLOR_YELLOW);
    gfx->setCursor(10, 295);
    gfx->setTextColor(COLOR_BLACK);
    gfx->setTextSize(1);
    gfx->print("TAP TO REJECT");
    
    Serial.println("[CALL] Incoming call screen drawn");
}

void displayOngoingCall(String name, int duration) {
    // Arduino_GFX version is DISABLED - LVGL handles all call screens
    // This function is kept for compatibility but does nothing
    Serial.println("[CALL] displayOngoingCall() disabled - LVGL handles this");
    return;
}

void displayMissedCall(String name, String number, int count) {
    // Check if we should use LVGL screen instead of Arduino_GFX
    UIScreen currentScreen = ui_get_current_screen();
    
    // If we're on any LVGL screen, use LVGL missed call screen
    if (currentScreen != UI_SCREEN_NAVIGATION) {
        // LVGL is handling screens - use LVGL missed call screen
        Serial.println("[CALL] Using LVGL missed call screen");
        ui_navigation_hide_all_objects(); // Hide navigation objects
        ui_show_screen(UI_SCREEN_MISSED_CALL, 0);  // No animation
        ui_missed_call_screen_update(name.c_str(), number.c_str(), count, "Just now");
        
        // Update state
        isPhoneCallActive = false;
        isMissedCallShowing = true;
        currentCallerName = name;
        currentCallerNumber = number;
        currentCallState = "MISSED";
        return;  // Skip Arduino_GFX drawing
    }
    
    // Fallback: Arduino_GFX drawing (for compatibility, but shouldn't normally happen)
    Serial.println("[CALL] Using Arduino_GFX for missed call (fallback)");
    
    // Save navigation state before showing missed call (if not already saved)
    if (!isPhoneCallActive && !isMissedCallShowing && !wasNavigationActive) {
        wasNavigationActive = true;
        savedDirection = currentDirection;
        savedDistance = currentDistance;
        savedManeuver = currentManeuver;
        savedETA = currentETA;
        
        if (DEBUG_CALLS) {
            Serial.printf("[CALL] Saving navigation state for missed call: dir=%s, dist=%d\n", 
                          savedDirection.c_str(), savedDistance);
        }
    }
    
    isMissedCallShowing = true;
    missedCallCount = count;
    currentCallerName = name;
    currentCallerNumber = number;
    currentCallState = "MISSED";
    missedCallTime = millis();
    
    // Clear screen completely
    gfx->fillScreen(COLOR_BLACK);
    
    // Header - RED background box for missed call
    gfx->fillRect(0, 0, SCREEN_WIDTH, 35, COLOR_RED);
    gfx->setCursor(10, 12);
    gfx->setTextColor(COLOR_WHITE);
    gfx->setTextSize(1);
    if (count > 1) {
        gfx->printf("MISSED CALL (%d)", count);
    } else {
        gfx->print("MISSED CALL");
    }
    
    // Caller name (large, white)
    gfx->setCursor(10, 60);
    gfx->setTextColor(COLOR_WHITE);
    gfx->setTextSize(2);
    gfx->print(name);
    
    // Phone number - only show if not "Unknown"
    if (number != "Unknown" && number.length() > 0) {
        gfx->setCursor(10, 100);
        gfx->setTextColor(COLOR_GRAY);
        gfx->setTextSize(1);
        gfx->print(number);
    }
    
    // Missed call animation (blinking exclamation)
    drawMissedCallAnimation();
    
    // Status text
    gfx->setCursor(10, 240);
    gfx->setTextColor(COLOR_GRAY);
    gfx->setTextSize(1);
    gfx->print("Just now");
    
    // Dismiss instruction (at bottom, red)
    gfx->fillRect(0, 285, SCREEN_WIDTH, 35, COLOR_YELLOW);
    gfx->setCursor(10, 295);
    gfx->setTextColor(COLOR_BLACK);
    gfx->setTextSize(1);
    gfx->print("TAP TO DISMISS");
}

void clearPhoneDisplay() {
    isPhoneCallActive = false;
    isMissedCallShowing = false;
    currentCallerName = "";
    currentCallerNumber = "";
    currentCallState = "";
    
    Serial.println("[CALL] Phone call dismissed - checking navigation state");
    
    // Reset scroll offset for clean display
    scrollOffset = 0;
    
    // Check if we have active navigation data (either saved or current)
    bool hasNavigation = false;
    String nav_direction = "";
    int nav_distance = 0;
    String nav_maneuver = "";
    String nav_eta = "";
    
    // First check saved navigation state
    if (wasNavigationActive && savedDirection.length() > 0) {
        hasNavigation = true;
        nav_direction = savedDirection;
        nav_distance = savedDistance;
        nav_maneuver = savedManeuver;
        nav_eta = savedETA;
        Serial.println("[CALL] Using saved navigation state");
    } 
    // Else check current navigation data
    else if (currentDirection.length() > 0 || currentDistance > 0 || currentManeuver.length() > 0 || currentETA.length() > 0) {
        hasNavigation = true;
        nav_direction = currentDirection;
        nav_distance = currentDistance;
        nav_maneuver = currentManeuver;
        nav_eta = currentETA;
        Serial.println("[CALL] Using current navigation data");
    }
    
    if (hasNavigation) {
        // Go back to NAVIGATION screen
        Serial.println("[CALL] Returning to navigation screen");
        ui_show_screen(UI_SCREEN_NAVIGATION, 0);  // No animation
        
        // Update navigation screen with data
        if (nav_direction.length() > 0) {
            ui_navigation_screen_update_direction(nav_direction.c_str(), false);
        }
        if (nav_distance > 0) {
            ui_navigation_screen_update_distance(nav_distance, false);
        }
        if (nav_maneuver.length() > 0) {
            ui_navigation_screen_update_maneuver(nav_maneuver.c_str());
        }
        if (nav_eta.length() > 0) {
            ui_navigation_screen_update_eta(nav_eta.c_str());
        }
        ui_navigation_screen_show_critical_alert(nav_distance > 0 && nav_distance < 100);
        
        Serial.println("[CALL] Navigation screen restored");
    } else {
        // No navigation - go to IDLE screen
        Serial.println("[CALL] No navigation active - returning to idle screen");
        ui_show_screen(UI_SCREEN_IDLE, 0);  // No animation
        ui_idle_screen_update_ble_status(deviceConnected);
        if (deviceConnected) {
            ui_idle_screen_start_pulse();
        }
    }
}

void drawRingingAnimation() {
    // DISABLED - LVGL handles call animations now
    return;
    // Draw pulsing dots for ringing effect
    int centerX = SCREEN_WIDTH / 2;
    int centerY = 200;
    int radius = 8;
    
    // Calculate pulsing effect based on time
    unsigned long time = millis();
    int pulse = (time / 500) % 4; // Pulse every 500ms
    
    for (int i = 0; i < 5; i++) {
        int x = centerX - 20 + (i * 10);
        int alpha = (i == pulse) ? 255 : 100;
        uint16_t color = (alpha > 150) ? COLOR_GREEN : COLOR_GRAY;
        
        gfx->fillCircle(x, centerY, radius, color);
    }
}

void drawCallingAnimation() {
    // DISABLED - LVGL handles call animations now
    return;
    // Draw pulsing circles for calling/outgoing effect
    int centerX = SCREEN_WIDTH / 2;
    int centerY = 200;
    
    // Calculate pulsing effect based on time
    unsigned long time = millis();
    int pulse = (time / 300) % 3; // Faster pulse for outgoing
    
    for (int i = 0; i < 3; i++) {
        int radius = 8 + (pulse == i ? 8 : 0); // Expand on pulse
        uint16_t color = (pulse == i) ? COLOR_CYAN : COLOR_GRAY;
        
        gfx->fillCircle(centerX - 20 + (i * 20), centerY, radius, color);
    }
}

void drawMissedCallAnimation() {
    // DISABLED - LVGL handles call animations now
    return;
    // Draw animated exclamation mark for missed call
    int centerX = SCREEN_WIDTH / 2;
    int centerY = 200;
    
    // Calculate blinking effect based on time
    unsigned long time = millis();
    int blink = (time / 400) % 2; // Blink every 400ms
    
    if (blink == 0) {
        // Draw large exclamation mark
        gfx->fillRect(centerX - 3, centerY - 30, 6, 30, COLOR_RED);
        gfx->fillRect(centerX - 3, centerY + 15, 6, 8, COLOR_RED);
        
        // Draw pulsing circles around it
        for (int r = 15; r <= 25; r += 5) {
            int alpha = (r == 15) ? 100 : 50;
            gfx->drawCircle(centerX, centerY, r, COLOR_RED);
        }
    }
}

void handlePhoneCallTouch(int x, int y) {
    if (DEBUG_TOUCH) {
        Serial.printf("[TOUCH] State: active=%d, missed=%d, state='%s'\n", 
                      isPhoneCallActive, isMissedCallShowing, currentCallState.c_str());
    }
    
    if (isPhoneCallActive || isMissedCallShowing) {
        if (DEBUG_TOUCH) Serial.printf("[TOUCH] Phone call tapped at (%d,%d)\n", x, y);
        
        if (currentCallState == "INCOMING") {
            Serial.println("[CALL] Rejected by user");
            displayMissedCall(currentCallerName, currentCallerNumber, 1);
        } else if (currentCallState == "MISSED" || isMissedCallShowing) {
            // User acknowledged missed call - mark as acknowledged and clear
            Serial.println("[CALL] Missed call acknowledged by user");
            persistentMissedCall.acknowledged = true;
            persistentMissedCall.callerName = "";
            persistentMissedCall.callerNumber = "";
            persistentMissedCall.count = 0;
            persistentMissedCall.firstMissedTime = 0;
            clearPhoneDisplay();
        } else {
            Serial.println("[CALL] Dismissed by user");
            clearPhoneDisplay();
        }
    }
}

// ==== BLE CALLBACKS ====
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *pServer) {
        deviceConnected = true;
        Serial.println("[BLE] Device connected - callback triggered");
        
        // Update welcome screen status (if on welcome screen)
        // Actual transition will happen in loop() to avoid blocking LVGL
        UIScreen currentScreen = ui_get_current_screen();
        if (currentScreen == UI_SCREEN_WELCOME) {
            Serial.println("[BLE] Updating welcome screen status (transition handled in loop())");
            ui_welcome_screen_update_ble_status(true);
        } else if (currentScreen == UI_SCREEN_IDLE) {
            ui_idle_screen_update_ble_status(true);
            Serial.println("[BLE] Updated idle screen status");
        }
        
        // Transition to idle is handled in loop() to ensure LVGL responsiveness
        Serial.println("[BLE] Connection callback complete - loop() will handle transition");
    }
    
    void onDisconnect(BLEServer *pServer) {
        deviceConnected = false;
        Serial.println("[BLE] Device disconnected - restarting advertising");
        
        // Restart advertising after disconnect
        // Use non-blocking delay - process LVGL while waiting
        unsigned long disconnect_start = millis();
        while (millis() - disconnect_start < 500 && deviceConnected) {
            lv_timer_handler();
            delay(10);  // Small delay to avoid CPU spinning
        }
        BLEDevice::startAdvertising();
        Serial.println("[BLE] Restarted advertising - waiting for new connection");
        
        // Update screen status based on current screen
        UIScreen currentScreen = ui_get_current_screen();
        if (currentScreen == UI_SCREEN_WELCOME) {
            ui_welcome_screen_update_ble_status(false);
        } else if (currentScreen == UI_SCREEN_IDLE) {
            ui_idle_screen_update_ble_status(false);
        } else if (currentScreen == UI_SCREEN_NAVIGATION) {
            // Return to welcome screen when disconnected during navigation
            Serial.println("[BLE] Disconnected during navigation - returning to welcome");
            ui_show_screen(UI_SCREEN_WELCOME, ANIM_TIME_SCREEN);
            ui_welcome_screen_update_ble_status(false);
        }
        // Call screens don't need BLE status updates

        // Always return to welcome screen on disconnect to reinitiate connection
        ui_show_screen(UI_SCREEN_WELCOME, 0);
        ui_welcome_screen_update_ble_status(false);
    }
};

class MyCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) {
        Serial.println("\n=== onWrite CALLBACK TRIGGERED ===");
        Serial.flush();
        
        String value = pChar->getValue();
        Serial.print("Value length: ");
        Serial.println(value.length());
        Serial.flush();
        
        if (value.length() > 0) {
            Serial.print("Received: ");
            Serial.println(value);
            Serial.flush();
            
            StaticJsonDocument<256> doc;
            DeserializationError error = deserializeJson(doc, value);
            
            Serial.print("JSON parse error: ");
            Serial.println(error.c_str());
            Serial.flush();
            
            if (error == DeserializationError::Ok) {
                const char* type = doc["type"];
                
                if (type == nullptr) {
                    Serial.println("ERROR: JSON missing 'type' field");
                    Serial.flush();
                    return;
                }
                
                Serial.print("Message type: ");
                Serial.println(type);
                Serial.flush();
                
                if (strcmp(type, "phone_call") == 0) {
                    // Handle phone call data
                    const char* callerName = doc["caller_name"];
                    const char* callerNumber = doc["caller_number"];
                    const char* callState = doc["call_state"];
                    int duration = doc["duration"] | 0;
                    
                    if (DEBUG_CALLS) {
                        Serial.printf("[CALL] Type=%s, State=%s, Name=%s, Number=%s\n", type, callState, callerName, callerNumber);
                    }
                    
                    Serial.printf("[DEBUG] Comparing callState='%s' with 'INCOMING' = %d\n", callState, strcmp(callState, "INCOMING"));
                    
                    if (strcmp(callState, "INCOMING") == 0) {
                        // Don't override MISSED state with INCOMING - prioritize missed calls
                        if (!isMissedCallShowing) {
                            Serial.println("[CALL] Displaying INCOMING call via LVGL");
                            phoneCallDisplayStartTime = millis();  // Track display start time
                            
                            // Use LVGL screen instead of Arduino_GFX (NO ANIMATION for stability)
                            ui_navigation_hide_all_objects(); // Hide navigation objects
                            ui_show_screen(UI_SCREEN_INCOMING_CALL, 0);
                            ui_incoming_call_screen_update(
                                callerName ? callerName : "Unknown",
                                callerNumber ? callerNumber : ""
                            );
                            
                            // Start ringing animation
                            ui_incoming_call_screen_start_ringing();
                            
                            // DON'T use Arduino_GFX displayIncomingCall - it will overwrite LVGL!
                            // displayIncomingCall(String(callerName ? callerName : "Unknown"), String(callerNumber ? callerNumber : ""));
                            Serial.println("[CALL] LVGL incoming call screen should be visible now");
                        } else {
                            Serial.println("[CALL] INCOMING ignored - missed call is showing");
                        }
                    } else if (strcmp(callState, "ONGOING") == 0) {
                        Serial.println("[CALL] Displaying ONGOING call via LVGL");
                        phoneCallDisplayStartTime = millis();  // Track display start time
                        
                        // Use LVGL screen for ongoing/outgoing calls (NO ANIMATION)
                        ui_navigation_hide_all_objects(); // Hide navigation objects
                        ui_show_screen(UI_SCREEN_OUTGOING_CALL, 0);
                        ui_outgoing_call_screen_update(callerName ? callerName : "Unknown");
                        
                        // Update status based on duration (0 = still calling, >0 = connected)
                        if (duration > 0) {
                            ui_outgoing_call_screen_set_connecting(false);  // Connected
                            ui_outgoing_call_screen_update_duration(duration);
                        } else {
                            ui_outgoing_call_screen_set_connecting(true);   // Still calling
                        }
                        
                        // Update call state
                        isPhoneCallActive = true;
                        currentCallerName = callerName ? String(callerName) : "Unknown";
                        currentCallState = "ONGOING";
                        
                        Serial.println("[CALL] LVGL outgoing/ongoing call screen should be visible now");
                    } else if (strcmp(callState, "MISSED") == 0) {
                        String name = currentCallerName.length() > 0 ? currentCallerName : String(callerName ? callerName : "Unknown");
                        String number = currentCallerNumber.length() > 0 ? currentCallerNumber : String(callerNumber ? callerNumber : "");
                        
                        // Store persistent missed call info (increment count if same number, replace if different)
                        if (persistentMissedCall.callerNumber == String(number)) {
                            persistentMissedCall.count++;
                        } else {
                            persistentMissedCall.callerName = name;
                            persistentMissedCall.callerNumber = number;
                            persistentMissedCall.count = 1;
                        }
                        persistentMissedCall.firstMissedTime = millis();
                        persistentMissedCall.acknowledged = false;
                        // Initialize reminder timer for first time
                        lastMissedCallReminderTime = 0;
                        
                        displayMissedCall(name, number, persistentMissedCall.count);
                    } else if (strcmp(callState, "ENDED") == 0) {
                        // Call ended - just restore navigation
                        // Note: Android app will send MISSED state separately if call was missed
                        Serial.println("[CALL] Call ended - restoring navigation");
                        clearPhoneDisplay();
                    }
                } else {
                    // Handle navigation data - ALWAYS UPDATE SAVED STATE, BUT ONLY REDRAW IF NO CALL
                    const char* dir = doc["direction"] | "";
                    int dist = doc["distance"] | 0;
                    const char* man = doc["maneuver"] | "";
                    const char* eta = doc["eta"] | "";
                    
                    String newDirection = String(dir ? dir : "");
                    
                    if (DEBUG_NAVIGATION) {
                        Serial.printf("[NAV] dir=%s, dist=%d, man=%s, eta=%s\n", dir, dist, man, eta);
                    }
                    
                    // Store old direction BEFORE updating for comparison
                    String oldDirection = currentDirection;
                    
                    // ALWAYS update both current AND saved state (silently during calls)
                    currentDirection = String(dir ? dir : "");
                    currentDistance = dist;
                    currentManeuver = String(man ? man : "");
                    currentETA = String(eta ? eta : "");
                    
                    savedDirection = currentDirection;
                    savedDistance = currentDistance;
                    savedManeuver = currentManeuver;
                    savedETA = currentETA;
                    wasNavigationActive = true;
                    lastNavUpdate = millis(); // Update last navigation update time
                    
                    if (DEBUG_NAVIGATION) {
                        Serial.printf("[NAV] Stored state - dir:%s, dist:%d, man:%s, eta:%s\n", 
                                      currentDirection.c_str(), currentDistance, currentManeuver.c_str(), currentETA.c_str());
                    }
                    
                    // Determine if we have real navigation data
                    bool hasNav = false;
                    {
                        const bool dirValid = (currentDirection.length() > 0 && currentDirection != "straight" && currentDirection != "forward");
                        const bool distValid = (currentDistance > 0);
                        const bool manValid = (currentManeuver.length() > 0);
                        const bool etaValid = (currentETA.length() > 0);
                        hasNav = (dirValid || distValid || manValid || etaValid);
                    }
                    if (hasNav) {
                        lastNavUpdate = millis(); // Only when real nav present
                    }

                    // Only redraw navigation if no call is active AND we have real nav data
                    if (!isPhoneCallActive && !isMissedCallShowing && hasNav) {
                        UIScreen currentScreen = ui_get_current_screen();
                        
                        // Switch to navigation screen if not already there
                        if (currentScreen != UI_SCREEN_NAVIGATION) {
                            Serial.println("[NAV] Switching to LVGL navigation screen");
                            ui_show_screen(UI_SCREEN_NAVIGATION, 0);  // Immediate load, no animation
                            
                            // Process LVGL a few times to ensure screen is ready
                            for (int i = 0; i < 3; i++) {
                                lv_timer_handler();
                                delay(1);
                            }
                            Serial.println("[NAV] Navigation screen ready");
                        }
                        
                        // Update LVGL navigation screen with latest data
                        if (currentDirection.length() > 0 && currentDirection != "straight" && currentDirection != "forward") {
                            ui_navigation_screen_update_direction(currentDirection.c_str(), false);
                        } else {
                            // Hide arrows if direction not meaningful
                            ui_navigation_screen_update_direction("", false);
                        }
                        if (currentDistance > 0) {
                            ui_navigation_screen_update_distance(currentDistance, false);
                        } else {
                            ui_navigation_screen_update_distance(0, false);
                        }
                        if (currentManeuver.length() > 0) {
                            ui_navigation_screen_update_maneuver(currentManeuver.c_str());
                        } else {
                            ui_navigation_screen_update_maneuver("");
                        }
                        if (currentETA.length() > 0) {
                            ui_navigation_screen_update_eta(currentETA.c_str());
                        } else {
                            ui_navigation_screen_update_eta("");
                        }
                        
                        Serial.println("[NAV] Navigation screen updated");
                    } else if (!isPhoneCallActive && !isMissedCallShowing && !hasNav) {
                        // No real nav data: ensure we are on idle
                        if (ui_get_current_screen() != UI_SCREEN_IDLE) {
                            Serial.println("[NAV] No real nav data - switching to IDLE");
                            ui_show_screen(UI_SCREEN_IDLE, 0);
                            ui_idle_screen_set_no_nav_msg(true);
                            ui_idle_screen_update_ble_status(deviceConnected);
                        }
                    }
                }
            }
        }
    }
};

// ==== SETUP ====
void setup() {
    Serial.begin(115200);
    
    pinMode(GFX_BL, OUTPUT);
    digitalWrite(GFX_BL, HIGH);
    gfx->begin();
    lcd_reg_init();
    
    // Don't clear screen or draw status here - LVGL will handle display
    // gfx->fillScreen(0x0000);  // Commented out - LVGL manages display
    // displayStatus("STARTING...", 0xFFE0);  // Commented out - LVGL shows welcome screen
    
    // Initialize touch
    // Configure I2C pins for touch controller
    Wire.begin(Touch_I2C_SDA, Touch_I2C_SCL);
    
    // Initialize touch controller using library's function
    bsp_touch_init(&Wire, Touch_RST, Touch_INT, gfx->getRotation(), gfx->width(), gfx->height());
    touchEnabled = true;
    Serial.println("Touch controller initialized");
    
    BLEDevice::init("ESP32_BLE");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    
    BLEService *pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    
    pCharacteristic->setCallbacks(new MyCallbacks());
    pCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    
    Serial.println("[BLE] Starting advertising...");
    Serial.printf("[BLE] Device name: ESP32_BLE\n");
    Serial.printf("[BLE] Service UUID: %s\n", SERVICE_UUID);
    Serial.printf("[BLE] Characteristic UUID: %s\n", CHARACTERISTIC_UUID);
    
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Advertising started - waiting for connection...");
    Serial.println("[BLE] Make sure your Android app is scanning and connecting to 'ESP32_BLE'");
    
    // Initialize LVGL - MUST call lv_init() first (done in lvgl_init)
    // Then initialize display driver AFTER Arduino_GFX is ready
    Serial.println("[LVGL] Initializing LVGL...");
    lvgl_init();  // This calls lv_init() internally
    
    // Initialize display driver (allocates buffers, sets up flush callback)
    lvgl_display_init(gfx);
    
    // Initialize touch input device for LVGL
    lv_indev_drv_init(&indev_drv);
    indev_drv.type = LV_INDEV_TYPE_POINTER;
    indev_drv.read_cb = lvgl_touchpad_read;
    indev = lv_indev_drv_register(&indev_drv);
    Serial.println("[LVGL] Touch input device registered");
    
    // Initialize UI theme first (before screens)
    ui_theme_init();
    
    // Initialize all UI screens
    ui_screens_init();
    Serial.println("[LVGL] All UI screens initialized");
    
    // Show welcome screen initially (will auto-transition to idle when BLE connects)
    Serial.println("[LVGL] About to show welcome screen...");
    ui_show_screen(UI_SCREEN_WELCOME, 0);  // No animation for immediate display
    ui_welcome_screen_update_ble_status(deviceConnected);
    Serial.println("[LVGL] Welcome screen displayed");
    Serial.println("[LVGL] Make sure to call lv_timer_handler() in loop()!");
    
    // Force first render by calling timer handler a few times
    for (int i = 0; i < 10; i++) {
        lv_timer_handler();
        delay(10);
    }
    Serial.println("[LVGL] Initial render completed");
    
    // Register dismiss callbacks for all call screens
    Serial.println("[UI] Registering dismiss callbacks for all call screens...");
    ui_incoming_call_screen_set_callbacks(nullptr, clearPhoneDisplay);
    ui_outgoing_call_screen_set_hangup_callback(clearPhoneDisplay);
    ui_missed_call_screen_set_dismiss_callback(clearPhoneDisplay);
    Serial.println("[UI] All dismiss callbacks registered");
}

void loop() {
    // LVGL task handler (must be called every 5-10ms for smooth UI)
    // This is CRITICAL - without this, LVGL screens won't update!
    lv_timer_handler();
    
    // Check for touch input using library's functions
    if (touchEnabled) {
        touch_data_t touch_data;
        bsp_touch_read();
        bool touchpad_pressed = bsp_touch_get_coordinates(&touch_data);
        
        if (touchpad_pressed) {
            Serial.printf("Touch detected: num=%d, x=%d, y=%d\n", 
                        touch_data.touch_num, 
                        touch_data.coords[0].x, 
                        touch_data.coords[0].y);
            
            // IMPORTANT: Touch coordinates are valid when bsp_touch_get_coordinates returns true
            // Even if touch_data.touch_num is 0, the coordinates are valid
            int x = touch_data.coords[0].x;
            int y = touch_data.coords[0].y;
            
            // Only process if coordinates are valid (greater than 0)
            if (x > 0 && y > 0) {
                Serial.printf("Processing touch at (%d, %d)\n", x, y);
                handlePhoneCallTouch(x, y);
            } else {
                Serial.println("Touch coordinates invalid");
            }
        }
        
        // Add periodic touch test
        static unsigned long lastTouchTest = 0;
        if (millis() - lastTouchTest > 5000) { // Every 5 seconds
            Serial.println("Touch system active - waiting for input...");
            lastTouchTest = millis();
        }
    }
    
    // Add timeout check for incoming calls
    if (isPhoneCallActive && currentCallState == "INCOMING") {
        unsigned long currentTime = millis();
        if (currentTime - callStartTime > 30000) { // 30 seconds timeout
            Serial.println("Incoming call timeout - treating as missed");
            displayMissedCall(currentCallerName, currentCallerNumber, 1);
        } else {
            // Animation disabled - LVGL handles this
            // drawRingingAnimation();
        }
    }
    
    // Periodic missed call reminder (if not acknowledged and timeout passed)
    // NOTE: Removed blocking delay() - use state machine instead to avoid freezing LVGL
    static unsigned long missedCallReminderStartTime = 0;
    static bool showingMissedCallReminder = false;
    
    if (!persistentMissedCall.acknowledged && persistentMissedCall.count > 0) {
        unsigned long currentTime = millis();
        
        // Check if initial display time has passed (10 seconds) OR if already in reminder mode
        if (currentTime - persistentMissedCall.firstMissedTime > INITIAL_MISSED_CALL_DISPLAY_TIME || lastMissedCallReminderTime > 0) {
            // Check if it's time for a new reminder (every 60 seconds)
            if (!showingMissedCallReminder && (currentTime - lastMissedCallReminderTime > MISSED_CALL_REMINDER_INTERVAL)) {
                // Start showing reminder
                if (DEBUG_CALLS) {
                    Serial.printf("[CALL] Showing missed call reminder: %s (%d times)\n", 
                                  persistentMissedCall.callerName.c_str(), persistentMissedCall.count);
                }
                displayMissedCall(persistentMissedCall.callerName, 
                                  persistentMissedCall.callerNumber, 
                                  persistentMissedCall.count);
                missedCallReminderStartTime = currentTime;
                showingMissedCallReminder = true;
                lastMissedCallReminderTime = currentTime;
            }
            
            // After REMINDER_DISPLAY_TIME, restore navigation (non-blocking)
            if (showingMissedCallReminder && (currentTime - missedCallReminderStartTime > REMINDER_DISPLAY_TIME)) {
                clearPhoneDisplay();  // This restores navigation
                showingMissedCallReminder = false;
            }
        }
    } else {
        // Reset reminder state if call was acknowledged
        showingMissedCallReminder = false;
        missedCallReminderStartTime = 0;
    }
    
    // Update screen BLE status based on current screen
    static bool lastBleState = false;
    static unsigned long lastBleStateChangeTime = 0;
    
    if (deviceConnected != lastBleState) {
        Serial.printf("[BLE] State changed: %d -> %d\n", lastBleState, deviceConnected);
        lastBleStateChangeTime = millis();
        UIScreen currentScreen = ui_get_current_screen();
        if (currentScreen == UI_SCREEN_WELCOME) {
            ui_welcome_screen_update_ble_status(deviceConnected);
            Serial.println("[BLE] Updated welcome screen status");
        } else if (currentScreen == UI_SCREEN_IDLE) {
            ui_idle_screen_update_ble_status(deviceConnected);
            Serial.println("[BLE] Updated idle screen status");
        }
        lastBleState = deviceConnected;
    }
    
    // Auto-transition: Welcome → Idle when BLE connects (if still on welcome)
    // Add small delay after BLE state change to ensure UI updates properly
    UIScreen currentScreen = ui_get_current_screen();
    if (deviceConnected && 
        currentScreen != UI_SCREEN_INCOMING_CALL && 
        currentScreen != UI_SCREEN_OUTGOING_CALL && 
        currentScreen != UI_SCREEN_MISSED_CALL &&
        currentScreen != UI_SCREEN_NAVIGATION &&
        currentScreen != UI_SCREEN_IDLE &&
        currentScreen != UI_SCREEN_WELCOME) {
        const bool dirValid = (currentDirection.length() > 0 && currentDirection != "straight" && currentDirection != "forward");
        const bool distValid = (currentDistance > 0);
        const bool manValid = (currentManeuver.length() > 0);
        const bool etaValid = (currentETA.length() > 0);
        bool hasNavAuto = (dirValid || distValid || manValid || etaValid);
        if (hasNavAuto) {
            ui_show_screen(UI_SCREEN_NAVIGATION, 0);
            if (dirValid) ui_navigation_screen_update_direction(currentDirection.c_str(), false);
            if (currentDistance > 0) ui_navigation_screen_update_distance(currentDistance, false);
            if (manValid) ui_navigation_screen_update_maneuver(currentManeuver.c_str());
            if (etaValid) ui_navigation_screen_update_eta(currentETA.c_str());
        } else {
            ui_show_screen(UI_SCREEN_IDLE, 0);
            ui_idle_screen_set_no_nav_msg(true);
            ui_idle_screen_update_ble_status(deviceConnected);
            ui_idle_screen_start_pulse();
        }
    }
    
    // Periodic status reporting
    static unsigned long lastHeartbeat = 0;
    static unsigned long lastBleAdvertiseCheck = 0;
    
    if (millis() - lastHeartbeat > 1000) {
        Serial.println("[HEARTBEAT] Loop running, LVGL active");
        Serial.printf("[STATUS] BLE connected: %d, Current screen: %d\n", deviceConnected, (int)ui_get_current_screen());
        lastHeartbeat = millis();
    }
    
    // Periodically check/advertise BLE if disconnected
    if (millis() - lastBleAdvertiseCheck > 5000 && !deviceConnected) {
        if (BLEDevice::getInitialized()) {
            // Try to restart advertising if it stopped
            BLEDevice::startAdvertising();
            Serial.println("[BLE] Restarting advertising (still not connected)");
        }
        lastBleAdvertiseCheck = millis();
    }
    
    // Minimal delay - LVGL needs frequent updates (5ms is good)
    delay(5);
}

// Touch functions are provided by the library - no need to implement them
