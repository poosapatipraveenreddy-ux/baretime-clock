'use strict';
(() => {
/* =====================================================================
   BareTime — static, offline-first clock designer.
   No backend. Settings live in the browser; themes can be shared as
   links or JSON files. See THEME-SPEC.md for the portable theme format.
   ===================================================================== */

/* ---------- helpers ---------- */
const $ = (s, r = document) => r.querySelector(s);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
function el(tag, props = {}, ...kids) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') n.className = v;
    else if (k === 'style') n.style.cssText = v;
    else if (k.startsWith('on')) n[k] = v;
    else n.setAttribute(k, v);
  }
  kids.flat().forEach(c => { if (c != null && c !== false) n.append(c.nodeType ? c : document.createTextNode(c)); });
  return n;
}
const readJSON = (k, d = null) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
const writeJSON = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage full or blocked */ } };
const hexToRgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const rgba = (h, a) => { const [r, g, b] = hexToRgb(h); return `rgba(${r},${g},${b},${a})`; };

const hashParams = new URLSearchParams(location.hash.slice(1));
const EMBED = hashParams.get('embed') === '1';
if (EMBED) document.body.classList.add('embed');

/* ---------- data: fonts, wallpapers, time zones ---------- */
const FB = { sans: 'system-ui,-apple-system,"Segoe UI",Roboto,sans-serif', serif: 'Georgia,"Times New Roman",serif', mono: 'ui-monospace,SFMono-Regular,Menlo,Consolas,monospace' };
const W = (a, b, s = 100) => { const o = []; for (let i = a; i <= b; i += s) o.push(i); return o; };
const FONTS = [
  { n: 'Inter', g: 1, w: W(200, 900), c: 'Minimal', fb: 'sans' },
  { n: 'Poppins', g: 1, w: [300, 400, 600, 800], c: 'Minimal', fb: 'sans' },
  { n: 'Montserrat', g: 1, w: W(200, 900), c: 'Minimal', fb: 'sans' },
  { n: 'Raleway', g: 1, w: W(200, 900), c: 'Minimal', fb: 'sans' },
  { n: 'Space Grotesk', g: 1, w: [300, 400, 500, 700], c: 'Minimal', fb: 'sans' },
  { n: 'System UI', g: 0, w: W(200, 800), c: 'Minimal', fb: 'sans', stack: FB.sans },
  { n: 'Playfair Display', g: 1, w: W(400, 900), c: 'Elegant', fb: 'serif' },
  { n: 'Cormorant Garamond', g: 1, w: W(300, 700), c: 'Elegant', fb: 'serif' },
  { n: 'Lora', g: 1, w: W(400, 700), c: 'Elegant', fb: 'serif' },
  { n: 'DM Serif Display', g: 1, w: [400], c: 'Elegant', fb: 'serif' },
  { n: 'Orbitron', g: 1, w: W(400, 900), c: 'Digital', fb: 'mono' },
  { n: 'Share Tech Mono', g: 1, w: [400], c: 'Digital', fb: 'mono' },
  { n: 'Audiowide', g: 1, w: [400], c: 'Digital', fb: 'sans' },
  { n: 'Syncopate', g: 1, w: [400, 700], c: 'Digital', fb: 'sans' },
  { n: 'Major Mono Display', g: 1, w: [400], c: 'Digital', fb: 'mono' },
  { n: 'VT323', g: 1, w: [400], c: 'Digital', fb: 'mono' },
  { n: 'System Mono', g: 0, w: [400, 700], c: 'Digital', fb: 'mono', stack: FB.mono },
  { n: 'Bebas Neue', g: 1, w: [400], c: 'Bold', fb: 'sans' },
  { n: 'Oswald', g: 1, w: W(200, 700), c: 'Bold', fb: 'sans' },
  { n: 'Righteous', g: 1, w: [400], c: 'Bold', fb: 'sans' },
  { n: 'Rubik', g: 1, w: W(300, 900), c: 'Bold', fb: 'sans' },
  { n: 'Comfortaa', g: 1, w: W(300, 700), c: 'Rounded', fb: 'sans' },
  { n: 'Nunito', g: 1, w: W(200, 900), c: 'Rounded', fb: 'sans' },
  { n: 'Quicksand', g: 1, w: W(300, 700), c: 'Rounded', fb: 'sans' },
  { n: 'Press Start 2P', g: 1, w: [400], c: 'Retro', fb: 'mono' },
  { n: 'Pacifico', g: 1, w: [400], c: 'Retro', fb: 'sans' }
];
const fontOf = n => FONTS.find(f => f.n === n) || FONTS[0];
const fontStack = f => f.stack || `"${f.n}",${FB[f.fb]}`;
const fontHref = f => `https://fonts.googleapis.com/css2?family=${f.n.replace(/ /g, '+')}${f.w.length > 1 ? ':wght@' + f.w.join(';') : ''}&display=swap`;

const WALLS = [
  { id: 'aurora', n: 'Aurora', dark: true, css: 'radial-gradient(60% 55% at 18% 22%,rgba(62,230,180,.55),transparent 62%),radial-gradient(55% 60% at 82% 28%,rgba(122,92,255,.75),transparent 62%),linear-gradient(160deg,#0b1030,#12204a 60%,#0a0f24)' },
  { id: 'sunset', n: 'Sunset', dark: true, css: 'linear-gradient(180deg,#1f1147 0%,#7b2d8e 38%,#ff6a5c 72%,#ffc371 100%)' },
  { id: 'ocean', n: 'Ocean', dark: true, css: 'linear-gradient(170deg,#021b3a,#05508c 55%,#3fb6d8)' },
  { id: 'forest', n: 'Forest', dark: true, css: 'linear-gradient(160deg,#08210f,#1b5e3a 55%,#8ed081)' },
  { id: 'violet', n: 'Violet', dark: true, css: 'radial-gradient(70% 60% at 30% 20%,#b06cff,transparent 65%),radial-gradient(60% 60% at 80% 80%,#ff5ea8,transparent 65%),#1a0b3a' },
  { id: 'midnight', n: 'Midnight', dark: true, css: 'linear-gradient(180deg,#05060f,#141a3a)' },
  { id: 'noir', n: 'Black', dark: true, css: '#000' },
  { id: 'dune', n: 'Dune', dark: false, css: 'linear-gradient(160deg,#f6e7cf,#e8b98a 60%,#c9805a)' },
  { id: 'cloud', n: 'Cloud', dark: false, css: 'linear-gradient(160deg,#f7f9fc,#c9d6ea)' }
];
const wallOf = id => WALLS.find(w => w.id === id);

