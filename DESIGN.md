# Glass Home design system

Reference: Apple's tvOS Human Interface Guidelines for spacing, focus and motion; the look of the
tvOS 26 home screen with Liquid Glass. Inspired by, not copied: no Apple fonts, symbols, logos or names.

Status: design system from stage 1 (`ui/theme`, `ui/glass`, `ui/motion`); home screen, grid, menus, folders and
settings from stage 2 (`ui/home`, `ui/apps`, `ui/overlay`, `ui/settings`). Values measured on the TV are noted.

## Canvas

- Design canvas: **960 × 540 dp**. Measured on the Homatics box (stage 0): UI surface 1920 × 1080 px, 320 dpi
  (xhdpi, density 2.0), display mode 3840 × 2160 @ 59.94 Hz (video plane), 3.4 GB RAM visible, 256 MB heap class.
  Never hardcode it: the real window size is read at runtime and images are sized to it.
- Safe margins: **48 dp** start/end, **27 dp** top/bottom. Content width: 864 dp.
- Layout mirrors fully in RTL (Hebrew). "Start"/"end" below follow the layout direction. Multi-line text in the
  hero aligns to the layout's start edge even when it runs the other way (an English synopsis in Hebrew); the
  performance overlay stays left to right.

## Colour

| Token | Value | Use |
|---|---|---|
| `background` | `#05060A` | Window, behind everything |
| `textPrimary` | `#F5F5F7` | Titles, values |
| `textSecondary` | `#EBEBF5` @ 60% | Metadata, synopsis, labels |
| `textTertiary` | `#EBEBF5` @ 30% | Disabled, hints |
| `focusFill` | `#FFFFFF` | Focused buttons fill white, content turns `onFocus` |
| `onFocus` | `#0A0A0F` | Text/icons on a focused white fill |
| `scrimStart` | scrim colour 85% → 0% over 60% of the width | Hero legibility (start side) |
| `scrimBottom` | scrim colour 90% → 0% over 55% of the height | Hero legibility (bottom) |
| scrim colour | Settings › Gradient color: black (default), from the image, or a dark blue `#0B1630`, purple `#24112E`, green `#0C2117`, red `#2A0C14`, teal `#08242A` or brown `#22160C` | Scrims and the grid's darkening |
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
| `body` | 17 sp / 1.35 | 400 | Synopsis (max 2 lines, ~520 dp wide) |
| `callout` | 15 sp | 500 | Metadata line ("S2 · E5 · Episode title"), source-app label |
| `caption` | 13 sp | 500 | Clock, progress "42 min left", small labels |

## Spacing and shape

- Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40, 48 dp.
- **Sizes are settings** (Settings): app size 5–8 cards per row (default 6), logo 50–100% (default 70%), details
  text 80–130% (default 100%). Card, shelf and hero geometry below are for the chosen size.
- **Gradient color "from the image"**: the hue carrying the most colour in the hero image (pixels vote with their
  chroma, 24 hue bins, so blacks, whites and greys do not vote) at HSV value 0.07–0.17 and saturation up to 0.75,
  both scaled by how colourful the whole image is: a colourful image gives a dark tint of its colour, a grey one
  stays almost black. Computed once per image from the grid's 192 × 108 copy, off the main thread; it springs to
  the new tone with the backdrop's cross-fade. Cached glass mixes the same colour in where it applies the scrims;
  live glass already sees them.
- Corner radii: app card **16 dp**; glass pill **fully rounded**; favorites shelf **28 dp**; menus and folders
  **24 dp**. Corners are continuous (`ContinuousRoundedShape`): each corner starts 1.5 × radius from the vertex
  and eases in with one cubic, so curvature ramps up from zero instead of jumping like a circular arc.
- App card: 16:9, 16 dp gaps, always spanning the 864 dp content width: 5 per row = 160 × 90 dp, 6 = 131 × 74 dp
  (default), 7 = 110 × 62 dp, 8 = 94 × 53 dp.
- App card fallback (no TV banner): the icon (44 dp) and the app name (`caption`) on a fill of the icon's dominant
  colour. The colour is the most common 4-bit colour bin, colourful pixels weighted above greys (so a brand colour
  beats a white background), scaled to an HSV value of 0.22–0.52 so white text reads on it. It is computed once per
  app version and cached on disk, so a card shows its colour at once while its image loads.
