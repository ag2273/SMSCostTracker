from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

ROOT = Path("mockups")
SCREENS = ROOT / "screens_png"
REVIEW = ROOT / "review"
REVIEW.mkdir(parents=True, exist_ok=True)

FONT_DIR = "/usr/share/fonts/truetype/noto"
def font(size, bold=False):
    return ImageFont.truetype(f"{FONT_DIR}/NotoSans-{'Bold' if bold else 'Regular'}.ttf", size)
def mono(size, bold=False):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", size)

LEDGER = {
    "bg": "#F7F4ED", "surface": "#FFFFFF", "surface_alt": "#F1ECE2",
    "outline": "#DCD5C7", "ink": "#14201C", "muted": "#64736E",
    "primary": "#0E6B5C", "accent": "#C97B2E", "danger": "#B23B36", "success": "#2E7D5B",
}

META = [
    ("01-welcome", "Welcome & privacy", "First run · 01", "Set the privacy promise before asking for anything.",
     ["Local-only message is the headline", "One clear primary action", "No bank login, no account"],
     "No data yet. The app explains what it will read and why."),
    ("02-permission", "SMS permission", "First run · 02", "Explain why SMS access is needed and what is read.",
     ["Permission only after the user taps", "Revoke path is visible", "No internet required"],
     "Android system prompt follows this screen; the user can decline."),
    ("03-threads", "Choose bank threads", "First run · 03", "Limit the scan to bank conversations the user owns.",
     ["Grouped list with message counts", "5 selected · 2,174 messages", "Add or remove later in Settings"],
     "SAB, Al Rajhi, CIB, SNB and Riyad Bank are selected; ANB and STC Bank are not."),
    ("04-scan", "Scan progress", "First run · 04", "Show on-device progress and what is being skipped.",
     ["Live transaction and skip counts", "Balance lines and duplicates are named", "Cancel is available"],
     "SAB 842 / 1,214 · 612 transactions · 148 skipped · 6 unparsed so far."),
    ("05-review-groups", "Review instruments", "First run · 05", "Group the 12 numbers by sender and suggest a type.",
     ["Grouped by sender, not one giant list", "Suggested split is visible", "Can pause and review later"],
     "SAB 4 values · Al Rajhi 2 · CIB 2 · SNB 2 · Riyad Bank 2."),
    ("06-review-confirm", "Confirm SAB numbers", "First run · 06", "Confirm card vs account per number using SMS wording.",
     ["Suggestions are pre-selected", "Every value is editable", "Saves 4 and moves to Al Rajhi"],
     "1214 Card · 001 Account · 0542 Card · 9981 Card."),
    ("07-complete", "Import complete", "First run · 07", "Summarize what landed and what still needs attention.",
     ["Headline outcome, not a log", "Skipped and unparsed counts", "Two clear next steps"],
     "1,284 transactions · 890 skipped · 6 / 12 confirmed · 6 need review."),
    ("08-home", "Home", "Daily · 08", "Answer “what did I spend this month?” in one screen.",
     ["SAR/EGP switch, never mixed", "Cards vs Accounts split", "Pending review and recent activity"],
     "October SAR 4,820.00 · Cards 3,140 · Accounts 1,680 · EGP 3,240 · Other 26% until Jarir is corrected."),
    ("09-activity", "Activity", "Daily · 09", "Scan, search and filter every transaction.",
     ["Active filter count is visible", "Transactions grouped by date", "Card/account shown with icon + label"],
     "128 transactions in October; Jarir Bookstore is flagged for review."),
    ("10-filters", "Filters", "Daily · 10", "Narrow the list without leaving the context.",
     ["Period, category, account, currency, merchant", "Clear all and result count", "4 active · 18 matches"],
     "This month · Shopping · SAB 1214 · SAR · merchant Amazon."),
    ("11-detail", "Transaction detail", "Daily · 11", "Explain what was parsed before showing diagnostics.",
     ["Parsed fields first", "Source SMS and why-it-matched", "One-tap category or instrument correction"],
     "Jarir Bookstore · SAR 1,249.00 · Al Rajhi 4432 · Other 62% match."),
    ("12-category", "Change category", "Daily · 12", "Correct a category and teach the classifier.",
     ["Current and new category are explicit", "15 categories, searchable", "Explains the learning effect"],
     "Other → Shopping for Jarir Bookstore; applies to future visits."),
    ("13-insights", "Insights", "Daily · 13", "Show category mix, card/account split, trend and merchants.",
     ["Currency is always explicit", "Totals and percentages are visible", "Trend and top merchants stay readable"],
     "SAR 4,820 · Groceries 32% · Shopping 26% · Dining 24% · Other 18% · after correcting Jarir."),
    ("14-settings", "Settings", "Daily · 14", "Manage linked banks, instruments, import, export and privacy.",
     ["Review banner at the top", "Every management path is one tap away", "Demo/danger actions are separated"],
     "5 threads · 2,174 messages · 4 cards · 2 accounts · 6 to review."),
    ("15-instruments", "Cards & accounts", "Manage · 15", "Confirm, rename or ignore instruments after onboarding.",
     ["Grouped by sender", "Confirmed vs needs-review states", "Only 6 pending values remain"],
     "SAB and Al Rajhi confirmed; CIB, SNB and Riyad still need review."),
    ("16-export", "Export & backup", "Manage · 16", "Explain exactly what leaves the phone.",
     ["CSV scope is explicit", "Drive backup is optional", "SMS text never leaves the device"],
     "1,284 transactions · SAR + EGP · last export 12 Oct 2026."),
]

