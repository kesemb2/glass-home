# Glass Home design system

Reference: Apple's tvOS Human Interface Guidelines for spacing, focus and motion; the look of the
tvOS 26 home screen with Liquid Glass. Inspired by, not copied: no Apple fonts, symbols, logos or names.

Status: **draft tokens** (stage 0). Stage 1 implements them in `ui/theme` and updates this file with
what was measured on the TV.

## Canvas

- Design canvas: **960 × 540 dp** (expected UI surface 1920 × 1080 px at density 2.0, to be confirmed by the
  stage-0 diagnostics screen; video uses its own 4K plane). Never hardcode it: the real window size is read at runtime and images are sized to it.
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

Inter (Latin) and Heebo (Hebrew), bundled as subset variable fonts; the system resolves per script.

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
  **24 dp**. Corners are continuous (squircle-like), not plain circular arcs.
- App card: 16:9. The brief asks for ~200 × 112 dp *and* 5 columns, but 5 × 200 dp does not fit the 864 dp content
  width. Proposal: **160 × 90 dp with 16 dp gaps** (5 × 160 + 4 × 16 = 864 dp exactly). To be confirmed in stage 2.
- App card fallback (no TV banner): the icon centred on a fill of its dominant colour (computed once, cached).

## Liquid glass (cheap on Mali-G31)

Never blur moving content in real time. Every glass surface is composed of layers that are static or
pre-computed:

1. **Backdrop sample**: for each hero backdrop, once, off the main thread: downscale to ~1/8, stack blur, boost
   saturation ~1.15, cache. A glass element draws the crop of this bitmap that lies behind it, scaled up with
   bilinear filtering.
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

- **Focus**: card scales to 1.12, lifts (shadow grows), a specular highlight sweeps across it once, it tilts
  2–3° toward the direction of travel, then settles. Neighbours shift smoothly.
- **Hero change** (Left/Right): backdrop cross-fades with a ~24 dp parallax slide; logo, metadata and synopsis
  fade/slide in with a 40 ms stagger. Page dots sit in a glass pill.
- **Auto-advance**: every 8 s, only while the hero is visible and focused; pauses for 20 s after any key press.
- **Leaving the hero**: its text fades out and the backdrop cross-fades to the blurred, darkened version that
  stays fixed behind the scrolling grid. Up returns to the hero.
- **Folders/menus**: open from their card or anchor (scale + fade).
- **Move mode**: the card wiggles slightly. This is the only repeating animation and runs only while moving.
- **Idle**: when nothing is focused-changing or playing, no frame is drawn.

## Images

- Backdrops decoded at most at the window width (≤ 1920 px); logos at most 600 px.
- Small memory cache, capped disk cache; prefetch only the next and previous hero items.
- A mostly dark logo gets a subtle light glow so it stays readable.
