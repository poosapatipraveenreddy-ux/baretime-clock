#!/usr/bin/env python3
"""Write <web_dir>/site.json: version + SHA-256 of every file.
The Android app uses it to download and verify new designer files.
Usage: python3 tools/make_site_json.py web"""
import hashlib, json, os, re, sys

root = sys.argv[1] if len(sys.argv) > 1 else 'web'
def read(p):
    with open(os.path.join(root, p), encoding='utf-8') as f: return f.read()

version = re.search(r"const VERSION = '([^']+)'", read('sw.js')).group(1)
bridge = re.search(r"MIN_BRIDGE = (\d+)", read('android-bridge.js')).group(1)
files = []
for d, _, names in os.walk(root):
    for n in sorted(names):
        full = os.path.join(d, n)
        rel = os.path.relpath(full, root).replace(os.sep, '/')
        if rel == 'site.json' or n.startswith('.'): continue
        data = open(full, 'rb').read()
        files.append({'path': rel, 'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data)})
files.sort(key=lambda f: f['path'])
out = {'version': version, 'minBridge': int(bridge), 'files': files}
with open(os.path.join(root, 'site.json'), 'w') as f: json.dump(out, f, indent=1)
print(f'site.json: version {version}, {len(files)} files')
