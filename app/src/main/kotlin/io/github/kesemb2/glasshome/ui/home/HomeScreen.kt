package io.github.kesemb2.glasshome.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.apps.GridItem
import io.github.kesemb2.glasshome.data.apps.HomeView
import io.github.kesemb2.glasshome.data.hero.HeroItem
import io.github.kesemb2.glasshome.ui.apps.CardFace
import io.github.kesemb2.glasshome.ui.apps.CardGrid
import io.github.kesemb2.glasshome.ui.apps.CardSize
import io.github.kesemb2.glasshome.ui.apps.LocalCardSize
import io.github.kesemb2.glasshome.ui.apps.FavoritesShelf
import io.github.kesemb2.glasshome.ui.apps.GridCard
import io.github.kesemb2.glasshome.ui.apps.ShelfMetrics
import io.github.kesemb2.glasshome.ui.apps.gridRows
import io.github.kesemb2.glasshome.ui.components.ClockPill
import io.github.kesemb2.glasshome.ui.components.GlassIconButton
import io.github.kesemb2.glasshome.ui.debug.PerfOverlay
import io.github.kesemb2.glasshome.ui.glass.GlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.LocalGlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.LocalGlassLive
import io.github.kesemb2.glasshome.ui.glass.LocalGlassStyle
import io.github.kesemb2.glasshome.ui.glass.LocalGlassScroll
import io.github.kesemb2.glasshome.ui.hero.HeroScreen
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.FOLDER_PREFIX
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.GRID_PREFIX
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.HERO
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.SETTINGS
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.SHELF_PREFIX
import io.github.kesemb2.glasshome.ui.motion.LocalFocusAllowed
import io.github.kesemb2.glasshome.ui.overlay.AnchoredMenu
import io.github.kesemb2.glasshome.ui.overlay.LocalBaseLayer
import io.github.kesemb2.glasshome.ui.overlay.OverlayLayer
import io.github.kesemb2.glasshome.ui.settings.SettingsSheet
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Spacing
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The grid starts this far below the hero; on screen, its first row sits [GRID_VIEW_TOP] from the top. */
private val GRID_GAP = 80.dp
private val GRID_VIEW_TOP = 96.dp
private val FOLDER_GRID_TOP = 116.dp

/** Between the hero text and the favorites shelf. */
private val HERO_SHELF_GAP = 24.dp

/**
 * The home screen: the Continue Watching hero with the favorites shelf at its bottom, the app grid below it,
 * and the glass overlays (menus, folders, settings). Focus decides where the camera looks: on the hero and the
 * shelf the hero fills the screen; in the grid the page scrolls up a row at a time while the backdrop turns into
 * a blurred, darker version of itself.
 *
 * While the launcher is in the background ([visible] false) nothing but the window colour is composed, so every
 * bitmap it held can be reclaimed; [nav] remembers where focus was.
 */
@Composable
fun HomeScreen(
    controller: HomeController,
    apps: AppsController,
    nav: HomeNav,
    homeSetup: HomeSetup,
    homeSignal: Int,
    visible: Boolean,
    resumed: Boolean,
    lastKeyAt: () -> Long,
    onPlay: (HeroItem) -> Unit,
    onRequestAccess: () -> Unit,
    onLanguage: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.Background),
    ) {
        if (visible) {
            DisposableEffect(Unit) { onDispose { nav.closeAll() } }
            val backdrop = remember { GlassBackdrop() }
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                HomeContent(controller, apps, nav, homeSetup, homeSignal, resumed, lastKeyAt, onPlay, onRequestAccess, onLanguage)
            }
        }
    }
}

