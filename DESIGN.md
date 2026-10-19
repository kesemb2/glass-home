# Glass Home design system

Reference: Apple's tvOS Human Interface Guidelines for spacing, focus and motion; the look of the
tvOS 26 home screen with Liquid Glass. Inspired by, not copied: no Apple fonts, symbols, logos or names.

Status: implemented in stage 1 (`ui/theme`, `ui/glass`, `ui/motion`). Values measured on the TV are noted.

## Canvas

- Design canvas: **960 × 540 dp**. Measured on the Homatics box (stage 0): UI surface 1920 × 1080 px, 320 dpi
  (xhdpi, density 2.0), display mode 3840 × 2160 @ 59.94 Hz (video plane), 3.4 GB RAM visible, 256 MB heap class.
  Never hardcode it: the real window size is read at runtime and images are sized to it.
- Safe margins: **48 dp** start/end, **27 dp** top/bottom. Content width: 864 dp.
- Layout mirrors fully in RTL (Hebrew). "Start"/"end" below follow the layout direction.

## Colour

| Token | Value | Use |
|---|---|---|
| `background` | `#05060A` | Window, behind everything |
| `textPrimary` | `#F5F5F7` | Titles, values |
| `textSecondary` | `#EBEBF5` @ 60% | Metadata, synopsis, labels |
| `textTertiary` | `#EBEBF5` @ 30% | Disabled, hints |
| `focusFill` | `#FFFFFF` | Focused buttons fill white, content turns `onFocus` |
| `onFocus` | `#0A0A0F` | Text/icons on a focused white fill |
| `scrimStart` | black 85% → 0% over 60% of the width | Hero legibility (start side) |
| `scrimBottom` | black 90% → 0% over 55% of the height | Hero legibility (bottom) |
| `progressTrack` / `progressFill` | white 25% / white 90% | Hero progress bar |

## Type