BG_TOP = (242, 244, 247)
BG_BOT = (228, 232, 238)

def gradient_bg(w, h):
    img = Image.new("RGB", (w, h), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img

def wrap(draw, text, f, maxw):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        test = (cur + " " + w).strip()
        if draw.textlength(test, font=f) <= maxw:
            cur = test
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines

def rounded_shadow(canvas, box, radius, offset=(0, 18), blur=28, alpha=80):
    layer = Image.new("RGBA", canvas.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    x0,y0,x1,y1 = box
    d.rounded_rectangle([x0+offset[0], y0+offset[1], x1+offset[0], y1+offset[1]], radius=radius, fill=(16,22,30,alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(layer)

def compose_review(slug, title, step, purpose, checks, sample):
    W,H = 1400,1100
    canvas = gradient_bg(W,H).convert("RGBA")
    # shadow + phone frame
    phone = (104,70,574,1030)
    rounded_shadow(canvas, phone, 58, offset=(0,26), blur=34, alpha=95)
    rounded_shadow(canvas, phone, 58, offset=(0,8), blur=14, alpha=55)
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle(phone, radius=58, fill=(16,18,22,255))
    # screen
    scr = Image.open(SCREENS / f"{slug}.png").convert("RGB").resize((448,938), Image.LANCZOS)
    mask = Image.new("L", (448,938), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0,0,447,937], radius=47, fill=255)
    canvas.paste(scr, (115,81), mask)
    # camera dot
    d.ellipse([329,91,339,101], fill=(5,6,8,255))
    # annotation
    x = 650
    y = 94
    d.text((x,y), step.upper(), font=font(15,True), fill=(123,132,146,255))
    # title
    ty = y + 32
    d.text((x,ty), title, font=font(44,True), fill=(18,26,36,255))
    ty += 62
    # purpose
    pf = font(19)
    for line in wrap(d, purpose, pf, 650):
        d.text((x,ty), line, font=pf, fill=(63,74,86,255))
        ty += 29
    ty += 14
    d.line([(x,ty),(1330,ty)], fill=(205,210,218,255), width=1)
    ty += 24
    d.text((x,ty), "WHAT TO CHECK", font=font(13,True), fill=(123,132,146,255))
    ty += 24
    bf = font(16)
    for item in checks:
        d.ellipse([x+2,ty+7,x+8,ty+13], fill=(14,107,92,255))
        d.text((x+18,ty), item, font=bf, fill=(45,55,66,255))
        ty += 29
    # sample card
    ty += 16
    card = [x, ty, 1330, ty+118]
    d.rounded_rectangle(card, radius=16, fill=(255,255,255,255), outline=(220,213,199,255), width=1)
    d.text((x+18,ty+16), "SAMPLE STATE", font=font(12,True), fill=(201,123,46,255))
    sy = ty + 42
    for line in wrap(d, sample, font(15), 620):
        d.text((x+18,sy), line, font=font(15), fill=(70,80,92,255))
        sy += 24
    # footer
    d.text((x, 1032), "Ledger · light mode · proposed UX", font=font(12), fill=(140,148,160,255))
    out = REVIEW / f"{slug}.png"
    canvas.convert("RGB").save(out, quality=95)
    return out

for slug,title,step,purpose,checks,sample in META:
    p = compose_review(slug,title,step,purpose,checks,sample)
    print("wrote", p)

# ---------------- storyboard ----------------
def compose_storyboard():
    cols, rows = 6, 3
    cw, ch = 240, 502
    gap_x, gap_y = 22, 56
    margin_x, top = 70, 132
    W = margin_x*2 + cols*cw + (cols-1)*gap_x
    H = top + rows*(ch+gap_y) + 60
    img = Image.new("RGB",(W,H),(247,244,237))
    d = ImageDraw.Draw(img)
    d.text((margin_x,36),"SMSCostTracker · proposed screens",font=font(30,True),fill=(20,30,38))
    d.text((margin_x,78),"Ledger palette · sample data · 16 screens",font=font(14),fill=(100,115,110))
    for i,(slug,title,step,purpose,checks,sample) in enumerate(META):
        r,c = divmod(i,cols)
        x = margin_x + c*(cw+gap_x)
        y = top + r*(ch+gap_y)
        # thumb shadow
        sh = Image.new("RGBA",(cw+20,ch+20),(0,0,0,0))
        ImageDraw.Draw(sh).rounded_rectangle([6,8,cw+4,ch+8],radius=18,fill=(16,22,30,40))
        sh = sh.filter(ImageFilter.GaussianBlur(8))
        img.paste(sh,(x-6,y-4),sh)
        scr = Image.open(SCREENS/f"{slug}.png").convert("RGB").resize((cw,ch),Image.LANCZOS)
        mask = Image.new("L",(cw,ch),0)
        ImageDraw.Draw(mask).rounded_rectangle([0,0,cw-1,ch-1],radius=16,fill=255)
        img.paste(scr,(x,y),mask)
        # caption
        d.rounded_rectangle([x,y+ch+8,x+cw,y+ch+46], radius=10, fill=(255,255,255))
        d.text((x+10,y+ch+14), f"{i+1:02d}  {title}", font=font(11,True), fill=(25,35,44))
        d.text((x+10,y+ch+29), step, font=font(9), fill=(110,120,130))
    # phase labels
    for c0,label in [(0,"FIRST RUN"),(6,"DAILY REVIEW"),(12,"MANAGE")]:
        # determine row/col
        r,c = divmod(c0,cols)
        x = margin_x + c*(cw+gap_x)
        y = top + r*(ch+gap_y) - 24
        d.text((x,y), label, font=font(11,True), fill=(14,107,92))
    img.save(ROOT/"storyboard.png", quality=95)
    print("wrote storyboard", img.size)

compose_storyboard()

# ---------------- user scenario flow ----------------
def arrow(d, x1, y1, x2, y2, color=(150,160,170), width=2):
    d.line([(x1,y1),(x2,y2)], fill=color, width=width)
    ang = math.atan2(y2-y1, x2-x1)
    L=8; a=0.5
    p1=(x2-L*math.cos(ang-a), y2-L*math.sin(ang-a))
    p2=(x2-L*math.cos(ang+a), y2-L*math.sin(ang+a))
    d.polygon([(x2,y2),p1,p2], fill=color)

def card_with_thumb(img, d, x, y, w, h, slug, title, step, accent=False):
    d.rounded_rectangle([x,y,x+w,y+h], radius=14, fill=(255,255,255), outline=(220,213,199), width=1)
    if slug:
        tw,th = 46,96
        scr = Image.open(SCREENS/f"{slug}.png").convert("RGB").resize((tw,th),Image.LANCZOS)
        mask = Image.new("L",(tw,th),0)
        ImageDraw.Draw(mask).rounded_rectangle([0,0,tw-1,th-1],radius=7,fill=255)
        img.paste(scr,(x+9,y+(h-th)//2),mask)
        tx = x+64
    else:
        tx = x+12
    d.text((tx,y+14), step, font=font(9,True), fill=(201,123,46) if accent else (123,132,146))
    d.text((tx,y+30), title, font=font(12,True), fill=(20,30,38))
    # small descriptor lines
    d.text((tx,y+52), "tap to review", font=font(8), fill=(120,130,140))

def compose_scenario():
    W,H = 2000,1280
    img = Image.new("RGB",(W,H),(247,244,237))
    d = ImageDraw.Draw(img)
    # header
    d.text((80,52),"SMSCostTracker · full user scenario",font=font(38,True),fill=(18,28,36))
    d.text((80,104),"Ledger palette · first run, daily review and management · sample data from SAB, Al Rajhi, CIB, SNB and Riyad Bank",font=font(15),fill=(90,105,100))
    d.rounded_rectangle([80,144,1920,151],radius=3,fill=(220,213,199))
    lanes = [
        ("FIRST RUN", "One-time setup. The user grants access, picks threads, scans, and confirms the numbers."),
        ("DAILY REVIEW", "The main loop: see the month, narrow the list, understand one transaction, correct it."),
        ("MANAGE", "After setup: linked banks, instruments, export and optional backup."),
        ("GUARDRAILS", "The states that keep the app trustworthy when the data is imperfect."),
    ]
    lane_y = [186, 424, 662, 900]
    lane_h = 196
    for i,(name,desc) in enumerate(lanes):
        y=lane_y[i]
        d.rounded_rectangle([80,y,1920,y+lane_h], radius=18, fill=(255,255,255), outline=(228,223,213), width=1)
        d.text((102,y+16), name, font=font(12,True), fill=(14,107,92))
        d.text((102,y+36), desc, font=font(11), fill=(105,118,112))
    # first run cards
    first = [
        ("01-welcome","Welcome","01"),("02-permission","Permission","02"),("03-threads","Threads","03"),
        ("04-scan","Scan","04"),("05-review-groups","Review","05"),("06-review-confirm","Confirm","06"),
        ("07-complete","Complete","07"),
    ]
    x0=102; y0=248; w=238; h=118; gap=20
    for i,(slug,title,step) in enumerate(first):
        x=x0+i*(w+gap)
        card_with_thumb(img,d,x,y0,w,h,slug,title,step, accent=(i==4))
        if i < len(first)-1:
            arrow(d,x+w+4,y0+h//2,x+w+gap-4,y0+h//2)
    # daily cards
    daily=[("08-home","Home","08"),("09-activity","Activity","09"),("10-filters","Filters","10"),("11-detail","Detail","11"),("12-category","Correct","12"),("13-insights","Insights","13")]
    y0=486
    for i,(slug,title,step) in enumerate(daily):
        x=x0+i*(w+gap)
        card_with_thumb(img,d,x,y0,w,h,slug,title,step, accent=(i==4))
        if i < len(daily)-1:
            arrow(d,x+w+4,y0+h//2,x+w+gap-4,y0+h//2)
    # manage cards
    manage=[("14-settings","Settings","14"),("15-instruments","Instruments","15"),("16-export","Export","16")]
    y0=724
    for i,(slug,title,step) in enumerate(manage):
        x=x0+i*(w+gap)
        card_with_thumb(img,d,x,y0,w,h,slug,title,step)
        if i < len(manage)-1:
            arrow(d,x+w+4,y0+h//2,x+w+gap-4,y0+h//2)
    # guardrail cards (no thumbs)
    guards=[
        ("Permission denied","Explain the value, offer “Not now”; the app can still open, but import waits for access."),
        ("6 instruments pending","Keep importing; tag affected transactions. A Home banner brings the user back."),
        ("6 unparsed SMS","Do not guess. Surface the count in Import and let the user inspect the source text."),
        ("Mixed currencies","Never add SAR and EGP. Currency is explicit on Home, Activity and Insights."),
    ]
    y0=962
    gw=430; gh=104
    for i,(title,body) in enumerate(guards):
        x=102+i*(gw+24)
        d.rounded_rectangle([x,y0,x+gw,y0+gh], radius=14, fill=(255,255,255), outline=(220,213,199), width=1)
        d.ellipse([x+12,y0+14,x+24,y0+26], fill=(201,123,46))
        d.text((x+32,y0+12), title, font=font(12,True), fill=(20,30,38))
        # wrap body
        lines=wrap(d,body,font(9.5),gw-24)
        yy=y0+34
        for line in lines[:4]:
            d.text((x+12,yy),line,font=font(9.5),fill=(95,108,102)); yy+=14
    # outcome card in the manage lane
    ox, oy, ow, oh = 900, 724, 998, 118
    d.rounded_rectangle([ox,oy,ox+ow,oy+oh], radius=14, fill=(234,244,240), outline=(190,220,208), width=1)
    d.text((ox+20,oy+16), "AFTER SETUP", font=font(10,True), fill=(14,107,92))
    stats=[("10 sec","monthly check"),("1 tap","category correction"),("0","SMS leaves phone")]
    for i,(big,small) in enumerate(stats):
        sx=ox+28+i*320
        d.text((sx,oy+40), big, font=font(24,True), fill=(14,107,92))
        d.text((sx,oy+74), small, font=font(10), fill=(72,92,84))
    d.text((ox+28,oy+94),"The user sees the month, opens one transaction, and fixes a mistake without leaving the app.",font=font(9),fill=(90,110,102))
    # footer
    d.text((80,1222),"Outcome: the user can answer “what did I spend?” in under 10 seconds, correct a wrong category once, and never wonder whether SMS left the phone.",font=font(13),fill=(80,95,90))
    img.save(ROOT/"user-scenario.png",quality=95)
    print("wrote user-scenario", img.size)

compose_scenario()
