#!/usr/bin/env python3
"""
Generate SVG chord reference diagrams for GKOS keyboard layouts.

Usage:
    python docs/generate_diagrams.py                   # All layouts
    python docs/generate_diagrams.py en.xml            # Specific layout(s)
    python docs/generate_diagrams.py en.xml de.xml     # Multiple layouts

Output: SVG files in docs/layouts/
"""

import xml.etree.ElementTree as ET
import os
import sys
from html import escape as html_escape

# ════════════════════════════════════════════════════════════════════
# Key constants
# ════════════════════════════════════════════════════════════════════
A, B, C, D, E, F = 1, 2, 4, 8, 16, 32
KEYS = [A, B, C, D, E, F]
KNAME = {A: 'A', B: 'B', C: 'C', D: 'D', E: 'E', F: 'F'}
KCOL = {A: 0, B: 0, C: 0, D: 1, E: 1, F: 1}
KROW = {A: 0, B: 1, C: 2, D: 0, E: 1, F: 2}
LEFT = {A, B, C}
RIGHT = {D, E, F}


def bits(mask):
    """Return list of key constants pressed in bitmask."""
    return [k for k in KEYS if mask & k]


def chord_label(mask):
    """Human-readable chord name like 'AB', 'DEF'."""
    return ''.join(KNAME[k] for k in bits(mask))


# ════════════════════════════════════════════════════════════════════
# Display mapping for action / special values
# ════════════════════════════════════════════════════════════════════
_ACTION = {
    'UpArrow': '\u2191', 'DownArrow': '\u2193',
    'LeftArrow': '\u2190', 'RightArrow': '\u2192',
    'backspace': '\u232b', 'space': '\u2423', 'enter': '\u23ce',
    'tab': '\u21e5', 'delete': '\u2326',
    'esc': 'Esc', 'PageUp': 'PgUp', 'PageDown': 'PgDn',
    'Home': 'Home', 'End': 'End',
    'shift': '\u21e7', 'symb': '@#',
    'mode_toggle': 'ABC\u21c4123',
    'ctrl': 'Ctrl', 'alt': 'Alt',
    'ScrollUp': '\u2191\u2191', 'ScrollDown': '\u2193\u2193',
    'WordLeft': '\u21d0', 'WordRight': '\u21d2',
    'PanLeft': '\u21c7', 'PanRight': '\u21c9',
    'PanLeftHome': '\u21e4', 'PanRightEnd': '\u21e5',
    'unicode_picker': 'Uni', 'emoji': '\U0001f600', 'Insert': 'Ins',
}


def disp(val):
    """Convert layout value to a display string."""
    if not val:
        return ''
    d = _ACTION.get(val)
    if d is not None:
        return d
    return val.rstrip()


def esc(s):
    """XML-escape a string for SVG text content."""
    return html_escape(str(s))


def font_size_for(char, base_size):
    """Return an adjusted font size based on character length."""
    n = len(char)
    if n <= 1:
        return base_size
    if n == 2:
        return max(9, base_size - 3)
    if n <= 4:
        return max(8, base_size - 5)
    return max(7, base_size - 7)


# ════════════════════════════════════════════════════════════════════
# Layout XML parsing
# ════════════════════════════════════════════════════════════════════
def parse_layout(path):
    """Parse a GKOS layout XML file.

    Returns (layout_id, layout_name, entries) where entries is
    {chord_bitmask: {'abc': ..., 'abc_shift': ..., 'num': ..., ...}}.
    """
    tree = ET.parse(path)
    root = tree.getroot()
    entries = {}
    for el in root.findall('entry'):
        ch = int(el.get('chord'))
        entries[ch] = {
            'abc': el.get('abc', ''),
            'abc_shift': el.get('abc_shift', ''),
            'num': el.get('num', ''),
            'num_shift': el.get('num_shift', ''),
            'symb': el.get('symb', ''),
            'symb_shift': el.get('symb_shift', ''),
        }
    return root.get('id'), root.get('name'), entries


