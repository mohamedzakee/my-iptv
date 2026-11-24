"""TV guide helper.

prepare <epg_repo> <out.xml>   pick one guide source per channel from the EPG grabber repo
convert <guide.xml> <out.json> turn XMLTV into a small now/next file for the app
"""
import glob, json, os, re, sys, time
import xml.etree.ElementTree as ET
from datetime import datetime

# Sources that tend to have good data for our channels and work from GitHub's servers.
PREFERRED = ["tataplay.com", "dishtv.in", "airtelxstream.in", "tvtv.us", "tvpassport.com", "i.mjh.nz",
             "sky.com", "freeview.co.uk", "shahid.mbc.net", "content.astro.com.my", "starhubtvplus.com",
             "derana.lk", "plex.tv", "zee5.com", "singtel.com", "nowplayer.now.com"]


def wanted_ids():
    groups = json.load(open(os.path.join(os.path.dirname(__file__), "channels.json"), encoding="utf-8"))
    return {cid for ids in groups.values() for cid in ids}


def prepare(repo, out):
    ids, options = wanted_ids(), {}
    for f in glob.glob(f"{repo}/sites/*/*.channels.xml"):
        for el in ET.parse(f).getroot().iter("channel"):
            cid = (el.get("xmltv_id") or "").split("@")[0]
            if cid in ids:
                options.setdefault(cid, []).append(el)
    rank = lambda el: PREFERRED.index(el.get("site")) if el.get("site") in PREFERRED else len(PREFERRED)
    root = ET.Element("channels")
    for cid, els in sorted(options.items()):
        best = min(els, key=rank)
        best.set("xmltv_id", cid)  # one guide per channel, whatever feed it came from
        root.append(best)
    ET.ElementTree(root).write(out, encoding="utf-8", xml_declaration=True)
    print(f"{len(options)} of {len(ids)} channels have a guide source")


def ts(s):
    return int(datetime.strptime(s.strip(), "%Y%m%d%H%M%S %z").timestamp())


def convert(guide, out):
    now = time.time()
    progs = {}
    for p in ET.parse(guide).getroot().iter("programme"):
        try:
            start, stop = ts(p.get("start")), ts(p.get("stop"))
        except (TypeError, ValueError):
            continue
        if stop < now - 3600 or start > now + 36 * 3600:
            continue
        title = (p.findtext("title") or "").strip()
        if title and not re.search(r"guide is not available|no information|to be announced", title, re.I):
            cid = p.get("channel", "").split("@")[0]
            progs.setdefault(cid, []).append([start, stop, re.sub(r"\s+", " ", title)[:80]])
    for v in progs.values():
        v.sort()
    json.dump({"generated": int(now), "channels": progs}, open(out, "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    print(f"guide for {len(progs)} channels, {sum(map(len, progs.values()))} programmes")


if __name__ == "__main__":
    {"prepare": prepare, "convert": convert}[sys.argv[1]](sys.argv[2], sys.argv[3])
