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

def draw_3d_sun(base, cx, cy, r=44, soft=False):
    glow_r = r * (1.8 if soft else 2.2)
    glow_alpha = 85 if soft else 140
    base = add_soft_glow(base, cx, cy, glow_r, (253, 224, 71, glow_alpha), blur=26)
    sun = new_canvas()
    d = ImageDraw.Draw(sun)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(250, 204, 21, 245))
    d.ellipse((cx - r * 0.80, cy - r * 0.80, cx + r * 0.80, cy + r * 0.80), fill=(254, 249, 195, 252))
    sun = sun.filter(ImageFilter.GaussianBlur(1.5))
    return Image.alpha_composite(base, sun)

def draw_realistic_moon(base, cx, cy, r=48, illum=1.0, waning=True):
    """
    Vẽ mặt trăng thiên văn thực tế theo tỷ lệ chiếu sáng (0.0 -> 1.0)
    Không dùng các chấm đối xứng gây cảm giác mặt người
    """
    base = add_soft_glow(base, cx, cy, r * 2.0, (254, 243, 199, int(115 * max(0.35, illum))), blur=24)

    # 1. Đĩa trăng nền (phần tối ánh đất mờ tự nhiên)
    dark_disc = new_canvas()
    dd = ImageDraw.Draw(dark_disc)
    dd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(51, 65, 85, 85))
    base = Image.alpha_composite(base, dark_disc)

    # 2. Mặt nạ chiếu sáng chuẩn hình học thiên văn
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    if illum >= 0.96:
        md.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    else:
        # Tạo polygon theo đường phân giới elip thực tế
        pts = []
        steps = 80
        rx = (2.0 * illum - 1.0) * r
        if not waning:
            # Trăng đầu tháng: sáng bên phải
            for i in range(steps + 1):
                ang = -math.pi / 2 + math.pi * i / steps
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
            for i in range(steps, -1, -1):
                ang = -math.pi / 2 + math.pi * i / steps
                pts.append((cx - rx * math.cos(ang), cy + r * math.sin(ang)))
        else:
            # Trăng cuối tháng (như 20/10 Âm): sáng bên trái
            for i in range(steps + 1):
                ang = math.pi / 2 + math.pi * i / steps
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
            for i in range(steps, -1, -1):
                ang = math.pi / 2 + math.pi * i / steps
                pts.append((cx - rx * math.cos(ang), cy + r * math.sin(ang)))
        md.polygon(pts, fill=255)

    mask = mask.filter(ImageFilter.GaussianBlur(1.8))

    # 3. Bề mặt trăng thật với các biển trăng (Maria) bất đối xứng tự nhiên
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
        mard.ellipse((bx - rw * r, by - rh * r, bx + rw * r, by + rh * r), fill=(180, 175, 155, 68))
    maria_layer = maria_layer.filter(ImageFilter.GaussianBlur(4))
    surf = Image.alpha_composite(surf, maria_layer)

    lit_moon = Image.composite(surf, new_canvas(), mask)
    return Image.alpha_composite(base, lit_moon)

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

def draw_rich_firework(base, fx, fy, radius, col1, col2, col3):
    base = add_soft_glow(base, fx, fy, radius * 1.25, (*col1[:3], 115), blur=26)
    fw = new_canvas()
    d = ImageDraw.Draw(fw)
    for deg in range(0, 360, 15):
        rad = math.radians(deg)
        length = radius if deg % 30 == 0 else radius * 0.76
        x1, y1 = fx + math.cos(rad) * 8, fy + math.sin(rad) * 8
        x2, y2 = fx + math.cos(rad) * length, fy + math.sin(rad) * length
        c = col1 if deg % 45 == 0 else (col2 if deg % 30 == 0 else col3)
        d.line((x1, y1, x2, y2), fill=c, width=3 if deg % 30 == 0 else 2)
        d.ellipse((x2 - 3.5, y2 - 3.5, x2 + 3.5, y2 + 3.5), fill=(255, 255, 255, 250))
        d.ellipse((x2 - 2, y2 + 5, x2 + 2, y2 + 9), fill=c)
    d.ellipse((fx - 7, fy - 7, fx + 7, fy + 7), fill=(255, 255, 255, 255))
    return Image.alpha_composite(base, fw)