- Card images are rendered once at the focused card's size (card × 1.12, so the card in focus is drawn 1:1; icons
  96 px) and moved to GPU memory (hardware bitmaps). Rendered cards are also saved as PNGs per app version and size,
  so after a restart they decode straight into GPU memory instead of being drawn again from each app's resources.
  Edges are anti-aliased by filling the card shape with a filtered image shader rather than clipping.
- Card glass (every app card, after tvOS): a sheen falling from the top-left (white 10% → 3% → 0 over the top
  ~60%, 16% on the focused card), composed into the card's own image shader on Android 13+ (the image inside is
  set to filter; unfiltered, the scaled-up focused card looked pixelated) or baked into the fallback's fill
  gradient, so it costs no extra pass; and a 1 dp rim lit at the top-left corner (white 55%) with a fainter
  reflection at the bottom-right (24%), at 65% strength until focused.

## Liquid glass (cheap on Mali-G31)

Two modes (Settings › Glass › Live glass), both with the same optics and the same settings: blur (0–32 dp,
default 6), refraction (0–200%), brightness (−30…+30%) and transparency (0–100%, default 70%: the frost tint).

**Live glass** (default, Android 13+) refracts what is really behind it, every frame it changes:

- The backdrop, the scrolling page and the whole screen are recorded into GraphicsLayers (display lists, not
  pixels) and drawn as usual. Each glass surface draws the region behind it again, from those layers, into a small
  offscreen layer: half resolution for surfaces over 120 000 px² (the shelf, panels), full for small ones, with a
  margin so the blur sees real content at the edges. A RenderEffect blurs it and runs the lens on it
  (`res/raw/glass_body.agsl`: refraction, dispersion, vibrancy, frost, brightness), and the layer is drawn scaled
  back up. The rim light (`res/raw/glass_rim.agsl`) is drawn on top at full resolution as a stroke along the edge,
  so only the edge band is shaded.
- What each surface sees: page glass (Play, page dots, shelf, folders) the backdrop; the top bar (clock, settings)
  the backdrop and the page, so cards scroll under it; overlays the whole screen, drawn at a quarter of the
  resolution and blurred by 20 dp as their background, and their glass refracts that. The screen under a fully
  shown overlay is not drawn on screen, only into that quarter-resolution background, so changes behind it (a
  setting taking effect) show through at once.
- Cost: only frames that are drawn do any of this; an idle screen draws nothing. The GPU renders a surface's sample
  again when the content behind it changes (the backdrop cross-fading, the page scrolling, focus moving under the
  top bar).

**Cached glass** (live glass off, or Android 12 and older) is the earlier design below: never blur moving content
in real time. Every glass surface samples one **glass source** per hero backdrop,
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

Both paths fill the shape's path, so edges stay anti-aliased on any shape.

**Cost on the S905X4.** Two limits, both measured with the performance overlay on the TV: the Mali-G31's shader
throughput and, at 1080p, memory bandwidth (each full-screen layer is ~8 MB read and written per frame; about
four fit in a 60 fps frame).

**Glass shader.** The first stage-2 build ran the lens for every glass surface on every frame, whatever
changed on screen, which held the TV at 8–15 fps. Now:

- each surface renders into a cached offscreen layer per backdrop image, re-rendered only when its image,
  position, size, focus or the scrims change; any other redraw just composites that layer, and a cross-fade
  between two images only changes the two layers' alpha;
- the shader takes one sample on the flat interior (most of a large surface) and does the refraction, dispersion
  and rim light only in the bevel band; the normal is analytic (one distance-field evaluation, not five), there is
  no `pow`, and colour math is half precision. Output is the same as before (max difference 2/255).

The shader runs only when a frame is drawn; an idle screen draws nothing. Glass on the scrolling page (Play, page
dots, shelf) samples the backdrop as if the page were at rest, so the camera moving does not render it again on
every frame (the page only rests where that is exact, or with it off screen); grid glass has its own backdrop.

