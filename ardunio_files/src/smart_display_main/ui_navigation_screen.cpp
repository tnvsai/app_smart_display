#include <Arduino.h>
#include "ui_navigation_screen.h"
#include "ui_theme.h"
// Arrow generation removed for now
#include <string.h>

// Forward declarations for internal helpers
static void set_arrow_points_keep(bool to_right);

// UI element references
static lv_obj_t *img_arrow = nullptr;         // Image widget for arrows
static lv_obj_t *line_shaft = nullptr;        // Arrow shaft
static lv_obj_t *line_head1 = nullptr;        // Arrow head part 1
static lv_obj_t *line_head2 = nullptr;        // Arrow head part 2
static lv_obj_t *line_poly = nullptr;         // Polyline for complex shapes (U-turn, roundabout)
static lv_obj_t *label_distance = nullptr;    // Large distance display
static lv_obj_t *label_maneuver = nullptr;     // Maneuver instruction text
static lv_obj_t *label_eta_banner = nullptr;   // ETA display
// Simplified UI: no status bar

// Arrow image dimensions (fits 60–200px zone = 140px height)
#define ARROW_WIDTH  170
#define ARROW_HEIGHT 140

// Current state
static char current_direction[32] = "";
static int current_distance = 0;
static bool critical_alert_active = false;

// Style initialization guard
static bool nav_styles_initialized = false;

// Styles (only initialize once)
static lv_style_t style_arrow_canvas;
static lv_style_t style_arrow_line;
static lv_style_t style_distance_text;
static lv_style_t style_maneuver_text;
static lv_style_t style_eta_text;

// Reusable point arrays
static lv_point_t pts_shaft[2];
static lv_point_t pts_head1[2];
static lv_point_t pts_head2[2];
static lv_point_t pts_poly[24];