Inter (Latin) and Noto Sans Hebrew (Hebrew), bundled as variable fonts subset to the needed characters and
limited to weights 400–700 (Inter 88 KB, Noto Sans Hebrew 43 KB; Inter's optical-size axis pinned at 18, Noto's
width axis at 100). Noto Sans Hebrew replaced Heebo after the first TV test: its open, rational letterforms sit
closer to the tvOS look (Apple's own Hebrew font cannot be used). On Android 10+ a
`Typeface.CustomFallbackBuilder` chains Inter → Noto Sans Hebrew → system per glyph, so Hebrew anywhere (UI or
content titles) renders in it while digits and Latin stay in Inter.

| Style | Size | Weight | Use |
|---|---|---|---|
| `display` | 48 sp | 700 | Hero title when no logo exists |
| `title1` | 32 sp | 600 | Screen titles |
| `title2` | 24 sp | 600 | Folder titles, dialogs |
| `headline` | 19 sp | 600 | Buttons, card titles |
| `body` | 17 sp / 1.35 | 400 | Synopsis (max 3 lines, ~520 dp wide) |
| `callout` | 15 sp | 500 | Metadata line ("S2 · E5 · Episode title"), source-app label |
| `caption` | 13 sp | 500 | Clock, progress "42 min left", small labels |

## Spacing and shape

- Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40, 48 dp.
- Corner radii: app card **16 dp**; glass pill **fully rounded**; favorites shelf **28 dp**; menus and folders
  **24 dp**. Corners are continuous (`ContinuousRoundedShape`): each corner starts 1.5 × radius from the vertex
  and eases in with one cubic, so curvature ramps up from zero instead of jumping like a circular arc.
- App card: 16:9, **160 × 90 dp with 16 dp gaps** in a 5-column grid (5 × 160 + 4 × 16 = 864 dp, the full content
  width). Chosen over the brief's ~200 × 112 dp, which does not fit 5 columns.
- App card fallback (no TV banner): the icon centred on a fill of its dominant colour (computed once, cached).

## Liquid glass (cheap on Mali-G31)

Never blur moving content in real time. Every glass surface samples one **glass source** per hero backdrop,
made once on Coil's decoder thread: centre-crop and downscale to 480 × 270 (1/4 of the UI surface), three box-blur
passes of radius 1, saturation × 1.15. It keeps visible structure, so refraction reads, and it doubles as the
instant placeholder while the sharp backdrop decodes. Surfaces cross-fade between the old and new source when the
hero changes.

**Lens path (Android 13+, default; `res/raw/glass_lens.agsl`)** — one shader pass per surface. The optical model
is adapted from [Liquid Glass Android](https://github.com/QWEA0/Liquid-Glass-Android) by pandadog (MIT):

1. Rounded-rectangle distance field → 1.5 px anti-aliased coverage and the outward normal (numeric gradient).
2. **Bevel profile**: a rim band of `min(48 px, 0.3 × short side)`; inverse-power falloff (exponent 2), so most of
   the bending sits in the outermost pixels.
3. **Refraction**: sample inward along the normal by up to `min(160 px, 0.7 × short side × min(1, short side /
   110 dp))` — the rim shows a compressed mirror of the inner backdrop, the signature of the material. The
   screen's scrims are refracted with the image (evaluated in screen space).
4. **Dispersion**: red/blue refract 12% less/more than green along the rim.
5. **Vibrancy** 1.5: low-saturation colours lifted more than saturated ones, highlights protected.
6. **Adaptive tint**: lighten towards white by 14% over dark content, darken by up to 22% over light content;
   focus adds 6% white.
7. **Rim light** from the same normals: two equal lobes, `pow(±dot(N, −L), 4.5)`, light from the top-start at 30°
   above horizontal (L = (0.866, 0.5)); a 2 px hairline just inside the edge on both lobes and a ≤ 6 px glow inward
   on the lit side only; strength 1.25 (× 1.3 when focused). No constant outline.

**Fallback path** (older Android, the "glass refraction" setting off, or if the shader fails to compile or draw):
the plain source crop, the scrims, tint (10% / 18%), an inner top highlight (white 12% → 0 over 40%), a 1 dp rim
bright at the top-start corner, and a faint reflection rim at the bottom-end corner.

Both paths fill the shape's path, so edges stay anti-aliased on any shape. The shader runs only when a frame is
drawn (focus and hero transitions); an idle screen draws nothing. Outer shadow: black 35%, via the focus lift or
`glassShadow` (6–24 dp).

Overlays above the grid (menus, folders) take one downscaled snapshot of the screen when opening, blur it once
and use it as their source.

The reference library captures and blurs the live content behind each surface every frame (`RenderEffect`, Android
12+); this app keeps the same optics on a pre-blurred source instead, which suits the Mali-G31. Kyant's "Backdrop"
library was rejected for the same reason.

## Motion

Springs everywhere; never linear tweens.

| Spring | Damping ratio | Stiffness | Use |
|---|---|---|---|
| `focus` | 0.75 | 400 | Focus scale, lift, tilt, neighbours shifting |
| `content` | 0.9 | 200 | Hero cross-fade, parallax, text in/out |
| `overlay` | 0.8 | 500 | Menus and folders scaling in from their anchor |
| `color` | 1.0 | 500 | Focus fills (critically damped so colours never overshoot) |
| `exit` | 1.0 | 1500 | Outgoing hero text |
| `sweep` | 1.0 | 40 | The one-shot specular highlight |

- **Focus** (`Modifier.focusEffect`): cards scale to 1.12 and buttons to 1.08, lift (shadow 0 → 18 dp / 12 dp),
  a specular band sweeps across once, and the element tilts 2.5° (buttons 1.5°) toward the last D-pad
  direction, then springs back to flat. Pressing OK shrinks it slightly. Neighbours shift smoothly.
- **OK / hold OK** (`Modifier.tvClickable`): click on release; hold for the long-press timeout opens the
  element's menu, and that key's release is swallowed.
- **Hero change** (Left/Right on Play): backdrop cross-fades with a 24 dp parallax slide in the direction of
  travel (images are drawn 24 dp wider than the screen on each side, so the slide never uncovers an edge; the
  item's soft glass source shows first and the sharp image fades in over it); source label, logo, metadata, synopsis and progress fade/slide in with a 40 ms stagger while the old
  text fades out fast. Page dots sit in a glass pill. Play is outside the animated block, so it keeps focus.
- **Auto-advance**: every 8 s, only while the hero is visible and focused; pauses for 20 s after any key press.
- **Leaving the hero**: its text fades out and the backdrop cross-fades to the blurred, darkened version that
  stays fixed behind the scrolling grid. Up returns to the hero.
- **Folders/menus**: open from their card or anchor (scale + fade).
- **Move mode**: the card wiggles slightly. This is the only repeating animation and runs only while moving.
- **Idle**: when nothing is focused-changing or playing, no frame is drawn.

## Images

- Backdrops decoded at the window size (≤ 1920 px wide) as hardware bitmaps, retried once as software bitmaps if
  that fails; logos fit in 600 × 600 px and are padded by 24 px.
- Memory cache 32 MB, disk cache 96 MB; the memory cache is cleared when the home screen is hidden.
- Small memory cache, capped disk cache; prefetch only the next and previous hero items.
- A mostly dark logo gets a subtle light glow so it stays readable.

## Hero layout

Start-aligned in the lower area (bottom edge 64 dp above the screen bottom), over a start-side and a bottom
scrim: source app (20 dp icon + caption) → logo (max 380 × 120 dp; title in `display` when there is none) →
metadata (`callout`, secondary) → synopsis (`body`, 85% white, max 3 lines, 520 dp) → 140 dp progress bar with
"42 min left" (`caption`) → glass Play button ("Resume" when there is progress). Page dots: bottom centre.
Clock: glass pill at the top-end corner.
