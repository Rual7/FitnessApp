"""Vectorizes the figure and the check mark of the reference logo.

Run:  python design/trace_logo.py      (needs only Pillow)

Reads   design/reference/logo-reference.jpg
Writes  design/logo_trace.json   polygons per color layer, in reference-image pixels
        design/trace_preview.png quick visual comparison (left: reference, right: trace)

The ring (arcs and dashes) is NOT traced: it is rebuilt exactly from measured angles in
generate_logo.py. Pixels belonging to the ring are masked out here.
Pure Python on purpose (numpy is not required).
"""
import json
import math
import os
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "reference", "logo-reference.jpg")

# Palette sampled from the reference image: name -> (rgb, drawn?)
PALETTE = [
    ("bg", (25, 25, 23), False),
    ("shadow", (12, 12, 10), False),
    ("node", (56, 54, 43), True),
    ("mid", (120, 104, 86), True),
    ("deep", (196, 168, 128), True),
    ("tan", (227, 205, 167), True),
    ("cream", (245, 230, 198), True),
]
DRAW_ORDER = ["deep", "mid", "tan", "cream", "node"]  # painted bottom to top

# Ring geometry measured from the image (see generate_logo.py)
RING_C = (575.1, 492.3)
RING_BAND = (385.0, 437.0)
ARCS = [(1.0, 23.0), (25.0, 52.25), (54.5, 82.25), (104.25, 127.25),
        (129.5, 135.75), (138.0, 175.75), (178.0, 187.5), (199.25, 245.5)]

EPS = 1.1          # polygon simplification tolerance, in pixels
MIN_AREA = 25      # drop specks smaller than this (px^2)


def angle_of(x, y):
    return math.degrees(math.atan2(y - RING_C[1], x - RING_C[0])) % 360


def classify(im):
    flat = []
    for _, rgb, _ in PALETTE:
        flat += list(rgb)
    flat += [0] * (768 - len(flat))
    pal = Image.new("P", (1, 1))
    pal.putpalette(flat)
    q = im.quantize(palette=pal, dither=Image.Dither.NONE)
    # keep the raw palette indices (convert("L") would map them to palette luminance)
    idx = Image.frombytes("L", im.size, q.tobytes())
    return idx.filter(ImageFilter.ModeFilter(3))  # remove JPEG speckle, keep thin lines


def cleanup_fringes(idx_img, im):
    """Anti-aliasing pixels along an edge are often classified as 'deep'/'mid' tan, which leaves thin
    tan slivers and specks around the shapes. Thin deep/mid regions (the ones that vanish under a
    morphological opening) are reclassified using only bg / tan / cream."""
    from PIL import ImageFilter as F
    names = [n for n, _, _ in PALETTE]
    deep, mid = names.index("deep"), names.index("mid")
    mask = idx_img.point(lambda v: 255 if v in (deep, mid) else 0)
    opened = mask.filter(F.MinFilter(5)).filter(F.MaxFilter(5))
    px, mp, op, rgb = idx_img.load(), mask.load(), opened.load(), im.load()
    cand = [(names.index(n), c) for n, c, _ in PALETTE if n in ("bg", "tan", "cream")]
    changed = 0
    W, H = idx_img.size
    for y in range(H):
        for x in range(W):
            if mp[x, y] and not op[x, y]:
                r, g, b = rgb[x, y]
                px[x, y] = min(cand, key=lambda t: (t[1][0] - r) ** 2 + (t[1][1] - g) ** 2 + (t[1][2] - b) ** 2)[0]
                changed += 1
    print("fringe pixels reclassified:", changed)


# Approximate node positions in reference pixels: (x, y, radius, class). Refined by circle fitting.
NODES = [
    (127, 392, 30, "cream"), (1053, 392, 30, "cream"),
    (458, 353, 27, "cream"), (720, 353, 27, "cream"),
    (519, 607, 25, "cream"), (659.6, 607.1, 25, "tan"),  # the right hip is in shade: tan, not cream
    (323, 413, 24, "node"), (855, 413, 24, "node"),
    (590, 378, 26, "node"), (590, 482, 26, "node"), (588, 712, 24, "node"),
]


