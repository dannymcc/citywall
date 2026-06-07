package io.dmcc.citywall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.dmcc.citywall.ui.CityWallTheme
import io.dmcc.citywall.ui.CwAccent
import io.dmcc.citywall.ui.CwMuted
import io.dmcc.citywall.ui.CwOutline
import io.dmcc.citywall.ui.CwSurface
import io.dmcc.citywall.ui.MapSlate
import io.dmcc.citywall.ui.MonoLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Year
import java.util.TimeZone

private val FREQ_LABELS = listOf(
    "15 minutes", "30 minutes", "Hourly", "Every 3 hours",
    "Every 6 hours", "Every 12 hours", "Daily",
)
private val FREQ_VALUES = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
private val PALETTE_NAMES = MapWallpaperGenerator.Palette.ALL.map { it.name }
private val TARGET_LABELS = listOf("Home & lock", "Home screen", "Lock screen")
private val TARGET_VALUES = listOf(Settings.TARGET_BOTH, Settings.TARGET_HOME, Settings.TARGET_LOCK)
private val RIVER_LABELS = listOf("Subtle", "Bold", "Off")
private val RIVER_VALUES = listOf("subtle", "bold", "off")
private val TAB_LABELS = listOf("Wallpaper", "Pathfinder", "Settings", "About")
// Country options for the embassy overlay: "" (none) + ISO codes sorted by name.
private val EMBASSY_CODES: List<String> =
    listOf("") + java.util.Locale.getISOCountries().sortedBy { java.util.Locale("", it).displayCountry }
private val EMBASSY_LABELS: List<String> =
    EMBASSY_CODES.map { if (it.isEmpty()) "None" else java.util.Locale("", it).displayCountry }

private enum class PermStep { LOCATION, BACKGROUND, NOTIFICATIONS, DONE }
private enum class Source { GPS, MANUAL, SAMPLE }
private class GenOutcome(val name: String, val bmp: Bitmap, val source: Source, val claim: String?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { CityWallTheme { CityWallScreen() } }
    }
}