const TZ = [
  ['local', 'Device time'], ['UTC', 'UTC'], ['Asia/Kolkata', 'India (Kolkata)'], ['Asia/Dubai', 'Dubai'],
  ['Asia/Singapore', 'Singapore'], ['Asia/Tokyo', 'Tokyo'], ['Australia/Sydney', 'Sydney'], ['Europe/London', 'London'],
  ['Europe/Paris', 'Paris / Berlin'], ['Europe/Moscow', 'Moscow'], ['Africa/Johannesburg', 'Johannesburg'],
  ['America/Sao_Paulo', 'São Paulo'], ['America/New_York', 'New York'], ['America/Chicago', 'Chicago'], ['America/Los_Angeles', 'Los Angeles']
];

/* ---------- settings schema ---------- */
const locale12 = (() => { try { return !!new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hour12; } catch { return false; } })();

const DEFAULTS = {
  mode: 'transparent', font: 'Inter', weight: 300, size: 100, spacing: 0, lh: 100, layout: 'single', align: 'center',
  offX: 0, offY: 0, autoFit: true,
  color: '#ffffff', gradText: false, color2: '#ffd1ff', opacity: 100,
  shadow: 35, glow: 0, outline: 0, outlineColor: '#000000',
  bgType: 'gradient', bg1: '#6d5dfc', bg2: '#ff6bcb', angle: 135, cardOpacity: 100, radius: 32, pad: 40,
  glassTint: 12, blur: 24,
  hour12: locale12, ampm: true, pad0: true, seconds: false, secBig: false, blink: false, tz: 'local',
  dateShow: true, dateFmt: 'long', weekday: true, dateSize: 22, dateOpacity: 80, dateSpacing: 2,
  wp: 'aurora', wpDim: 0, wpBlur: 0
};

const isMode = (...m) => s => m.includes(s.mode);
const TABS = {
  style: [
    { t: 'seg', k: 'mode', label: 'Display mode', opts: [['transparent', 'Transparent'], ['color', 'Color'], ['glass', 'Glass']] },
    { t: 'hint', text: 'Transparent shows only the numbers over your wallpaper.', show: isMode('transparent') },
    { t: 'h', label: 'Card', show: isMode('color', 'glass') },
    { t: 'seg', k: 'bgType', label: 'Fill', opts: [['solid', 'Solid'], ['gradient', 'Gradient']], show: isMode('color') },
    { t: 'color', k: 'bg1', label: 'Card color', show: isMode('color') },
    { t: 'color', k: 'bg2', label: 'Second color', show: s => s.mode === 'color' && s.bgType === 'gradient' },
    { t: 'range', k: 'angle', label: 'Gradient angle', min: 0, max: 360, unit: '°', show: s => s.mode === 'color' && s.bgType === 'gradient' },
    { t: 'range', k: 'cardOpacity', label: 'Card opacity', min: 10, max: 100, unit: '%', show: isMode('color') },
    { t: 'range', k: 'glassTint', label: 'Glass tint', min: 0, max: 60, unit: '%', show: isMode('glass') },
    { t: 'range', k: 'blur', label: 'Glass blur', min: 0, max: 60, unit: 'px', show: isMode('glass') },
    { t: 'range', k: 'radius', label: 'Corner radius', min: 0, max: 100, show: isMode('color', 'glass') },
    { t: 'range', k: 'pad', label: 'Padding', min: 0, max: 120, show: isMode('color', 'glass') },
    { t: 'h', label: 'Text color' },
    { t: 'color', k: 'color', label: 'Color' },
    { t: 'toggle', k: 'gradText', label: 'Gradient text' },
    { t: 'color', k: 'color2', label: 'Gradient end color', show: s => s.gradText },
    { t: 'range', k: 'opacity', label: 'Text opacity', min: 10, max: 100, unit: '%' },
    { t: 'h', label: 'Readability & effects' },
    { t: 'range', k: 'shadow', label: 'Shadow', min: 0, max: 100 },
    { t: 'range', k: 'glow', label: 'Glow', min: 0, max: 100 },
    { t: 'range', k: 'outline', label: 'Outline', min: 0, max: 12 },
    { t: 'color', k: 'outlineColor', label: 'Outline color', show: s => s.outline > 0 }
  ],
  text: [
    { t: 'font', k: 'font', label: 'Font' },
    { t: 'weight', k: 'weight', label: 'Weight' },
    { t: 'h', label: 'Size & spacing' },
    { t: 'range', k: 'size', label: 'Size', min: 20, max: 300, unit: '%' },
    { t: 'toggle', k: 'autoFit', label: 'Keep inside screen' },
    { t: 'range', k: 'spacing', label: 'Letter spacing', min: -10, max: 30 },
    { t: 'range', k: 'lh', label: 'Line height', min: 60, max: 140, unit: '%' },
    { t: 'h', label: 'Layout' },
    { t: 'seg', k: 'layout', label: 'Time layout', opts: [['single', 'One line'], ['stacked', 'Stacked']] },
    { t: 'seg', k: 'align', label: 'Alignment', opts: [['left', 'Left'], ['center', 'Center'], ['right', 'Right']] },
    { t: 'range', k: 'offX', label: 'Move left / right', min: -40, max: 40 },
    { t: 'range', k: 'offY', label: 'Move up / down', min: -40, max: 40 }
  ],
  time: [
    { t: 'seg', k: 'hour12', label: 'Hours', opts: [[false, '24-hour'], [true, '12-hour']] },
    { t: 'toggle', k: 'ampm', label: 'Show AM / PM', show: s => s.hour12 },
    { t: 'toggle', k: 'pad0', label: 'Leading zero' },
    { t: 'toggle', k: 'seconds', label: 'Show seconds' },
    { t: 'toggle', k: 'secBig', label: 'Large seconds', show: s => s.seconds },
    { t: 'toggle', k: 'blink', label: 'Blinking colon' },
    { t: 'select', k: 'tz', label: 'Time zone', opts: TZ },
    { t: 'h', label: 'Date' },
    { t: 'toggle', k: 'dateShow', label: 'Show date' },
    { t: 'select', k: 'dateFmt', label: 'Format', show: s => s.dateShow, opts: [['long', 'Long — 27 October 2026'], ['medium', 'Short — 27 Oct'], ['dmy', 'Numeric — 27/10/2026'], ['mdy', 'Numeric — 10/27/2026'], ['iso', 'ISO — 2026-10-27']] },
    { t: 'toggle', k: 'weekday', label: 'Include weekday', show: s => s.dateShow },
    { t: 'range', k: 'dateSize', label: 'Date size', min: 8, max: 80, unit: '%', show: s => s.dateShow },
    { t: 'range', k: 'dateOpacity', label: 'Date opacity', min: 10, max: 100, unit: '%', show: s => s.dateShow },
    { t: 'range', k: 'dateSpacing', label: 'Date letter spacing', min: 0, max: 30, show: s => s.dateShow }
  ],
  wall: [
    { t: 'wall', k: 'wp' },
    { t: 'range', k: 'wpDim', label: 'Dim wallpaper', min: 0, max: 80, unit: '%' },
    { t: 'range', k: 'wpBlur', label: 'Blur wallpaper', min: 0, max: 30, unit: 'px' }
  ]
};