# ════════════════════════════════════════════════════════════════════
# SVG builder
# ════════════════════════════════════════════════════════════════════
class Svg:
    """Minimal SVG string builder."""

    def __init__(self):
        self._parts = []

    def _a(self, s):
        self._parts.append(s)

    def rect(self, x, y, w, h, **kw):
        fill = kw.get('fill', 'white')
        stroke = kw.get('stroke', '#333')
        sw = kw.get('stroke_width', 1.5)
        rx = kw.get('rx', 5)
        self._a(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}" rx="{rx}"/>'
        )

    def text(self, x, y, txt, **kw):
        sz = kw.get('size', 14)
        fill = kw.get('fill', 'black')
        anchor = kw.get('anchor', 'middle')
        weight = kw.get('weight', 'normal')
        bl = kw.get('baseline', 'central')
        self._a(
            f'<text x="{x:.1f}" y="{y:.1f}" font-size="{sz}" fill="{fill}" '
            f'text-anchor="{anchor}" font-weight="{weight}" '
            f'dominant-baseline="{bl}">{esc(txt)}</text>'
        )

    def open_g(self, tx=0, ty=0):
        if tx or ty:
            self._a(f'<g transform="translate({tx:.1f},{ty:.1f})">')
        else:
            self._a('<g>')

    def close_g(self):
        self._a('</g>')

    def comment(self, s):
        self._a(f'<!-- {s} -->')

    def build(self, w, h):
        body = '\n'.join(self._parts)
        return (
            f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<svg xmlns="http://www.w3.org/2000/svg" '
            f'viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n'
            f'<defs><style>\n'
            f'  text {{ font-family: "Segoe UI", Roboto, "Helvetica Neue", '
            f'Arial, sans-serif; }}\n'
            f'</style></defs>\n'
            f'<rect width="{w}" height="{h}" fill="white"/>\n'
            f'{body}\n'
            f'</svg>\n'
        )


# ════════════════════════════════════════════════════════════════════
# Drawing primitives
# ════════════════════════════════════════════════════════════════════
def draw_key(svg, x, y, w, h, char='', pressed=False, font_size=18,
             text_fill=None, dimmed=False):
    """Draw a single rounded-rect key with optional character."""
    if pressed:
        svg.rect(x, y, w, h, fill='#222', stroke='#222', rx=4, stroke_width=1.5)
        fill = text_fill or 'white'
    elif dimmed:
        svg.rect(x, y, w, h, fill='#f0f0f0', stroke='#ccc', rx=4, stroke_width=1)
        fill = text_fill or '#888'
    else:
        svg.rect(x, y, w, h, fill='white', stroke='#555', rx=4, stroke_width=1.5)
        fill = text_fill or 'black'
    if char:
        fs = font_size_for(char, font_size)
        svg.text(x + w / 2, y + h / 2, char, size=fs, fill=fill, weight='bold')


def draw_6keys(svg, ox, oy, kw, kh, gx, gy, key_info):
    """Draw the 6-key keyboard grid.

    key_info: list of 6 dicts (one per key in KEYS order):
        {'char': str, 'pressed': bool, 'font_size': int,
         'text_fill': str|None, 'dimmed': bool}
    """
    for i, key in enumerate(KEYS):
        col = KCOL[key]
        row = KROW[key]
        x = ox + col * (kw + gx)
        y = oy + row * (kh + gy)
        info = key_info[i]
        draw_key(svg, x, y, kw, kh,
                 char=info.get('char', ''),
                 pressed=info.get('pressed', False),
                 font_size=info.get('font_size', 16),
                 text_fill=info.get('text_fill'),
                 dimmed=info.get('dimmed', False))


# ════════════════════════════════════════════════════════════════════
# Diagram section drawing
# ════════════════════════════════════════════════════════════════════

def _draw_num_symb(svg, x, y_num, y_symb, num_char, symb_char,
                   anchor, num_size=10, symb_size=9):
    """Draw gray NUM and blue SYMB annotation pair."""
    if num_char:
        svg.text(x, y_num, num_char,
                 size=num_size, fill='#999', anchor=anchor, weight='bold')
    if symb_char:
        svg.text(x, y_symb, symb_char,
                 size=symb_size, fill='#2266cc', anchor=anchor)