def draw_blossom(draw, cx, cy, r, petal_col, center_col):
    for i in range(5):
        ang = math.radians(i * 72 - 18)
        px = cx + math.cos(ang) * r * 0.58
        py = cy + math.sin(ang) * r * 0.58
        draw.ellipse((px - r * 0.48, py - r * 0.48, px + r * 0.48, py + r * 0.48), fill=petal_col)
    draw.ellipse((cx - r * 0.25, cy - r * 0.25, cx + r * 0.25, cy + r * 0.25), fill=center_col)

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

    # 3. MOON (Chu kỳ mặt trăng tự nhiên: trăng tròn, bán nguyệt, lưỡi liềm)
    im_full = draw_realistic_moon(new_canvas(), 635, 124, r=48, illum=1.0, waning=True)
    im_full.save(f"{OUT_DIR}/art_moon_full.png")

    im_half = draw_realistic_moon(new_canvas(), 635, 124, r=48, illum=0.52, waning=True)
    im_half.save(f"{OUT_DIR}/art_moon_half.png")

    im_cres = draw_realistic_moon(new_canvas(), 635, 124, r=48, illum=0.22, waning=True)
    im_cres.save(f"{OUT_DIR}/art_moon_crescent.png")

    # 4. MOON_CLOUD
    im = draw_realistic_moon(new_canvas(), 645, 114, r=45, illum=0.55, waning=True)
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
    im = add_soft_glow(new_canvas(), 630, 145, 240, (185, 28, 28, 120), blur=45)
    im = draw_rich_firework(im, 510, 112, 82, (253, 224, 71, 245), (251, 146, 60, 235), (244, 63, 94, 235))
    im = draw_rich_firework(im, 685, 96, 68, (253, 164, 175, 245), (103, 232, 249, 235), (253, 224, 71, 235))
    im = draw_rich_firework(im, 780, 138, 52, (254, 240, 138, 240), (251, 113, 133, 230), (167, 243, 208, 230))
    im = draw_lantern(im, 425, 56, scale=0.82)
    im = draw_lantern(im, 815, 54, scale=0.78)
    branch_layer = new_canvas()
    bd = ImageDraw.Draw(branch_layer)
    bd.line([(845, 220), (740, 185), (645, 168)], fill=(92, 45, 22, 240), width=5)
    draw_blossom(bd, 645, 168, 15, (253, 224, 71, 250), (217, 119, 6, 255))
    draw_blossom(bd, 705, 152, 13, (254, 240, 138, 250), (217, 119, 6, 255))
    draw_blossom(bd, 765, 172, 14, (253, 164, 175, 250), (225, 29, 72, 255))
    im = Image.alpha_composite(im, branch_layer)
    im.save(f"{OUT_DIR}/art_tet.png")

    # 8. NATIONAL
    im = add_soft_glow(new_canvas(), 620, 145, 200, (185, 28, 28, 115), blur=40)
    im = draw_rich_firework(im, 465, 102, 72, (253, 224, 71, 245), (251, 146, 60, 235), (255, 255, 255, 235))
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

    # 9. MID_AUTUMN: Trăng rằm có bóng cây đa + Chú Cuội ngồi gốc đa + Chị Hằng bay + Đèn ông sao & Đèn lồng
    mcx, mcy, mr = 640, 116, 56
    im = draw_realistic_moon(new_canvas(), mcx, mcy, r=mr, illum=1.0, waning=True)

    # Bóng nghệ thuật cây đa, Chú Cuội ngồi gốc đa & Chị Hằng in mềm bên trong đĩa trăng
    sil = new_canvas()
    sd = ImageDraw.Draw(sil)
    sil_col = (146, 64, 14, 135)
    # Gò đất & thân cây đa bên phải trăng
    sd.pieslice((mcx - mr, mcy - mr, mcx + mr, mcy + mr), 15, 165, fill=sil_col)
    sd.line([(mcx + 22, mcy + 36), (mcx + 18, mcy + 8), (mcx + 6, mcy - 8)], fill=sil_col, width=5)
    sd.line([(mcx + 18, mcy + 14), (mcx + 34, mcy + 2)], fill=sil_col, width=4)
    # Tán lá đa xum xuê
    for bx, by, br in [(mcx + 8, mcy - 12, 16), (mcx + 24, mcy - 10, 15), (mcx + 36, mcy - 2, 13), (mcx - 4, mcy - 4, 12)]:
        sd.ellipse((bx - br, by - br, bx + br, by + br), fill=sil_col)
    # Chú Cuội ngồi tựa gốc đa
    sd.ellipse((mcx + 2, mcy + 18, mcx + 10, mcy + 26), fill=sil_col)
    sd.rounded_rectangle((mcx + 1, mcy + 26, mcx + 12, mcy + 38), radius=3, fill=sil_col)
    # Chị Hằng uyển chuyển bay bên trái trăng kèm dải lụa mềm
    sd.ellipse((mcx - 28, mcy - 18, mcx - 21, mcy - 11), fill=sil_col)
    sd.polygon([(mcx - 25, mcy - 10), (mcx - 32, mcy + 8), (mcx - 18, mcy + 6)], fill=sil_col)
    sd.arc((mcx - 42, mcy - 22, mcx - 12, mcy + 4), 160, 340, fill=sil_col, width=2)

    sil = sil.filter(ImageFilter.GaussianBlur(1.0))
    im = Image.alpha_composite(im, sil)

    # Đèn ông sao 5 cánh & đèn lồng dọc mép trên dải lịch
    im = draw_star_lantern(im, 205, 224, r=16)
    im = draw_lantern(im, 372, 224, scale=0.58)
    im = draw_star_lantern(im, 540, 224, r=16)
    im = draw_lantern(im, 708, 224, scale=0.58)
    im.save(f"{OUT_DIR}/art_mid_autumn.png")

    # 10. XMAS: Cây thông Noel 3D phủ tuyết + Ngôi sao + Dây kim tuyến + Quả châu + Hộp quà
    im = add_soft_glow(new_canvas(), 665, 135, 190, (6, 95, 70, 125), blur=40)
    tree = new_canvas()
    d = ImageDraw.Draw(tree)
    tx, by = 668, 214
    # Thân gỗ
    d.rectangle((tx - 11, by - 14, tx + 11, by + 18), fill=(120, 53, 15, 245))
    # 4 tầng tán thông xanh đậm + lớp tuyết trắng viền mép dưới tán
    tiers = [(by - 12, 72, 50), (by - 42, 58, 46), (by - 70, 44, 42), (by - 96, 30, 36)]
    for ty, hw, th in tiers:
        d.polygon([(tx, ty - th), (tx - hw, ty), (tx + hw, ty)], fill=(6, 95, 70, 245))
        d.polygon([(tx, ty - th), (tx - hw * 0.85, ty - 4), (tx + hw * 0.85, ty - 4)], fill=(16, 185, 129, 235))
        # Tuyết phủ đầu tán lá
        d.polygon([(tx, ty - th), (tx - hw * 0.36, ty - th * 0.58), (tx + hw * 0.36, ty - th * 0.58)], fill=(255, 255, 255, 220))

    # Dây kim tuyến vàng uốn lượn
    d.arc((tx - 46, by - 82, tx + 46, by - 54), 15, 165, fill=(253, 224, 71, 235), width=3)
    d.arc((tx - 60, by - 52, tx + 60, by - 22), 15, 165, fill=(253, 224, 71, 235), width=3)

    # Quả châu trang trí nhiều màu (Đỏ, Vàng, Xanh dương)
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

    # Ngôi sao vàng đỉnh cây thông
    sy = by - 134
    s_pts = []
    for i in range(10):
        sr = 15 if i % 2 == 0 else 6
        ang = math.radians(i * 36 - 90)
        s_pts.append((tx + math.cos(ang) * sr, sy + math.sin(ang) * sr))
    d.polygon(s_pts, fill=(254, 240, 138, 255), outline=(245, 158, 11, 255))

    # Hộp quà Giáng sinh dưới gốc cây
    d.rounded_rectangle((tx - 46, by + 2, tx - 22, by + 20), radius=3, fill=(220, 38, 38, 245))
    d.line([(tx - 34, by + 2), (tx - 34, by + 20)], fill=(253, 224, 71, 255), width=3)
    d.rounded_rectangle((tx + 22, by + 4, tx + 44, by + 20), radius=3, fill=(37, 99, 235, 245))
    d.line([(tx + 33, by + 4), (tx + 33, by + 20)], fill=(254, 240, 138, 255), width=3)

    im = Image.alpha_composite(im, tree)
    im.save(f"{OUT_DIR}/art_xmas.png")

if __name__ == "__main__":
    build_all()
    print("Generated 13 3D PNG assets successfully.")