/* validation metadata: used for share links, imports and saved data */
const META = {};
Object.values(TABS).flat().forEach(c => { if (c.k) META[c.k] = c; });
function valid(c, v) {
  switch (c.t) {
    case 'seg': case 'select': return c.opts.some(o => o[0] === v);
    case 'range': return typeof v === 'number' && isFinite(v);
    case 'color': return typeof v === 'string' && /^#[0-9a-f]{6}$/i.test(v);
    case 'font': return FONTS.some(f => f.n === v);
    case 'weight': return typeof v === 'number' && isFinite(v);
    case 'wall': return v === 'none' || v === 'custom' || !!wallOf(v);
    default: return typeof v === 'boolean';
  }
}
function sanitize(o) {
  const out = { ...DEFAULTS };
  if (!o || typeof o !== 'object') return out;
  for (const k of Object.keys(DEFAULTS)) {
    const c = META[k], v = o[k];
    if (v === undefined || !c || typeof v !== typeof DEFAULTS[k] || !valid(c, v)) continue;
    out[k] = typeof v === 'number' && c.min !== undefined ? clamp(v, c.min, c.max) : v;
  }
  const f = fontOf(out.font);
  out.weight = f.w.reduce((a, b) => Math.abs(b - out.weight) < Math.abs(a - out.weight) ? b : a);
  return out;
}

const PRESETS = [
  { n: 'Bare White', s: {} },
  { n: 'Glass Dawn', s: { mode: 'glass', font: 'Poppins', weight: 300, wp: 'sunset', glassTint: 14, blur: 26, radius: 40, pad: 45, dateSize: 20 } },
  { n: 'Nightstand', s: { font: 'Share Tech Mono', weight: 400, color: '#ff4d4d', wp: 'noir', glow: 55, shadow: 0, seconds: true, dateOpacity: 60, size: 110 } },
  { n: 'Candy Card', s: { mode: 'color', font: 'Righteous', weight: 400, bgType: 'gradient', bg1: '#ff6bcb', bg2: '#ffb347', angle: 135, radius: 45, pad: 50, shadow: 25, wp: 'violet', dateSize: 24 } },
  { n: 'Serif Calm', s: { font: 'Cormorant Garamond', weight: 500, color: '#3b2a1d', shadow: 0, wp: 'dune', layout: 'stacked', lh: 85, dateOpacity: 75, dateSize: 20 } },
  { n: 'Stacked Bold', s: { font: 'Bebas Neue', weight: 400, layout: 'stacked', lh: 80, size: 130, spacing: 2, wp: 'ocean', shadow: 40, dateSize: 18 } },
  { n: 'Retro Arcade', s: { font: 'Press Start 2P', weight: 400, color: '#39ff14', glow: 60, shadow: 0, wp: 'noir', size: 70, spacing: 2, dateSize: 22 } },
  { n: 'Neon Future', s: { font: 'Orbitron', weight: 700, color: '#00e5ff', glow: 70, shadow: 0, wp: 'midnight', spacing: 4, dateSize: 18, seconds: true } },
  { n: 'Paper', s: { mode: 'color', font: 'Lora', weight: 500, bgType: 'solid', bg1: '#f6f1e7', radius: 20, pad: 50, color: '#24201a', shadow: 0, wp: 'cloud', dateSize: 20 } },
  { n: 'Forest Glass', s: { mode: 'glass', font: 'Quicksand', weight: 600, wp: 'forest', color: '#eaffea', glassTint: 10, blur: 30, radius: 60, pad: 50 } },
  { n: 'Gradient Type', s: { font: 'Montserrat', weight: 800, gradText: true, color: '#ff8a00', color2: '#e52e71', wp: 'midnight', shadow: 0, spacing: -2, size: 115 } },
  { n: 'Mono Minimal', s: { font: 'Space Grotesk', weight: 300, wp: 'noir', shadow: 0, spacing: -3, dateOpacity: 55 } }
];

/* ---------- state ---------- */
const KEY = 'baretime.settings.v1', TKEY = 'baretime.themes.v1', PKEY = 'baretime.prefs.v1', LKEY = 'baretime.wlum.v1';
let S = sanitize(readJSON(KEY));
let themes = readJSON(TKEY, []).filter(t => t && typeof t.name === 'string' && t.s);
const prefs = readJSON(PKEY, {});
let linked = false;