@Composable
private fun HomeContent(
    controller: HomeController,
    apps: AppsController,
    nav: HomeNav,
    homeSetup: HomeSetup,
    homeSignal: Int,
    resumed: Boolean,
    lastKeyAt: () -> Long,
    onPlay: (HeroItem) -> Unit,
    onRequestAccess: () -> Unit,
    onLanguage: (String) -> Unit,
) {
    val settings = apps.settings
    val cardSize = CardSize(settings.appColumns)
    val hero = controller.hero
    val view = remember(apps.layout, apps.apps, settings.showPhoneApps) { apps.view() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val moving = nav.moving
    val overlaysOpen = nav.overlays.isNotEmpty()

    fun focusShelf(): Boolean =
        nav.focus(SHELF_PREFIX + nav.shelfPkg) || view.favorites.any { nav.focus(SHELF_PREFIX + it.packageName) }

    fun focusGrid(): Boolean =
        nav.focus(GRID_PREFIX + nav.gridKey) || view.grid.firstOrNull()?.let { nav.focus(GRID_PREFIX + it.key) } == true

    fun focusHero(): Boolean = nav.focus(HERO)

    // Back from the TV's own settings (opened from Settings › Home and startup): show that page again.
    LaunchedEffect(Unit) {
        val page = nav.reopenSettings ?: return@LaunchedEffect
        nav.reopenSettings = null
        nav.open(Overlay.Settings(nav.newId(), SETTINGS, page))
    }

    // Home while already here: back to the top (the activity has reset the navigation state).
    LaunchedEffect(homeSignal) {
        if (homeSignal == 0) return@LaunchedEffect
        withFrameNanos { }
        if (!focusHero()) focusShelf()
    }

    // Always handled: this is the home screen, so Back never leaves it.
    BackHandler {
        when {
            moving != null -> nav.moving = null
            overlaysOpen -> {
                val top = nav.overlays.lastOrNull()
                when {
                    top is Overlay.Menu && top.page != MenuPage.Main -> nav.replaceTop(top.copy(page = MenuPage.Main))
                    top is Overlay.Settings && top.page != SettingsPage.Main -> nav.replaceTop(top.copy(page = SettingsPage.Main))
                    else -> nav.closeTop()
                }
            }
            else -> focusHero() || focusShelf()
        }
    }

    // Folders that disappeared (emptied, hidden) close, with anything opened above them.
    val folderIds = remember(view) { view.grid.filterIsInstance<GridItem.Folder>().mapTo(HashSet()) { it.folder.id } }
    LaunchedEffect(folderIds, nav.overlays.size) {
        val gone = nav.overlays.indexOfFirst { it is Overlay.Folder && it.folderId !in folderIds }
        if (gone >= 0) {
            val restore = nav.overlays[gone].restoreKey
            while (nav.overlays.size > gone) nav.closeTop(restore)
        }
    }

    // Puts focus back when it was lost: after an overlay closed, a focused card disappeared, or on return
    // from another app. Menus and settings focus their own first entry.
    var hasFocus by remember { mutableStateOf(false) }
    val heroFocusable = hero.items.isNotEmpty() || hero.access == AccessState.Requestable
    LaunchedEffect(hasFocus, heroFocusable, hero.loaded, view, nav.overlays.size) {
        if (hasFocus) return@LaunchedEffect
        withFrameNanos { }
        when (val top = nav.overlays.lastOrNull()) {
            null -> {
                if (nav.focus(nav.focusKey) || focusHero()) return@LaunchedEffect
                if (!hero.loaded && heroFocusable.not() && nav.focusKey == null) return@LaunchedEffect
                if (focusShelf() || focusGrid()) return@LaunchedEffect
                nav.focus(SETTINGS)
            }
            is Overlay.Folder -> {
                val apps = view.grid.filterIsInstance<GridItem.Folder>().firstOrNull { it.folder.id == top.folderId }?.apps
                if (!nav.focus(FOLDER_PREFIX + nav.folderPkg)) apps?.firstOrNull()?.let { nav.focus(FOLDER_PREFIX + it.packageName) }
            }
            else -> Unit
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event -> moving != null && handleMoveKey(event, moving, rtl, cardSize.columns, apps, nav) },
    ) {
        val screenHeight = maxHeight
        val shelfHeight = ShelfMetrics.height(cardSize)
        val gridTop = screenHeight + GRID_GAP
        val rows = gridRows(view.grid.size, cardSize.columns)
        val gridIndex = view.grid.indexOfFirst { it.key == nav.gridKey }.coerceAtLeast(0)
        val row = gridIndex / cardSize.columns
        val lastTarget = remember { FloatArray(1) }
        val target = when (nav.area) {
            Area.Hero, Area.Shelf -> 0f
            Area.Grid -> with(density) { cameraFor(row, rows, gridTop - GRID_VIEW_TOP, cardSize.rowPitch).toPx() }
            Area.Top -> lastTarget[0]
        }
        lastTarget[0] = target
        val cameraState = animateFloatAsState(target, Springs.camera(), label = "camera")
        val camera by cameraState
        val gridAmount by animateFloatAsState(if (target > 0f) 1f else 0f, Springs.content(), label = "grid")
        // Glass on the page samples the backdrop as if the page were at rest (see LocalGlassScroll).
        val glassScroll: () -> Float = remember(cameraState) { { cameraState.value.roundToInt().toFloat() } }
        // Glass in the grid refracts the grid's own blurred, darkened backdrop (the hero sets its darkening).
        val gridGlass = remember { GlassBackdrop() }
        // While an overlay fully covers the screen, nothing under it is drawn.
        var coverCount by remember { mutableIntStateOf(0) }
        val onCovering: (Boolean) -> Unit = remember { { covering -> if (covering) coverCount++ else coverCount-- } }

        // Live glass: the backdrop, the scrolling page and the whole screen are recorded into layers (display lists,
        // not pixels) that glass and overlays draw again, blurred, behind themselves.
        val backdropLayer = rememberGraphicsLayer()
        val pageLayer = rememberGraphicsLayer()
        val baseLayer = rememberGraphicsLayer()
        val behindPage: DrawScope.() -> Unit = remember(backdropLayer) { { drawLayer(backdropLayer) } }
        val behindTopBar: DrawScope.() -> Unit = remember(backdropLayer, pageLayer) {
            {
                drawLayer(backdropLayer)
                drawLayer(pageLayer)
            }
        }

        // Remembered, so focus moving elsewhere does not recompose the hero.
        val heroFocus = remember(nav, view) {
            Modifier
                .focusKey(nav, HERO)
                .onFocusChanged { if (it.isFocused) nav.focused(HERO, Area.Hero) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focusShelf() || focusGrid()
                        Key.DirectionUp -> nav.focus(SETTINGS)
                        else -> false
                    }
                }
        }

        CompositionLocalProvider(LocalCardSize provides cardSize, LocalGlassStyle provides settings.glassStyle) {
        CompositionLocalProvider(LocalFocusAllowed provides !overlaysOpen) {
          Box(
              Modifier
                  .fillMaxSize()
                  // Recorded for overlays; while one fully covers the screen it is not drawn here at all.
                  .drawWithContent {
                      baseLayer.record { this@drawWithContent.drawContent() }
                      if (coverCount == 0) drawLayer(baseLayer)
                  },
          ) {
            CompositionLocalProvider(LocalGlassScroll provides glassScroll, LocalGlassLive provides behindPage) {
                HeroScreen(
                    state = hero,
                    autoAdvance = resumed && settings.heroAutoAdvance && !overlaysOpen,
                    lastKeyAt = lastKeyAt,
                    camera = { camera },
                    grid = { gridAmount },
                    contentBottom = Spacing.ScreenVertical + shelfHeight + HERO_SHELF_GAP,
                    logoScale = settings.logoPercent / 100f,
                    textScale = settings.textPercent / 100f,
                    scrimColor = settings.scrimColor,
                    gridGlass = gridGlass,
                    backdropLayer = backdropLayer,
                    focusModifier = heroFocus,
                    onPlay = onPlay,
                    onDismiss = controller::dismiss,
                    onRequestAccess = onRequestAccess,
                )
            }

            // The page that scrolls: the favorites shelf at the bottom of the hero, the app grid below it. Recorded
            // (in screen coordinates) for the glass of the top bar, which floats over it.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        pageLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(pageLayer)
                    }
                    .offset { IntOffset(0, -camera.roundToInt()) },
            ) {
              CompositionLocalProvider(LocalGlassScroll provides glassScroll, LocalGlassLive provides behindPage) {
                FavoritesShelf(
                    apps = view.favorites,
                    visuals = apps.visuals,
                    focusedPkg = nav.shelfPkg,
                    movingPkg = (moving?.takeIf { it.area == MoveArea.Favorites })?.key,
                    focusModifier = { app -> Modifier.focusKey(nav, SHELF_PREFIX + app.packageName) },
                    onFocused = { app ->
                        nav.shelfPkg = app.packageName
                        nav.focused(SHELF_PREFIX + app.packageName, Area.Shelf)
                    },
                    onClick = { app -> apps.launch(app) },
                    onMenu = { app, anchor ->
                        nav.open(Overlay.Menu(nav.newId(), MenuTarget.ShelfApp(app), anchor, SHELF_PREFIX + app.packageName))
                    },
                    onDown = { column ->
                        view.grid.getOrNull(column.coerceAtMost(view.grid.size - 1))
                            ?.let { nav.focus(GRID_PREFIX + it.key) } == true || focusGrid()
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = screenHeight - Spacing.ScreenVertical - shelfHeight)
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp &&
                                (focusHero() || nav.focus(SETTINGS))
                        },
                )

                // Grid glass refracts where it really is: the grid scrolls within the grid, where the camera rests.
                CompositionLocalProvider(LocalGlassBackdrop provides gridGlass, LocalGlassScroll provides null) {
                CardGrid(
                    items = view.grid,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = gridTop)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp &&
                                nav.area == Area.Grid && row == 0 && (focusShelf() || focusHero())
                        },
                ) { item ->
                    val focusKey = GRID_PREFIX + item.key
                    GridCard(
                        item = item,
                        visuals = apps.visuals,
                        focusModifier = Modifier.focusKey(nav, focusKey),
                        moving = moving != null && moving.area == MoveArea.Grid && moving.key == item.key,
                        showLabel = true,
                        onFocused = {
                            nav.gridKey = item.key
                            nav.focused(focusKey, Area.Grid)
                        },
                        onClick = {
                            when (item) {
                                is GridItem.App -> apps.launch(item.app)
                                is GridItem.Folder -> nav.open(Overlay.Folder(nav.newId(), item.folder.id, focusKey))
                            }
                        },
                        onMenu = { anchor ->
                            val menuTarget = when (item) {
                                is GridItem.App -> MenuTarget.GridApp(item.app)
                                is GridItem.Folder -> MenuTarget.GridFolder(item)
                            }
                            nav.open(Overlay.Menu(nav.newId(), menuTarget, anchor, focusKey))
                        },
                    )
                }
                }
              }
            }

            // Fixed at the top: settings and the clock, their glass over the page and the backdrop.
            CompositionLocalProvider(LocalGlassLive provides behindTopBar) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Spacing.ScreenVertical, end = Spacing.ScreenHorizontal)
                    .onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown &&
                            (nav.focus(nav.contentKey) || focusHero() || focusShelf() || focusGrid())
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = R.drawable.ic_settings,
                    onClick = { nav.open(Overlay.Settings(nav.newId(), SETTINGS)) },
                    modifier = Modifier
                        .focusKey(nav, SETTINGS)
                        .onFocusChanged { if (it.isFocused) nav.focused(SETTINGS, Area.Top) },
                )
                Spacer(Modifier.width(Spacing.M))
                ClockPill()
            }
            }

            if (moving != null) {
                MoveHint(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Spacing.ScreenVertical + 6.dp),
                )
            }
          }
        }

        CompositionLocalProvider(LocalBaseLayer provides baseLayer) {
        nav.overlays.forEachIndexed { i, overlay ->
            key(overlay.id) {
                val top = i == nav.overlays.lastIndex
                when (overlay) {
                    is Overlay.Menu -> MenuLayer(overlay, top, view, apps, nav, onCovering)
                    is Overlay.Folder -> FolderLayer(overlay, top, view, apps, nav, onCovering)
                    is Overlay.Settings -> SettingsSheet(
                        page = overlay.page,
                        top = top,
                        apps = apps,
                        onPage = { page -> nav.replaceTop(overlay.copy(page = page)) },
                        onLanguage = onLanguage,
                        homeSetup = homeSetup,
                        onCovering = onCovering,
                    )
                }
            }
        }
        }

        if (settings.perfOverlay) {
            PerfOverlay(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Spacing.ScreenHorizontal, top = Spacing.ScreenVertical),
            )
        }
        }
    }
}

