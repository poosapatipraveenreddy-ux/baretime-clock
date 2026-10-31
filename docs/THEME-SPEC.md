# BareTime theme format (v1)

A theme is a JSON object. Export files wrap it as:

```json
{ "app": "baretime", "v": 1, "name": "My theme", "settings": { "mode": "glass", "font": "Poppins" } }
```

Any key may be left out; missing keys use the default. Share links store the same object
(only values that differ from defaults) as base64url in the URL: `#t=<base64url(json)>`.
Unknown or invalid values are ignored, and numbers are clamped to the ranges below.

| Key | Type / allowed values | Default |
|---|---|---|
| `mode` | `transparent` \| `color` \| `glass` | transparent |
| `font` | one of the 26 font names in `app.js` (`FONTS`) | Inter |
| `weight` | 200–900 (snaps to the font's available weights) | 300 |
| `size` | 20–300 (%) | 100 |
| `spacing` | -10–30 (1/100 em) | 0 |
| `lh` | 60–140 (% line height) | 100 |
| `layout` | `single` \| `stacked` | single |
| `align` | `left` \| `center` \| `right` | center |
| `offX`, `offY` | -40–40 (% of screen) | 0 |
| `autoFit` | boolean | true |
| `color`, `color2`, `outlineColor` | `#rrggbb` | #ffffff, #ffd1ff, #000000 |
| `gradText` | boolean | false |
| `opacity` | 10–100 | 100 |
| `shadow`, `glow` | 0–100 | 35, 0 |
| `outline` | 0–12 | 0 |
| `bgType` | `solid` \| `gradient` | gradient |
| `bg1`, `bg2` | `#rrggbb` | #6d5dfc, #ff6bcb |
| `angle` | 0–360 | 135 |
| `cardOpacity` | 10–100 | 100 |
| `radius`, `pad` | 0–100, 0–120 | 32, 40 |
| `glassTint`, `blur` | 0–60 | 12, 24 |
| `hour12`, `ampm`, `pad0`, `seconds`, `secBig`, `blink` | boolean | device locale, true, true, false, false, false |
| `tz` | `local` or an IANA zone from the list in `app.js` | local |
| `dateShow`, `weekday` | boolean | true, true |
| `dateFmt` | `long` \| `medium` \| `dmy` \| `mdy` \| `iso` | long |
| `dateSize`, `dateOpacity`, `dateSpacing` | 8–80, 10–100, 0–30 | 22, 80, 2 |
| `wp` | `none` \| `aurora` \| `sunset` \| `ocean` \| `forest` \| `violet` \| `midnight` \| `noir` \| `dune` \| `cloud` | aurora |
| `wpDim`, `wpBlur` | 0–80, 0–30 | 0, 0 |

## Using it in an Android widget later
A native Kotlin widget can read the same JSON (import from file or paste a share link, base64url-decode the `t=` value).
Relevant keys for a widget: `mode`, `font`, `weight`, `color`, `size`, `layout`, `align`, `hour12`, `dateShow`,
`dateFmt`, `weekday`, `bg1/bg2/cardOpacity/radius` (Color mode). Transparent mode = no background drawable.
Wallpaper keys are ignored by a widget, since the real wallpaper shows behind it.