function b64enc(o) { return btoa(unescape(encodeURIComponent(JSON.stringify(o)))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
function b64dec(s) { return JSON.parse(decodeURIComponent(escape(atob(s.replace(/-/g, '+').replace(/_/g, '/'))))); }
if (hashParams.get('t')) {
  try { S = sanitize(b64dec(hashParams.get('t'))); linked = !EMBED; } catch { /* ignore bad link */ }
}

/* ---------- IndexedDB for the custom wallpaper ---------- */
const idb = {
  db: null,
  open() {
    return this.db || (this.db = new Promise((res, rej) => {
      const r = indexedDB.open('baretime', 1);
      r.onupgradeneeded = () => r.result.createObjectStore('kv');
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    }));
  },
  async run(mode, fn) {
    const db = await this.open();
    return new Promise((res, rej) => { const q = fn(db.transaction('kv', mode).objectStore('kv')); q.onsuccess = () => res(q.result); q.onerror = () => rej(q.error); });
  },
  get(k) { return this.run('readonly', s => s.get(k)); },
  set(k, v) { return this.run('readwrite', s => s.put(v, k)); },
  del(k) { return this.run('readwrite', s => s.delete(k)); }
};
let customURL = null;
let customLum = readJSON(LKEY, 0.1);

/* ---------- elements ---------- */
const clock = $('#clock'), timeEl = $('#time'), dateEl = $('#date'), stage = $('#stage'), wall = $('#wall');

/* ---------- rendering ---------- */
function save() { if (!EMBED) writeJSON(KEY, S); }

function apply() {
  const f = fontOf(S.font), st = clock.style;
  const set = (k, v) => st.setProperty(k, v);
  set('--ff', fontStack(f)); set('--fw', S.weight);
  set('--scale', S.size / 100); set('--ls', S.spacing); set('--lh', S.lh / 100);
  set('--ox', S.offX); set('--oy', S.offY);
  set('--c1', S.color); set('--c2', S.color2); set('--op', S.opacity / 100);
  set('--ds', S.dateSize); set('--do', S.dateOpacity); set('--dls', S.dateSpacing);
  set('--dmt', (0.14 / (S.dateSize / 100)).toFixed(3) + 'em');
  set('--pad', S.pad); set('--radius', S.radius); set('--blur', S.blur); set('--tint', S.glassTint / 100);
  set('--ol', S.outline); set('--olc', S.outlineColor);
  const a = S.cardOpacity / 100;
  set('--cardbg', S.bgType === 'solid' ? rgba(S.bg1, a) : `linear-gradient(${S.angle}deg,${rgba(S.bg1, a)},${rgba(S.bg2, a)})`);
  const fx = [];
  if (S.shadow > 0) fx.push(`drop-shadow(0 .03em .06em rgba(0,0,0,${(S.shadow / 100 * 0.85).toFixed(2)}))`);
  if (S.glow > 0) { const g = rgba(S.color, Math.min(1, S.glow / 70)); fx.push(`drop-shadow(0 0 ${(S.glow * 0.004).toFixed(3)}em ${g})`, `drop-shadow(0 0 ${(S.glow * 0.012).toFixed(3)}em ${g})`); }
  set('--filter', fx.length ? fx.join(' ') : 'none');
  clock.className = `m-${S.mode} a-${S.align}${S.layout === 'stacked' ? ' stacked' : ''}${S.gradText ? ' t-grad' : ''}`;
  applyWall();
  lastKey = '';
  render();
}

function applyWall() {
  let id = S.wp;
  if (id === 'custom' && !customURL) id = 'aurora';
  const w = wallOf(id);
  if (id === 'custom') { wall.style.background = `center/cover no-repeat url("${customURL}")`; }
  else if (id === 'none') { wall.style.background = '#0b0b0f'; }
  else { wall.style.background = w.css; }
  wall.style.setProperty('--dim', S.wpDim / 100);
  wall.style.filter = S.wpBlur ? `blur(${S.wpBlur}px)` : 'none';
  document.body.classList.toggle('wall-none', S.wp === 'none');
}

const fmtCache = {};
function timeParts(now) {
  const tz = S.tz === 'local' ? undefined : S.tz;
  const k = tz || 'local';
  const f = fmtCache[k] || (fmtCache[k] = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: 'numeric', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }));
  const o = {};
  f.formatToParts(now).forEach(p => { o[p.type] = p.value; });
  return { h: parseInt(o.hour, 10) % 24, m: o.minute, s: o.second };
}
function dateText(now) {
  const tz = S.tz === 'local' ? undefined : S.tz;
  const loc = navigator.language || 'en';
  const wd = (style) => new Intl.DateTimeFormat(loc, { timeZone: tz, weekday: style }).format(now);
  switch (S.dateFmt) {
    case 'long': return new Intl.DateTimeFormat(loc, { timeZone: tz, weekday: S.weekday ? 'long' : undefined, day: 'numeric', month: 'long', year: 'numeric' }).format(now);
    case 'medium': return new Intl.DateTimeFormat(loc, { timeZone: tz, weekday: S.weekday ? 'short' : undefined, day: 'numeric', month: 'short' }).format(now);
    default: {
      const p = {};
      new Intl.DateTimeFormat('en-US', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now).forEach(x => { p[x.type] = x.value; });
      const body = S.dateFmt === 'iso' ? `${p.year}-${p.month}-${p.day}` : S.dateFmt === 'mdy' ? `${p.month}/${p.day}/${p.year}` : `${p.day}/${p.month}/${p.year}`;
      return (S.weekday ? wd('short') + ', ' : '') + body;
    }
  }
}

let lastKey = '';
function render() {
  const now = new Date(), p = timeParts(now);
  let hh = p.h, ap = '';
  if (S.hour12) { ap = hh >= 12 ? 'PM' : 'AM'; hh = hh % 12 || 12; }
  const h = S.pad0 ? String(hh).padStart(2, '0') : String(hh);
  const sep = `<span class="sep${S.blink ? ' blink' : ''}">:</span>`;
  const apH = S.hour12 && S.ampm ? `<span class="ap">${ap}</span>` : '';
  let html;
  if (S.layout === 'stacked') {
    html = `<span class="hr">${h}</span><span class="mn">${p.m}</span>` + (S.seconds ? `<span class="sec">${p.s}</span>` : '') + apH;
  } else {
    html = `<span class="hr">${h}</span>${sep}<span class="mn">${p.m}</span>` +
      (S.seconds ? (S.secBig ? `${sep}<span class="mn">${p.s}</span>` : `<span class="sec">:${p.s}</span>`) : '') + apH;
  }
  if (html !== timeEl.innerHTML) timeEl.innerHTML = html;
  const dt = S.dateShow ? dateText(now) : '';
  if (dt !== dateEl.textContent) dateEl.textContent = dt;
  clock.setAttribute('aria-label', `${h}:${p.m}${ap ? ' ' + ap : ''}`);
  const key = html.length + '|' + dt.length + '|' + S.autoFit;
  if (key !== lastKey) { lastKey = key; fit(); }
}

