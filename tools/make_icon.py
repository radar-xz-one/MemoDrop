import math, os
from PIL import Image, ImageDraw

BG = "#2F6F8F"; LINE = "#1B2A33"
parts = [  # (polygon in pencil coords, fill)
    ([(-32,-7),(-26,-7),(-26,7),(-32,7)], "#E8456B"),
    ([(-26,-7),(-20,-7),(-20,7),(-26,7)], "#B8BEC4"),
    ([(-20,-7),(18,-7),(18,7),(-20,7)], "#F6B400"),
    ([(-20,-7),(18,-7),(18,-2.5),(-20,-2.5)], "#FFD23F"),
    ([(-20,3),(18,3),(18,7),(-20,7)], "#D48A00"),
    ([(18,-7),(32,0),(18,7)], "#F0C48A"),
    ([(26.7,-2.7),(32,0),(26.7,2.7)], "#2B2B2B"),
]
outlines = [parts[0][0], parts[1][0], parts[2][0], parts[5][0]]
ANG = math.radians(-35)

def rot(pt, cx=54, cy=54):
    x, y = pt[0]*1.15, pt[1]*1.15
    return (cx + x*math.cos(ANG) - y*math.sin(ANG), cy + x*math.sin(ANG) + y*math.cos(ANG))

def vec_path(poly):
    pts = [rot(p) for p in poly]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"

# vector foreground
xml = ['<?xml version="1.0" encoding="utf-8"?>',
 '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
 '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">']
for poly, c in parts:
    xml.append(f'    <path android:fillColor="{c}" android:pathData="{vec_path(poly)}" />')
for poly in outlines:
    xml.append(f'    <path android:strokeColor="{LINE}" android:strokeWidth="1.2" android:strokeLineJoin="round" android:pathData="{vec_path(poly)}" />')
xml.append('</vector>')
os.makedirs('app/src/main/res/drawable', exist_ok=True)
open('app/src/main/res/drawable/ic_launcher_foreground.xml', 'w', encoding='utf-8').write("\n".join(xml) + "\n")

os.makedirs('app/src/main/res/mipmap-anydpi-v26', exist_ok=True)
for n in ('ic_launcher', 'ic_launcher_round'):
    open(f'app/src/main/res/mipmap-anydpi-v26/{n}.xml', 'w', encoding='utf-8').write(
'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''')
open('app/src/main/res/values/ic_launcher_background.xml', 'w', encoding='utf-8').write(
'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#2F6F8F</color>
</resources>
''')

def render(size, shape):
    S = 4; N = size*S; k = N/108
    img = Image.new("RGBA", (N, N), (0,0,0,0))
    d = ImageDraw.Draw(img)
    if shape == "square":
        d.rectangle([0,0,N,N], fill=BG)
    elif shape == "rounded":
        d.rounded_rectangle([0,0,N-1,N-1], radius=N*0.22, fill=BG)
    else:
        d.ellipse([0,0,N-1,N-1], fill=BG)
    sc = 1.0 if shape == "square" else 1.0
    for poly, c in parts:
        d.polygon([(x*k, y*k) for x, y in map(rot, poly)], fill=c)
    for poly in outlines:
        pts = [(x*k, y*k) for x, y in map(rot, poly)]
        d.line(pts + [pts[0]], fill=LINE, width=max(1, int(1.2*k)), joint="curve")
    return img.resize((size, size), Image.LANCZOS)

os.makedirs('store', exist_ok=True)
render(512, "square").convert("RGB").save('store/icon-512.png')
for dpi, px in (('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)):
    d = f'app/src/main/res/mipmap-{dpi}'; os.makedirs(d, exist_ok=True)
    render(px, "rounded").save(f'{d}/ic_launcher.png')
    render(px, "round").save(f'{d}/ic_launcher_round.png')