**Full-screen passes.** The hero is the backdrop plus one scrim pass (both scrims baked into one small bitmap drawn
stretched), three during an item change. The grid backdrop is one pass (darkened by a colour filter) and the hero
backdrop is not drawn under it. An overlay's dim is baked into its snapshot, and once the overlay is fully shown
the screen under it is not drawn at all. Outer shadow: black 35%, via the focus lift or
`glassShadow` (6–24 dp).

Over the app grid the backdrop is replaced by a far more blurred version (192 × 108 px, box-blur radius 3), and
glass refracts that instead, with an even 40% darkening term (`shadeX.w` in the shader) matching the screen.

Glass surfaces observe their own position on screen, so a surface that moves (the camera scrolling, a focus
scale) redraws its sample; one that does not move draws nothing.

Overlays (menus, folders, settings) take one snapshot of the screen when they open (`PixelCopy` into 480 × 270 px,
three box-blur passes of radius 4) and use it as their backdrop and as the source their glass refracts. The layer
stays invisible until the snapshot is taken, so it never captures itself. Nothing is blurred while it is open.

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
| `hurry` | 1.0 | 900 | Finishing a hero change early because the next one is waiting |
| `camera` | 1.0 | 300 | The home screen scrolling between hero and grid (never bounces) |
| `sweep` | 1.0 | 40 | The one-shot specular highlight |

- **Focus** (`Modifier.focusEffect`): cards scale to 1.12 and buttons to 1.08, lift (shadow 0 → 18 dp / 12 dp),
  a specular band sweeps across once, and the element tilts 2.5° (buttons 1.5°) toward the last D-pad
  direction, then springs back to flat. Pressing OK shrinks it slightly. Neighbours shift smoothly.
- **OK / hold OK** (`Modifier.tvClickable`): click on release; hold for the long-press timeout opens the
  element's menu, and that key's release is swallowed.