// Helpers
static void set_arrow_points_left_right(bool to_right) {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int y_mid = origin_y + ARROW_HEIGHT / 2;
    int x_start = origin_x + 20;
    int x_end   = origin_x + ARROW_WIDTH - 20;

    pts_shaft[0].x = to_right ? x_start : x_end - 10; pts_shaft[0].y = y_mid;
    pts_shaft[1].x = to_right ? x_end - 10 : x_start; pts_shaft[1].y = y_mid;

    if (to_right) {
        pts_head1[0].x = x_end - 20; pts_head1[0].y = y_mid - 15; pts_head1[1].x = x_end - 2;  pts_head1[1].y = y_mid;
        pts_head2[0].x = x_end - 20; pts_head2[0].y = y_mid + 15; pts_head2[1].x = x_end - 2;  pts_head2[1].y = y_mid;
    } else {
        pts_head1[0].x = x_start + 20; pts_head1[0].y = y_mid - 15; pts_head1[1].x = x_start + 2;  pts_head1[1].y = y_mid;
        pts_head2[0].x = x_start + 20; pts_head2[0].y = y_mid + 15; pts_head2[1].x = x_start + 2;  pts_head2[1].y = y_mid;
    }

    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

static void set_arrow_points_straight(void) {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x_mid = origin_x + ARROW_WIDTH / 2;
    int y_top = origin_y + 10;
    int y_bot = origin_y + ARROW_HEIGHT - 20;

    pts_shaft[0].x = x_mid; pts_shaft[0].y = y_bot;
    pts_shaft[1].x = x_mid; pts_shaft[1].y = y_top + 15;

    pts_head1[0].x = x_mid - 15; pts_head1[0].y = y_top + 15; pts_head1[1].x = x_mid;      pts_head1[1].y = y_top;
    pts_head2[0].x = x_mid + 15; pts_head2[0].y = y_top + 15; pts_head2[1].x = x_mid;      pts_head2[1].y = y_top;

    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

// Helper for U-turn (India/Europe: curve left, head at bottom left unless "right" specified)
static void set_arrow_points_uturn(bool to_left) {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x_mid = origin_x + ARROW_WIDTH / 2;
    int y_bot = origin_y + ARROW_HEIGHT - 10;
    int y_top = origin_y + 15;
    int arc_r = 40;
    int stem_len = 35;
    // Stem up (center)
    int x_start = x_mid;
    int y_start = y_bot;
    int y_stem_top = y_bot - stem_len;
    int n = 0;
    // Go up
    pts_poly[n++] = { (lv_coord_t)x_start, (lv_coord_t)y_start };
    pts_poly[n++] = { (lv_coord_t)x_start, (lv_coord_t)y_stem_top };
    // 180 degree arc over, 15 deg steps, 7 points
    for (int i=0; i<=7; ++i) {
        float t = (float)i/7.0f;
        float ang = to_left ? (3.14159f * (1.0f + t)) : (3.14159f * (2.0f - t));
        int xx = x_mid + (to_left ? -arc_r : arc_r) + (int)(arc_r * cosf(ang));
        int yy = y_stem_top + (int)(arc_r * sinf(ang));
        pts_poly[n++] = { (lv_coord_t)xx, (lv_coord_t)yy };
    }
    // Down leg
    int x_end = x_mid + (to_left ? -2 * arc_r : 2 * arc_r);
    int y_end = y_bot - 15;
    pts_poly[n++] = { (lv_coord_t)x_end, (lv_coord_t)y_end };
    lv_line_set_points(line_poly, pts_poly, n);
    // Arrowhead (bottom tip)
    pts_shaft[0].x = x_end;              pts_shaft[0].y = y_end - 15;
    pts_shaft[1].x = x_end;              pts_shaft[1].y = y_end;
    pts_head1[0].x = x_end - 10;         pts_head1[0].y = y_end - 7; pts_head1[1].x = x_end; pts_head1[1].y = y_end;
    pts_head2[0].x = x_end + 10;         pts_head2[0].y = y_end - 7; pts_head2[1].x = x_end; pts_head2[1].y = y_end;
    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

static void set_arrow_points_slight(bool to_right) {
    // Mild curve from bottom center, veers out left/right, arrowhead at end
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x0 = origin_x + ARROW_WIDTH / 2;
    int y0 = origin_y + ARROW_HEIGHT - 10;
    int x1 = x0 + (to_right ? 45 : -45);
    int y1 = y0 - 90;
    int x_ctrl = x0 + (to_right ? 30 : -30);
    int y_ctrl = y0 - 40;
    // Gentle polyline as 3 segments
    pts_poly[0] = { (lv_coord_t)x0, (lv_coord_t)y0 };
    pts_poly[1] = { (lv_coord_t)x_ctrl, (lv_coord_t)y_ctrl };
    pts_poly[2] = { (lv_coord_t)x1, (lv_coord_t)y1 };
    lv_line_set_points(line_poly, pts_poly, 3);
    // Arrowhead at tip
    float angle = atan2f((float)(y1 - y_ctrl), (float)(x1 - x_ctrl));
    int len = 17;
    pts_shaft[0].x = x1 - (int)(len * cosf(angle));
    pts_shaft[0].y = y1 - (int)(len * sinf(angle));
    pts_shaft[1].x = x1; pts_shaft[1].y = y1;
    pts_head1[0].x = x1 - (int)(8 * cosf(angle + 2.2f));
    pts_head1[0].y = y1 - (int)(8 * sinf(angle + 2.2f));
    pts_head1[1].x = x1; pts_head1[1].y = y1;
    pts_head2[0].x = x1 - (int)(8 * cosf(angle - 2.2f));
    pts_head2[0].y = y1 - (int)(8 * sinf(angle - 2.2f));
    pts_head2[1].x = x1; pts_head2[1].y = y1;
    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

static void set_arrow_points_sharp(bool to_right) {
    // Short up, then sharp right/left at top
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x0 = origin_x + ARROW_WIDTH / 2;
    int y0 = origin_y + ARROW_HEIGHT - 10;
    int y1 = y0 - 50;
    int x2 = to_right ? x0 + 40 : x0 - 40;
    int y2 = y1 - 40;
    // Shaft: up then horizontal right/left
    pts_poly[0] = { (lv_coord_t)x0, (lv_coord_t)y0 };
    pts_poly[1] = { (lv_coord_t)x0, (lv_coord_t)y1 };
    pts_poly[2] = { (lv_coord_t)x2, (lv_coord_t)y1 };
    pts_poly[3] = { (lv_coord_t)x2, (lv_coord_t)y2 };
    lv_line_set_points(line_poly, pts_poly, 4);
    // Arrowhead at end
    float angle = atan2f((float)(y2 - y1), (float)(x2 - x0));
    int x_tip = x2, y_tip = y2;
    pts_shaft[0].x = x2 - (int)(12 * cosf(angle));
    pts_shaft[0].y = y2 - (int)(12 * sinf(angle));
    pts_shaft[1].x = x_tip; pts_shaft[1].y = y_tip;
    pts_head1[0].x = x_tip - (int)(8 * cosf(angle + 2.2f));
    pts_head1[0].y = y_tip - (int)(8 * sinf(angle + 2.2f));
    pts_head1[1].x = x_tip; pts_head1[1].y = y_tip;
    pts_head2[0].x = x_tip - (int)(8 * cosf(angle - 2.2f));
    pts_head2[0].y = y_tip - (int)(8 * sinf(angle - 2.2f));
    pts_head2[1].x = x_tip; pts_head2[1].y = y_tip;
    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

static void set_arrow_points_roundabout(int exit_dir) {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int cx = origin_x + ARROW_WIDTH / 2;
    int cy = origin_y + ARROW_HEIGHT / 2;
    int r  = 40;

    int n = 0;
    for (int i = 0; i <= 12; i++) {
        float ang = (float)i / 12.0f * 6.28318f;
        int x = cx + (int)(r * cosf(ang));
        int y = cy + (int)(r * sinf(ang));
        pts_poly[n++] = { (lv_coord_t)x, (lv_coord_t)y };
    }
    lv_line_set_points(line_poly, pts_poly, n);

    if (exit_dir == 0) {
        pts_shaft[0].x = cx; pts_shaft[0].y = cy - r;
        pts_shaft[1].x = cx; pts_shaft[1].y = cy - r - 15;
        pts_head1[0].x = cx - 8; pts_head1[0].y = cy - r - 5; pts_head1[1].x = cx; pts_head1[1].y = cy - r - 15;
        pts_head2[0].x = cx + 8; pts_head2[0].y = cy - r - 5; pts_head2[1].x = cx; pts_head2[1].y = cy - r - 15;
    } else if (exit_dir < 0) {
        pts_shaft[0].x = cx - r; pts_shaft[0].y = cy;
        pts_shaft[1].x = cx - r - 15; pts_shaft[1].y = cy;
        pts_head1[0].x = cx - r - 5; pts_head1[0].y = cy - 8; pts_head1[1].x = cx - r - 15; pts_head1[1].y = cy;
        pts_head2[0].x = cx - r - 5; pts_head2[0].y = cy + 8; pts_head2[1].x = cx - r - 15; pts_head2[1].y = cy;
    } else {
        pts_shaft[0].x = cx + r; pts_shaft[0].y = cy;
        pts_shaft[1].x = cx + r + 15; pts_shaft[1].y = cy;
        pts_head1[0].x = cx + r + 5; pts_head1[0].y = cy - 8; pts_head1[1].x = cx + r + 15; pts_head1[1].y = cy;
        pts_head2[0].x = cx + r + 5; pts_head2[0].y = cy + 8; pts_head2[1].x = cx + r + 15; pts_head2[1].y = cy;
    }

    lv_line_set_points(line_shaft, pts_shaft, 2);
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
}

// Utility to hide all arrows
static void hide_all_arrows() {
    if (line_shaft)  lv_obj_add_flag(line_shaft, LV_OBJ_FLAG_HIDDEN);
    if (line_head1)  lv_obj_add_flag(line_head1, LV_OBJ_FLAG_HIDDEN);
    if (line_head2)  lv_obj_add_flag(line_head2, LV_OBJ_FLAG_HIDDEN);
    if (line_poly)   lv_obj_add_flag(line_poly,  LV_OBJ_FLAG_HIDDEN);
}
// Utility to make visible only those needed
static void show_shaft_head() {
    lv_obj_clear_flag(line_shaft, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(line_head1, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(line_head2, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_flag(line_poly, LV_OBJ_FLAG_HIDDEN);
}
static void show_poly_with_heads() {
    lv_obj_clear_flag(line_shaft, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(line_head1, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(line_head2, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(line_poly, LV_OBJ_FLAG_HIDDEN);
}

static lv_obj_t *flag_pole = nullptr;
static lv_obj_t *flag_triangle = nullptr;
static lv_style_t style_flag_pole;
static lv_style_t style_flag_triangle;

// Point arrays for flag
lv_point_t pts_flag_pole[2];
lv_point_t pts_flag_head[3];

// Helper for destination (flag)
static void set_flag_symbol() {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x_left = origin_x + ARROW_WIDTH / 2 - 28;
    int y_bot = origin_y + ARROW_HEIGHT - 35;
    int y_top = y_bot - 66;
    // Flag pole
    pts_flag_pole[0] = { (lv_coord_t)x_left, (lv_coord_t)y_bot };
    pts_flag_pole[1] = { (lv_coord_t)x_left, (lv_coord_t)y_top };
    lv_line_set_points(flag_pole, pts_flag_pole, 2);
    // Flag triangle
    pts_flag_head[0] = { (lv_coord_t)x_left, (lv_coord_t)y_top };
    pts_flag_head[1] = { (lv_coord_t)x_left, (lv_coord_t)(y_top + 24) };
    pts_flag_head[2] = { (lv_coord_t)(x_left+36), (lv_coord_t)(y_top + 12) };
    lv_line_set_points(flag_triangle, pts_flag_head, 3);
}

// Update arrow image based on direction
static void update_arrow_image(const char* direction, uint16_t color) {
    if (!line_shaft || !line_head1 || !line_head2 || !line_poly) return;
    lv_obj_set_style_line_color(line_shaft, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_head1, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_head2, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_poly,  lv_color_hex(color), LV_PART_MAIN);
    // Hide everything by default
    hide_all_arrows();
    if (flag_pole) lv_obj_add_flag(flag_pole, LV_OBJ_FLAG_HIDDEN);
    if (flag_triangle) lv_obj_add_flag(flag_triangle, LV_OBJ_FLAG_HIDDEN);

    // If no direction provided or empty, leave all hidden (no default straight)
    if (direction == nullptr || *direction == '\0') {
        return;
    }

    // Normalize to lowercase for robust matching
    char dir_norm[64];
    size_t len = strnlen(direction, sizeof(dir_norm) - 1);
    for (size_t i = 0; i < len; ++i) {
        char c = direction[i];
        dir_norm[i] = (c >= 'A' && c <= 'Z') ? (c - 'A' + 'a') : c;
    }
    dir_norm[len] = '\0';

    // Map common synonyms to our internal buckets via substring tests on dir_norm
    // Left/right turn synonyms
    bool is_left  = strstr(dir_norm, "left")  || strstr(dir_norm, "turn_left")  || strstr(dir_norm, "left_turn");
    bool is_right = strstr(dir_norm, "right") || strstr(dir_norm, "turn_right") || strstr(dir_norm, "right_turn");
    // Slight/bear/keep synonyms
    bool is_keep  = strstr(dir_norm, "keep_") || strstr(dir_norm, "keep ") || strstr(dir_norm, "bear_") || strstr(dir_norm, "bear ");
    bool is_slight= strstr(dir_norm, "slight");
    bool is_sharp = strstr(dir_norm, "sharp");
    bool is_uturn = strstr(dir_norm, "uturn") || strstr(dir_norm, "u_turn") || strstr(dir_norm, "u-turn") || strstr(dir_norm, "turn_around");
    bool is_round = strstr(dir_norm, "roundabout") || strstr(dir_norm, "rotary") || strstr(dir_norm, "circle");
    bool is_straight = strstr(dir_norm, "straight") || strstr(dir_norm, "forward") || strstr(dir_norm, "continue");
    bool is_dest = strstr(dir_norm, "destination") || strstr(dir_norm, "arrived") || strstr(dir_norm, "end");

    // Color policy (use normalized dir)
    uint16_t arrow_color = color;
    if (is_straight) arrow_color = COLOR_ARROW_STRAIGHT;
    else if (is_uturn) arrow_color = 0xF81F; // magenta
    else if (is_round) arrow_color = COLOR_ACCENT_YELLOW;
    else if (is_keep || is_sharp || is_slight || is_left || is_right) arrow_color = 0xFD20; // orange
    else if (is_dest) arrow_color = 0xF800; // red

    // Routing
    if (is_uturn) {
        set_arrow_points_uturn(false); // always right per spec
        show_poly_with_heads();
        goto ARROW_COLOR;
    }
    if (is_keep && is_right) { set_arrow_points_keep(true);  show_poly_with_heads(); goto ARROW_COLOR; }
    if (is_keep && is_left)  { set_arrow_points_keep(false); show_poly_with_heads(); goto ARROW_COLOR; }
    if (is_sharp && is_right) { set_arrow_points_sharp(true);  show_poly_with_heads(); goto ARROW_COLOR; }
    if (is_sharp && is_left)  { set_arrow_points_sharp(false); show_poly_with_heads(); goto ARROW_COLOR; }
    if ((is_slight || is_keep) && is_right) { set_arrow_points_slight(true);  show_poly_with_heads(); goto ARROW_COLOR; }
    if ((is_slight || is_keep) && is_left)  { set_arrow_points_slight(false); show_poly_with_heads(); goto ARROW_COLOR; }
    if (is_round) {
        int exit_dir = 0;
        if (is_left) exit_dir = -1; else if (is_right) exit_dir = 1;
        set_arrow_points_roundabout(exit_dir);
        show_poly_with_heads();
        goto ARROW_COLOR;
    }
    if (is_right) { set_arrow_points_left_right(true);  show_shaft_head(); goto ARROW_COLOR; }
    if (is_left)  { set_arrow_points_left_right(false); show_shaft_head(); goto ARROW_COLOR; }
    if (is_dest) {
        // Lazily create flag objects if they were not created
        if (!flag_pole) {
            flag_pole = lv_line_create(lv_obj_get_parent(line_shaft));
            lv_style_init(&style_flag_pole);
            lv_style_set_line_width(&style_flag_pole, 6);
            lv_style_set_line_color(&style_flag_pole, lv_color_hex(0xF800));
            lv_obj_add_style(flag_pole, &style_flag_pole, 0);
        }
        if (!flag_triangle) {
            flag_triangle = lv_line_create(lv_obj_get_parent(line_shaft));
            lv_style_init(&style_flag_triangle);
            lv_style_set_line_width(&style_flag_triangle, 6);
            lv_style_set_line_color(&style_flag_triangle, lv_color_hex(0xF800));
            lv_obj_add_style(flag_triangle, &style_flag_triangle, 0);
        }
        set_flag_symbol();
        if (flag_pole) { lv_obj_clear_flag(flag_pole, LV_OBJ_FLAG_HIDDEN); lv_obj_set_style_line_color(flag_pole, lv_color_hex(0xF800), LV_PART_MAIN); }
        if (flag_triangle) { lv_obj_clear_flag(flag_triangle, LV_OBJ_FLAG_HIDDEN); lv_obj_set_style_line_color(flag_triangle, lv_color_hex(0xF800), LV_PART_MAIN); }
        return;
    }
    if (is_straight) {
        set_arrow_points_straight();
        show_shaft_head();
        goto ARROW_COLOR;
    }
    // Unknown: keep hidden
    return;
ARROW_COLOR:
    lv_obj_set_style_line_color(line_shaft, lv_color_hex(arrow_color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_head1, lv_color_hex(arrow_color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_head2, lv_color_hex(arrow_color), LV_PART_MAIN);
    lv_obj_set_style_line_color(line_poly,  lv_color_hex(arrow_color), LV_PART_MAIN);
}

void ui_navigation_hide_all_objects() {
    if (line_shaft)  lv_obj_add_flag(line_shaft, LV_OBJ_FLAG_HIDDEN);
    if (line_head1)  lv_obj_add_flag(line_head1, LV_OBJ_FLAG_HIDDEN);
    if (line_head2)  lv_obj_add_flag(line_head2, LV_OBJ_FLAG_HIDDEN);
    if (line_poly)   lv_obj_add_flag(line_poly,  LV_OBJ_FLAG_HIDDEN);
    if (flag_pole)   lv_obj_add_flag(flag_pole, LV_OBJ_FLAG_HIDDEN);
    if (flag_triangle) lv_obj_add_flag(flag_triangle, LV_OBJ_FLAG_HIDDEN);
}
static void set_arrow_points_keep(bool to_right) {
    const int origin_x = (172 - ARROW_WIDTH) / 2;
    const int origin_y = 60;
    int x_mid = origin_x + ARROW_WIDTH / 2;
    int y_bot = origin_y + ARROW_HEIGHT - 10;
    int y_top = origin_y + 22;
    int y_tick = y_top + 30;
    int tick_len = 24;
    // Upward shaft
    pts_shaft[0].x = x_mid; pts_shaft[0].y = y_bot;
    pts_shaft[1].x = x_mid; pts_shaft[1].y = y_top;
    lv_line_set_points(line_shaft, pts_shaft, 2);
    // Arrowhead at top
    pts_head1[0].x = x_mid - 15; pts_head1[0].y = y_top + 15; pts_head1[1].x = x_mid; pts_head1[1].y = y_top;
    pts_head2[0].x = x_mid + 15; pts_head2[0].y = y_top + 15; pts_head2[1].x = x_mid; pts_head2[1].y = y_top;
    lv_line_set_points(line_head1, pts_head1, 2);
    lv_line_set_points(line_head2, pts_head2, 2);
    // Tick on left/right
    if (to_right) {
        pts_poly[0].x = x_mid; pts_poly[0].y = y_tick;
        pts_poly[1].x = x_mid + tick_len; pts_poly[1].y = y_tick - 16;
    } else {
        pts_poly[0].x = x_mid; pts_poly[0].y = y_tick;
        pts_poly[1].x = x_mid - tick_len; pts_poly[1].y = y_tick - 16;
    }
    lv_line_set_points(line_poly, pts_poly, 2);
}

void ui_navigation_screen_create(lv_obj_t *parent) {
    if (parent == nullptr) {
        Serial.println("[UI] Error: parent is null in ui_navigation_screen_create");
        return;
    }
    
    Serial.println("[UI] Creating navigation screen with image-based arrows");
    
    if (!nav_styles_initialized) {
        lv_style_init(&style_arrow_canvas);
        lv_style_set_bg_opa(&style_arrow_canvas, LV_OPA_TRANSP);
        lv_style_set_border_width(&style_arrow_canvas, 0);
        lv_style_set_pad_all(&style_arrow_canvas, 0);
        
        lv_style_init(&style_arrow_line);
        lv_style_set_line_width(&style_arrow_line, 8);
        lv_style_set_line_rounded(&style_arrow_line, true);
        lv_style_set_line_color(&style_arrow_line, lv_color_hex(COLOR_ARROW_STRAIGHT));
        
        lv_style_init(&style_distance_text);
        lv_style_set_text_font(&style_distance_text, &lv_font_montserrat_28);
        lv_style_set_text_color(&style_distance_text, lv_color_hex(0xFFFF));
        lv_style_set_text_align(&style_distance_text, LV_TEXT_ALIGN_CENTER);
        lv_style_set_text_letter_space(&style_distance_text, 3);
        lv_style_set_bg_opa(&style_distance_text, LV_OPA_TRANSP);
        
        lv_style_init(&style_maneuver_text);
        lv_style_set_text_font(&style_maneuver_text, &lv_font_montserrat_20);
        lv_style_set_text_color(&style_maneuver_text, lv_color_hex(COLOR_ACCENT_YELLOW));
        lv_style_set_text_align(&style_maneuver_text, LV_TEXT_ALIGN_CENTER);
        lv_style_set_text_letter_space(&style_maneuver_text, 2);
        lv_style_set_bg_opa(&style_maneuver_text, LV_OPA_TRANSP);
        lv_style_set_pad_all(&style_maneuver_text, 8);
        
        lv_style_init(&style_eta_text);
        lv_style_set_text_font(&style_eta_text, &lv_font_montserrat_20);
        lv_style_set_text_color(&style_eta_text, lv_color_hex(COLOR_ACCENT_YELLOW));
        lv_style_set_text_align(&style_eta_text, LV_TEXT_ALIGN_CENTER);
        lv_style_set_text_letter_space(&style_eta_text, 2);
        lv_style_set_bg_opa(&style_eta_text, LV_OPA_TRANSP);
        
        nav_styles_initialized = true;
        Serial.println("[UI] Navigation styles initialized");
    }
    
    lv_obj_set_style_bg_color(parent, lv_color_hex(0x000000), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, LV_PART_MAIN);
    
    line_poly = lv_line_create(parent);
    lv_obj_add_style(line_poly, &style_arrow_line, 0);
    lv_obj_align(line_poly, LV_ALIGN_TOP_LEFT, 0, 0);

    line_shaft = lv_line_create(parent);
    lv_obj_add_style(line_shaft, &style_arrow_line, 0);
    lv_obj_align(line_shaft, LV_ALIGN_TOP_LEFT, 0, 0);

    line_head1 = lv_line_create(parent);
    lv_obj_add_style(line_head1, &style_arrow_line, 0);
    lv_obj_align(line_head1, LV_ALIGN_TOP_LEFT, 0, 0);

    line_head2 = lv_line_create(parent);
    lv_obj_add_style(line_head2, &style_arrow_line, 0);
    lv_obj_align(line_head2, LV_ALIGN_TOP_LEFT, 0, 0);

    // Do not default to straight; start with no direction and hidden arrows
    strncpy(current_direction, "", sizeof(current_direction) - 1);
    current_direction[sizeof(current_direction) - 1] = '\0';
    ui_navigation_hide_all_objects();
    
    label_distance = lv_label_create(parent);
    if (!label_distance) {
        Serial.println("[UI] ERROR: Failed to create distance label");
        return;
    }
    
    lv_obj_add_style(label_distance, &style_distance_text, 0);
    lv_label_set_text(label_distance, "");
    lv_obj_set_size(label_distance, 170, 50);
    lv_obj_set_style_text_color(label_distance, lv_color_hex(0xFFFF), LV_PART_MAIN);
    lv_obj_set_style_text_font(label_distance, lv_font_default(), LV_PART_MAIN);
    lv_obj_set_style_text_letter_space(label_distance, 3, LV_PART_MAIN);
    lv_obj_set_style_text_align(label_distance, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(label_distance, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_align(label_distance, LV_ALIGN_TOP_MID, 0, 200);
    lv_obj_clear_flag(label_distance, LV_OBJ_FLAG_CLICKABLE);
    
    Serial.println("[UI] Created HUGE distance (170x100) in WHITE)");
    
    label_maneuver = lv_label_create(parent);
    if (label_maneuver) {
        lv_obj_add_style(label_maneuver, &style_maneuver_text, 0);
        lv_label_set_text(label_maneuver, "");
        lv_obj_set_width(label_maneuver, 170);
        lv_obj_set_height(label_maneuver, 50);
        lv_obj_set_style_text_color(label_maneuver, lv_color_hex(COLOR_ACCENT_YELLOW), LV_PART_MAIN);
        lv_obj_set_style_text_font(label_maneuver, lv_font_default(), LV_PART_MAIN);
        lv_obj_set_style_text_letter_space(label_maneuver, 2, LV_PART_MAIN);
        lv_obj_set_style_text_align(label_maneuver, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
        lv_obj_set_style_bg_opa(label_maneuver, LV_OPA_TRANSP, LV_PART_MAIN);
        lv_obj_align(label_maneuver, LV_ALIGN_TOP_MID, 0, 250);
        lv_label_set_long_mode(label_maneuver, LV_LABEL_LONG_WRAP);
        lv_obj_set_style_pad_all(label_maneuver, 8, LV_PART_MAIN);
    }
    
    label_eta_banner = lv_label_create(parent);
    if (label_eta_banner) {
        lv_obj_add_style(label_eta_banner, &style_eta_text, 0);
        lv_label_set_text(label_eta_banner, "");
        lv_obj_set_size(label_eta_banner, 170, 30);
        lv_obj_set_style_text_color(label_eta_banner, lv_color_hex(COLOR_ACCENT_YELLOW), LV_PART_MAIN);
        lv_obj_set_style_text_font(label_eta_banner, lv_font_default(), LV_PART_MAIN);
        lv_obj_set_style_text_letter_space(label_eta_banner, 2, LV_PART_MAIN);
        lv_obj_set_style_text_align(label_eta_banner, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
        lv_obj_set_style_bg_opa(label_eta_banner, LV_OPA_TRANSP, LV_PART_MAIN);
        lv_obj_align(label_eta_banner, LV_ALIGN_TOP_MID, 0, 30);
    }
    
    uint16_t arrow_color = COLOR_ARROW_STRAIGHT;
    update_arrow_image("straight", arrow_color);
    
    Serial.println("[UI] Navigation screen created (LINE-BASED ARROWS, initially hidden)");
}

void ui_navigation_screen_update_direction(const char* direction, bool animated) {
    if (!direction) {
        Serial.println("[NAV] Warning: direction is null");
        return;
    }
    
    if (strcmp(current_direction, direction) == 0) {
        return;
    }
    
    if (!direction || !*direction) {
        ui_navigation_hide_all_objects();
        strncpy(current_direction, "", sizeof(current_direction) - 1);
        current_direction[sizeof(current_direction) - 1] = '\0';
        Serial.println("[NAV] Blank direction received, hiding arrows.");
        return;
    }
    
    // Accept explicit straight/forward from app (do not suppress)
    // Update and render normally
    strncpy(current_direction, direction, sizeof(current_direction) - 1);
    current_direction[sizeof(current_direction) - 1] = '\0';
    
    uint16_t arrow_color = COLOR_ARROW_STRAIGHT;
    update_arrow_image(direction, arrow_color);
    Serial.printf("[NAV] Updated direction to: %s\n", direction);
}

void ui_navigation_screen_update_distance(int distance, bool animated) {
    if (!label_distance) return;
    
    current_distance = distance;
    
    if (distance <= 0) {
        lv_label_set_text(label_distance, "");
        ui_navigation_screen_show_critical_alert(false);
        return;
    }

    char distance_str[32];
    if (distance >= 1000) {
        snprintf(distance_str, sizeof(distance_str), "%.1f km", distance / 1000.0f);
    } else {
        snprintf(distance_str, sizeof(distance_str), "%d m", distance);
    }
    
    lv_label_set_text(label_distance, distance_str);
    
    bool should_show_alert = (distance > 0 && distance < 100);
    if (should_show_alert != critical_alert_active) {
        ui_navigation_screen_show_critical_alert(should_show_alert);
    }
}

void ui_navigation_screen_update_maneuver(const char* maneuver) {
    if (!label_maneuver || !maneuver) return;
    lv_label_set_text(label_maneuver, maneuver);
    Serial.printf("[NAV] Updated maneuver: %s\n", maneuver);
}

void ui_navigation_screen_update_eta(const char* eta) {
    if (!label_eta_banner || !eta) return;
    lv_label_set_text(label_eta_banner, eta);
    Serial.printf("[NAV] Updated ETA: %s\n", eta);
}

void ui_navigation_screen_show_critical_alert(bool show) {
    critical_alert_active = show;
    if (show) {
        Serial.println("[NAV] CRITICAL ALERT: Very close to turn!");
    } else {
        Serial.println("[NAV] Critical alert cleared");
    }
}

void ui_navigation_screen_update_compass(int heading) {
    Serial.printf("[NAV] Compass heading: %d degrees\n", heading);
}

void ui_navigation_screen_clear(void) {
    if (label_distance) lv_label_set_text(label_distance, "");
    if (label_maneuver) lv_label_set_text(label_maneuver, "");
    if (label_eta_banner) lv_label_set_text(label_eta_banner, "");

    strncpy(current_direction, "", sizeof(current_direction) - 1);
    current_direction[sizeof(current_direction) - 1] = '\0';
    critical_alert_active = false;
    ui_navigation_hide_all_objects();
    Serial.println("[NAV] Navigation screen cleared");
}

void ui_navigation_screen_set_ble(bool connected) { (void)connected; }
void ui_navigation_screen_set_signal(int bars) { (void)bars; }
