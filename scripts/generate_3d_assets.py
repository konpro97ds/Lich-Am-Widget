import os
import math
from PIL import Image, ImageDraw, ImageFilter

OUT_DIR = "app/src/main/res/drawable-nodpi"
os.makedirs(OUT_DIR, exist_ok=True)

W, H = 860, 400

def new_canvas():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))

def add_soft_glow(base, cx, cy, radius, color, blur=35):
    layer = new_canvas()
    d = ImageDraw.Draw(layer)
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=color)
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    return Image.alpha_composite(base, layer)

def draw_3d_cloud(base, cx, cy, scale=1.0, dark=False):
    shadow_col = (15, 23, 42, 160) if dark else (30, 41, 59, 95)
    body_col = (71, 85, 105, 235) if dark else (226, 232, 240, 235)
    top_col = (148, 163, 184, 245) if dark else (255, 255, 255, 250)

    puffs = [
        (-75, 12, 46), (-32, -18, 62), (24, -12, 54), (72, 10, 42), (0, 18, 48)
    ]
    # Đổ bóng đáy mây
    s_layer = new_canvas()
    sd = ImageDraw.Draw(s_layer)
    for dx, dy, r in puffs:
        x, y, rad = cx + dx * scale, cy + (dy + 12) * scale, r * scale
        sd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=shadow_col)
    s_layer = s_layer.filter(ImageFilter.GaussianBlur(14))
    base = Image.alpha_composite(base, s_layer)

    # Thân mây chính
    b_layer = new_canvas()
    bd = ImageDraw.Draw(b_layer)
    for dx, dy, r in puffs:
        x, y, rad = cx + dx * scale, cy + dy * scale, r * scale
        bd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=body_col)
    b_layer = b_layer.filter(ImageFilter.GaussianBlur(3))
    base = Image.alpha_composite(base, b_layer)

    # Viền sáng đỉnh mây tạo khối 3D
    h_layer = new_canvas()
    hd = ImageDraw.Draw(h_layer)
    for dx, dy, r in puffs[:3]:
        x, y, rad = cx + dx * scale, cy + (dy - 8) * scale, r * 0.78 * scale
        hd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=top_col)
    h_layer = h_layer.filter(ImageFilter.GaussianBlur(8))
    return Image.alpha_composite(base, h_layer)

def draw_3d_sun(base, cx, cy, r=44, soft=False):
    glow_r = r * (1.8 if soft else 2.3)
    glow_alpha = 90 if soft else 145
    base = add_soft_glow(base, cx, cy, glow_r, (253, 224, 71, glow_alpha), blur=28)
    sun = new_canvas()
    d = ImageDraw.Draw(sun)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(250, 204, 21, 245))
    d.ellipse((cx - r * 0.82, cy - r * 0.82, cx + r * 0.72, cy + r * 0.72), fill=(254, 249, 195, 250))
    sun = sun.filter(ImageFilter.GaussianBlur(1.5))
    return Image.alpha_composite(base, sun)

def draw_3d_moon(base, cx, cy, r=46, phase="full"):
    base = add_soft_glow(base, cx, cy, r * 2.1, (254, 240, 138, 115), blur=26)
    moon = new_canvas()
    d = ImageDraw.Draw(moon)
    # Phần nền tối của quả cầu trăng
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(71, 85, 105, 110))

    lit = Image.new("L", (W, H), 0)
    ld = ImageDraw.Draw(lit)
    if phase == "full":
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    elif phase == "half":
        ld.pieslice((cx - r, cy - r, cx + r, cy + r), 90, 270, fill=255)
    else:  # crescent
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
        ld.ellipse((cx - r * 0.45, cy - r * 1.05, cx + r * 1.25, cy + r * 1.05), fill=0)

    lit = lit.filter(ImageFilter.GaussianBlur(2))
    surf = new_canvas()
    sd = ImageDraw.Draw(surf)
    sd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(254, 249, 195, 255))
    # Hố thiên thạch mờ tự nhiên
    for dx, dy, cr in [(-14, -10, 11), (12, -8, 8), (-6, 15, 9), (10, 12, 6)]:
        sd.ellipse((cx + dx - cr, cy + dy - cr, cx + dx + cr, cy + dy + cr), fill=(203, 213, 225, 95))

    moon_lit = Image.composite(surf, new_canvas(), lit)
    return Image.alpha_composite(Image.alpha_composite(base, moon), moon_lit)

def draw_firework(base, fx, fy, radius, col1, col2):
    base = add_soft_glow(base, fx, fy, radius * 1.1, (*col1[:3], 95), blur=22)
    fw = new_canvas()
    d = ImageDraw.Draw(fw)
    for deg in range(0, 360, 15):
        rad = math.radians(deg)
        length = radius if deg % 30 == 0 else radius * 0.72
        x1, y1 = fx + math.cos(rad) * 10, fy + math.sin(rad) * 10
        x2, y2 = fx + math.cos(rad) * length, fy + math.sin(rad) * length
        d.line((x1, y1, x2, y2), fill=col1 if deg % 30 == 0 else col2, width=3)
        d.ellipse((x2 - 3, y2 - 3, x2 + 3, y2 + 3), fill=(255, 255, 255, 240))
    return Image.alpha_composite(base, fw)