function fit() {
  clock.style.setProperty('--fit', 1);
  if (!S.autoFit) return;
  const w = stage.clientWidth, h = stage.clientHeight, cw = clock.offsetWidth, ch = clock.offsetHeight;
  if (!cw || !ch) return;
  const f = Math.min(1, (w * 0.94) / cw, (h * 0.94) / ch);
  clock.style.setProperty('--fit', f.toFixed(4));
}
new ResizeObserver(fit).observe(stage);

let timer;
function tick() {
  clearTimeout(timer);
  render();
  const step = S.seconds ? 1000 : 60000, n = Date.now();
  timer = setTimeout(tick, step - (n % step) + 8);
}
document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });

/* ---------- fonts (loaded from Google Fonts, cached by the service worker) ---------- */
function loadFonts() {
  const order = [fontOf(S.font), ...FONTS.filter(f => f.n !== S.font)];
  order.filter(f => f.g).forEach(f => {
    const l = el('link', { rel: 'stylesheet', href: fontHref(f) });
    l.onload = () => { lastKey = ''; fit(); };
    document.head.append(l);
  });
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { lastKey = ''; fit(); });
}

/* ---------- toast ---------- */
let toastT;
function toast(msg, label, fn, ms = 4200) {
  const t = $('#toast');
  t.replaceChildren(el('span', {}, msg));
  if (label) t.append(el('button', { type: 'button', onclick: () => { fn && fn(); t.classList.remove('show'); } }, label));
  t.classList.add('show');
  clearTimeout(toastT);
  if (ms) toastT = setTimeout(() => t.classList.remove('show'), ms);
}

/* ---------- panel / controls ---------- */
const bound = [];
function snapWeight() {
  const ws = fontOf(S.font).w;
  S.weight = ws.reduce((a, b) => Math.abs(b - S.weight) < Math.abs(a - S.weight) ? b : a);
}
function change(k, v) {
  S[k] = v;
  if (k === 'font') snapWeight();
  linked = false;
  commit();
}
function commit() { save(); apply(); syncUI(); }
function syncUI() {
  bound.forEach(b => {
    if (b.c.k) b.setter(S[b.c.k]);
    if (b.c.show) b.row.hidden = !b.c.show(S);
  });
}

function build(c) {
  let node, setter = () => {};
  switch (c.t) {
    case 'h': node = null; break;
    case 'hint': node = null; break;
    case 'seg': {
      node = el('div', { class: 'seg' });
      c.opts.forEach(([v, l]) => { const b = el('button', { type: 'button', onclick: () => change(c.k, v) }, l); b._v = v; node.append(b); });
      setter = v => [...node.children].forEach(b => b.classList.toggle('on', b._v === v));
      break;
    }
    case 'range': {
      const inp = el('input', { type: 'range', min: c.min, max: c.max, step: c.step || 1, 'aria-label': c.label });
      const out = el('output');
      inp.oninput = () => change(c.k, +inp.value);
      node = el('div', { class: 'rng' }, inp, out);
      setter = v => { inp.value = v; out.textContent = v + (c.unit || ''); };
      break;
    }
    case 'color': {
      const inp = el('input', { type: 'color', 'aria-label': c.label });
      inp.oninput = () => change(c.k, inp.value);
      node = inp; setter = v => { inp.value = v; };
      break;
    }
    case 'toggle': {
      const inp = el('input', { type: 'checkbox', 'aria-label': c.label });
      inp.onchange = () => change(c.k, inp.checked);
      node = el('span', { class: 'switch' }, inp, el('i'));
      setter = v => { inp.checked = v; };
      break;
    }
    case 'select': {
      const sel = el('select', { 'aria-label': c.label }, c.opts.map(([v, l]) => el('option', { value: v }, l)));
      sel.onchange = () => change(c.k, sel.value);
      node = sel; setter = v => { sel.value = v; };
      break;
    }
    case 'weight': {
      const sel = el('select', { 'aria-label': 'Weight' });
      sel.onchange = () => change('weight', +sel.value);
      const names = { 100: 'Thin', 200: 'Extra light', 300: 'Light', 400: 'Regular', 500: 'Medium', 600: 'Semi-bold', 700: 'Bold', 800: 'Extra bold', 900: 'Black' };
      node = sel;
      setter = v => {
        const ws = fontOf(S.font).w;
        sel.replaceChildren(...ws.map(w => el('option', { value: w }, `${names[w] || w} (${w})`)));
        sel.value = v; sel.disabled = ws.length < 2;
      };
      break;
    }
    case 'font': node = buildFontPicker(c, fn => { setter = fn; }); break;
    case 'wall': node = buildWallPicker(c, fn => { setter = fn; }); break;
  }
  let row;
  if (c.t === 'h') row = el('div', { class: 'h' }, c.label);
  else if (c.t === 'hint') row = el('div', { class: 'hint' }, c.text);
  else row = el('div', { class: 'row' + (c.t === 'toggle' ? ' inline' : '') }, c.label && c.t !== 'wall' ? el('label', {}, c.label) : null, node);
  const b = { c, row, setter: v => setter(v) };
  bound.push(b);
  return row;
}

function buildFontPicker(c, reg) {
  const wrap = el('div');
  const tiles = [];
  const cats = [...new Set(FONTS.map(f => f.c))];
  cats.forEach(cat => {
    wrap.append(el('div', { class: 'fgroup' }, cat));
    const g = el('div', { class: 'fgrid' });
    FONTS.filter(f => f.c === cat).forEach(f => {
      const t = el('button', { type: 'button', class: 'ftile', onclick: () => change('font', f.n), 'aria-label': f.n },
        el('span', { class: 's', style: `font-family:${fontStack(f)};font-weight:${f.w.includes(400) ? 400 : f.w[0]}` }, '10:45'),
        el('small', {}, f.n));
      t._n = f.n; tiles.push(t); g.append(t);
    });
    wrap.append(g);
  });
  reg(v => tiles.forEach(t => t.classList.toggle('on', t._n === v)));
  return wrap;
}