def fit_circle(pts):
    n = len(pts)
    sx = sum(p[0] for p in pts); sy = sum(p[1] for p in pts)
    sxx = sum(p[0] ** 2 for p in pts); syy = sum(p[1] ** 2 for p in pts); sxy = sum(p[0] * p[1] for p in pts)
    b1 = sum(p[0] * (p[0] ** 2 + p[1] ** 2) for p in pts)
    b2 = sum(p[1] * (p[0] ** 2 + p[1] ** 2) for p in pts)
    b3 = sum(p[0] ** 2 + p[1] ** 2 for p in pts)
    A = [[sxx, sxy, sx], [sxy, syy, sy], [sx, sy, n]]
    B = [b1, b2, b3]
    M = [r[:] + [b] for r, b in zip(A, B)]
    for i in range(3):
        p = max(range(i, 3), key=lambda r: abs(M[r][i])); M[i], M[p] = M[p], M[i]
        for r in range(3):
            if r != i:
                f = M[r][i] / M[i][i]; M[r] = [a - f * b for a, b in zip(M[r], M[i])]
    a, b, c = [M[i][3] / M[i][i] for i in range(3)]
    cx, cy = a / 2, b / 2
    return cx, cy, math.sqrt(max(c + cx * cx + cy * cy, 0))


def detect_nodes(idx_img):
    """Fits a perfect circle to every node, using only the rays that leave the node cleanly."""
    names = [n for n, _, _ in PALETTE]
    px = idx_img.load()
    W, H = idx_img.size
    found = []
    for x0, y0, r0, cname in NODES:
        cls = names.index(cname)
        cx, cy, r = float(x0), float(y0), float(r0)
        for _ in range(3):
            pts = []
            for k in range(120):
                t = math.radians(k * 3)
                prev_in = True
                rad = r * 0.4
                hit = None
                while rad < r + 12:
                    x = int(round(cx + rad * math.cos(t))); y = int(round(cy + rad * math.sin(t)))
                    inside = 0 <= x < W and 0 <= y < H and px[x, y] == cls
                    if not inside:
                        hit = rad
                        break
                    rad += 0.5
                if hit is not None and abs(hit - r) < 5:
                    pts.append((cx + hit * math.cos(t), cy + hit * math.sin(t)))
            if len(pts) < 12:
                break
            cx, cy, r = fit_circle(pts)
            # drop outliers and refit once
            pts = [p for p in pts if abs(math.hypot(p[0] - cx, p[1] - cy) - r) < 1.6]
            if len(pts) >= 12:
                cx, cy, r = fit_circle(pts)
        print(f"node {cname:5s} approx ({x0},{y0},{r0}) -> ({cx:.1f},{cy:.1f}) r={r:.1f} from {len(pts)} points")
        found.append({"cx": round(cx, 2), "cy": round(cy, 2), "r": round(r, 2), "cls": cname})
    return found


def blank_discs(idx_img, nodes):
    """Erases every node disc (slightly smaller than the fitted circle); the perfect circle is drawn
    on top later, so limbs end cleanly under it."""
    px = idx_img.load()
    W, H = idx_img.size
    for n in nodes:
        r = n["r"] - 0.7
        for y in range(int(n["cy"] - r) - 1, int(n["cy"] + r) + 2):
            for x in range(int(n["cx"] - r) - 1, int(n["cx"] + r) + 2):
                if 0 <= x < W and 0 <= y < H and (x + 0.5 - n["cx"]) ** 2 + (y + 0.5 - n["cy"]) ** 2 <= r * r:
                    px[x, y] = 0


def mask_ring(idx_img):
    """Erase ring arcs and dim dashes so only the figure and the check remain."""
    px = idx_img.load()
    W, H = idx_img.size
    node = [n for n, _, _ in PALETTE].index("node")
    bg = 0
    for y in range(H):
        for x in range(W):
            if px[x, y] == bg:
                continue
            dx, dy = x - RING_C[0], y - RING_C[1]
            r = math.hypot(dx, dy)
            if not (RING_BAND[0] < r < RING_BAND[1]):
                continue
            a = math.degrees(math.atan2(dy, dx)) % 360
            in_arc = any(s - 0.6 <= a <= e + 0.6 for s, e in ARCS)
            if in_arc:
                px[x, y] = bg
            elif 238 <= a <= 358 and px[x, y] == node:
                px[x, y] = bg  # dim dashes are classified as "node"