/** Camera position (page offset) for focus in grid [row]: the first two rows stay put, then it follows a row at a time. */
private fun cameraFor(row: Int, rows: Int, base: Dp, pitch: Dp): Dp {
    val lastStart = max(0, rows - 3)
    return base + pitch * min(max(0, row - 1), lastStart)
}

/** D-pad moves the card, OK or Back ends move mode. Every key is consumed while moving. */
private fun handleMoveKey(event: KeyEvent, moving: Moving, rtl: Boolean, gridColumns: Int, apps: AppsController, nav: HomeNav): Boolean {
    val columns = if (moving.area == MoveArea.Favorites) 0 else gridColumns
    val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
    val backward = if (rtl) Key.DirectionRight else Key.DirectionLeft
    val delta = when (event.key) {
        forward -> 1
        backward -> -1
        Key.DirectionDown -> columns
        Key.DirectionUp -> -columns
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            if (event.type == KeyEventType.KeyUp) nav.moving = null
            return true
        }
        else -> return false
    }
    if (event.type == KeyEventType.KeyDown && delta != 0) apps.move(moving.area, moving.key, delta)
    return true
}

@Composable
private fun MoveHint(modifier: Modifier) {
    val type = LocalGlassType.current
    Box(
        modifier
            .background(GlassColors.Background.copy(alpha = 0.7f), io.github.kesemb2.glasshome.ui.theme.Shapes.Pill)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        BasicText(androidx.compose.ui.res.stringResource(R.string.move_hint), style = type.caption.copy(color = GlassColors.TextPrimary))
    }
}

