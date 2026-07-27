#!/usr/bin/env python3
"""Generate every app, favicon and notification icon from scripts/source-app-icon.png.

One-off regeneration tool: the generated PNGs are committed, so you only need to run
this when the artwork changes. Requires `pip install Pillow numpy`.

    cd frontend && python3 scripts/generate-notification-assets.py

`scripts/source-app-icon.png` is the pristine artwork (compass rose in dark teal on
white, with the N/W/E/S letters and a wide margin). Never regenerate it from the
outputs, or the crop would compound on every run.

What this fixes:

* The compass only covered 72% of the source canvas -- the rest was margin and the
  four thin letters -- so the installed app icon looked tiny. Every output here
  drops the letters and scales the compass to fill its tile.
* The manifest had no `maskable` icon, so Android letterboxed the square icon
  inside the launcher shape. The maskable variant keeps the 80% safe zone.
* Chrome on Android renders a notification's status-bar icon from the `badge`
  option using only its ALPHA channel. Passing an opaque icon (as we used to)
  flattens to a plain white box, so the badge is a white-on-transparent silhouette.
* The app icon is dark ink on white, which reads as a white block in Android dark
  mode, so the notification icon inverts it onto a brand-teal tile.
"""

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

FRONTEND = Path(__file__).resolve().parent.parent
PUBLIC = FRONTEND / "public"
SOURCE = FRONTEND / "scripts" / "source-app-icon.png"

WHITE = (255, 255, 255)
BRAND_TEAL = (22, 86, 86)  # #165656, matches manifest theme_color and the MUI primary
INK_RED = 14  # red channel of the source ink #0E4750; the widest range against white
INK_THRESHOLD = 100  # alpha above which a pixel counts as ink for component labelling
CENTRE_TOLERANCE = 20  # px from the canvas centre within which the compass dot sits

# Fraction of each tile the compass covers. Plain icons nearly fill it; the maskable
# one stays inside Android's 80% safe zone; the notification icon leaves room for
# Android 12+'s circular crop of the large icon.
ICON_FILL = 0.92
MASKABLE_FILL = 0.8
BADGE_FILL = 0.88
NOTIFICATION_ICON_FILL = 0.78

CORNER_RADIUS_FRACTION = 0.125  # rounded-square radius of the notification tile
SUPERSAMPLE = 4  # keeps the rounded corners smooth


def ink_alpha(image: Image.Image) -> np.ndarray:
    """Turn dark-ink-on-white artwork into an alpha mask, preserving anti-aliasing.

    The artwork is anti-aliased ink over white, i.e. `pixel = a * ink + (1 - a) * white`,
    so coverage is recovered from a single channel. Using the red channel (ink 14 vs
    white 255) keeps solid ink fully opaque; keying off luminance instead would leave
    the disc at ~83% alpha and wash the colour out when composited back onto white.
    """
    red = np.array(image.convert("RGB"), dtype=np.float32)[:, :, 0]
    return np.clip((255.0 - red) / (255.0 - INK_RED) * 255.0, 0, 255).astype(np.uint8)


def label_components(alpha: np.ndarray) -> tuple[np.ndarray, dict[int, int]]:
    """Label 4-connected ink components. Returns (labels, {label: pixel count})."""
    ink = alpha > INK_THRESHOLD
    height, width = ink.shape
    labels = np.zeros((height, width), dtype=np.int32)
    sizes: dict[int, int] = {}
    next_label = 0
    for y in range(height):
        for x in range(width):
            if not ink[y, x] or labels[y, x]:
                continue
            next_label += 1
            labels[y, x] = next_label
            queue = deque([(y, x)])
            count = 0
            while queue:
                cy, cx = queue.popleft()
                count += 1
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < height and 0 <= nx < width and ink[ny, nx] and not labels[ny, nx]:
                        labels[ny, nx] = next_label
                        queue.append((ny, nx))
            sizes[next_label] = count
    return labels, sizes


def split_mark(alpha: np.ndarray) -> tuple[Image.Image, Image.Image]:
    """Split the artwork into the two masks we need.

    The mark is made of a filled disc (largest component), a thin outer ring, a
    centre dot inside the star, and the four N/W/E/S letters. Returns:

    * `compass` -- disc, ring and centre dot: the letters are dropped so the compass
      can fill the tile instead of leaving a wide margin.
    * `badge` -- disc and centre dot only: the thin ring blurs into noise at 24dp,
      while the disc with the star cut out of it stays readable in the status bar.

    The tick marks need no special handling: they are holes in the disc, not
    components of their own.
    """
    labels, sizes = label_components(alpha)
    by_size = sorted(sizes, key=lambda label: sizes[label], reverse=True)
    disc, ring = by_size[0], by_size[1]

    height, width = alpha.shape
    centre_y, centre_x = height / 2, width / 2
    dot = np.zeros(alpha.shape, dtype=bool)
    for label in by_size[2:]:
        ys, xs = np.where(labels == label)
        if abs(ys.mean() - centre_y) < CENTRE_TOLERANCE and abs(xs.mean() - centre_x) < CENTRE_TOLERANCE:
            dot |= labels == label

    def mask(keep: np.ndarray) -> Image.Image:
        return Image.fromarray(np.where(keep, alpha, 0).astype(np.uint8), "L")

    return mask((labels == disc) | (labels == ring) | dot), mask((labels == disc) | dot)