def draw_center_cell(svg, cx, cy, entries):
    """Draw the main 6-key cell with ABC chars + NUM/SYMB annotations."""
    kw, kh = 55, 42
    gx, gy = 26, 8
    total_w = 2 * kw + gx
    total_h = 3 * kh + 2 * gy
    ox = cx - total_w / 2
    oy = cy - total_h / 2

    key_info = []
    for key in KEYS:
        e = entries.get(key)
        char = disp(e['abc']) if e else ''
        key_info.append({'char': char, 'pressed': False, 'font_size': 24})
    draw_6keys(svg, ox, oy, kw, kh, gx, gy, key_info)

    # NUM (gray) and SYMB (blue) annotations outside every key
    for key in KEYS:
        e = entries.get(key)
        if not e:
            continue
        col = KCOL[key]
        row = KROW[key]
        kx = ox + col * (kw + gx)
        ky = oy + row * (kh + gy)

        num_char = disp(e['num'])
        symb_char = disp(e['symb'])

        if key in LEFT:
            _draw_num_symb(svg, kx - 7, ky + kh * 0.30, ky + kh * 0.72,
                           num_char, symb_char, 'end', 12, 11)
        else:
            _draw_num_symb(svg, kx + kw + 7, ky + kh * 0.30, ky + kh * 0.72,
                           num_char, symb_char, 'start', 12, 11)


def draw_inner_chord_cell(svg, cx, cy, entries, base_chord):
    """Draw a chord cell inside the main 5x3 grid.

    Shows the base 2-key chord character as a floating badge between
    the pressed (inverted) keys, 3-key extension characters on
    opposite-side unpressed keys, and NUM/SYMB annotations for all.
    For non-adjacent pairs (AC, DF) the badge is offset outward.
    """
    kw, kh = 42, 30
    gx, gy = 14, 6
    total_w = 2 * kw + gx
    total_h = 3 * kh + 2 * gy
    ox = cx - total_w / 2
    oy = cy - total_h / 2

    pressed = set(bits(base_chord))
    base_entry = entries.get(base_chord)
    base_char = disp(base_entry['abc']) if base_entry else ''
    base_num = disp(base_entry['num']) if base_entry else ''
    base_symb = disp(base_entry['symb']) if base_entry else ''

    # Opposite side for 3-key extensions
    pressed_on_left = pressed <= LEFT
    pressed_on_right = pressed <= RIGHT
    if pressed_on_left:
        ext_side = RIGHT
    elif pressed_on_right:
        ext_side = LEFT
    else:
        ext_side = set()

    # Collect extension data: abc, num, symb per key
    ext_data = {}
    for key in ext_side:
        ext_chord = base_chord | key
        ext_entry = entries.get(ext_chord)
        if ext_entry:
            ext_data[key] = (
                disp(ext_entry['abc']),
                disp(ext_entry['num']),
                disp(ext_entry['symb']),
            )

    # Detect non-adjacent pair (AC or DF) for badge offset
    pressed_rows = sorted([KROW[k] for k in pressed])
    is_non_adjacent = (pressed_rows[-1] - pressed_rows[0]) > 1

    # Draw 6 keys: pressed=black, extensions=white+char, rest=dimmed
    key_info = []
    for key in KEYS:
        if key in pressed:
            key_info.append({'char': '', 'pressed': True, 'font_size': 14})
        elif key in ext_data:
            key_info.append({
                'char': ext_data[key][0], 'pressed': False, 'font_size': 13
            })
        else:
            key_info.append({'char': '', 'pressed': False, 'dimmed': True})
    draw_6keys(svg, ox, oy, kw, kh, gx, gy, key_info)

    # Floating badge for base chord character between pressed keys
    if base_char:
        col = KCOL[list(pressed)[0]]
        lx = ox + col * (kw + gx) + kw / 2
        y_top = oy + pressed_rows[0] * (kh + gy) + kh / 2
        y_bot = oy + pressed_rows[-1] * (kh + gy) + kh / 2
        ly = (y_top + y_bot) / 2

        # Issue 4: offset badge outward for non-adjacent pairs
        if is_non_adjacent:
            if pressed_on_left:
                lx -= 14
            else:
                lx += 14

        fs = font_size_for(base_char, 16)
        badge_w = max(kw - 2, len(base_char) * fs * 0.7 + 8)
        badge_h = fs + 8
        svg.rect(lx - badge_w / 2, ly - badge_h / 2, badge_w, badge_h,
                 fill='#333', stroke='#333', stroke_width=0, rx=badge_h / 2)
        svg.text(lx, ly, base_char, size=fs, fill='white', weight='bold')

        # NUM/SYMB annotations for the base chord badge
        if pressed_on_left:
            ann_x = lx - badge_w / 2 - 4
            anchor = 'end'
        else:
            ann_x = lx + badge_w / 2 + 4
            anchor = 'start'
        _draw_num_symb(svg, ann_x, ly - 6, ly + 6,
                       base_num, base_symb, anchor, 9, 8)

    # NUM/SYMB annotations for extension keys (outside the key)
    for key in ext_side:
        if key not in ext_data:
            continue
        _, ext_num, ext_symb = ext_data[key]
        key_col = KCOL[key]
        key_row = KROW[key]
        kx = ox + key_col * (kw + gx)
        ky = oy + key_row * (kh + gy)
        if key in LEFT:
            _draw_num_symb(svg, kx - 4, ky + kh * 0.30, ky + kh * 0.72,
                           ext_num, ext_symb, 'end', 9, 8)
        else:
            _draw_num_symb(svg, kx + kw + 4, ky + kh * 0.30, ky + kh * 0.72,
                           ext_num, ext_symb, 'start', 9, 8)


