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

Inter (Latin) and Heebo (Hebrew), bundled as variable fonts subset to the needed characters and limited to
weights 400–700 (Inter 88 KB, Heebo 36 KB; Inter's optical-size axis pinned at 18). On Android 10+ a
`Typeface.CustomFallbackBuilder` chains Inter → Heebo → system per glyph, so Hebrew anywhere (UI or content
titles) renders in Heebo.

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
- App card: 16:9. The brief asks for ~200 × 112 dp *and* 5 columns, but 5 × 200 dp does not fit the 864 dp content
  width. Proposal: **160 × 90 dp with 16 dp gaps** (5 × 160 + 4 × 16 = 864 dp exactly). To be confirmed in stage 2.
- App card fallback (no TV banner): the icon centred on a fill of its dominant colour (computed once, cached).

## Liquid glass (cheap on Mali-G31)

Never blur moving content in real time. Every glass surface is composed of layers that are static or
pre-computed:

1. **Backdrop sample**: for each hero backdrop, once, on Coil's decoder thread: centre-crop and downscale to
   240 × 135 (1/8), blur (three box passes, radius 6 ≈ Gaussian), saturate × 1.15; Coil caches the result. A glass
   element draws the crop of this bitmap that lies behind it, through an `ImageShader` with bilinear filtering.
   When the hero changes, glass cross-fades between the old and new blurred bitmaps.
2. **Tint**: white 10% at rest, 18% focused.
3. **Rim**: 1 dp stroke, linear gradient white 50% at top-start → 0% at bottom-end.
4. **Inner highlight**: white 12% → 0% across the top 40% of the shape.
5. **Outer shadow**: black 35%, 24 dp blur, 8 dp y-offset (focused: 45%, 36 dp, 16 dp).

Overlays above the grid (menus, folders) take one downscaled snapshot of the screen when opening, blur it once
and use it as their backdrop. An optional AGSL refraction pass (Android 13+, focused element or shelf only)
stays behind a setting and is kept only if it holds 60 fps.

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
  travel; source label, logo, metadata, synopsis and progress fade/slide in with a 40 ms stagger while the old
  text fades out fast. Page dots sit in a glass pill. Play is outside the animated block, so it keeps focus.
- **Auto-advance**: every 8 s, only while the hero is visible and focused; pauses for 20 s after any key press.
- **Leaving the hero**: its text fades out and the backdrop cross-fades to the blurred, darkened version that
  stays fixed behind the scrolling grid. Up returns to the hero.
- **Folders/menus**: open from their card or anchor (scale + fade).
- **Move mode**: the card wiggles slightly. This is the only repeating animation and runs only while moving.
- **Idle**: when nothing is focused-changing or playing, no frame is drawn.

## Images

- Backdrops decoded at the window size (≤ 1920 px wide); logos fit in 600 × 600 px and are padded by 24 px.
- Memory cache 32 MB, disk cache 96 MB; the memory cache is cleared when the home screen is hidden.
- Small memory cache, capped disk cache; prefetch only the next and previous hero items.
- A mostly dark logo gets a subtle light glow so it stays readable.

## Hero layout

Start-aligned in the lower area (bottom edge 64 dp above the screen bottom), over a start-side and a bottom
scrim: source app (20 dp icon + caption) → logo (max 380 × 120 dp; title in `display` when there is none) →
metadata (`callout`, secondary) → synopsis (`body`, 85% white, max 3 lines, 520 dp) → 140 dp progress bar with
"42 min left" (`caption`) → glass Play button ("Resume" when there is progress). Page dots: bottom centre.
Clock: glass pill at the top-end corner.
