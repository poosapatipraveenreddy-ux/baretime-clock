#!/usr/bin/env python3
"""Download the designer's Google Fonts as TTF files into the Android assets,
and write fonts/index.json. Run by the build workflow (needs internet).
Usage: python3 tools/fetch_fonts.py"""
import json, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
APP_JS = os.path.join(HERE, '..', 'web', 'app.js')
OUT = os.path.join(HERE, '..', 'android', 'app', 'src', 'main', 'assets', 'fonts')
# An old Android user agent makes Google serve plain .ttf files.
UA = 'Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; Nexus 5 Build/KOT49H) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30'

def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=30) as r: return r.read()

def list_fonts(src):
    fonts = []
    for m in re.finditer(r"\{ n: '([^']+)', g: 1, w: (W\((\d+), (\d+)(?:, (\d+))?\)|\[[^\]]*\])", src):
        name, spec = m.group(1), m.group(2)
        if spec.startswith('W('):
            a, b, s = int(m.group(3)), int(m.group(4)), int(m.group(5) or 100)
            weights = list(range(a, b + 1, s))
        else:
            weights = [int(x) for x in re.findall(r'\d+', spec)]
        fonts.append((name, weights))
    return fonts

def parse_css(css):
    """-> list of (weight, url) from @font-face blocks."""
    out = []
    for blk in re.findall(r'@font-face\s*\{[^}]*\}', css):
        w = re.search(r'font-weight:\s*(\d+)', blk)
        u = re.search(r'url\(([^)]+)\)', blk)
        if u: out.append((int(w.group(1)) if w else 400, u.group(1).strip('\'"')))
    return out

def main():
    src = open(APP_JS, encoding='utf-8').read()
    os.makedirs(OUT, exist_ok=True)
    index, failed = {}, []
    for name, weights in list_fonts(src):
        fam = name.replace(' ', '+') + (':wght@' + ';'.join(map(str, weights)) if len(weights) > 1 else '')
        slug = name.replace(' ', '')
        try:
            faces = parse_css(get(f'https://fonts.googleapis.com/css2?family={fam}&display=swap').decode())
            if not faces: raise RuntimeError('no @font-face found')
            by_url = {}
            for w, u in faces: by_url.setdefault(u, []).append(w)
            entries = []
            for u, ws in by_url.items():
                variable = len(ws) > 1
                fn = f'{slug}-var.ttf' if variable else f'{slug}-{ws[0]}.ttf'
                path = os.path.join(OUT, fn)
                if not os.path.exists(path): open(path, 'wb').write(get(u))
                for w in ws: entries.append({'w': w, 'f': fn, 'v': 1 if variable else 0})
            index[name] = sorted(entries, key=lambda e: e['w'])
            print('ok  ', name, len(entries), 'weights')
        except Exception as e:
            failed.append(name); print('FAIL', name, e, file=sys.stderr)
    json.dump(index, open(os.path.join(OUT, 'index.json'), 'w'), indent=1)
    print(f'{len(index)} fonts written, {len(failed)} failed', failed or '')
    if len(index) == 0: sys.exit(1)

if __name__ == '__main__': main()