def interior_holes(solid: np.ndarray) -> np.ndarray:
    """Areas enclosed by `solid`: background pixels that cannot reach the border."""
    height, width = solid.shape
    outside = np.zeros(solid.shape, dtype=bool)
    queue = deque()
    for y in range(height):
        for x in (0, width - 1):
            if not solid[y, x] and not outside[y, x]:
                outside[y, x] = True
                queue.append((y, x))
    for x in range(width):
        for y in (0, height - 1):
            if not solid[y, x] and not outside[y, x]:
                outside[y, x] = True
                queue.append((y, x))
    while queue:
        cy, cx = queue.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < height and 0 <= nx < width and not solid[ny, nx] and not outside[ny, nx]:
                outside[ny, nx] = True
                queue.append((ny, nx))
    return ~solid & ~outside


def transparent_mark(artwork: Image.Image, compass: Image.Image) -> Image.Image:
    """The compass with a transparent surround, for the favicons.

    The star and tick marks are white in the artwork, so simply dropping the
    background would punch them through as holes. Instead the closed outer ring is
    filled to rebuild the white face, and only the area outside the ring is cut away.
    The mask is left hard-edged on purpose: these outputs are downscaled by ~10x,
    which supplies smoother anti-aliasing than feathering the mask here would.
    """
    solid = np.array(compass) > INK_THRESHOLD
    face = Image.fromarray(np.where(solid | interior_holes(solid), 255, 0).astype(np.uint8), "L")
    mark = Image.new("RGBA", compass.size, (0, 0, 0, 0))
    mark.paste(Image.new("RGBA", compass.size, (*WHITE, 255)), (0, 0), face)
    mark.paste(artwork, (0, 0), artwork)
    return mark


def fit(glyph: Image.Image, canvas: int, fill: float) -> Image.Image:
    """Crop a glyph to its ink and scale it to cover `fill` of a square canvas."""
    cropped = glyph.crop(glyph.getbbox())
    width, height = cropped.size
    scale = canvas * fill / max(width, height)
    return cropped.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.LANCZOS)


def centred(canvas: int, size: tuple[int, int]) -> tuple[int, int]:
    return ((canvas - size[0]) // 2, (canvas - size[1]) // 2)


def coloured(mask: Image.Image, colour: tuple[int, int, int]) -> Image.Image:
    """Paint a solid colour through an alpha mask."""
    layer = Image.new("RGBA", mask.size, (*colour, 255))
    layer.putalpha(mask)
    return layer


def app_icon(artwork: Image.Image, size: int, fill: float) -> Image.Image:
    """The compass in its original ink colour, centred on an opaque white tile."""
    glyph = fit(artwork, size, fill)
    icon = Image.new("RGBA", (size, size), (*WHITE, 255))
    icon.paste(glyph, centred(size, glyph.size), glyph)
    return icon


def favicon(mark: Image.Image, size: int) -> Image.Image:
    """The compass edge to edge on a transparent background, for browser tabs."""
    glyph = fit(mark, size, 1.0)
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon.paste(glyph, centred(size, glyph.size), glyph)
    return icon


def notification_icon(compass: Image.Image, size: int) -> Image.Image:
    """White compass on a brand-teal rounded square, readable in light and dark mode."""
    canvas = size * SUPERSAMPLE
    tile = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    ImageDraw.Draw(tile).rounded_rectangle(
        (0, 0, canvas - 1, canvas - 1),
        radius=round(canvas * CORNER_RADIUS_FRACTION),
        fill=(*BRAND_TEAL, 255),
    )
    glyph = fit(compass, canvas, NOTIFICATION_ICON_FILL)
    white = coloured(glyph, WHITE)
    tile.paste(white, centred(canvas, glyph.size), white)
    return tile.resize((size, size), Image.LANCZOS)


def badge_icon(badge: Image.Image, size: int) -> Image.Image:
    """White-on-transparent silhouette: Android uses only the alpha channel of this."""
    glyph = fit(badge, size, BADGE_FILL)
    icon = Image.new("RGBA", (size, size), (*WHITE, 0))
    white = coloured(glyph, WHITE)
    icon.paste(white, centred(size, glyph.size), white)
    return icon


def save(image: Image.Image, name: str, **options) -> None:
    path = PUBLIC / name
    image.save(path, **options)
    print(f"wrote {path.relative_to(FRONTEND)}")


def main() -> None:
    source = Image.open(SOURCE)
    alpha = ink_alpha(source)
    compass_mask, badge_mask = split_mark(alpha)

    # keep the original ink colour by masking the source pixels rather than recolouring
    artwork = source.convert("RGB").convert("RGBA")
    artwork.putalpha(compass_mask)

    # PWA / launcher icons
    save(app_icon(artwork, 192, ICON_FILL), "icon-192x192.png")
    save(app_icon(artwork, 512, ICON_FILL), "icon-512x512.png")
    save(app_icon(artwork, 512, MASKABLE_FILL), "icon-512x512-maskable.png")

    # iOS home screen (opaque: iOS ignores transparency)
    save(app_icon(artwork, 180, ICON_FILL), "apple-touch-icon.png")

    # browser tab, on a transparent background so it sits on any tab colour
    mark = transparent_mark(artwork, compass_mask)
    save(favicon(mark, 32), "favicon-32x32.png")
    save(favicon(mark, 16), "favicon-16x16.png")
    save(favicon(mark, 48), "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])

    # notifications
    save(notification_icon(compass_mask, 192), "notification-icon-192x192.png")
    save(badge_icon(badge_mask, 96), "notification-badge-96x96.png")


if __name__ == "__main__":
    main()
