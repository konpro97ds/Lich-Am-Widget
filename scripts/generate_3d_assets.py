import os
import math
import random
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
    shadow_col = (15, 23, 42, 165) if dark else (30, 41, 59, 95)
    body_col = (71, 85, 105, 238) if dark else (226, 232, 240, 235)
    top_col = (148, 163, 184, 245) if dark else (255, 255, 255, 250)

    puffs = [
        (-75, 12, 46), (-32, -18, 62), (24, -12, 54), (72, 10, 42), (0, 18, 48)
    ]
    s_layer = new_canvas()
    sd = ImageDraw.Draw(s_layer)
    for dx, dy, r in puffs:
        x, y, rad = cx + dx * scale, cy + (dy + 12) * scale, r * scale
        sd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=shadow_col)
    s_layer = s_layer.filter(ImageFilter.GaussianBlur(14))
    base = Image.alpha_composite(base, s_layer)

    b_layer = new_canvas()
    bd = ImageDraw.Draw(b_layer)
    for dx, dy, r in puffs:
        x, y, rad = cx + dx * scale, cy + dy * scale, r * scale
        bd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=body_col)
    b_layer = b_layer.filter(ImageFilter.GaussianBlur(3))
    base = Image.alpha_composite(base, b_layer)

    h_layer = new_canvas()
    hd = ImageDraw.Draw(h_layer)
    for dx, dy, r in puffs[:3]:
        x, y, rad = cx + dx * scale, cy + (dy - 8) * scale, r * 0.78 * scale
        hd.ellipse((x - rad, y - rad, x + rad, y + rad), fill=top_col)
    h_layer = h_layer.filter(ImageFilter.GaussianBlur(8))
    return Image.alpha_composite(base, h_layer)

def draw_3d_sun(base, cx, cy, r=46, soft=False):
    glow_r = r * (1.8 if soft else 2.2)
    glow_alpha = 85 if soft else 140
    base = add_soft_glow(base, cx, cy, glow_r, (253, 224, 71, glow_alpha), blur=26)
    sun = new_canvas()
    d = ImageDraw.Draw(sun)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(250, 204, 21, 245))
    d.ellipse((cx - r * 0.80, cy - r * 0.80, cx + r * 0.80, cy + r * 0.80), fill=(254, 249, 195, 252))
    sun = sun.filter(ImageFilter.GaussianBlur(1.5))
    return Image.alpha_composite(base, sun)

def draw_realistic_moon(base, cx, cy, r=54, illum=1.0, waning=True):
    base = add_soft_glow(base, cx, cy, r * 1.95, (254, 243, 199, int(125 * max(0.45, illum))), blur=24)

    dark_disc = new_canvas()
    dd = ImageDraw.Draw(dark_disc)
    dd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(51, 65, 85, 95))
    base = Image.alpha_composite(base, dark_disc)

    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    if illum >= 0.96:
        md.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    else:
        pts = []
        steps = 90
        rx = (2.0 * illum - 1.0) * r
        if not waning:
            for i in range(steps + 1):
                ang = -math.pi / 2 + math.pi * i / steps
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
            for i in range(steps, -1, -1):
                ang = -math.pi / 2 + math.pi * i / steps
                pts.append((cx - rx * math.cos(ang), cy + r * math.sin(ang)))
        else:
            for i in range(steps + 1):
                ang = math.pi / 2 + math.pi * i / steps
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
            for i in range(steps, -1, -1):
                ang = math.pi / 2 + math.pi * i / steps
                pts.append((cx - rx * math.cos(ang), cy + r * math.sin(ang)))
        md.polygon(pts, fill=255)

    mask = mask.filter(ImageFilter.GaussianBlur(1.8))

    surf = new_canvas()
    sd = ImageDraw.Draw(surf)
    sd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(254, 249, 195, 255))

    maria_layer = new_canvas()
    mard = ImageDraw.Draw(maria_layer)
    maria_blobs = [
        (-0.32, -0.25, 0.34, 0.24),
        (-0.12, -0.35, 0.26, 0.20),
        (0.18, -0.18, 0.28, 0.22),
        (-0.28, 0.14, 0.30, 0.26),
        (0.05, 0.22, 0.22, 0.18),
        (0.28, 0.12, 0.18, 0.22),
    ]
    for dx, dy, rw, rh in maria_blobs:
        bx, by = cx + dx * r, cy + dy * r
        mard.ellipse((bx - rw * r, by - rh * r, bx + rw * r, by + rh * r), fill=(180, 175, 155, 72))
    maria_layer = maria_layer.filter(ImageFilter.GaussianBlur(4))
    surf = Image.alpha_composite(surf, maria_layer)

    lit_moon = Image.composite(surf, new_canvas(), mask)
    return Image.alpha_composite(base, lit_moon)