def draw_lantern(base, lx, ly, scale=1.0):
    base = add_soft_glow(base, lx, ly, 45 * scale, (239, 68, 68, 120), blur=18)
    lan = new_canvas()
    d = ImageDraw.Draw(lan)
    d.line((lx, ly - 35 * scale, lx, ly - 18 * scale), fill=(250, 204, 21, 255), width=3)
    d.ellipse((lx - 22 * scale, ly - 20 * scale, lx + 22 * scale, ly + 20 * scale), fill=(220, 38, 38, 250))
    d.ellipse((lx - 12 * scale, ly - 18 * scale, lx + 12 * scale, ly + 18 * scale), fill=(248, 113, 113, 210))
    d.rectangle((lx - 11 * scale, ly - 23 * scale, lx + 11 * scale, ly - 17 * scale), fill=(250, 204, 21, 255))
    d.rectangle((lx - 11 * scale, ly + 17 * scale, lx + 11 * scale, ly + 23 * scale), fill=(250, 204, 21, 255))
    d.line((lx, ly + 23 * scale, lx, ly + 38 * scale), fill=(250, 204, 21, 255), width=2)
    return Image.alpha_composite(base, lan)

def build_all():
    # 1. SUN
    im = draw_3d_sun(new_canvas(), 630, 125, r=46, soft=False)
    im = draw_3d_cloud(im, 480, 155, scale=0.78, dark=False)
    im.save(f"{OUT_DIR}/art_sun.png")

    # 2. SUN_CLOUD
    im = draw_3d_sun(new_canvas(), 645, 118, r=38, soft=True)
    im = draw_3d_cloud(im, 535, 145, scale=1.05, dark=False)
    im = draw_3d_cloud(im, 690, 158, scale=0.88, dark=False)
    im.save(f"{OUT_DIR}/art_sun_cloud.png")

    # 3. MOON (Full / Half / Crescent)
    for name, ph in [("art_moon_full", "full"), ("art_moon_half", "half"), ("art_moon_crescent", "crescent")]:
        im = draw_3d_moon(new_canvas(), 630, 125, r=46, phase=ph)
        im.save(f"{OUT_DIR}/{name}.png")

    # 4. MOON_CLOUD
    im = draw_3d_moon(new_canvas(), 645, 115, r=44, phase="half")
    im = draw_3d_cloud(im, 540, 148, scale=1.0, dark=True)
    im = draw_3d_cloud(im, 695, 160, scale=0.85, dark=True)
    im.save(f"{OUT_DIR}/art_moon_cloud.png")

    # 5. CLOUD
    im = draw_3d_cloud(new_canvas(), 430, 132, scale=1.05, dark=True)
    im = draw_3d_cloud(im, 580, 122, scale=1.25, dark=True)
    im = draw_3d_cloud(im, 735, 142, scale=0.98, dark=True)
    im.save(f"{OUT_DIR}/art_cloud.png")

    # 6. RAIN & THUNDER
    for fname in ["art_rain", "art_thunder"]:
        im = draw_3d_cloud(new_canvas(), 460, 120, scale=1.15, dark=True)
        im = draw_3d_cloud(im, 640, 128, scale=1.28, dark=True)
        d = ImageDraw.Draw(im)
        for gx, gy, gr in [(180, 90, 5), (320, 140, 6), (510, 190, 5), (710, 110, 6), (780, 175, 4)]:
            d.ellipse((gx - gr, gy - gr, gx + gr, gy + gr), fill=(224, 242, 254, 130), outline=(255, 255, 255, 190))
        im.save(f"{OUT_DIR}/{fname}.png")

    # 7. TET
    im = add_soft_glow(new_canvas(), 640, 150, 220, (185, 28, 28, 110), blur=45)
    im = draw_firework(im, 540, 115, 75, (253, 224, 71, 240), (251, 146, 60, 220))
    im = draw_firework(im, 720, 100, 60, (253, 164, 175, 235), (253, 224, 71, 215))
    im = draw_lantern(im, 450, 58, scale=0.85)
    im = draw_lantern(im, 785, 55, scale=0.8)
    im.save(f"{OUT_DIR}/art_tet.png")

    # 8. NATIONAL
    im = add_soft_glow(new_canvas(), 620, 145, 200, (185, 28, 28, 115), blur=40)
    im = draw_firework(im, 470, 105, 65, (253, 224, 71, 240), (251, 146, 60, 220))
    d = ImageDraw.Draw(im)
    fx, fy, fw, fh = 550, 82, 175, 116
    d.rounded_rectangle((fx, fy, fx + fw, fy + fh), radius=10, fill=(220, 38, 38, 245))
    scx, scy = fx + fw / 2, fy + fh / 2
    pts = []
    for i in range(10):
        rad_s = 34 if i % 2 == 0 else 13
        ang = math.radians(i * 36 - 90)
        pts.append((scx + math.cos(ang) * rad_s, scy + math.sin(ang) * rad_s))
    d.polygon(pts, fill=(250, 204, 21, 255))
    im.save(f"{OUT_DIR}/art_national.png")

    # 9. MID_AUTUMN
    im = draw_3d_moon(new_canvas(), 630, 120, r=50, phase="full")
    for lx in [220, 380, 540, 700]:
        im = draw_lantern(im, lx, 205, scale=0.58)
    im.save(f"{OUT_DIR}/art_mid_autumn.png")

    # 10. XMAS
    im = add_soft_glow(new_canvas(), 660, 145, 180, (6, 95, 70, 115), blur=38)
    d = ImageDraw.Draw(im)
    tx, by = 665, 215
    d.rectangle((tx - 10, by - 12, tx + 10, by + 18), fill=(120, 53, 15, 235))
    for ty, hw, th in [(by - 12, 64, 46), (by - 40, 52, 42), (by - 66, 40, 38), (by - 90, 28, 34)]:
        d.polygon([(tx, ty - th), (tx - hw, ty), (tx + hw, ty)], fill=(16, 185, 129, 235))
    im.save(f"{OUT_DIR}/art_xmas.png")

if __name__ == "__main__":
    build_all()
    print("Generated 13 3D PNG assets successfully.")