function buildWallPicker(c, reg) {
  const wrap = el('div');
  const grid = el('div', { class: 'grid', style: 'margin-bottom:14px' });
  const tiles = [];
  const mk = (id, name, bg) => {
    const t = el('button', { type: 'button', class: 'tile wtile', style: `background:${bg}`, onclick: () => { change('wp', id); }, 'aria-label': name }, el('em', {}, name));
    t._id = id; tiles.push(t); grid.append(t); return t;
  };
  mk('none', 'None', 'repeating-conic-gradient(#222 0 25%,#333 0 50%) 50%/16px 16px');
  WALLS.forEach(w => mk(w.id, w.n, w.css));
  const customTile = mk('custom', 'Yours', '#222'); customTile.hidden = true;
  wrap.append(grid);
  const up = el('button', { type: 'button', class: 'btn pri', onclick: () => $('#wpFile').click() }, 'Upload your wallpaper');
  const rm = el('button', { type: 'button', class: 'btn danger', onclick: removeCustom }, 'Remove mine');
  const ac = el('button', { type: 'button', class: 'btn', onclick: autoContrast }, 'Auto contrast');
  wrap.append(el('div', { class: 'btns' }, up, ac), el('div', { class: 'btns one' }, rm));
  wrap.append(el('div', { class: 'hint', style: 'margin-top:-2px' }, 'Your photo never leaves this device. “None” keeps the page background see-through when embedded.'));
  reg(v => {
    tiles.forEach(t => t.classList.toggle('on', t._id === v));
    customTile.hidden = !customURL;
    if (customURL) customTile.style.background = `center/cover url("${customURL}")`;
    rm.hidden = !customURL;
  });
  return wrap;
}

/* wallpaper upload + auto contrast */
async function ingestImage(file) {
  try {
    const bmp = await createImageBitmap(file);
    const max = 1920, sc = Math.min(1, max / Math.max(bmp.width, bmp.height));
    const cv = document.createElement('canvas');
    cv.width = Math.round(bmp.width * sc); cv.height = Math.round(bmp.height * sc);
    cv.getContext('2d').drawImage(bmp, 0, 0, cv.width, cv.height);
    const blob = await new Promise(r => cv.toBlob(r, 'image/jpeg', 0.86));
    customLum = lumOf(cv); writeJSON(LKEY, customLum);
    try { await idb.set('wallpaper', blob); } catch { toast('Couldn’t store the image for next time, but it works for now.'); }
    setCustom(blob);
    S.wp = 'custom'; linked = false;
    autoContrast(true);
    commit();
  } catch { toast('That image couldn’t be read. Try a JPG or PNG.'); }
}
function lumOf(source) {
  const c = document.createElement('canvas'); c.width = c.height = 24;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(source, 0, 0, 24, 24);
  const d = x.getImageData(4, 4, 16, 16).data; let t = 0;
  for (let i = 0; i < d.length; i += 4) t += (0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]) / 255;
  return t / (d.length / 4);
}
function setCustom(blob) { if (customURL) URL.revokeObjectURL(customURL); customURL = blob ? URL.createObjectURL(blob) : null; }
async function removeCustom() {
  try { await idb.del('wallpaper'); } catch { /* ignore */ }
  setCustom(null);
  if (S.wp === 'custom') S.wp = 'aurora';
  commit();
}
function autoContrast(quiet) {
  let lum = S.wp === 'custom' ? customLum : S.wp === 'none' ? 0.05 : (wallOf(S.wp) || WALLS[0]).dark ? 0.15 : 0.8;
  lum *= 1 - S.wpDim / 100;
  S.color = lum > 0.55 ? '#14141a' : '#ffffff';
  S.gradText = false;
  if (lum > 0.3 && lum < 0.7) S.shadow = Math.max(S.shadow, 55);
  commit();
  if (quiet !== true) toast(lum > 0.55 ? 'Text set to dark for this bright wallpaper.' : 'Text set to white for this dark wallpaper.');
}
$('#wpFile').onchange = e => { const f = e.target.files[0]; e.target.value = ''; if (f) ingestImage(f); };