@Composable
private fun MenuLayer(
    overlay: Overlay.Menu,
    top: Boolean,
    view: HomeView,
    apps: AppsController,
    nav: HomeNav,
    onCovering: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val item: GridItem = when (val t = overlay.target) {
        is MenuTarget.GridApp -> GridItem.App(t.app)
        is MenuTarget.ShelfApp -> GridItem.App(t.app)
        is MenuTarget.FolderApp -> GridItem.App(t.app)
        is MenuTarget.GridFolder -> t.folder
    }
    OverlayLayer(top = top, dim = 0.25f, onCovering = onCovering) { appear ->
        val content = menuContent(context, overlay, view, apps, nav)
        AnchoredMenu(
            anchor = overlay.anchor,
            title = content.title,
            actions = content.actions,
            appear = appear,
            page = overlay.page,
        ) { CardFace(item, apps.visuals) { 1f } }
    }
}

/** An open folder: its name and its apps over the blurred home screen; scrolls by rows like the main grid. */
@Composable
private fun FolderLayer(
    overlay: Overlay.Folder,
    top: Boolean,
    view: HomeView,
    apps: AppsController,
    nav: HomeNav,
    onCovering: (Boolean) -> Unit,
) {
    val type = LocalGlassType.current
    val density = LocalDensity.current
    val folder = view.grid.filterIsInstance<GridItem.Folder>().firstOrNull { it.folder.id == overlay.folderId } ?: return
    val moving = nav.moving
    val cardSize = LocalCardSize.current
    OverlayLayer(top = top, dim = 0.3f, onCovering = onCovering) { appear ->
        LaunchedEffect(Unit) {
            withFrameNanos { }
            val first = folder.apps.firstOrNull() ?: return@LaunchedEffect
            nav.folderPkg = null
            nav.focus(FOLDER_PREFIX + first.packageName)
        }
        val index = folder.apps.indexOfFirst { it.packageName == nav.folderPkg }.coerceAtLeast(0)
        val target = with(density) {
            cameraFor(index / cardSize.columns, gridRows(folder.apps.size, cardSize.columns), 0.dp, cardSize.rowPitch).toPx()
        }
        val camera by animateFloatAsState(target, Springs.camera(), label = "folderCamera")
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 0.94f + 0.06f * appear()
                    scaleX = s
                    scaleY = s
                },
        ) {
            Spacer(Modifier.height(Spacing.ScreenVertical + 20.dp))
            BasicText(
                text = folder.folder.name,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
                style = type.title1.copy(color = GlassColors.TextPrimary),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = FOLDER_GRID_TOP - Spacing.ScreenVertical - 20.dp - 40.dp)
                    .clipToBounds(),
            ) {
                CardGrid(
                    items = folder.apps.map { GridItem.App(it) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, -camera.roundToInt()) },
                ) { item ->
                    val app = (item as GridItem.App).app
                    val focusKey = FOLDER_PREFIX + app.packageName
                    GridCard(
                        item = item,
                        visuals = apps.visuals,
                        focusModifier = Modifier.focusKey(nav, focusKey),
                        moving = moving != null && moving.area == MoveArea.Folder(folder.folder.id) && moving.key == app.packageName,
                        showLabel = true,
                        onFocused = { nav.folderPkg = app.packageName },
                        onClick = { apps.launch(app) },
                        onMenu = { anchor ->
                            nav.open(Overlay.Menu(nav.newId(), MenuTarget.FolderApp(app, folder.folder.id), anchor, focusKey))
                        },
                    )
                }
            }
        }
    }
}