def draw_outer_chord_cell(svg, cx, cy, entries, chord_mask,
                          cell_w=95, cell_h=116):
    """Draw a chord cell outside the main box.

    Taller cell with ABC char at top, mini keyboard in middle,
    and gray NUM / blue SYMB at bottom.
    """
    entry = entries.get(chord_mask)
    char = disp(entry['abc']) if entry else ''
    num_char = disp(entry['num']) if entry else ''
    symb_char = disp(entry['symb']) if entry else ''

    svg.rect(cx - cell_w / 2, cy - cell_h / 2, cell_w, cell_h,
             fill='#fafafa', stroke='#ddd', stroke_width=1, rx=7)

    # Character label near top (moved up half an em)
    if char:
        fs = font_size_for(char, 20)
        svg.text(cx, cy - cell_h / 2 + 14, char,
                 size=fs, fill='#222', weight='bold')

    # Mini keyboard centered in middle
    mkw, mkh = 20, 15
    mgx, mgy = 5, 3
    kb_w = 2 * mkw + mgx
    kb_h = 3 * mkh + 2 * mgy
    kb_ox = cx - kb_w / 2
    kb_oy = cy - kb_h / 2 - 2

    pressed = set(bits(chord_mask))
    key_info = []
    for key in KEYS:
        key_info.append({
            'char': '', 'pressed': key in pressed,
            'font_size': 7, 'dimmed': key not in pressed
        })
    draw_6keys(svg, kb_ox, kb_oy, mkw, mkh, mgx, mgy, key_info)

    # Gray NUM and blue SYMB near bottom
    bottom = cy + cell_h / 2
    _draw_num_symb(svg, cx, bottom - 20, bottom - 8,
                   num_char, symb_char, 'middle', 10, 9)


def draw_direction_cell(svg, cx, cy, entries, chord_mask,
                        cell_w=82, cell_h=95):
    """Draw a navigation/action chord cell."""
    entry = entries.get(chord_mask)
    char = disp(entry['abc']) if entry else ''
    num_char = disp(entry['num']) if entry else ''
    symb_char = disp(entry['symb']) if entry else ''

    svg.rect(cx - cell_w / 2, cy - cell_h / 2, cell_w, cell_h,
             fill='#f0f4ff', stroke='#b0c0e0', stroke_width=1, rx=7)

    # Mini keyboard centered in cell
    mkw, mkh = 16, 12
    mgx, mgy = 4, 2
    kb_w = 2 * mkw + mgx
    kb_h = 3 * mkh + 2 * mgy
    kb_ox = cx - kb_w / 2
    kb_oy = cy - kb_h / 2 + 6

    pressed = set(bits(chord_mask))
    key_info = []
    for key in KEYS:
        key_info.append({
            'char': '', 'pressed': key in pressed,
            'font_size': 6, 'dimmed': key not in pressed
        })
    draw_6keys(svg, kb_ox, kb_oy, mkw, mkh, mgx, mgy, key_info)

    if char:
        fs = font_size_for(char, 16)
        svg.text(cx, cy - cell_h / 2 + 18, char,
                 size=fs, fill='#333', weight='bold')

    # Gray NUM and blue SYMB near bottom
    bottom = cy + cell_h / 2
    _draw_num_symb(svg, cx, bottom - 18, bottom - 6,
                   num_char, symb_char, 'middle', 10, 9)