def boundary_loops(mask, W, H):
    """Crack-edge boundary loops of a binary mask. Interior is on the right of each edge."""
    nxt = {}
    def add(a, b):
        nxt.setdefault(a, []).append(b)
    for y in range(H):
        row = mask[y]
        up = mask[y - 1] if y > 0 else None
        dn = mask[y + 1] if y < H - 1 else None
        for x in range(W):
            if not row[x]:
                continue
            if up is None or not up[x]:
                add((x, y), (x + 1, y))
            if x == W - 1 or not row[x + 1]:
                add((x + 1, y), (x + 1, y + 1))
            if dn is None or not dn[x]:
                add((x + 1, y + 1), (x, y + 1))
            if x == 0 or not row[x - 1]:
                add((x, y + 1), (x, y))
    loops = []
    while nxt:
        start = next(iter(nxt))
        loop = [start]
        cur = start
        prev_dir = None
        while True:
            outs = nxt.get(cur)
            if not outs:
                break
            if len(outs) == 1 or prev_dir is None:
                nx = outs.pop(0)
            else:
                # at a pinch point prefer the sharpest right turn: keeps diagonal pixels separate
                def turn(o):
                    d = (o[0] - cur[0], o[1] - cur[1])
                    return prev_dir[0] * d[1] - prev_dir[1] * d[0]
                outs.sort(key=turn, reverse=True)
                nx = outs.pop(0)
            if not outs:
                del nxt[cur]
            prev_dir = (nx[0] - cur[0], nx[1] - cur[1])
            cur = nx
            if cur == start:
                break
            loop.append(cur)
        if len(loop) > 3:
            loops.append(loop)
    return loops


def area(poly):
    s = 0.0
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        s += x1 * y2 - x2 * y1
    return s / 2.0


def smooth(poly, k=2):
    n = len(poly)
    out = []
    for i in range(n):
        sx = sy = 0.0
        for j in range(-k, k + 1):
            p = poly[(i + j) % n]
            sx += p[0]; sy += p[1]
        out.append((sx / (2 * k + 1), sy / (2 * k + 1)))
    return out


def dp(points, eps):
    if len(points) < 3:
        return points
    (x1, y1), (x2, y2) = points[0], points[-1]
    dx, dy = x2 - x1, y2 - y1
    norm = math.hypot(dx, dy)
    best, idx = -1.0, 0
    for i in range(1, len(points) - 1):
        x0, y0 = points[i]
        d = (abs(dy * (x0 - x1) - dx * (y0 - y1)) / norm) if norm else math.hypot(x0 - x1, y0 - y1)
        if d > best:
            best, idx = d, i
    if best > eps:
        return dp(points[:idx + 1], eps)[:-1] + dp(points[idx:], eps)
    return [points[0], points[-1]]


def _intersect(p1, p2, p3, p4):
    d1 = (p2[0] - p1[0], p2[1] - p1[1])
    d2 = (p4[0] - p3[0], p4[1] - p3[1])
    den = d1[0] * d2[1] - d1[1] * d2[0]
    if abs(den) < 1e-9:
        return None
    t = ((p3[0] - p1[0]) * d2[1] - (p3[1] - p1[1]) * d2[0]) / den
    return (p1[0] + t * d1[0], p1[1] + t * d1[1])


def sharpen_corners(poly):
    """Smoothing bevels sharp corners into a short edge. Where a short edge sits between two long
    edges, replace it by the intersection of those two edges, which restores the original corner."""
    def dist(a, b):
        return math.hypot(a[0] - b[0], a[1] - b[1])
    changed = True
    while changed and len(poly) > 4:
        changed = False
        n = len(poly)
        for i in range(n):
            a, b, c, d = poly[(i - 1) % n], poly[i], poly[(i + 1) % n], poly[(i + 2) % n]
            if dist(b, c) < 7 and dist(a, b) > 10 and dist(c, d) > 10:
                p = _intersect(a, b, c, d)
                mid = ((b[0] + c[0]) / 2, (b[1] + c[1]) / 2)
                if p and dist(p, mid) < 5:
                    j = (i + 1) % n
                    poly[i] = p
                    del poly[j]
                    changed = True
                    break
    return poly


