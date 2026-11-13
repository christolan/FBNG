#!/usr/bin/env python3
"""Build the cover index from the bundled DAT and the public FBNeo artwork tree."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DAT = ROOT / "app/src/main/assets/fbneo/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat"
OUTPUT = ROOT / "app/src/main/assets/fbneo/cover-index.json"
REPOSITORY = "libretro-thumbnails/FBNeo_-_Arcade_Games"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tree", type=Path, help="Use a previously downloaded GitHub tree response")
    args = parser.parse_args()
    if args.tree:
        tree = json.loads(args.tree.read_text())
    else:
        request = urllib.request.Request(
            f"https://api.github.com/repos/{REPOSITORY}/git/trees/master?recursive=1",
            headers={"User-Agent": "FBNG-cover-index"},
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            tree = json.load(response)
    if tree.get("truncated") or not tree.get("tree"):
        raise SystemExit("Refusing an incomplete artwork tree")
    paths = {item["path"] for item in tree["tree"] if item["type"] == "blob"}
    games = {g.get("name"): g for g in ET.parse(DAT).getroot().findall("game") if g.get("isbios") != "yes"}
    assets = []
    asset_ids = {}
    sets = {}
    for name, game in sorted(games.items()):
        seen = set()
        while game is not None and game.get("name") not in seen:
            seen.add(game.get("name"))
            title = re.sub(r'[&*/:`<>?\\|\"]', "_", game.findtext("description", ""))
            path = f"Named_Boxarts/{title}.png"
            if path in paths:
                if path not in asset_ids:
                    asset_ids[path] = len(assets)
                    assets.append(path)
                sets[name] = asset_ids[path]
                break
            game = games.get(game.get("cloneof"))
    index = {
        "repository": REPOSITORY,
        "sourceRevision": tree["sha"],
        "datSha256": hashlib.sha256(DAT.read_bytes()).hexdigest(),
        "assets": assets,
        "sets": sets,
    }
    OUTPUT.write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"{len(sets)} / {len(games)} sets, {len(assets)} distinct covers, {OUTPUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