def draw_chrysanthemum_firework_3d(base, fx, fy, radius, c_core, c_mid, c_tip):
    """
    Pháo hoa hoa cúc đại đóa 3D:
    - Quầng sáng trung tâm rực rỡ
    - 36 tia cong mềm theo trọng lực (parabolic droop)
    - Đầu tia phát sáng đa tầng + bụi kim tuyến lấp lánh
    """
    base = add_soft_glow(base, fx, fy, radius * 1.35, (*c_mid[:3], 130), blur=28)
    fw = new_canvas()
    d = ImageDraw.Draw(fw)

    rng = random.Random(int(fx * 100 + fy))
    for i in range(36):
        deg = i * 10 + rng.uniform(-2.5, 2.5)
        rad = math.radians(deg)
        scale_len = rng.uniform(0.68, 1.05)
        L = radius * scale_len

        # Vẽ đường cong tia pháo hoa rủ nhẹ theo trọng lực (5 đoạn)
        pts = []
        for step in range(6):
            t = step / 5.0
            px = fx + math.cos(rad) * (L * t)
            py = fy + math.sin(rad) * (L * t) + (t * t) * (radius * 0.18)
            pts.append((px, py))

        col = c_mid if i % 2 == 0 else c_tip
        d.line(pts, fill=col, width=3 if i % 3 == 0 else 2)

        # Hạt đầu tia rực sáng
        ex, ey = pts[-1]
        d.ellipse((ex - 3.5, ey - 3.5, ex + 3.5, ey + 3.5), fill=(255, 255, 255, 255))
        d.ellipse((ex - 5.5, ey - 5.5, ex + 5.5, ey + 5.5), fill=(*col[:3], 140))

    # Tâm nổ trắng chói
    d.ellipse((fx - 9, fy - 9, fx + 9, fy + 9), fill=c_core)
    fw = fw.filter(ImageFilter.GaussianBlur(0.8))
    return Image.alpha_composite(base, fw)

def draw_3d_blossom(draw, cx, cy, r, petal_col, inner_col, center_col):
    # Bóng mờ dưới hoa
    for i in range(5):
        ang = math.radians(i * 72 - 18)
        px = cx + math.cos(ang) * r * 0.56
        py = cy + math.sin(ang) * r * 0.56 + 2
        draw.ellipse((px - r * 0.50, py - r * 0.50, px + r * 0.50, py + r * 0.50), fill=(60, 20, 10, 90))
    # 5 cánh hoa tròn đầy
    for i in range(5):
        ang = math.radians(i * 72 - 18)
        px = cx + math.cos(ang) * r * 0.56
        py = cy + math.sin(ang) * r * 0.56
        draw.ellipse((px - r * 0.48, py - r * 0.48, px + r * 0.48, py + r * 0.48), fill=petal_col)
        draw.ellipse((px - r * 0.30, py - r * 0.30, px + r * 0.30, py + r * 0.30), fill=inner_col)
    # Nhụy hoa
    draw.ellipse((cx - r * 0.24, cy - r * 0.24, cx + r * 0.24, cy + r * 0.24), fill=center_col)

def draw_star_lantern(base, cx, cy, r=16):
    base = add_soft_glow(base, cx, cy, r * 1.9, (250, 204, 21, 130), blur=12)
    lan = new_canvas()
    d = ImageDraw.Draw(lan)
    d.line((cx, cy - r * 1.6, cx, cy - r), fill=(253, 224, 71, 240), width=2)
    pts = []
    for i in range(10):
        rad = r if i % 2 == 0 else r * 0.42
        ang = math.radians(i * 36 - 90)
        pts.append((cx + math.cos(ang) * rad, cy + math.sin(ang) * rad))
    d.polygon(pts, fill=(239, 68, 68, 245), outline=(254, 240, 138, 255))
    d.ellipse((cx - r * 0.25, cy - r * 0.25, cx + r * 0.25, cy + r * 0.25), fill=(254, 249, 195, 255))
    return Image.alpha_composite(base, lan)

