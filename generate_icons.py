import os
from PIL import Image, ImageDraw

workspace = r"c:\Users\vansh\Documents\Vibecode\QuickNovel-Enhanced"
source_icon = os.path.join(workspace, "app", "assets", "NeoQN Icon.png")
res_dir = os.path.join(workspace, "app", "src", "main", "res")

if not os.path.exists(source_icon):
    print(f"Error: Source icon not found at {source_icon}")
    exit(1)

img = Image.open(source_icon).convert("RGBA")

# 1. Density mappings
legacy_sizes = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192
}

adaptive_sizes = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432
}

def create_circular_mask(size):
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    return mask

def resize_and_pad(img, target_size, fit_factor=1.0, nudge_up_pct=0.0):
    """
    Resizes maintaining aspect ratio and pads to fit a target_size square frame.
    nudge_up_pct nudges the image up by a percentage of target_size to fix visual bottom-heavy weights.
    """
    canvas_size = target_size
    active_size = int(target_size * fit_factor)

    # Calculate bounding box fitting keeping aspect ratio
    ratio = min(active_size / img.width, active_size / img.height)
    new_w = int(img.width * ratio)
    new_h = int(img.height * ratio)
    
    resized = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
    
    # Create transparent square base
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0,0,0,0))
    offset_x = (canvas_size - new_w) // 2
    offset_y = (canvas_size - new_h) // 2 - int(canvas_size * nudge_up_pct) # Nudge up
    canvas.paste(resized, (offset_x, max(0, offset_y)), mask=resized if 'A' in img.getbands() else None)
    
    return canvas

print(f"Loading source icon: {source_icon} ({img.size})")
for density in legacy_sizes:
    target_dir = os.path.join(res_dir, f"mipmap-{density}")
    os.makedirs(target_dir, exist_ok=True)
    size = legacy_sizes[density]
    adapt_size = adaptive_sizes[density]
    print(f"Generating icons for {density} buckets")

    # ----------------------------------------------------
    # A. Legacy Icon: Nudge up slightly (0.04)
    # ----------------------------------------------------
    legacy_canvas = resize_and_pad(img, size, fit_factor=1.0, nudge_up_pct=0.04)
    legacy_canvas.save(os.path.join(target_dir, "ic_launcher.png"), "PNG")
    
    # B. Legacy Round Icon: Mask circular bounds
    mask = create_circular_mask(size)
    round_img = Image.new("RGBA", (size, size), (0,0,0,0))
    round_img.paste(legacy_canvas, (0, 0), mask=mask)
    round_img.save(os.path.join(target_dir, "ic_launcher_round.png"), "PNG")

    # ----------------------------------------------------
    # C. Adaptive Icon Foreground: Nudge up slightly (0.04)
    # ----------------------------------------------------
    adaptive_canvas = resize_and_pad(img, adapt_size, fit_factor=0.90, nudge_up_pct=0.04)
    adaptive_canvas.save(os.path.join(target_dir, "ic_launcher_foreground.png"), "PNG")

print("Icons recentered and generated successfully!")
