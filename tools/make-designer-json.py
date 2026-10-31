#!/usr/bin/env python3
"""Writes designer.json next to the website files.

The Android app downloads this small file to learn whether a newer designer exists
(and, optionally, whether a newer APK was released).
The designer version is the VERSION constant in web/sw.js, so you only bump ONE number.

usage: make-designer-json.py <web-dir> [<release-tag e.g. v1.0.3> <release-page-url>]
"""
import json, re, sys, pathlib


def code_from_name(name):
    """1.2.3 -> 1002003. The Gradle build uses the same rule, so the numbers always agree."""
    a, b, c = (int(x) for x in re.match(r"(\d+)\.(\d+)\.(\d+)", name).groups())
    return a * 1_000_000 + b * 1_000 + c


web = pathlib.Path(sys.argv[1])
sw = (web / "sw.js").read_text(encoding="utf-8")
m = re.search(r"VERSION\s*=\s*'([^']+)'", sw)
if not m:
    sys.exit("VERSION not found in sw.js")
out = {"version": m.group(1), "files": ["index.html", "app.js", "styles.css"]}
if len(sys.argv) >= 4 and re.match(r"v\d+\.\d+\.\d+", sys.argv[2]):
    name = sys.argv[2][1:]
    out["apk"] = {"versionCode": code_from_name(name), "versionName": name, "url": sys.argv[3]}
(web / "designer.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
print("designer.json ->", out)