def draw_lantern(base, lx, ly, scale=1.0):
    base = add_soft_glow(base, lx, ly, 44 * scale, (239, 68, 68, 125), blur=18)
    lan = new_canvas()
    d = ImageDraw.Draw(lan)
    d.line((lx, ly - 35 * scale, lx, ly - 18 * scale), fill=(250, 204, 21, 255), width=3)
    d.ellipse((lx - 22 * scale, ly - 20 * scale, lx + 22 * scale, ly + 20 * scale), fill=(220, 38, 38, 250))
    d.ellipse((lx - 12 * scale, ly - 18 * scale, lx + 12 * scale, ly + 18 * scale), fill=(248, 113, 113, 215))
    d.rectangle((lx - 11 * scale, ly - 23 * scale, lx + 11 * scale, ly - 17 * scale), fill=(250, 204, 21, 255))
    d.rectangle((lx - 11 * scale, ly + 17 * scale, lx + 11 * scale, ly + 23 * scale), fill=(250, 204, 21, 255))
    d.line((lx, ly + 23 * scale, lx, ly + 38 * scale), fill=(250, 204, 21, 255), width=2)
    return Image.alpha_composite(base, lan)

def build_all():
    # 1. SUN
    im = draw_3d_sun(new_canvas(), 625, 126, r=46, soft=False)
    im = draw_3d_cloud(im, 480, 155, scale=0.78, dark=False)
    im.save(f"{OUT_DIR}/art_sun.png")

    # 2. SUN_CLOUD
    im = draw_3d_sun(new_canvas(), 645, 118, r=38, soft=True)
    im = draw_3d_cloud(im, 535, 145, scale=1.05, dark=False)
    im = draw_3d_cloud(im, 690, 158, scale=0.88, dark=False)
    im.save(f"{OUT_DIR}/art_sun_cloud.png")

    # 3. MOON (To, rõ nét ngay trung tâm vùng thời tiết)
    im_full = draw_realistic_moon(new_canvas(), 620, 128, r=54, illum=1.0, waning=True)
    im_full.save(f"{OUT_DIR}/art_moon_full.png")

    im_half = draw_realistic_moon(new_canvas(), 620, 128, r=54, illum=0.52, waning=True)
    im_half.save(f"{OUT_DIR}/art_moon_half.png")

    im_cres = draw_realistic_moon(new_canvas(), 620, 128, r=54, illum=0.24, waning=True)
    im_cres.save(f"{OUT_DIR}/art_moon_crescent.png")

    # 4. MOON_CLOUD
    im = draw_realistic_moon(new_canvas(), 640, 118, r=50, illum=0.55, waning=True)
    im = draw_3d_cloud(im, 540, 148, scale=1.0, dark=True)
    im = draw_3d_cloud(im, 695, 160, scale=0.85, dark=True)
    im.save(f"{OUT_DIR}/art_moon_cloud.png")

    # 5. CLOUD
    im = draw_3d_cloud(new_canvas(), 430, 132, scale=1.05, dark=True)
    im = draw_3d_cloud(im, 580, 122, scale=1.25, dark=True)
    im = draw_3d_cloud(im, 735, 142, scale=0.98, dark=True)
    im.save(f"{OUT_DIR}/art_cloud.png")

        # 6. RAIN & THUNDER (Đáy mây nằm đúng Y=142px để sấm sét đánh ra từ dưới bụng mây)
    for fname in ["art_rain", "art_thunder"]:
        im = draw_3d_cloud(new_canvas(), 460, 92, scale=1.15, dark=True)
        im = draw_3d_cloud(im, 640, 100, scale=1.28, dark=True)
        d = ImageDraw.Draw(im)
        for gx, gy, gr in [(180, 90, 5), (320, 140, 6), (510, 190, 5), (710, 110, 6), (780, 175, 4)]:
            d.ellipse((gx - gr, gy - gr, gx + gr, gy + gr), fill=(224, 242, 254, 130), outline=(255, 255, 255, 190))
        im.save(f"{OUT_DIR}/{fname}.png")

    # 7. TET: Pháo hoa hoa cúc 3D rực rỡ + Cành mai vàng & Cành đào hồng rủ ở cả 4 GÓC
    im = add_soft_glow(new_canvas(), 620, 145, 250, (185, 28, 28, 125), blur=45)
    im = draw_chrysanthemum_firework_3d(im, 535, 112, 86, (255, 255, 255, 255), (253, 224, 71, 245), (244, 63, 94, 240))
    im = draw_chrysanthemum_firework_3d(im, 705, 96, 70, (255, 255, 255, 255), (253, 164, 175, 245), (103, 232, 249, 240))
    im = draw_lantern(im, 420, 54, scale=0.78)
    im = draw_lantern(im, 805, 54, scale=0.76)

    branches = new_canvas()
    bd = ImageDraw.Draw(branches)
    wood_col = (92, 45, 22, 245)

    # Góc Trên Trái: Cành đào hồng rủ xuống
    bd.line([(0, 18), (68, 36), (125, 58)], fill=wood_col, width=5)
    bd.line([(55, 32), (88, 64)], fill=wood_col, width=3)
    draw_3d_blossom(bd, 125, 58, 14, (253, 164, 175, 255), (254, 205, 211, 255), (225, 29, 72, 255))
    draw_3d_blossom(bd, 88, 64, 12, (251, 113, 133, 255), (253, 164, 175, 255), (253, 224, 71, 255))
    draw_3d_blossom(bd, 42, 34, 13, (253, 164, 175, 255), (255, 228, 230, 255), (225, 29, 72, 255))

    # Góc Trên Phải: Cành mai vàng rủ xuống
    bd.line([(W, 20), (W - 72, 38), (W - 135, 62)], fill=wood_col, width=5)
    bd.line([(W - 60, 34), (W - 95, 68)], fill=wood_col, width=3)
    draw_3d_blossom(bd, W - 135, 62, 15, (250, 204, 21, 255), (254, 240, 138, 255), (180, 83, 9, 255))
    draw_3d_blossom(bd, W - 95, 68, 13, (253, 224, 71, 255), (254, 249, 195, 255), (217, 119, 6, 255))
    draw_3d_blossom(bd, W - 44, 34, 14, (250, 204, 21, 255), (254, 240, 138, 255), (180, 83, 9, 255))

    # Góc Dưới Trái: Nhánh mai vàng vươn lên nhẹ
    bd.line([(0, H - 18), (62, H - 36), (112, H - 52)], fill=wood_col, width=4)
    draw_3d_blossom(bd, 112, H - 52, 13, (253, 224, 71, 250), (254, 249, 195, 255), (217, 119, 6, 255))
    draw_3d_blossom(bd, 56, H - 36, 12, (250, 204, 21, 250), (254, 240, 138, 255), (180, 83, 9, 255))

    # Góc Dưới Phải: Nhánh đào hồng vươn lên nhẹ
    bd.line([(W, H - 18), (W - 66, H - 38), (W - 118, H - 54)], fill=wood_col, width=4)
    draw_3d_blossom(bd, W - 118, H - 54, 13, (253, 164, 175, 250), (255, 228, 230, 255), (225, 29, 72, 255))
    draw_3d_blossom(bd, W - 58, H - 36, 12, (251, 113, 133, 250), (253, 164, 175, 255), (253, 224, 71, 255))

    im = Image.alpha_composite(im, branches)
    im.save(f"{OUT_DIR}/art_tet.png")

    # 8. NATIONAL
    im = add_soft_glow(new_canvas(), 620, 145, 200, (185, 28, 28, 115), blur=40)
    im = draw_chrysanthemum_firework_3d(im, 465, 102, 74, (255, 255, 255, 255), (253, 224, 71, 245), (251, 146, 60, 240))
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
    mcx, mcy, mr = 625, 120, 56
    im = draw_realistic_moon(new_canvas(), mcx, mcy, r=mr, illum=1.0, waning=True)

    sil = new_canvas()
    sd = ImageDraw.Draw(sil)
    sil_col = (146, 64, 14, 135)
    sd.pieslice((mcx - mr, mcy - mr, mcx + mr, mcy + mr), 15, 165, fill=sil_col)
    sd.line([(mcx + 22, mcy + 36), (mcx + 18, mcy + 8), (mcx + 6, mcy - 8)], fill=sil_col, width=5)
    sd.line([(mcx + 18, mcy + 14), (mcx + 34, mcy + 2)], fill=sil_col, width=4)
    for bx, by, br in [(mcx + 8, mcy - 12, 16), (mcx + 24, mcy - 10, 15), (mcx + 36, mcy - 2, 13), (mcx - 4, mcy - 4, 12)]:
        sd.ellipse((bx - br, by - br, bx + br, by + br), fill=sil_col)
    sd.ellipse((mcx + 2, mcy + 18, mcx + 10, mcy + 26), fill=sil_col)
    sd.rounded_rectangle((mcx + 1, mcy + 26, mcx + 12, mcy + 38), radius=3, fill=sil_col)
    sd.ellipse((mcx - 28, mcy - 18, mcx - 21, mcy - 11), fill=sil_col)
    sd.polygon([(mcx - 25, mcy - 10), (mcx - 32, mcy + 8), (mcx - 18, mcy + 6)], fill=sil_col)
    sd.arc((mcx - 42, mcy - 22, mcx - 12, mcy + 4), 160, 340, fill=sil_col, width=2)

    sil = sil.filter(ImageFilter.GaussianBlur(1.0))
    im = Image.alpha_composite(im, sil)

    im = draw_star_lantern(im, 205, 224, r=16)
    im = draw_lantern(im, 372, 224, scale=0.58)
    im = draw_star_lantern(im, 540, 224, r=16)
    im = draw_lantern(im, 708, 224, scale=0.58)
    im.save(f"{OUT_DIR}/art_mid_autumn.png")

    # 10. XMAS
    im = add_soft_glow(new_canvas(), 665, 135, 190, (6, 95, 70, 125), blur=40)
    tree = new_canvas()
    d = ImageDraw.Draw(tree)
    tx, by = 668, 214
    d.rectangle((tx - 11, by - 14, tx + 11, by + 18), fill=(120, 53, 15, 245))
    tiers = [(by - 12, 72, 50), (by - 42, 58, 46), (by - 70, 44, 42), (by - 96, 30, 36)]
    for ty, hw, th in tiers:
        d.polygon([(tx, ty - th), (tx - hw, ty), (tx + hw, ty)], fill=(6, 95, 70, 245))
        d.polygon([(tx, ty - th), (tx - hw * 0.85, ty - 4), (tx + hw * 0.85, ty - 4)], fill=(16, 185, 129, 235))
        d.polygon([(tx, ty - th), (tx - hw * 0.36, ty - th * 0.58), (tx + hw * 0.36, ty - th * 0.58)], fill=(255, 255, 255, 220))

    d.arc((tx - 46, by - 82, tx + 46, by - 54), 15, 165, fill=(253, 224, 71, 235), width=3)
    d.arc((tx - 60, by - 52, tx + 60, by - 22), 15, 165, fill=(253, 224, 71, 235), width=3)

    ornaments = [
        (tx - 18, by - 84, (239, 68, 68, 255)),
        (tx + 16, by - 76, (250, 204, 21, 255)),
        (tx - 30, by - 54, (56, 189, 248, 255)),
        (tx + 8, by - 50, (239, 68, 68, 255)),
        (tx + 34, by - 46, (250, 204, 21, 255)),
        (tx - 42, by - 24, (250, 204, 21, 255)),
        (tx - 10, by - 22, (56, 189, 248, 255)),
        (tx + 24, by - 20, (239, 68, 68, 255)),
        (tx + 48, by - 18, (167, 243, 208, 255)),
    ]
    for ox, oy, ocol in ornaments:
        d.ellipse((ox - 5, oy - 5, ox + 5, oy + 5), fill=ocol, outline=(255, 255, 255, 200))

    sy = by - 134
    s_pts = []
    for i in range(10):
        sr = 15 if i % 2 == 0 else 6
        ang = math.radians(i * 36 - 90)
        s_pts.append((tx + math.cos(ang) * sr, sy + math.sin(ang) * sr))
    d.polygon(s_pts, fill=(254, 240, 138, 255), outline=(245, 158, 11, 255))

    d.rounded_rectangle((tx - 46, by + 2, tx - 22, by + 20), radius=3, fill=(220, 38, 38, 245))
    d.line([(tx - 34, by + 2), (tx - 34, by + 20)], fill=(253, 224, 71, 255), width=3)
    d.rounded_rectangle((tx + 22, by + 4, tx + 44, by + 20), radius=3, fill=(37, 99, 235, 245))
    d.line([(tx + 33, by + 4), (tx + 33, by + 20)], fill=(254, 240, 138, 255), width=3)

    im = Image.alpha_composite(im, tree)
    im.save(f"{OUT_DIR}/art_xmas.png")

if __name__ == "__main__":
    build_all()
    print("Generated 13 3D PNG assets successfully.")
