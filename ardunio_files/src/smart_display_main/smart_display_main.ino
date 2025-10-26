#include <Arduino_GFX_Library.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>

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
#define ZONE_ARROW_H 160
#define ZONE_DISTANCE_Y 220
#define ZONE_DISTANCE_H 50
#define ZONE_MANEUVER_Y 270
#define ZONE_MANEUVER_H 40
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

// ==== Global State ====
String currentETA = "";
String currentManeuver = "";
int scrollOffset = 0;
unsigned long lastScrollTime = 0;

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
        WRITE_C8_D8, 0xDE, 0x00, WRITE_C8_D8, 0x35, 0x00, WRITE_C8_D8, 0x3A, 0x05,
        WRITE_COMMAND_8, 0x2A, WRITE_BYTES, 4, 0x00, 0x22, 0x00, 0xCD,
        WRITE_COMMAND_8, 0x2B, WRITE_BYTES, 4, 0x00, 0x00, 0x01, 0x3F,
        WRITE_C8_D8, 0xDE, 0x02, WRITE_COMMAND_8, 0xE5, WRITE_BYTES, 3, 0x00, 0x02, 0x00,
        WRITE_C8_D8, 0xDE, 0x00, WRITE_C8_D8, 0x36, 0x00, WRITE_COMMAND_8, 0x21, END_WRITE,
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

// STRAIGHT arrow - Pointing UP (Enhanced)
void drawStraightArrow(int x, int y) {
    // Draw white outline first
    for (int offset = -4; offset <= 4; offset++) {
        gfx->drawLine(x+offset, y, x+offset, y-42, COLOR_WHITE); // White outline
    }
    gfx->fillTriangle(x, y-63, x-17, y-42, x+17, y-42, COLOR_WHITE); // White outline
    
    // Draw colored arrow on top
    for (int offset = -3; offset <= 3; offset++) {
        gfx->drawLine(x+offset, y, x+offset, y-40, COLOR_GREEN); // Green shaft (thicker)
    }
    gfx->fillTriangle(x, y-60, x-15, y-40, x+15, y-40, COLOR_GREEN); // Green arrow head
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

// MERGE LEFT - Yellow curved (Enhanced)
void drawMergeLeft(int x, int y) {
    // Enhanced merge curve from left
    for (int i = 0; i < 7; i++) {
        gfx->drawLine(x-42+i, y-14, x-21, y+7, COLOR_YELLOW); // Yellow merge lines
    }
}

// MERGE RIGHT - Yellow curved (Enhanced)
void drawMergeRight(int x, int y) {
    // Enhanced merge curve from right
    for (int i = 0; i < 7; i++) {
        gfx->drawLine(x+42-i, y-14, x+21, y+7, COLOR_YELLOW); // Yellow merge lines
    }
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
    } else if (dir == "merge_left" || dir == "merge_slight_left") {
        drawMergeLeft(midX, midY);
    } else if (dir == "merge_right" || dir == "merge_slight_right") {
        drawMergeRight(midX, midY);
    } else if (dir == "keep_left") {
        drawKeepLeft(midX, midY);
    } else if (dir == "keep_right") {
        drawKeepRight(midX, midY);
    }
}

// STATUS BAR (Enhanced with icons)
void displayStatus(String text, uint16_t color) {
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
void displayManeuver(String text) {
    currentManeuver = text;
    gfx->fillRect(0, ZONE_MANEUVER_Y, SCREEN_WIDTH, ZONE_MANEUVER_H, COLOR_BLACK);
    
    // Update scrolling
    unsigned long currentTime = millis();
    if (currentTime - lastScrollTime > 100) { // Scroll every 100ms
        scrollOffset++;
        if (scrollOffset > text.length() * 6) { // Approximate character width
            scrollOffset = -SCREEN_WIDTH;
        }
        lastScrollTime = currentTime;
    }
    
    gfx->setCursor(10 + scrollOffset, ZONE_MANEUVER_Y + 12);
    gfx->setTextColor(COLOR_YELLOW);
    gfx->setTextSize(2);
    gfx->println(text);
}

// ==== BLE CALLBACKS ====
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *pServer) {
        deviceConnected = true;
        displayStatus("CONNECTED", COLOR_GREEN); // Green
    }
    
    void onDisconnect(BLEServer *pServer) {
        deviceConnected = false;
        displayStatus("DISCONNECTED", COLOR_RED); // Red
        gfx->fillRect(0, ZONE_ARROW_Y, SCREEN_WIDTH, SCREEN_HEIGHT - ZONE_ARROW_Y, COLOR_BLACK);
    }
};

class MyCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) {
        String value = pChar->getValue();
        if (value.length() > 0) {
            Serial.print("Received: ");
            Serial.println(value);
            
            StaticJsonDocument<256> doc;
            if (deserializeJson(doc, value) == DeserializationError::Ok) {
                const char* dir = doc["direction"];
                int dist = doc["distance"];
                const char* man = doc["maneuver"];
                
                // Only parse ETA field (remove speed)
                const char* eta = doc["eta"];
                
                drawArrow(String(dir));
                displayDistance(dist);
                displayManeuver(String(man ? man : ""));
                
                // Only display ETA if available
                if (eta) {
                    displayETA(String(eta));
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
    gfx->fillScreen(0x0000);
    
    displayStatus("STARTING...", 0xFFE0);
    
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
    BLEDevice::startAdvertising();
    
    displayStatus("READY", 0x07E0);
    Serial.println("ESP32 BLE Ready");
}

void loop() {
    delay(1000);
}
