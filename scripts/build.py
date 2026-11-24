"""Build Tamil_SriLanka_Cricket.m3u from iptv-org, keeping only streams that respond."""
import csv, glob, json, os, re, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

SRC = sys.argv[1]          # folder with cloned iptv-org/iptv and iptv-org/database
OUT = sys.argv[2]
MAX_PER_CHANNEL = 2
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

# group -> channel ids in iptv-org database, in display order. Edit scripts/channels.json to change.
CHANNELS = json.load(open(os.path.join(os.path.dirname(__file__), "channels.json"), encoding="utf-8"))
# drop foreign-language versions of the same channel
SKIP = re.compile(r"Latin America|Spain|Italy|Turkiye|Thailand|Espa|Arabia|Anim\u00e9", re.I)

db = os.path.join(SRC, "database/data")
names = {r["id"]: r["name"] for r in csv.DictReader(open(f"{db}/channels.csv", encoding="utf-8"))}
def logo_score(r):
    """Prefer clean transparent logos (Gracenote/Wikimedia), PNG/WebP, landscape, reasonably large."""
    url, fmt = r["url"], r["format"].upper()
    try:
        w, h = int(r["width"] or 0), int(r["height"] or 0)
    except ValueError:
        w = h = 0
    if fmt in ("JPEG", "JPG", "SVG") or "LANDSCAPE_169" in url:
        return -1
    s = 0
    if "tmsimg.com" in url: s += 4
    if "wikimedia.org" in url or "logopedia" in url: s += 3
    if r.get("in_use", "TRUE") == "TRUE": s += 1
    if h and 1.0 <= w / h <= 3.2: s += 2
    return s + min(w, 800) / 800

logos = {}
for r in csv.DictReader(open(f"{db}/logos.csv", encoding="utf-8")):
    sc = logo_score(r)
    if sc >= 0 and sc > logos.get(r["channel"], (-1, ""))[0]:
        logos[r["channel"]] = (sc, r["url"])
logos = {k: v[1] for k, v in logos.items()}

def parse(path):
    cur = None
    for ln in open(path, encoding="utf-8", errors="ignore").read().splitlines():
        if ln.startswith("#EXTINF"):
            cur = {"inf": ln, "opts": []}
        elif ln.startswith("#") and cur and not ln.startswith("#EXTM3U"):
            cur["opts"].append(ln)
        elif ln.strip() and cur:
            cur["url"] = ln.strip(); yield cur; cur = None

streams = {}
for f in sorted(glob.glob(f"{SRC}/iptv/streams/*.m3u")):
    for e in parse(f):
        m = re.search(r'tvg-id="([^"@]*)(@[^"]*)?"', e["inf"])
        if m:
            e["name"] = e["inf"].split(",", 1)[1].strip()
            streams.setdefault(m.group(1), []).append(e)
            if m.group(2):  # also index by feed, e.g. "WOWKidz.in@Tamil", to pick one language
                streams.setdefault(m.group(1) + m.group(2), []).append(e)

def works(e):
    headers = {"User-Agent": UA}
    for o in e["opts"]:
        m = re.match(r"#EXTVLCOPT:http-(user-agent|referrer)=(.*)", o)
        if m:
            headers["User-Agent" if m.group(1) == "user-agent" else "Referer"] = m.group(2)
    try:
        req = urllib.request.Request(e["url"], headers=headers)
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status == 200 and b"#EXTM3U" in r.read(4096)
    except Exception:
        return False

wanted = [(g, cid, e) for g, ids in CHANNELS.items() for cid in ids for e in streams.get(cid, [])]
with ThreadPoolExecutor(32) as ex:
    ok = list(ex.map(lambda x: works(x[2]), wanted))

lines, report, count = ["#EXTM3U"], [], {}
listed = {g: [] for g in CHANNELS}
for (g, cid, e), good in zip(wanted, ok):
    report.append(f"{'OK  ' if good else 'DEAD'} {g:18} {e['name']}  {e['url']}")
    if not good or count.get(cid, 0) >= MAX_PER_CHANNEL or "Geo-blocked" in e["name"] or SKIP.search(e["name"]):
        continue
    count[cid] = count.get(cid, 0) + 1
    name = re.sub(r"\s*\[.*?\]", "", e["name"]).strip()
    if count[cid] == 1:
        listed[g].append(names.get(cid.split("@")[0], name))
    lines.append(f'#EXTINF:-1 tvg-id="{cid}" tvg-logo="{logos.get(cid.split("@")[0], "")}" group-title="{g}",{name}')
    lines += e["opts"] + [e["url"]]

open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
open(os.path.join(os.path.dirname(OUT) or ".", "report.txt"), "w", encoding="utf-8").write("\n".join(report) + "\n")
print(f"{sum(count.values())} working streams for {len(count)} channels (tested {len(wanted)})")

# README with the current channel list
from datetime import datetime, timezone, timedelta
now = datetime.now(timezone(timedelta(hours=5, minutes=30))).strftime("%d %b %Y, %I:%M %p")
md = ["# My IPTV", "",
      "Tamil, Sri Lankan, Cricket, English kids and Islamic TV channels, filtered from "
      "[iptv-org/iptv](https://github.com/iptv-org/iptv).", "",
      "## Playlist URL", "", "```", "https://raw.githubusercontent.com/mohamedzakee/my-iptv/main/tv.m3u", "```", "",
      "Paste this link into VLC (Media \u2192 Open Network Stream), TiviMate or IPTV Smarters.", "",
      "## Android TV app", "",
      "Download: [my-iptv.apk](https://github.com/mohamedzakee/my-iptv/releases/download/app/my-iptv.apk) "
      "(built automatically from the `android/` folder). Install it on the TV with the Downloader app.", "",
      f"## Channels ({sum(len(v) for v in listed.values())} working)", "",
      f"_Last checked: {now} (Sri Lanka time). Updated automatically every day; dead streams are dropped "
      "and up to 2 working streams are kept per channel._", ""]
for g, chs in listed.items():
    md += [f"### {g} ({len(chs)})", ""]
    md += [f"- {c}" for c in chs] if chs else ["_No working streams right now._"]
    md.append("")
md += ["## Notes", "",
       "- These are free public streams found by the iptv-org community. Some may stop working or play only at certain hours.",
       "- See [report.txt](report.txt) for the result of every stream tested.",
       "- Channel list is set in [scripts/build.py](scripts/build.py)."]
open(os.path.join(os.path.dirname(OUT) or ".", "README.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")