def simplify_closed(poly, eps):
    if len(poly) < 6:
        return poly
    poly = smooth(poly)
    # split at the two points farthest apart so the closed loop has stable anchors
    a = 0
    b = max(range(len(poly)), key=lambda i: (poly[i][0] - poly[a][0]) ** 2 + (poly[i][1] - poly[a][1]) ** 2)
    first = dp(poly[a:b + 1], eps)
    second = dp(poly[b:] + poly[:a + 1], eps)
    return sharpen_corners(first[:-1] + second[:-1])


def trace(mask, W, H):
    out = []
    for loop in boundary_loops(mask, W, H):
        if abs(area(loop)) < MIN_AREA:
            continue
        poly = simplify_closed(loop, EPS)
        if len(poly) >= 3 and abs(area(poly)) >= MIN_AREA:
            # drop compression specks and hair-thin slivers along the edges (real lines are > 5 px thick)
            perim = sum(math.hypot(poly[i][0] - poly[i - 1][0], poly[i][1] - poly[i - 1][1]) for i in range(len(poly)))
            if abs(area(poly)) < 90 or 2 * abs(area(poly)) / max(perim, 1e-6) < 2.6:
                continue
            # leftovers of the masked ring: small blobs lying in the ring band
            cx = sum(p[0] for p in poly) / len(poly)
            cy = sum(p[1] for p in poly) / len(poly)
            if abs(area(poly)) < 1200 and math.hypot(cx - RING_C[0], cy - RING_C[1]) > 370:
                continue
            out.append([[round(x, 1), round(y, 1)] for x, y in poly])
    return out


def main():
    im = Image.open(SRC).convert("RGB")
    W, H = im.size
    idx = classify(im)
    cleanup_fringes(idx, im)
    nodes = detect_nodes(idx)
    blank_discs(idx, nodes)
    mask_ring(idx)
    names = [n for n, _, _ in PALETTE]
    px = idx.load()

    layers = []
    # silhouette under everything: hides hairline seams between neighbouring polygons
    # inset by 2 px so the base never shows as a fringe outside the shapes painted on top of it
    sil_img = idx.point(lambda v: 0 if v in (0, 1) else 255).filter(ImageFilter.MinFilter(5))
    sp = sil_img.load()
    sil = [[sp[x, y] > 0 for x in range(W)] for y in range(H)]
    layers.append({"name": "silhouette", "color": "#C4A880", "loops": trace(sil, W, H)})

    colors = {n: "#%02X%02X%02X" % rgb for n, rgb, _ in PALETTE}
    for name in DRAW_ORDER:
        c = names.index(name)
        m = [[px[x, y] == c for x in range(W)] for y in range(H)]
        layers.append({"name": name, "color": colors[name], "loops": trace(m, W, H)})
        print(name, len(layers[-1]["loops"]), "loops")

    with open(os.path.join(HERE, "logo_trace.json"), "w") as fh:
        json.dump({"size": [W, H], "ringCenter": RING_C, "layers": layers, "nodes": nodes}, fh)

    # preview: even-odd fill per layer (holes stay holes), like the Android vector
    from PIL import ImageChops
    prev = Image.new("RGB", (W * 2, H), (25, 25, 23))
    prev.paste(im, (0, 0))
    for layer in layers:
        col = tuple(int(layer["color"][i:i + 2], 16) for i in (1, 3, 5))
        acc = Image.new("1", (W, H), 0)
        for loop in layer["loops"]:
            tmp = Image.new("1", (W, H), 0)
            ImageDraw.Draw(tmp).polygon([tuple(p) for p in loop], fill=1)
            acc = ImageChops.logical_xor(acc, tmp)
        right = prev.crop((W, 0, W * 2, H))
        right.paste(Image.new("RGB", (W, H), col), (0, 0), acc)
        prev.paste(right, (W, 0))
    prev.save(os.path.join(HERE, "trace_preview.png"))
    print("done", sum(len(l["loops"]) for l in layers), "loops total")


if __name__ == "__main__":
    main()