/* presets, themes, sharing */
function applyObj(o, msg) {
  S = sanitize(o);
  if (S.wp === 'custom' && !customURL) S.wp = 'aurora';
  linked = false; commit();
  if (msg) toast(msg);
}
function presetTile(p) {
  const s = sanitize({ ...DEFAULTS, ...p.s });
  const f = fontOf(s.font);
  const wp = s.wp === 'none' ? '#111' : (wallOf(s.wp) || WALLS[0]).css;
  let style = `font-family:${fontStack(f)};font-weight:${s.weight};color:${s.color};`;
  if (s.mode === 'color') style += `background:${s.bgType === 'solid' ? s.bg1 : `linear-gradient(${s.angle}deg,${s.bg1},${s.bg2})`};border-radius:12px;`;
  if (s.mode === 'glass') style += 'background:rgba(255,255,255,.22);border:1px solid rgba(255,255,255,.4);border-radius:12px;backdrop-filter:blur(6px);';
  if (s.gradText) style += `background:linear-gradient(135deg,${s.color},${s.color2});-webkit-background-clip:text;background-clip:text;color:transparent;`;
  if (s.glow) style += `text-shadow:0 0 8px ${s.color};`;
  const t = el('button', { type: 'button', class: 'tile', style: `background:${wp}`, 'aria-label': p.n, onclick: () => applyObj({ ...DEFAULTS, hour12: S.hour12, tz: S.tz, ampm: S.ampm, pad0: S.pad0, ...p.s }, `Applied “${p.n}”`) },
    el('span', { class: 't', style }, s.layout === 'stacked' ? '12 45' : '12:45'), el('em', {}, p.n));
  return t;
}
function shareLink() {
  const diff = {};
  for (const k of Object.keys(DEFAULTS)) if (S[k] !== DEFAULTS[k] && !(k === 'wp' && S.wp === 'custom')) diff[k] = S[k];
  return (window.BARETIME_BASE || location.href.split('#')[0]) + '#t=' + b64enc(diff);
}
async function copy(text, okMsg) {
  try { await navigator.clipboard.writeText(text); toast(okMsg); }
  catch { window.prompt('Copy this:', text); }
}
function embedCode() {
  return `<iframe src="${shareLink()}&embed=1" style="border:0;width:100%;height:260px;background:transparent" allowtransparency="true" loading="lazy" title="Clock"></iframe>`;
}
function saveTheme(name) {
  name = (name || '').trim() || `Theme ${themes.length + 1}`;
  themes.unshift({ id: Date.now().toString(36), name: name.slice(0, 40), s: { ...S } });
  themes = themes.slice(0, 60); writeJSON(TKEY, themes); renderThemes();
  toast(`Saved “${name}”.`);
}
function renderThemes() {
  const box = $('#savedList'); if (!box) return;
  box.replaceChildren();
  if (!themes.length) box.append(el('div', { class: 'empty' }, 'No saved themes yet.'));
  themes.forEach(t => box.append(el('div', { class: 'saved' },
    el('span', {}, t.name),
    el('button', { type: 'button', onclick: () => applyObj(t.s, `Applied “${t.name}”`) }, 'Use'),
    el('button', { type: 'button', class: 'del', 'aria-label': `Delete ${t.name}`, onclick: () => { themes = themes.filter(x => x.id !== t.id); writeJSON(TKEY, themes); renderThemes(); } }, 'Delete'))));
}
function exportTheme() {
  const name = ($('#themeName').value || 'my-theme').trim();
  const s = { ...S }; if (s.wp === 'custom') delete s.wp;
  const blob = new Blob([JSON.stringify({ app: 'baretime', v: 1, name, settings: s }, null, 2)], { type: 'application/json' });
  const a = el('a', { href: URL.createObjectURL(blob), download: `baretime-${name.toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'theme'}.json` });
  document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
$('#importFile').onchange = async e => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  try {
    const o = JSON.parse(await f.text());
    const s = o.settings || o.s || o;
    if (typeof s !== 'object' || !Object.keys(s).some(k => k in DEFAULTS)) throw 0;
    applyObj(s, `Imported${o.name ? ' “' + String(o.name).slice(0, 40) + '”' : ''}.`);
  } catch { toast('That file isn’t a BareTime theme.'); }
};

/* install, fullscreen, keep-awake, updates */
let deferredPrompt = null, swReg = null;
const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;
const isiOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
function updateInstallUI() { $('#btnInstall').hidden = !deferredPrompt; const b = $('#appInstall'); if (b) b.hidden = !deferredPrompt; }
addEventListener('beforeinstallprompt', e => { e.preventDefault(); deferredPrompt = e; updateInstallUI(); });
addEventListener('appinstalled', () => { deferredPrompt = null; updateInstallUI(); toast('BareTime is installed.'); });
async function install() { if (!deferredPrompt) return; deferredPrompt.prompt(); await deferredPrompt.userChoice.catch(() => {}); deferredPrompt = null; updateInstallUI(); }

const canFs = !!(document.documentElement.requestFullscreen);
function toggleFs() {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen({ navigationUI: 'hide' }).catch(() => toast('Full screen isn’t available here.'));
}
document.addEventListener('fullscreenchange', () => { if (document.fullscreenElement) openPanel(false); });

let wakeLock = null;
async function setAwake(on) {
  prefs.awake = on; writeJSON(PKEY, prefs);
  try {
    if (on) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => { wakeLock = null; }); }
    else if (wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch { toast('Keep-screen-on isn’t available on this browser.'); }
}
document.addEventListener('visibilitychange', () => { if (!document.hidden && prefs.awake && !wakeLock && 'wakeLock' in navigator) setAwake(true); });

function swVersion() {
  return new Promise(res => {
    const c = navigator.serviceWorker && navigator.serviceWorker.controller;
    if (!c) return res(null);
    const ch = new MessageChannel(); ch.port1.onmessage = e => res(e.data && e.data.version);
    c.postMessage({ type: 'GET_VERSION' }, [ch.port2]); setTimeout(() => res(null), 1200);
  });
}
async function checkUpdates() {
  if (!swReg) return toast('Updates work once the app is served over https and installed.');
  try {
    await swReg.update();
    if (swReg.waiting) showUpdate(swReg.waiting);
    else if (!swReg.installing) toast('You’re on the latest version.');
  } catch { toast('Couldn’t check — you may be offline.'); }
}
function showUpdate(w) { toast('A new version of BareTime is ready.', 'Update now', () => w.postMessage({ type: 'SKIP_WAITING' }), 0); }

