"""Generates the app logo assets from the reference image measurements.

Pipeline
  1. design/trace_logo.py   reference image -> design/logo_trace.json (figure, check and joints, traced)
  2. design/generate_logo.py (this file)   -> vector assets + the ring constants used by the splash animation

Run:  python design/generate_logo.py      (needs Pillow)

Outputs
  design/logo.svg                                            final logo (preview / export)
  res/drawable/ic_launcher_foreground.xml                    adaptive icon foreground (final logo)
  res/drawable/ic_launcher_monochrome.xml                    themed (monochrome) icon
  res/drawable/ic_splash_icon.xml                            system splash: logo with the ring still empty
  res/drawable/ic_logo_figure.xml                            figure + check only (the ring is animated in Compose)
  java/.../feature/splash/RingSpec.kt                        ring segments and dashes for the animation

Ring design
  * SEGMENT_DEG long equal segments separated by GAP_DEG, laid out from SWEEP_START_DEG. The solid
    segments are the filled part of the bar; the thin dashes after them are the unfilled part.
  * Where the figure or the check crosses the ring, the segments are cut by a "knockout": the figure
    outline grown by CUT_CLEARANCE_PX, painted in the background colour just below the figure. The
    segments stay whole underneath, so the cut ends follow the outline of the check and of the
    hands (angled, as if the check had cut them). The knockout is part of the figure drawable, so the
    animated ring in Compose is cut in exactly the same way.
  * The thin dashes are centred on the radial middle of the segment band.

Logo space: 1040 x 1040 units, 1 unit = 1 pixel of the reference image, ring centre at (520, 520).
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")
KOTLIN_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "fitnessapp", "feature", "splash")

TRACE = json.load(open(os.path.join(HERE, "logo_trace.json")))
REF_CENTER = TRACE["ringCenter"]

# ---- Logo space ------------------------------------------------------------------------------
SIZE = 1040.0
CX = CY = SIZE / 2

BG = "#191917"
DIM_TRACK = "#2A2822"     # empty part of the solid segments (splash start state)
DASH = "#34312A"          # thin dashes that stay dim

# ---- Ring (angles in degrees, clockwise from 3 o'clock, screen coordinates) ------------------------
R_IN, R_OUT = 392.0, 430.0                 # solid segments
_MID = (R_IN + R_OUT) / 2
DASH_THICK = 15.0
D_IN, D_OUT = _MID - DASH_THICK / 2, _MID + DASH_THICK / 2   # thin dashes: centred on the band

SEGMENT_DEG = 33.0         # length of every solid segment
GAP_DEG = 2.2              # gap between neighbouring segments
SWEEP_START_DEG = 0.5      # where the first segment starts
FILL_MAX_DEG = 249.0       # the last solid segment ends before this angle
DASH_DEG = 9.2             # length of every thin dash
DASH_GAP_DEG = 2.2
CUT_CLEARANCE_PX = 14      # distance kept between the figure and the segments
TAN_RING, CREAM_RING = "#D9C09A", "#F4E7C6"   # measured segment colours (tan on the right, cream on the left)

# Filled in by build_ring(): (start, end, colour) solid segments, (start, end) dim dashes
ARCS = []
DASHES = []
FREE = []                  # angular intervals not touched by the figure (used by the monochrome icon)
SWEEP_START = SWEEP_END = 0.0


# ---- Helpers ---------------------------------------------------------------------------------
def fnum(n, d=2):
    s = ("%." + str(d) + "f") % n
    return s.rstrip("0").rstrip(".") if "." in s else s


def mix(c1, c2, t):
    a = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#%02X%02X%02X" % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Xf:
    """Maps logo space to the output coordinate space."""
    def __init__(self, k=1.0, ox=0.0, oy=0.0):
        self.k, self.ox, self.oy = k, ox, oy

    def pt(self, p):
        return (self.ox + self.k * p[0], self.oy + self.k * p[1])


def sector_path(a0, a1, r_in, r_out, xf):
    def at(r, a):
        t = math.radians(a)
        return xf.pt((CX + r * math.cos(t), CY + r * math.sin(t)))
    ro, ri = r_out * xf.k, r_in * xf.k
    o0, o1, i1, i0 = at(r_out, a0), at(r_out, a1), at(r_in, a1), at(r_in, a0)
    large = 1 if (a1 - a0) > 180 else 0
    return (f"M{fnum(o0[0])},{fnum(o0[1])} A{fnum(ro)},{fnum(ro)} 0 {large},1 {fnum(o1[0])},{fnum(o1[1])} "
            f"L{fnum(i1[0])},{fnum(i1[1])} A{fnum(ri)},{fnum(ri)} 0 {large},0 {fnum(i0[0])},{fnum(i0[1])} Z")


# ---- Figure data -------------------------------------------------------------------------------
NODE_COLORS = {"cream": "#F5E6C6", "tan": "#E3CDA7", "node": "#38362B"}


def figure_layers():
    """Traced layers converted to logo space: [(name, colour, [loop, ...])]."""
    layers = []
    for layer in TRACE["layers"]:
        loops = [[(x - REF_CENTER[0] + CX, y - REF_CENTER[1] + CY) for x, y in loop] for loop in layer["loops"]]
        layers.append((layer["name"], layer["color"], loops))
    return layers


def figure_nodes():
    """Perfect circles for the joints, in logo space: [(cx, cy, r, colour, class)]."""
    return [(n["cx"] - REF_CENTER[0] + CX, n["cy"] - REF_CENTER[1] + CY, n["r"], NODE_COLORS[n["cls"]], n["cls"])
            for n in TRACE.get("nodes", [])]


def circle_path(cx, cy, r, xf):
    c = xf.pt((cx, cy))
    r *= xf.k
    d = 1 if xf.k > 0.5 else 2
    return (f"M{fnum(c[0] - r, d)},{fnum(c[1], d)} a{fnum(r, d)},{fnum(r, d)} 0 1,0 {fnum(2 * r, d)},0 "
            f"a{fnum(r, d)},{fnum(r, d)} 0 1,0 {fnum(-2 * r, d)},0 Z")


def loops_path(loops, xf):
    parts = []
    d = 1 if xf.k > 0.5 else 2
    for loop in loops:
        q = [xf.pt(p) for p in loop]
        parts.append("M" + " L".join(f"{fnum(x, d)},{fnum(y, d)}" for x, y in q) + " Z")
    return " ".join(parts)


def argb(col, alpha=1.0):
    return "#%02X%s" % (round(alpha * 255), col[1:])


# ---- Ring layout -------------------------------------------------------------------------------
def blocked_angles(step=0.5):
    """For each angle step: True if the figure is closer than CUT_CLEARANCE_PX to the segment band."""
    from PIL import Image, ImageChops, ImageDraw
    size = int(SIZE)
    silhouette = [layer for layer in figure_layers() if layer[0] == "silhouette"][0]
    acc = Image.new("1", (size, size), 0)
    for loop in silhouette[2]:
        tmp = Image.new("1", acc.size, 0)
        ImageDraw.Draw(tmp).polygon(loop, fill=1)
        acc = ImageChops.logical_xor(acc, tmp)
    for cx, cy, r, _, _ in figure_nodes():  # joint discs are not part of the traced silhouette
        ImageDraw.Draw(acc).ellipse([cx - r, cy - r, cx + r, cy + r], fill=1)
    px = acc.load()
    c = CUT_CLEARANCE_PX
    offsets = [(dx, dy) for dy in range(-c, c + 1, 2) for dx in range(-c, c + 1, 2) if dx * dx + dy * dy <= c * c]

    def near(x, y):
        for dx, dy in offsets:
            xx, yy = int(x + dx), int(y + dy)
            if 0 <= xx < size and 0 <= yy < size and px[xx, yy]:
                return True
        return False

    out = []
    for i in range(int(360 / step)):
        t = math.radians(i * step)
        out.append(any(near(CX + r * math.cos(t), CY + r * math.sin(t)) for r in range(int(R_IN), int(R_OUT) + 1, 6)))
    return out


def intervals(flags, step, value):
    out, start = [], None
    for i, f in enumerate(flags + [not value]):
        if f == value and start is None:
            start = i * step
        if f != value and start is not None:
            out.append((start, i * step))
            start = None
    return out


def clip_free(a0, a1, free, min_len=5.0):
    """Parts of [a0, a1] that lie in the free angular intervals."""
    pieces = []
    for f0, f1 in free:
        lo, hi = max(a0, f0), min(a1, f1)
        if hi - lo >= min_len:
            pieces.append((lo, hi))
    return pieces


def build_ring():
    global ARCS, DASHES, FREE, SWEEP_START, SWEEP_END
    step = 0.5
    blocked = blocked_angles(step)
    zones = intervals(blocked, step, True)
    FREE = intervals(blocked, step, False)

    ARCS, k = [], 0
    while True:
        a0 = SWEEP_START_DEG + k * (SEGMENT_DEG + GAP_DEG)
        a1 = a0 + SEGMENT_DEG
        if a1 > FILL_MAX_DEG:
            break
        mid = (a0 + a1) / 2
        t = min(1.0, max(0.0, (mid - 90.0) / 40.0))     # tan up to ~90 degrees, cream from ~130
        ARCS.append((round(a0, 2), round(a1, 2), mix(TAN_RING, CREAM_RING, t)))
        k += 1
    SWEEP_START, SWEEP_END = ARCS[0][0], ARCS[-1][1]

    # thin dashes: from the last solid segment to the next stretch touched by the figure, centred
    first = SWEEP_END + GAP_DEG
    limit = min(z[0] for z in zones if z[0] > SWEEP_END)
    count = int((limit - first + DASH_GAP_DEG) // (DASH_DEG + DASH_GAP_DEG))
    used = count * DASH_DEG + (count - 1) * DASH_GAP_DEG
    start = first + (limit - first - used) / 2
    DASHES = [(round(start + i * (DASH_DEG + DASH_GAP_DEG), 2), round(start + i * (DASH_DEG + DASH_GAP_DEG) + DASH_DEG, 2))
              for i in range(count)]

    cut = [(a0, a1) for a0, a1, _ in ARCS if any(a0 < z1 and a1 > z0 for z0, z1 in zones)]
    print("figure zones (deg):", [(round(a, 1), round(b, 1)) for a, b in zones])
    print(f"solid segments: {len(ARCS)} x {SEGMENT_DEG} deg, ends at {SWEEP_END}; dashes: {len(DASHES)}; cut by the figure: {len(cut)}")


# ---- Element builders: (pathData, colour, alpha, evenOdd, stroke) with stroke = (colour, width) or None -------
def ring_elements(xf, arcs_color=None, mono=False):
    out = []
    for a0, a1, col in ARCS:
        pieces = clip_free(a0, a1, FREE) if mono else [(a0, a1)]
        for p0, p1 in pieces:
            out.append((sector_path(p0, p1, R_IN, R_OUT, xf), arcs_color or col, 1.0, False, None))
    for a0, a1 in DASHES:
        pieces = clip_free(a0, a1, FREE) if mono else [(a0, a1)]
        for p0, p1 in pieces:
            out.append((sector_path(p0, p1, D_IN, D_OUT, xf), DASH, 0.35 if mono else 1.0, False, None))
    return out


def figure_elements(xf, silhouette_only=False):
    out = []
    layers = figure_layers()
    if not silhouette_only:
        # knockout: the figure outline grown by the clearance, in the background colour. Painted first,
        # so it cuts the ring segments that lie below it (the segments themselves stay whole).
        stroke = (BG, 2 * (CUT_CLEARANCE_PX + 2) * xf.k)
        sil = [l for l in layers if l[0] == "silhouette"][0]
        out.append((loops_path(sil[2], xf), BG, 1.0, True, stroke))
        for cx, cy, r, _, _ in figure_nodes():
            out.append((circle_path(cx, cy, r, xf), BG, 1.0, False, stroke))
    for name, col, loops in layers:
        if not loops:
            continue
        if silhouette_only and name != "silhouette":
            continue
        out.append((loops_path(loops, xf), col, 1.0, True, None))
    # joints on top, as perfect circles (light ones first, dark ones last)
    for cx, cy, r, col, cls in sorted(figure_nodes(), key=lambda n: n[4] == "node"):
        if silhouette_only and cls == "node":
            continue
        out.append((circle_path(cx, cy, r, xf), col, 1.0, False, None))
    return out


# ---- Writers ---------------------------------------------------------------------------------
def write_vector(name, size_dp, viewport, elements, mono=False):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generated by design/generate_logo.py: do not edit by hand -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{fnum(size_dp)}dp"', f'    android:height="{fnum(size_dp)}dp"',
             f'    android:viewportWidth="{fnum(viewport)}"', f'    android:viewportHeight="{fnum(viewport)}">']
    for d, col, alpha, evenodd, stroke in elements:
        if mono:
            col = "#FFFFFF"
        attrs = f'android:fillColor="{argb(col, alpha)}"'
        if evenodd:
            attrs += ' android:fillType="evenOdd"'
        if stroke:
            attrs += (f' android:strokeColor="{argb(stroke[0])}" android:strokeWidth="{fnum(stroke[1])}"'
                      ' android:strokeLineJoin="round"')
        lines.append(f'    <path {attrs} android:pathData="{d}" />')
    lines.append('</vector>')
    path = os.path.join(RES, "drawable", name)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print("wrote", path, os.path.getsize(path), "bytes")


def write_svg(path, xf):
    parts = ['<?xml version="1.0" encoding="UTF-8"?>',
             f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {fnum(SIZE)} {fnum(SIZE)}" width="1040" height="1040">',
             f'  <rect width="{fnum(SIZE)}" height="{fnum(SIZE)}" fill="{BG}" />']
    for d, col, alpha, evenodd, stroke in ring_elements(xf) + figure_elements(xf):
        attrs = f'fill="{col}"'
        if evenodd:
            attrs += ' fill-rule="evenodd"'
        if stroke:
            attrs += f' stroke="{stroke[0]}" stroke-width="{fnum(stroke[1])}" stroke-linejoin="round"'
        parts.append(f'  <path d="{d}" {attrs} />')
    parts.append('</svg>')
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(parts) + "\n")
    print("wrote", path)


def write_kotlin():
    os.makedirs(KOTLIN_DIR, exist_ok=True)
    arcs = ",\n".join(f"        RingArc({a0}f, {a1}f, 0xFF{col[1:]})" for a0, a1, col in ARCS)
    dashes = ",\n".join(f"        RingDash({a0}f, {a1}f)" for a0, a1 in DASHES)
    src = f'''// Generated by design/generate_logo.py: do not edit by hand.
package com.fitnessapp.feature.splash

import androidx.compose.ui.graphics.Color

/** One solid segment of the logo ring. Angles are degrees, clockwise from 3 o'clock. */
data class RingArc(val startDeg: Float, val endDeg: Float, val argb: Long) {{
    val color: Color get() = Color(argb)
    val lengthDeg: Float get() = endDeg - startDeg
}}

/** One thin dim dash of the logo ring (the part of the ring that stays unfilled). */
data class RingDash(val startDeg: Float, val endDeg: Float)

/**
 * Ring geometry measured from the reference logo. Units are the logo's {fnum(SIZE)} x {fnum(SIZE)} space.
 * The segments are whole here; the figure drawable paints a knockout over the places where the
 * check and the hands cut them.
 */
object RingSpec {{
    const val LOGO_SIZE = {fnum(SIZE)}f
    const val ARC_INNER = {fnum(R_IN)}f
    const val ARC_OUTER = {fnum(R_OUT)}f
    const val DASH_INNER = {fnum(D_IN)}f
    const val DASH_OUTER = {fnum(D_OUT)}f

    /** The fill sweeps from [SWEEP_START_DEG] to [SWEEP_END_DEG] (clockwise). */
    const val SWEEP_START_DEG = {SWEEP_START}f
    const val SWEEP_END_DEG = {SWEEP_END}f

    val TrackColor = Color(0xFF{DIM_TRACK[1:]})
    val DashColor = Color(0xFF{DASH[1:]})

    val arcs: List<RingArc> = listOf(
{arcs},
    )

    val dashes: List<RingDash> = listOf(
{dashes},
    )
}}
'''
    path = os.path.join(KOTLIN_DIR, "RingSpec.kt")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(src)
    print("wrote", path)


def main():
    build_ring()
    ident = Xf()
    write_svg(os.path.join(HERE, "logo.svg"), ident)
    write_vector("ic_logo_figure.xml", 200, SIZE, figure_elements(ident))

    # Adaptive icon layers and system splash: 108 dp canvas, logo 66 dp wide (the safe zone)
    k = 66.0 / SIZE
    xf = Xf(k, 54.0 - k * CX, 54.0 - k * CY)
    write_vector("ic_launcher_foreground.xml", 108, 108, ring_elements(xf) + figure_elements(xf))
    write_vector("ic_launcher_monochrome.xml", 108, 108,
                 ring_elements(xf, mono=True) + figure_elements(xf, silhouette_only=True), mono=True)
    write_vector("ic_splash_icon.xml", 108, 108, ring_elements(xf, arcs_color=DIM_TRACK) + figure_elements(xf))
    write_kotlin()


if __name__ == "__main__":
    main()