@Composable
private fun CityWallScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    val explorerId = remember { settings.explorerId }

    var selectedTab by remember { mutableStateOf(0) }

    var paletteName by remember { mutableStateOf(settings.paletteName) }
    var freqMinutes by remember { mutableStateOf(settings.updateMinutes) }
    var useCapital by remember { mutableStateOf(settings.useCapital) }
    var autoUpdate by remember { mutableStateOf(settings.autoUpdate) }
    var joinWorldMap by remember { mutableStateOf(settings.joinWorldMap) }
    var wallpaperTarget by remember { mutableStateOf(settings.wallpaperTarget) }
    var embassyCountry by remember { mutableStateOf(settings.embassyCountry) }
    var zoomMetres by remember { mutableStateOf(settings.zoomMetres) }
    var riverStyle by remember { mutableStateOf(settings.riverStyle) }
    var manualName by remember { mutableStateOf(settings.manualLocation) }
    var manualQuery by remember { mutableStateOf("") }
    var dismissedBg by remember { mutableStateOf(settings.dismissedBackgroundPrompt) }
    var dismissedNotif by remember { mutableStateOf(settings.dismissedNotificationsPrompt) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewCity by remember { mutableStateOf<String?>(null) }
    var previewSource by remember { mutableStateOf(Source.SAMPLE) }
    var generating by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var hasBackup by remember { mutableStateOf(WallpaperBackup.available(context)) }
    var showLicences by remember { mutableStateOf(false) }
    var leaderboard by remember { mutableStateOf<List<PathfinderApi.Leader>>(emptyList()) }
    var leaderboardLoading by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }

    var permRefresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasCoarse = remember(permRefresh) {
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val hasBackground = remember(permRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val hasNotifications = remember(permRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val step = when {
        !hasCoarse -> PermStep.LOCATION
        !hasBackground && !dismissedBg -> PermStep.BACKGROUND
        !hasNotifications && !dismissedNotif -> PermStep.NOTIFICATIONS
        else -> PermStep.DONE
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permRefresh++ }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permRefresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permRefresh++ }

    fun generate(apply: Boolean) {
        generating = true
        statusMsg = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val manual = settings.manualFix()
                    val pair: Pair<CityFix?, Source> = when {
                        manual != null -> manual to Source.MANUAL
                        hasCoarse -> CityResolver(context).currentCity(useCapital) to Source.GPS
                        else -> sampleCapital(context)
                            .let { CityFix(it.name, it.lat, it.lon) } to Source.SAMPLE
                    }
                    val fix = pair.first
                        ?: return@withContext Result.failure(IllegalStateException("no-location"))
                    val (w, h) = WallpaperWorker.screenSize(context)
                    val local = MapWallpaperGenerator(
                        palette = settings.palette,
                        halfHeightMetres = settings.zoomMetres.toDouble(),
                        geometryCacheDir = File(context.filesDir, "geometry"),
                    )
                    val generator = RemoteWallpaperGenerator(
                        settings.palette, local, settings.embassyCountry,
                        settings.zoomMetres, settings.riverStyle,
                    )
                    val bmp = WallpaperRepository(context, generator)
                        .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
                    var claim: String? = null
                    if (apply) {
                        WallpaperBackup.backupOnce(context)
                        WallpaperWorker.applyWallpaper(context, bmp, settings.wallpaperFlags())
                        if (pair.second == Source.GPS && joinWorldMap) {
                            claim = PathfinderApi.claim(explorerId, fix.name, fix.lat, fix.lon, false)
                        }
                    }
                    Result.success(GenOutcome(fix.name, bmp, pair.second, claim))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            generating = false
            outcome.onSuccess { o ->
                preview = o.bmp
                previewCity = o.name
                previewSource = o.source
                if (apply) {
                    hasBackup = WallpaperBackup.available(context)
                    statusMsg = when (o.claim) {
                        "claimed" -> "Wallpaper set — you're the Pathfinder of ${o.name}!"
                        "taken" -> "Wallpaper set. ${o.name} is already claimed by another explorer."
                        "yours" -> "Wallpaper set for ${o.name} — your claim."
                        else -> "Wallpaper set for ${o.name}"
                    }
                }
            }.onFailure {
                statusMsg = if (it.message == "no-location") {
                    "Couldn't get a location — set one manually in Settings or allow access."
                } else {
                    "Couldn't generate the map: ${it.message}"
                }
            }
        }
    }

    fun restoreWallpaper() {
        generating = true
        statusMsg = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) { WallpaperBackup.restore(context) }
            generating = false
            statusMsg = if (ok) "Previous wallpaper restored" else "No backup available to restore"
        }
    }

    fun resolveManual(query: String) {
        if (query.isBlank()) return
        generating = true
        statusMsg = null
        scope.launch {
            val coords = withContext(Dispatchers.IO) { CityResolver(context).coordsFor(query) }
            generating = false
            if (coords != null) {
                settings.setManual(query, coords.first, coords.second)
                manualName = query
                generate(false)
            } else {
                statusMsg = "Couldn't find “$query”."
            }
        }
    }

    fun refreshLeaderboard() {
        leaderboardLoading = true
        scope.launch {
            val l = withContext(Dispatchers.IO) { PathfinderApi.leaderboard() }
            leaderboard = l
            leaderboardLoading = false
        }
    }

    fun checkForUpdate() {
        updating = true
        updateMsg = "Checking…"
        scope.launch {
            val latest = withContext(Dispatchers.IO) { Updater.latest() }
            if (latest == null) {
                updating = false
                updateMsg = "Couldn't reach the update server."
                return@launch
            }
            if (latest.versionName.isEmpty() || latest.versionName == BuildConfig.VERSION_NAME) {
                updating = false
                updateMsg = "You're on the latest version (${BuildConfig.VERSION_NAME})."
                return@launch
            }
            updateMsg = "Downloading ${latest.versionName}…"
            val apk = withContext(Dispatchers.IO) { Updater.download(context) }
            updating = false
            if (apk == null) {
                updateMsg = "Download failed — try again."
                return@launch
            }
            updateMsg = if (Updater.install(context, apk)) {
                "Opening installer for ${latest.versionName}…"
            } else {
                "Allow installing apps from CityWall, then tap Check for updates again."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (preview == null) generate(apply = false)
    }
    LaunchedEffect(joinWorldMap, selectedTab) {
        if (joinWorldMap && selectedTab == 1) refreshLeaderboard()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = CwSurface) {
                TAB_LABELS.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        icon = { TabIcon(i, selectedTab == i) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CwAccent,
                            selectedTextColor = CwAccent,
                            unselectedIconColor = CwMuted,
                            unselectedTextColor = CwMuted,
                            indicatorColor = CwAccent.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { inner ->
        when (selectedTab) {
            0 -> TabColumn(inner) {
                Header()
                HeroPreview(preview, previewCity, previewSource, generating)
                OutlinedButton(
                    onClick = { generate(apply = false) },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        when {
                            manualName != null -> "Preview"
                            hasCoarse -> "Preview my city"
                            else -> "Preview a sample"
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Button(
                    onClick = { generate(apply = true) },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CwAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Working…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Generate & set wallpaper", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (hasBackup) {
                    TextButton(onClick = { restoreWallpaper() }, enabled = !generating) {
                        Text("Restore previous wallpaper", color = CwMuted)
                    }
                }
                preview?.let { bmp ->
                    TextButton(onClick = { shareWallpaper(context, bmp) }, enabled = !generating) {
                        Text("Share this map", color = CwAccent)
                    }
                }
                statusMsg?.let { Text(it, color = CwMuted, fontSize = 13.sp) }
                if (step != PermStep.DONE) {
                    PermissionStepCard(
                        step = step,
                        onAction = {
                            when (step) {
                                PermStep.LOCATION ->
                                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                PermStep.BACKGROUND ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        openAppSettings(context)
                                    } else {
                                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                PermStep.NOTIFICATIONS ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                PermStep.DONE -> {}
                            }
                        },
                        onDismiss = when (step) {
                            PermStep.BACKGROUND -> {
                                { settings.dismissedBackgroundPrompt = true; dismissedBg = true }
                            }
                            PermStep.NOTIFICATIONS -> {
                                { settings.dismissedNotificationsPrompt = true; dismissedNotif = true }
                            }
                            else -> null
                        },
                    )
                }
                SectionLabel("WALLPAPER")
                PickerRow("Apply to", TARGET_LABELS, TARGET_VALUES.indexOf(wallpaperTarget)) { i ->
                    wallpaperTarget = TARGET_VALUES[i]
                    settings.wallpaperTarget = wallpaperTarget
                }
                SectionLabel("LOOK")
                PickerRow("Palette", PALETTE_NAMES, PALETTE_NAMES.indexOf(paletteName)) { i ->
                    paletteName = PALETTE_NAMES[i]
                    settings.paletteName = paletteName
                    if (preview != null) generate(apply = false)
                }
                PickerRow("Rivers", RIVER_LABELS, RIVER_VALUES.indexOf(riverStyle).coerceAtLeast(0)) { i ->
                    riverStyle = RIVER_VALUES[i]
                    settings.riverStyle = riverStyle
                    if (preview != null) generate(apply = false)
                }
                SectionLabel("ZOOM")
                Text("$zoomMetres m view — smaller is closer", color = CwMuted, fontSize = 12.sp)
                Slider(
                    value = zoomMetres.toFloat(),
                    onValueChange = { zoomMetres = it.toInt() },
                    valueRange = 1200f..4500f,
                    onValueChangeFinished = {
                        settings.zoomMetres = zoomMetres
                        if (preview != null) generate(apply = false)
                    },
                )
            }

            1 -> TabColumn(inner) {
                Header()
                SectionLabel("PATHFINDER")
                SwitchRow(
                    "Join Pathfinder",
                    "Claim the cities you visit and climb the leaderboard.",
                    joinWorldMap,
                ) { on ->
                    joinWorldMap = on
                    settings.joinWorldMap = on
                    if (on) refreshLeaderboard()
                }
                if (!joinWorldMap) {
                    PathfinderExplainer()
                } else {
                    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Your explorer ID", style = MonoLabel, color = CwMuted)
                            Text(explorerId, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your anonymous identity on the leaderboard.", color = CwMuted, fontSize = 12.sp)
                        }
                    }
                    SectionLabel("LEADERBOARD")
                    when {
                        leaderboardLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CwAccent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Loading…", color = CwMuted, fontSize = 13.sp)
                        }
                        leaderboard.isEmpty() -> Text(
                            "No pathfinders yet — be the first to claim a city.",
                            color = CwMuted,
                            fontSize = 13.sp,
                        )
                        else -> leaderboard.forEach { LeaderRow(it, it.explorerId == explorerId) }
                    }
                    Text(
                        "Never shared: your live location or home. Manual-location previews can't claim.",
                        color = CwMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            2 -> TabColumn(inner) {
                Header()
                SectionLabel("LOCATION")
                OutlinedTextField(
                    value = manualQuery,
                    onValueChange = { manualQuery = it },
                    singleLine = true,
                    label = { Text("Search any place on earth") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { resolveManual(manualQuery) },
                    enabled = !generating && manualQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CwAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Use this location")
                }
                manualName?.let { mn ->
                    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Manual location", style = MonoLabel, color = CwMuted)
                                Text(mn, color = MaterialTheme.colorScheme.onBackground)
                            }
                            TextButton(onClick = {
                                settings.clearManual()
                                manualName = null
                                generate(false)
                            }) { Text("Use GPS", color = CwAccent) }
                        }
                    }
                }
                SwitchRow(
                    "Capital cities",
                    "When using GPS, map the country's capital instead of your town.",
                    useCapital,
                ) { on ->
                    useCapital = on
                    settings.useCapital = on
                }
                SectionLabel("EMBASSIES")
                PickerRow(
                    "Show embassies from",
                    EMBASSY_LABELS,
                    EMBASSY_CODES.indexOf(embassyCountry ?: "").coerceAtLeast(0),
                ) { i ->
                    val code = EMBASSY_CODES[i].ifEmpty { null }
                    embassyCountry = code
                    settings.embassyCountry = code
                    if (preview != null) generate(apply = false)
                }
                Text(
                    "Marks that country's embassies on the map as subtle dots, where they fall in view.",
                    color = CwMuted,
                    fontSize = 12.sp,
                )
                SectionLabel("UPDATES")
                PickerRow("Every", FREQ_LABELS, FREQ_VALUES.indexOf(freqMinutes)) { i ->
                    freqMinutes = FREQ_VALUES[i]
                    settings.updateMinutes = freqMinutes
                    if (autoUpdate) WallpaperScheduler.enablePeriodic(context)
                }
                SwitchRow("Auto-update", "Refresh the wallpaper in the background.", autoUpdate) { on ->
                    autoUpdate = on
                    settings.autoUpdate = on
                    if (on) WallpaperScheduler.enablePeriodic(context)
                    else WallpaperScheduler.disablePeriodic(context)
                }
            }

            else -> TabColumn(inner) {
                Header()
                SectionLabel("DATA")
                Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "CityWall draws maps from OpenStreetMap road and water data.",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                        )
                        Text(
                            "To save time, popular and already-claimed places are fetched ready-made " +
                                "from our server instead of being drawn on your device. Anything the " +
                                "server doesn't have is generated locally.",
                            color = CwMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                SectionLabel("UPDATES")
                Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Version ${BuildConfig.VERSION_NAME}",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(
                            onClick = { checkForUpdate() },
                            enabled = !updating,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Check for updates", color = MaterialTheme.colorScheme.onBackground)
                        }
                        updateMsg?.let { Text(it, color = CwMuted, fontSize = 12.sp) }
                    }
                }
                SectionLabel("ABOUT")
                Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "© ${Year.now().value} Danny McClelland",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "dmcc.io",
                            color = CwAccent,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { openUrl(context, "https://dmcc.io") },
                        )
                        OutlinedButton(
                            onClick = { showLicences = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Open source licences", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }

    if (showLicences) {
        LicencesDialog(onOpenUrl = { openUrl(context, it) }, onDismiss = { showLicences = false })
    }
}

@Composable
private fun TabColumn(inner: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun TabIcon(index: Int, active: Boolean) {
    val c = if (active) CwAccent else CwMuted
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f)
        when (index) {
            0 -> {
                drawRoundRect(
                    c,
                    topLeft = Offset(s * 0.12f, s * 0.16f),
                    size = Size(s * 0.76f, s * 0.68f),
                    cornerRadius = CornerRadius(s * 0.12f, s * 0.12f),
                    style = stroke,
                )
                drawLine(c, Offset(s * 0.12f, s * 0.56f), Offset(s * 0.88f, s * 0.42f), s * 0.08f, StrokeCap.Round)
            }
            1 -> {
                drawCircle(c, radius = s * 0.36f, center = Offset(s / 2, s / 2), style = stroke)
                drawLine(c, Offset(s * 0.38f, s * 0.62f), Offset(s * 0.62f, s * 0.38f), s * 0.09f, StrokeCap.Round)
                drawLine(c, Offset(s * 0.62f, s * 0.38f), Offset(s * 0.54f, s * 0.5f), s * 0.09f, StrokeCap.Round)
            }
            2 -> {
                for (k in 0..2) {
                    val y = s * (0.3f + 0.2f * k)
                    drawLine(c, Offset(s * 0.18f, y), Offset(s * 0.82f, y), s * 0.08f, StrokeCap.Round)
                    drawCircle(c, radius = s * 0.07f, center = Offset(s * (0.3f + 0.25f * k), y))
                }
            }
            else -> {
                drawCircle(c, radius = s * 0.36f, center = Offset(s / 2, s / 2), style = stroke)
                drawCircle(c, radius = s * 0.04f, center = Offset(s / 2, s * 0.34f))
                drawLine(c, Offset(s / 2, s * 0.46f), Offset(s / 2, s * 0.66f), s * 0.08f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            "CityWall",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        Text(
            "Your city, drawn in roads.",
            color = CwMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun HeroPreview(preview: Bitmap?, cityName: String?, source: Source, generating: Boolean) {
    Surface(
        color = MapSlate,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlaceholderMap()
            }

            val badge = when (source) {
                Source.SAMPLE -> "SAMPLE"
                Source.MANUAL -> "MANUAL"
                Source.GPS -> null
            }
            if (badge != null && preview != null) {
                Surface(
                    color = Color(0xCC0E1116),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Text(
                        badge,
                        style = MonoLabel,
                        color = CwAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (cityName != null && preview != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(20.dp),
                ) {
                    Column {
                        Text(cityName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (source) {
                                Source.SAMPLE -> "a sample — preview your own next"
                                Source.MANUAL -> "manual location · can't claim"
                                Source.GPS -> "cached on this device"
                            },
                            color = CwMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (generating) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = CwAccent)
            }
        }
    }
}

@Composable
private fun PlaceholderMap() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val faint = Color(0xFF161D26)
            val w = size.width
            val h = size.height
            for (k in 1..5) drawLine(faint, Offset(0f, h * k / 6f), Offset(w, h * k / 6f), 1.5f)
            for (k in 1..3) drawLine(faint, Offset(w * k / 4f, 0f), Offset(w * k / 4f, h), 1.5f)
            drawLine(Color(0xFF00040B), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 4f, StrokeCap.Round)
        }
        Text("Generating a preview…", color = CwMuted, fontSize = 14.sp)
    }
}

@Composable
private fun PathfinderExplainer() {
    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Be a Pathfinder", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(
                "Be the first to set a wallpaper for a city and you claim it — you become its " +
                    "Pathfinder. Turning this on enters you on a global leaderboard of who's " +
                    "claimed the most cities.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            )
            Text(
                "When on, you share the names of cities you claim and your explorer ID. Never " +
                    "shared: your live location or home — and nothing at all while this is off. " +
                    "Manual-location previews can't claim. You can leave any time.",
                color = CwMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LeaderRow(leader: PathfinderApi.Leader, isMe: Boolean) {
    Surface(
        color = if (isMe) CwAccent.copy(alpha = 0.14f) else CwSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${leader.rank}", color = CwAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
            Text(
                leader.explorerId + if (isMe) "  (you)" else "",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text("${leader.claims}", color = CwMuted)
        }
    }
}

@Composable
private fun LicencesDialog(onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CwSurface,
        title = { Text("Open source", color = MaterialTheme.colorScheme.onBackground) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = CwAccent) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LicenceBlock(
                    "Map data",
                    "© OpenStreetMap contributors, under the Open Database Licence (ODbL).",
                    "openstreetmap.org/copyright",
                    "https://www.openstreetmap.org/copyright",
                    onOpenUrl,
                )
                LicenceBlock("Map queries", "Overpass API.", "overpass-api.de", "https://overpass-api.de", onOpenUrl)
                Text(
                    "Maps are rendered from OpenStreetMap road and water data — no third-party " +
                        "map tiles or imagery providers.",
                    color = CwMuted,
                    fontSize = 13.sp,
                )
                Text("Built with", style = MonoLabel, color = CwMuted)
                Text(
                    "• Jetpack Compose & AndroidX — Apache 2.0\n" +
                        "• Material 3 — Apache 2.0\n" +
                        "• Kotlin & Kotlinx Coroutines — Apache 2.0\n" +
                        "• AndroidX WorkManager, Core, SplashScreen — Apache 2.0",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                )
            }
        },
    )
}

@Composable
private fun LicenceBlock(
    title: String,
    body: String,
    linkText: String,
    linkUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
        Text(body, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
        Text(linkText, color = CwAccent, fontSize = 12.sp, modifier = Modifier.clickable { onOpenUrl(linkUrl) })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MonoLabel, color = CwMuted, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun PickerRow(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.getOrElse(selectedIndex) { options.firstOrNull().orEmpty() }
    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(current, color = CwAccent, fontWeight = FontWeight.Medium)
                Text("  ▾", color = CwMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onSelect(i)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                Text(subtitle, color = CwMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = CwAccent,
                    uncheckedTrackColor = CwSurface,
                    uncheckedBorderColor = CwOutline,
                ),
            )
        }
    }
}

@Composable
private fun PermissionStepCard(step: PermStep, onAction: () -> Unit, onDismiss: (() -> Unit)?) {
    val (title, body, cta) = when (step) {
        PermStep.LOCATION -> Triple(
            "Step 1 — Location",
            "To map the town you're actually in, CityWall needs coarse location. That's it — never precise.",
            "Allow location",
        )
        PermStep.BACKGROUND -> Triple(
            "Step 2 — Background location",
            "So the wallpaper can update as you travel: open Settings → Permissions → Location → " +
                "“Allow all the time”. (That's the location permission, not the battery setting.)",
            "Open settings",
        )
        PermStep.NOTIFICATIONS -> Triple(
            "Step 3 — Notifications (optional)",
            "Let CityWall tell you when it sets a new wallpaper.",
            "Allow notifications",
        )
        PermStep.DONE -> Triple("", "", "")
    }
    Surface(color = CwAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MonoLabel, color = CwAccent)
            Text(body, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CwAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(cta)
                }
                if (onDismiss != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("Not now", color = CwMuted) }
                }
            }
        }
    }
}

// --- helpers ---

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** A capital to preview before any permission: prefer the device timezone's city
 *  (often where you actually are), then the locale country, then London. */
private fun sampleCapital(context: Context): Capital {
    val tzCity = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
    Capitals.byName(tzCity)?.let { return it }
    val country = context.resources.configuration.locales[0].country
    return Capitals.forCountry(country) ?: Capitals.forCountry("GB")!!
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

private fun shareWallpaper(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.externalCacheDir, "citywall-share.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share map").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: Exception) {
    }
}