/* panel build */
const TAB_LIST = [['presets', 'Presets'], ['style', 'Style'], ['text', 'Text'], ['time', 'Time & date'], ['wall', 'Wallpaper'], ['themes', 'Save & share'], ['app', 'App']];
function buildPanel() {
  const nav = $('#tabnav'), tabs = $('#tabs');
  TAB_LIST.forEach(([id, label], i) => {
    const b = el('button', { type: 'button', 'data-tab': id, onclick: () => showTab(id) }, label);
    const p = el('section', { class: 'tab', 'data-tab': id });
    nav.append(b); tabs.append(p);
  });
  const pane = id => $(`.tab[data-tab="${id}"]`);
  const pg = el('div', { class: 'grid' }, PRESETS.map(presetTile));
  pane('presets').append(el('div', { class: 'hint', style: 'margin:0 0 12px' }, 'Pick a starting point, then tweak anything. Your 12/24-hour and time-zone choices are kept.'), pg);
  ['style', 'text', 'time', 'wall'].forEach(id => TABS[id].forEach(c => pane(id).append(build(c))));

  const th = pane('themes');
  th.append(
    el('div', { class: 'h' }, 'Save this design'),
    el('div', { class: 'inputrow' }, el('input', { type: 'text', id: 'themeName', placeholder: 'Theme name', maxlength: 40, 'aria-label': 'Theme name' }),
      el('button', { type: 'button', class: 'btn pri', onclick: () => { saveTheme($('#themeName').value); $('#themeName').value = ''; } }, 'Save')),
    el('div', { id: 'savedList' }),
    el('div', { class: 'h' }, 'Share & move designs'),
    el('div', { class: 'btns' },
      el('button', { type: 'button', class: 'btn', onclick: () => copy(shareLink(), 'Share link copied.') }, 'Copy share link'),
      el('button', { type: 'button', class: 'btn', onclick: () => copy(embedCode(), 'Embed code copied.') }, 'Copy embed code'),
      el('button', { type: 'button', class: 'btn', onclick: exportTheme }, 'Export file'),
      el('button', { type: 'button', class: 'btn', onclick: () => $('#importFile').click() }, 'Import file')),
    el('div', { class: 'note' }, el('b', {}, 'No account needed. '), 'A share link holds the whole design in the address itself, so whoever opens it sees your clock. Your own wallpaper photo isn’t included. Embed code puts the clock on any website; pick “None” as the wallpaper for a see-through background.'));

  const ap = pane('app');
  ap.append(el('div', { class: 'h' }, 'Display'));
  if (canFs) ap.append(el('div', { class: 'btns one' }, el('button', { type: 'button', class: 'btn', onclick: toggleFs }, 'Full screen')));
  if ('wakeLock' in navigator) {
    const inp = el('input', { type: 'checkbox', 'aria-label': 'Keep screen on' }); inp.checked = !!prefs.awake; inp.onchange = () => setAwake(inp.checked);
    ap.append(el('div', { class: 'row inline' }, el('label', {}, 'Keep screen on'), el('span', { class: 'switch' }, inp, el('i'))));
  }
  ap.append(el('div', { class: 'h' }, 'Install'),
    el('div', { class: 'btns one' }, el('button', { type: 'button', id: 'appInstall', class: 'btn pri', onclick: install, hidden: '' }, 'Install BareTime')));
  ap.append(el('div', { class: 'note' }, standalone ? 'BareTime is running as an installed app.' : isiOS ? 'On iPhone or iPad: tap Share, then “Add to Home Screen”.' : 'Use your browser menu → “Install app” or “Add to Home screen” to open BareTime like a normal app, fully offline.'));
  const ver = el('span', {}, '…');
  ap.append(el('div', { class: 'h' }, 'Updates'),
    el('div', { class: 'note' }, 'BareTime updates itself. When a new version is published you’ll see an “Update now” message — nothing to download. Version: ', ver),
    el('div', { class: 'btns one' }, el('button', { type: 'button', class: 'btn', onclick: checkUpdates }, 'Check for updates')),
    el('div', { class: 'h' }, 'Reset'),
    el('div', { class: 'btns one' }, el('button', { type: 'button', class: 'btn danger', onclick: () => { if (confirm('Reset the clock to its default look? Saved themes are kept.')) applyObj({}, 'Back to defaults.'); } }, 'Reset design')));
  swVersion().then(v => { ver.textContent = v || 'dev'; });

  renderThemes();
  showTab(prefs.tab && TAB_LIST.some(t => t[0] === prefs.tab) ? prefs.tab : 'presets');
  syncUI(); updateInstallUI();
}
function showTab(id) {
  document.querySelectorAll('#tabnav button').forEach(b => b.classList.toggle('on', b.dataset.tab === id));
  document.querySelectorAll('.tab').forEach(t => t.classList.toggle('on', t.dataset.tab === id));
  prefs.tab = id; writeJSON(PKEY, prefs);
  const t = $(`#tabnav button[data-tab="${id}"]`); if (t && t.scrollIntoView) t.scrollIntoView({ block: 'nearest', inline: 'center' });
}
const panelOpen = () => document.body.classList.contains('panel-open');
function openPanel(on = !panelOpen()) { document.body.classList.toggle('panel-open', on); if (on) $('#toast').classList.remove('show'); }

/* ---------- wiring ---------- */
$('#btnPanel').onclick = () => openPanel(true);
$('#btnClose').onclick = () => openPanel(false);
$('#btnFs').onclick = toggleFs;
$('#btnInstall').onclick = install;
if (!canFs) $('#btnFs').hidden = true;
stage.addEventListener('click', () => { if (!EMBED) openPanel(); });
addEventListener('keydown', e => {
  if (EMBED || e.target.matches('input[type=text],select') || e.metaKey || e.ctrlKey || e.altKey) return;
  const k = e.key.toLowerCase();
  if (k === 'f' && canFs) toggleFs();
  else if (k === 'c') openPanel();
  else if (k === 'escape') openPanel(false);
});

let idleT;
function wake() { document.body.classList.remove('idle'); clearTimeout(idleT); idleT = setTimeout(() => { if (!panelOpen()) document.body.classList.add('idle'); }, 3500); }
['pointermove', 'pointerdown', 'keydown', 'touchstart'].forEach(ev => addEventListener(ev, wake, { passive: true }));

/* ---------- service worker with self-update ---------- */
function registerSW() {
  if (EMBED || !('serviceWorker' in navigator) || !/^https?:$/.test(location.protocol)) return;
  const hadController = !!navigator.serviceWorker.controller;
  navigator.serviceWorker.register('sw.js').then(reg => {
    swReg = reg;
    if (reg.waiting && navigator.serviceWorker.controller) showUpdate(reg.waiting);
    reg.addEventListener('updatefound', () => {
      const nw = reg.installing; if (!nw) return;
      nw.addEventListener('statechange', () => { if (nw.state === 'installed' && navigator.serviceWorker.controller) showUpdate(nw); });
    });
    setInterval(() => reg.update().catch(() => {}), 30 * 60 * 1000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) reg.update().catch(() => {}); });
  }).catch(() => {});
  let reloaded = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (!hadController || reloaded) return; reloaded = true; location.reload(); });
}

/* ---------- start ---------- */
(async function init() {
  try { const blob = await idb.get('wallpaper'); if (blob) setCustom(blob); } catch { /* private mode */ }
  if (S.wp === 'custom' && !customURL) S.wp = 'aurora';
  apply(); tick();
  if (!EMBED) {
    buildPanel(); wake();
    if (prefs.awake) setAwake(true);
    if (linked) toast('Design loaded from a link.', 'Save it', () => { openPanel(true); showTab('themes'); }, 7000);
  }
  loadFonts();
  registerSW();
})();
})();
