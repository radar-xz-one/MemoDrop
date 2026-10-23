import math
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
BG = "#2F6F8F"; LINE = "#1B2A33"
parts = [
    ([(-32,-7),(-26,-7),(-26,7),(-32,7)], "#E8456B"),
    ([(-26,-7),(-20,-7),(-20,7),(-26,7)], "#B8BEC4"),
    ([(-20,-7),(18,-7),(18,7),(-20,7)], "#F6B400"),
    ([(-20,-7),(18,-7),(18,-2.5),(-20,-2.5)], "#FFD23F"),
    ([(-20,3),(18,3),(18,7),(-20,7)], "#D48A00"),
    ([(18,-7),(32,0),(18,7)], "#F0C48A"),
    ([(26.7,-2.7),(32,0),(26.7,2.7)], "#2B2B2B"),
]
outlines = [parts[0][0], parts[1][0], parts[2][0], parts[5][0]]
ANG = math.radians(-35); S = 4

img = Image.new("RGB", (W*S, H*S), BG)
d = ImageDraw.Draw(img)

cx, cy, sc = 745*S, 235*S, 6.2*S
def rot(p):
    x, y = p[0]*sc, p[1]*sc
    return (cx + x*math.cos(ANG) - y*math.sin(ANG), cy + x*math.sin(ANG) + y*math.cos(ANG))

tip = rot((32, 0))
pts = []
for i in range(0, 61):
    t = i/60
    pts.append((tip[0] - 40*S - t*190*S*-1 if False else tip[0] + 10*S + t*0, 0))
wave = [(tip[0] - 20*S - t*330*S, tip[1] + 24*S + math.sin(t*math.pi*5)*14*S + t*60*S) for t in [i/80 for i in range(81)]]
d.line(wave, fill="#FFFFFF", width=int(7*S), joint="curve")

for poly, c in parts:
    d.polygon([rot(p) for p in poly], fill=c)
for poly in outlines:
    q = [rot(p) for p in poly]
    d.line(q + [q[0]], fill=LINE, width=int(1.2*sc), joint="curve")

img = img.resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(img)
bold = "C:/Windows/Fonts/YuGothB.ttc"
reg = "C:/Windows/Fonts/YuGothM.ttc"
d.text((70, 120), "メモポイ", font=ImageFont.truetype(bold, 110), fill="#FFFFFF")
d.text((74, 250), "MemoDrop", font=ImageFont.truetype(bold, 56), fill="#FFD23F")
d.text((74, 340), "書いて、めくって、捨てる。", font=ImageFont.truetype(reg, 34), fill="#FFFFFF")
d.text((74, 388), "Write. Peel. Toss.", font=ImageFont.truetype(reg, 30), fill=(255, 255, 255))
img.save("store/feature-graphic-1024x500.png")