def draw_mode_cell(svg, cx, cy, entries, chord_mask, label,
                   cell_w=95, cell_h=95):
    """Draw a mode switch chord cell."""
    entry = entries.get(chord_mask)
    num_char = disp(entry['num']) if entry else ''
    symb_char = disp(entry['symb']) if entry else ''

    svg.rect(cx - cell_w / 2, cy - cell_h / 2, cell_w, cell_h,
             fill='#fff8f0', stroke='#e0c8a0', stroke_width=1, rx=7)

    mkw, mkh = 16, 12
    mgx, mgy = 4, 2
    kb_w = 2 * mkw + mgx
    kb_h = 3 * mkh + 2 * mgy
    kb_ox = cx - kb_w / 2
    kb_oy = cy - kb_h / 2 + 6

    pressed = set(bits(chord_mask))
    key_info = []
    for key in KEYS:
        key_info.append({
            'char': '', 'pressed': key in pressed,
            'font_size': 6, 'dimmed': key not in pressed
        })
    draw_6keys(svg, kb_ox, kb_oy, mkw, mkh, mgx, mgy, key_info)

    svg.text(cx, cy - cell_h / 2 + 18, label,
             size=13, fill='#333', weight='bold')

    # Gray NUM and blue SYMB near bottom
    bottom = cy + cell_h / 2
    _draw_num_symb(svg, cx, bottom - 18, bottom - 6,
                   num_char, symb_char, 'middle', 10, 9)


def draw_control_cell(svg, cx, cy, entries, chord_mask, label,
                      cell_w=95, cell_h=95):
    """Draw a 5-key control chord cell (Esc, Enter, Tab, etc.)."""
    entry = entries.get(chord_mask)
    num_char = disp(entry['num']) if entry else ''
    symb_char = disp(entry['symb']) if entry else ''

    svg.rect(cx - cell_w / 2, cy - cell_h / 2, cell_w, cell_h,
             fill='#f4f0ff', stroke='#c0b0e0', stroke_width=1, rx=7)

    mkw, mkh = 16, 12
    mgx, mgy = 4, 2
    kb_w = 2 * mkw + mgx
    kb_h = 3 * mkh + 2 * mgy
    kb_ox = cx - kb_w / 2
    kb_oy = cy - kb_h / 2 + 6

    pressed = set(bits(chord_mask))
    key_info = []
    for key in KEYS:
        key_info.append({
            'char': '', 'pressed': key in pressed,
            'font_size': 6, 'dimmed': key not in pressed
        })
    draw_6keys(svg, kb_ox, kb_oy, mkw, mkh, mgx, mgy, key_info)

    svg.text(cx, cy - cell_h / 2 + 18, label,
             size=13, fill='#333', weight='bold')

    # Gray NUM and blue SYMB near bottom
    bottom = cy + cell_h / 2
    _draw_num_symb(svg, cx, bottom - 18, bottom - 6,
                   num_char, symb_char, 'middle', 10, 9)


# ════════════════════════════════════════════════════════════════════
# Main diagram assembly
# ════════════════════════════════════════════════════════════════════

