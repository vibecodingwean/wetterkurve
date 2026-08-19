#!/usr/bin/env python3
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
SRC_ICONS = ROOT / "extension" / "icons"
DESKTOP_ICONS = ROOT / "windows" / "Wetterkurve.Desktop" / "Assets" / "icons"
WIDGET_ICONS = ROOT / "windows" / "Wetterkurve.Widget" / "WebIcons"
WIDGET_ASSETS = ROOT / "windows" / "Wetterkurve.Widget" / "Assets"
PROVIDER = ROOT / "windows" / "Wetterkurve.Widget" / "ProviderAssets"
DESKTOP_ASSETS = ROOT / "windows" / "Wetterkurve.Desktop" / "Assets"
OVERVIEW = ROOT / "assets" / "wetterkurve-overview.png"

for path in (DESKTOP_ICONS, WIDGET_ICONS, WIDGET_ASSETS, PROVIDER, DESKTOP_ASSETS):
    path.mkdir(parents=True, exist_ok=True)


def render_svg(svg: Path, size: int) -> Image.Image:
    png = cairosvg.svg2png(url=str(svg), output_width=size, output_height=size)
    return Image.open(BytesIO(png)).convert("RGBA")


def rounded(image: Image.Image, radius: int) -> Image.Image:
    image = image.convert("RGBA")
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, image.width, image.height), radius, fill=255)
    image.putalpha(mask)
    return image


def solid(size: tuple[int, int], color: tuple[int, int, int, int]) -> Image.Image:
    image = Image.new("RGBA", size, color)
    return image


def paste_centered(base: Image.Image, overlay: Image.Image) -> None:
    x = (base.width - overlay.width) // 2
    y = (base.height - overlay.height) // 2
    base.alpha_composite(overlay, (x, y))


def main() -> None:
    for svg in sorted(SRC_ICONS.glob("*.svg")):
        render_svg(svg, 256).save(DESKTOP_ICONS / f"{svg.stem}.png")
        render_svg(svg, 128).save(WIDGET_ICONS / f"{svg.stem}.png")

    sun = render_svg(SRC_ICONS / "clear.svg", 256)
    logo_bg = (32, 42, 58, 255)
    for name, size in {
        "StoreLogo.png": 50,
        "Square44x44Logo.png": 44,
        "Square150x150Logo.png": 150,
        "LockScreenLogo.png": 48,
    }.items():
        canvas = solid((size, size), logo_bg)
        icon = sun.copy()
        icon.thumbnail((int(size * 0.78), int(size * 0.78)), Image.Resampling.LANCZOS)
        paste_centered(canvas, icon)
        canvas.save(WIDGET_ASSETS / name)

    wide = solid((310, 150), logo_bg)
    icon = sun.copy()
    icon.thumbnail((96, 96), Image.Resampling.LANCZOS)
    paste_centered(wide, icon)
    wide.save(WIDGET_ASSETS / "Wide310x150Logo.png")

    splash = solid((620, 300), logo_bg)
    icon = sun.copy()
    icon.thumbnail((140, 140), Image.Resampling.LANCZOS)
    paste_centered(splash, icon)
    splash.save(WIDGET_ASSETS / "SplashScreen.png")

    provider_icon = solid((64, 64), logo_bg)
    icon = sun.copy()
    icon.thumbnail((50, 50), Image.Resampling.LANCZOS)
    paste_centered(provider_icon, icon)
    provider_icon.save(PROVIDER / "Icon.png")

    overview = Image.open(OVERVIEW).convert("RGBA")
    popup = overview.crop((1800, 60, 2853, 819))
    shot = Image.new("RGBA", (300, 304), (32, 42, 58, 255))
    fitted = popup.copy()
    fitted.thumbnail((292, 296), Image.Resampling.LANCZOS)
    shot.alpha_composite(fitted, ((300 - fitted.width) // 2, (304 - fitted.height) // 2))
    rounded(shot, 18).save(PROVIDER / "Screenshot.png")

    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    ico_images = []
    for size in ico_sizes:
        canvas = solid((size, size), (0, 0, 0, 0))
        icon = sun.copy()
        icon.thumbnail((size, size), Image.Resampling.LANCZOS)
        paste_centered(canvas, icon)
        ico_images.append(canvas)
    ico_images[-1].save(
        DESKTOP_ASSETS / "wetterkurve.ico",
        format="ICO",
        sizes=[(size, size) for size in ico_sizes],
        append_images=ico_images[:-1],
    )
    print("generated Windows assets")


if __name__ == "__main__":
    main()