- **Hero change** (Left/Right on Play): backdrop cross-fades with a 24 dp parallax slide in the direction of
  travel (images are drawn 24 dp wider than the screen on each side, so the slide never uncovers an edge; the
  item's soft glass source shows first and the sharp image fades in over it); logo, metadata, synopsis and
  progress fade/slide in with a 40 ms stagger while the old text fades out fast, both pinned to the start edge
  (`AnimatedContent` aligns as if left to right, so the alignment is absolute: right in Hebrew). Page dots sit in a
  glass pill. Play is outside the animated block, so it keeps focus.
- **Fast changes**: backdrop changes run one at a time. A key press during a change makes it finish quickly
  (`hurry`), and only the newest item waiting is shown next, so the stage never stacks more than the old image and
  the new one (each a full-screen pass) and the glass over it follows a clean cross-fade. The small images (soft
  backdrop, the grid's, logo) of two items either way are prefetched at once; the neighbours' sharp backdrops only
  after the selection rests for 300 ms, and cancelled when it moves on.
- **Auto-advance**: every 8 s, only while the hero is visible and focused; pauses for 20 s after any key press and
  stops after 5 minutes without one (the TV left on), until the next key.
- **Leaving the hero**: its text fades out and the backdrop cross-fades to the blurred, darkened version that
  stays fixed behind the scrolling grid. Up from the first grid row returns to the shelf, Back to Play.
- **Camera**: the page (hero text, shelf, grid) moves as one; the backdrop, clock and settings button stay fixed.
  The first two grid rows are shown together; below that the page follows focus one row at a time.
- **Menus**: open next to their card (below it when there is room, else above), scaling in from the card's side;
  a sharp copy of the card stays lifted over the blur.
- **Folders**: open full screen over the blurred home screen (scale 0.94 → 1 and fade).
- **Home**: from anywhere (the system's home intent or the Home helper) closes every overlay and returns to Play
  with the camera at the top.
- **Back** steps out one level: move mode, then a menu's or settings' sub-page (to its main page, focus on the row
  that opened it), then the menu, folder or settings panel; on the home screen itself (shelf, grid, top bar) it
  returns to Play like Home, and on Play it does nothing: this is the home screen. The activity takes the Back
  key before anything else and hands it to the screen's own handlers (`HomeBack`): Compose would spend it moving
  focus out of a scrolling list, and the system back dispatcher did not reliably reach the screen.
- **Settings**: a glass panel slides in from the end edge.
- **Focus shine**: the tilt and the light sweep run to the end on the element that started them, even after focus
  has moved on.
- **Move mode**: the card wiggles slightly (±1.6°, eased, 140 ms per swing) while the D-pad moves it and its
  neighbours slide aside (`focus` spring). This is the only repeating animation and runs only while moving.
- **Idle**: when nothing is focused-changing or playing, no frame is drawn.

## Images

- Backdrops decoded at most 1280 px wide (TMDB's size; decoding larger only upscales in memory, 3.7 MB instead of
  8.3 per image, and the GPU scales them while drawing) as hardware bitmaps, retried once as software bitmaps if
  that fails; logos fit in 600 × 600 px and are padded by 24 px.
- Memory cache 32 MB, disk cache 96 MB. Requests have explicit memory-cache keys, so an image already in memory is
  drawn in the first frame (no blank frame, no logo placeholder). When the home screen is hidden the whole screen
  (its layers, glass and images) is disposed and the large images leave the memory cache; up to 12 MB of the small
  ones (soft backdrops, logos) and the app card images (~200 KB each, GPU memory) stay, so returning home shows
  everything at once (the first backdrop without a fade). `TRIM_MEMORY_BACKGROUND` arrives on every trip to the
  background, so only `TRIM_MEMORY_COMPLETE` drops those too.
- A mostly dark logo gets a subtle light glow so it stays readable.

## Home layout

The page, top to bottom (960 × 540 dp screen):

- **Hero** (one screen high). Start-aligned text block ending 24 dp above the shelf, over a start-side and a bottom
  scrim: logo (max 380 × 96 dp × logo size; the title in `display` × logo size when there is none) → the episode or
  movie line (14 sp × text size, secondary) → glass Play button ("Resume" when there is progress). Only while Play
  is focused, the synopsis (14/19 sp × text size, 80% white, max 2 lines, 480 dp) and a 110 dp × 3 dp progress bar
  with "42 min left" (12 sp) expand in between (spring, fade). The source app is not shown. Page dots: glass pill
  at the end side, level with Play.
- **Favorites shelf**: a glass tray (28 dp corners) 896 dp wide and one card + 32 dp high, 27 dp above the bottom,
  holding one row of cards aligned with the grid columns. More scroll a card at a time; cards outside the window
  fade out rather than being clipped, so the focused card's lift is never cut. No labels (banners carry the name).
- **App grid**: starts 80 dp below the hero (so no part of the shelf shows above the grid); cards 16 dp apart, row
  pitch card + 36 dp; the focused card's name appears 10 dp below it. On screen the first row sits 96 dp from the
  top. Favorites are not repeated in the grid; an app that leaves the shelf is back in its old grid place.
- **Fixed top bar**: round glass settings button (34 dp) and the clock pill at the top-end corner.

Focus moves geometrically, with explicit links where the layout needs them: Down from Play → the last focused
favorite; Down from a favorite → the grid card below it; Up from the first grid row → the last focused favorite;
Up from Play or the shelf → settings; Down from settings → wherever focus was. Back from the grid or the shelf
returns to Play. Rows wrap around: past the last card of the shelf or of a grid (or folder) row, focus goes to
the first card of that row, and back. When the focused element disappears (hidden, moved into a folder or onto
the shelf, uninstalled), focus goes to its neighbour, never to nothing.

Hold OK opens the card's menu: apps — Move, Add to/Remove from Favorites, Add to Folder (existing folders + New
Folder), Hide, App Info; folders — Move, Remove Folder (its apps take its place); apps in a folder — Move, Remove
from Folder, Favorites, Hide, App Info. Move mode: the D-pad moves the card (Up/Down by a row), OK or Back ends it.

The layout (favorites, grid order, folders, hidden apps) is saved as JSON on every change. Apps are listed from
their TV launcher activities, plus phone-only apps when that setting is on; installs, updates and removals arrive
through `LauncherApps` callbacks while the launcher is visible. First run fills the shelf with up to five apps
(known streaming apps first).

A **performance overlay** (setting) shows rendered frames in the last second (from the window's frame metrics, so
an idle screen reads ~0), slow frames (> 16.7 ms), the 95th-percentile frame time, the process PSS and the last
remote key received (to see what a remote's button sends).