def generate_svg(layout_id, layout_name, entries, variant=''):
    """Generate the complete SVG chord reference diagram."""
    svg = Svg()

    # ── Layout geometry ───────────────────────────────────────────
    col_w = [130, 125, 175, 125, 130]
    row_h = [118, 140, 118]
    main_w = sum(col_w)       # 685
    main_h = sum(row_h)       # 376

    col_x = []
    cx = 0
    for w in col_w:
        col_x.append(cx)
        cx += w
    row_y = []
    ry = 0
    for h in row_h:
        row_y.append(ry)
        ry += h

    # Outer panels
    outer_cw = 97
    outer_ch = 118
    outer_panel_w = outer_cw * 2 + 8
    outer_gap = 16

    # Positioning
    title_h = 68
    main_box_x = outer_panel_w + outer_gap + 16
    main_box_y = title_h
    main_pad = 14

    outer_left_x = 8
    outer_right_x = main_box_x + main_w + 2 * main_pad + outer_gap

    # Below section
    below_gap = 28
    below_y = main_box_y + main_h + 2 * main_pad + below_gap

    # Control row (5-key chords) below directions/modes
    ctrl_row_gap = 24
    dir_ch = 95
    dir_gy = 5
    dir_block_h_est = 2 * dir_ch + dir_gy
    ctrl_row_y = below_y + dir_block_h_est + ctrl_row_gap

    # Total dimensions
    total_w = int(outer_right_x + outer_panel_w + 12)
    total_h = int(ctrl_row_y + dir_ch + 20)

    # ── Title ─────────────────────────────────────────────────────
    title = layout_name or layout_id
    if variant:
        title += f' ({variant})'
    svg.text(total_w / 2, 30, title, size=22, fill='#222', weight='bold')

    # ── Main box outline ──────────────────────────────────────────
    bx = main_box_x - main_pad
    by = main_box_y - main_pad
    bw = main_w + 2 * main_pad
    bh = main_h + 2 * main_pad
    svg.rect(bx, by, bw, bh,
             fill='white', stroke='#999', stroke_width=2, rx=16)

    # ── Center cell ───────────────────────────────────────────────
    ccx = main_box_x + col_x[2] + col_w[2] / 2
    ccy = main_box_y + row_y[1] + row_h[1] / 2
    svg.comment('Center: single keys')
    draw_center_cell(svg, ccx, ccy, entries)

    # ── Inner chord cells ─────────────────────────────────────────
    inner_chords = [
        (0, 1, A | B),
        (0, 3, D | E),
        (1, 0, A | C),
        (1, 4, D | F),
        (2, 1, B | C),
        (2, 3, E | F),
    ]
    for row, col, chord in inner_chords:
        icx = main_box_x + col_x[col] + col_w[col] / 2
        icy = main_box_y + row_y[row] + row_h[row] / 2
        svg.comment(f'Inner chord: {chord_label(chord)}')
        draw_inner_chord_cell(svg, icx, icy, entries, chord)

    # ── Outer chord cells (left panel) ────────────────────────────
    left_outer = [
        (0, 0, A | E),
        (0, 1, A | B | E | F),
        (1, 0, A | C | E | F),
        (1, 1, A | C | D | E),
        (2, 0, C | E),
        (2, 1, C | D),
    ]
    outer_v_total = main_h + 2 * main_pad
    outer_v_gap = (outer_v_total - 3 * outer_ch) / 2
    for row, col, chord in left_outer:
        ocx = outer_left_x + col * (outer_cw + 8) + outer_cw / 2
        ocy = main_box_y - main_pad + row * (outer_ch + outer_v_gap) + outer_ch / 2
        svg.comment(f'Left outer: {chord_label(chord)}')
        draw_outer_chord_cell(svg, ocx, ocy, entries, chord,
                              cell_w=outer_cw - 2, cell_h=outer_ch - 2)

    # ── Outer chord cells (right panel) ───────────────────────────
    right_outer = [
        (0, 0, B | C | D | E),
        (0, 1, B | D),
        (1, 0, A | B | D | F),
        (1, 1, B | C | D | F),
        (2, 0, A | F),
        (2, 1, B | F),
    ]
    for row, col, chord in right_outer:
        ocx = outer_right_x + col * (outer_cw + 8) + outer_cw / 2
        ocy = main_box_y - main_pad + row * (outer_ch + outer_v_gap) + outer_ch / 2
        svg.comment(f'Right outer: {chord_label(chord)}')
        draw_outer_chord_cell(svg, ocx, ocy, entries, chord,
                              cell_w=outer_cw - 2, cell_h=outer_ch - 2)

    # ── Below: Directions ─────────────────────────────────────────
    svg.comment('Directions section')
    dir_cw, dir_ch = 82, 95
    dir_gx, dir_gy = 5, 5
    dir_block_w = 2 * dir_cw + dir_gx
    dir_center_x = main_box_x + main_w / 2 + main_pad
    dir_ox = dir_center_x - dir_block_w / 2

    dir_chords = [
        (0, 0, A | D),
        (0, 1, A | B | D | E),
        (1, 0, C | F),
        (1, 1, B | C | E | F),
    ]
    for row, col, chord in dir_chords:
        dcx = dir_ox + col * (dir_cw + dir_gx) + dir_cw / 2
        dcy = below_y + row * (dir_ch + dir_gy) + dir_ch / 2
        draw_direction_cell(svg, dcx, dcy, entries, chord,
                            cell_w=dir_cw, cell_h=dir_ch)

    dir_block_h = 2 * dir_ch + dir_gy
    dir_mid_y = below_y + dir_block_h / 2

    # ABC (backspace) left of 2x2
    abc_cx = dir_ox - dir_cw / 2 - 12
    draw_direction_cell(svg, abc_cx, dir_mid_y, entries, A | B | C,
                        cell_w=dir_cw, cell_h=dir_ch)

    # DEF (space) right of 2x2
    def_cx = dir_ox + dir_block_w + dir_cw / 2 + 12
    draw_direction_cell(svg, def_cx, dir_mid_y, entries, D | E | F,
                        cell_w=dir_cw, cell_h=dir_ch)

    # ── Mode switches ─────────────────────────────────────────────
    svg.comment('Mode switches')
    mode_cw = 95
    mode_gap = 8
    mode_start_x = def_cx + dir_cw / 2 + 28

    modes = [
        (B | E, 'SHIFT'),
        (A | C | D | F, 'SYMB'),
        (A | B | C | D | E | F, 'ABC\u21c4123'),
    ]
    for i, (chord, label) in enumerate(modes):
        mcx = mode_start_x + i * (mode_cw + mode_gap) + mode_cw / 2
        draw_mode_cell(svg, mcx, dir_mid_y, entries, chord, label,
                       cell_w=mode_cw, cell_h=dir_ch)

    # ── Section labels ────────────────────────────────────────────
    nav_center_x = (abc_cx + def_cx) / 2
    svg.text(nav_center_x, below_y - 10, 'Navigation & Actions',
             size=10, fill='#aaa')
    svg.text(mode_start_x + (3 * mode_cw + 2 * mode_gap) / 2,
             below_y - 10, 'Mode Switches',
             size=10, fill='#aaa')

    # ── 5-key control chords ──────────────────────────────────────
    svg.comment('5-key control chords')
    ctrl_cw = 95
    ctrl_gap = 8
    ctrl_chords = [
        (A | B | C | D | E, 'Esc'),          # ABCDE
        (A | B | C | D | F, 'Ctrl'),         # ABCDF
        (A | B | C | E | F, 'Alt'),          # ABCEF
        (A | B | D | E | F, '\u23ce Enter'), # ABDEF
        (A | C | D | E | F, '\u21e5 Tab'),   # ACDEF
        (B | C | D | E | F, '\u2326 Del'),   # BCDEF
    ]
    ctrl_total_w = len(ctrl_chords) * ctrl_cw + (len(ctrl_chords) - 1) * ctrl_gap
    ctrl_start_x = main_box_x + main_w / 2 + main_pad - ctrl_total_w / 2

    svg.text(ctrl_start_x + ctrl_total_w / 2, ctrl_row_y - 8,
             '5-Key Control Chords', size=10, fill='#aaa')

    for i, (chord, label) in enumerate(ctrl_chords):
        ccx = ctrl_start_x + i * (ctrl_cw + ctrl_gap) + ctrl_cw / 2
        ccy = ctrl_row_y + dir_ch / 2
        draw_control_cell(svg, ccx, ccy, entries, chord, label,
                          cell_w=ctrl_cw, cell_h=dir_ch)

    return svg.build(total_w, total_h)


# ════════════════════════════════════════════════════════════════════
# CLI
# ════════════════════════════════════════════════════════════════════
def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    layouts_dir = os.path.join(
        project_root, 'app', 'src', 'main', 'assets', 'layouts')
    output_dir = os.path.join(script_dir, 'layouts')
    os.makedirs(output_dir, exist_ok=True)

    if len(sys.argv) > 1:
        files = []
        for arg in sys.argv[1:]:
            if arg.startswith('--'):
                continue
            path = (os.path.join(layouts_dir, arg)
                    if not os.path.isabs(arg) else arg)
            if os.path.exists(path):
                files.append(path)
            else:
                print(f'Warning: {path} not found, skipping')
    else:
        files = sorted(
            os.path.join(layouts_dir, f)
            for f in os.listdir(layouts_dir) if f.endswith('.xml')
        )

    for path in files:
        fname = os.path.basename(path)
        layout_id, layout_name, entries = parse_layout(path)
        variant = 'Standard' if '-standard' in fname else 'Optimized'
        out_name = fname.replace('.xml', '.svg')
        out_path = os.path.join(output_dir, out_name)

        svg_content = generate_svg(layout_id, layout_name, entries, variant)
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(svg_content)
        print(f'  \u2713 {out_path}')

    print(f'\nDone: generated {len(files)} diagram(s) in {output_dir}')


if __name__ == '__main__':
    main()
