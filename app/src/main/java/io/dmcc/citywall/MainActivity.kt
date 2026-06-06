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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val FREQ_LABELS = listOf(
    "15 minutes", "30 minutes", "Hourly", "Every 3 hours",
    "Every 6 hours", "Every 12 hours", "Daily",
)
private val FREQ_VALUES = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
private val PALETTE_NAMES = MapWallpaperGenerator.Palette.ALL.map { it.name }
private val CAPITAL_NAMES = Capitals.ALL.map { it.name }
private val TARGET_LABELS = listOf("Home & lock", "Home screen", "Lock screen")
private val TARGET_VALUES = listOf(Settings.TARGET_BOTH, Settings.TARGET_HOME, Settings.TARGET_LOCK)

private enum class PermStep { LOCATION, BACKGROUND, NOTIFICATIONS, DONE }
private enum class Source { GPS, MANUAL, SAMPLE }

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

    var paletteName by remember { mutableStateOf(settings.paletteName) }
    var freqMinutes by remember { mutableStateOf(settings.updateMinutes) }
    var useCapital by remember { mutableStateOf(settings.useCapital) }
    var autoUpdate by remember { mutableStateOf(settings.autoUpdate) }
    var joinWorldMap by remember { mutableStateOf(settings.joinWorldMap) }
    var wallpaperTarget by remember { mutableStateOf(settings.wallpaperTarget) }
    var manualLocation by remember { mutableStateOf(settings.manualLocation) }
    var dismissedBg by remember { mutableStateOf(settings.dismissedBackgroundPrompt) }
    var dismissedNotif by remember { mutableStateOf(settings.dismissedNotificationsPrompt) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewCity by remember { mutableStateOf<String?>(null) }
    var previewSource by remember { mutableStateOf(Source.SAMPLE) }
    var generating by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var hasBackup by remember { mutableStateOf(WallpaperBackup.available(context)) }

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

    // Generate the map (preview only, or generate-and-set). Source priority: a manual
    // city, else GPS, else a sample capital (so something shows before any permission).
    fun generate(apply: Boolean) {
        generating = true
        statusMsg = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val manual = if (manualLocation != null) settings.manualFix() else null
                    val pair: Pair<CityFix?, Source> = when {
                        manual != null -> manual to Source.MANUAL
                        hasCoarse -> CityResolver(context).currentCity(useCapital) to Source.GPS
                        else -> sampleCapital(context)
                            .let { CityFix(it.name, it.lat, it.lon) } to Source.SAMPLE
                    }
                    val fix = pair.first
                        ?: return@withContext Result.failure(IllegalStateException("no-location"))
                    val (w, h) = WallpaperWorker.screenSize(context)
                    val generator = MapWallpaperGenerator(
                        palette = settings.palette,
                        geometryCacheDir = File(context.filesDir, "geometry"),
                    )
                    val bmp = WallpaperRepository(context, generator)
                        .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
                    if (apply) {
                        WallpaperBackup.backupOnce(context)
                        WallpaperWorker.applyWallpaper(context, bmp, settings.wallpaperFlags())
                    }
                    Result.success(Triple(fix.name, bmp, pair.second))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            generating = false
            result.onSuccess { (name, bmp, src) ->
                preview = bmp
                previewCity = name
                previewSource = src
                if (apply) {
                    hasBackup = WallpaperBackup.available(context)
                    statusMsg = "Wallpaper set for $name"
                }
            }.onFailure {
                statusMsg = if (it.message == "no-location") {
                    "Couldn't get a location — set one manually below or allow access."
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

    // Show what the app does before any permission: preview the manual city, or a
    // sample capital. Cached after the first time, so later launches are free.
    LaunchedEffect(Unit) {
        if (preview == null && (manualLocation != null || !hasCoarse)) generate(apply = false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                    manualLocation != null -> "Preview"
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
            if (preview != null) generate(apply = false) // re-preview straight away
        }

        SectionLabel("LOCATION")
        SwitchRow(
            "Set location manually",
            "Pick a city instead of GPS. Manual spots are preview-only — they can't claim a city.",
            manualLocation != null,
        ) { on ->
            manualLocation = if (on) (manualLocation ?: sampleCapital(context).name) else null
            settings.manualLocation = manualLocation
            generate(apply = false)
        }
        val manualCity = manualLocation
        if (manualCity != null) {
            PickerRow("City", CAPITAL_NAMES, CAPITAL_NAMES.indexOf(manualCity)) { i ->
                manualLocation = CAPITAL_NAMES[i]
                settings.manualLocation = CAPITAL_NAMES[i]
                generate(apply = false)
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

        SectionLabel("WORLD MAP")
        SwitchRow(
            "Join the world map",
            "Opt in to claim cities. Off keeps everything on this device.",
            joinWorldMap,
        ) { on ->
            joinWorldMap = on
            settings.joinWorldMap = on
        }
        WorldMapInfo(joinWorldMap)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val s = size.minDimension
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) =
                    drawLine(c, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), w, StrokeCap.Round)
                line(0.10f, 0.58f, 0.92f, 0.58f, 4f, CwAccent)
                line(0.46f, 0.10f, 0.46f, 0.92f, 3f, Color(0xFF566073))
                line(0.18f, 0.30f, 0.80f, 0.74f, 2.5f, Color(0xFF444E5F))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "CityWall",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }
        Text(
            "Your city, drawn in roads.",
            color = CwMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, top = 2.dp),
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
private fun WorldMapInfo(joined: Boolean) {
    Surface(color = CwSurface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What's shared", style = MonoLabel, color = CwMuted)
            Text(
                "• The names of cities you claim, and a public handle you choose — shown on a shared world map.\n" +
                    "• Being first to a city makes you its “Pathfinder”.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            )
            Text(
                "Never shared: your live location, your home, or anything at all while this is off. " +
                    "Manual-location previews can't claim cities. You can leave any time.",
                color = CwMuted,
                fontSize = 12.sp,
            )
            if (joined) {
                Text("Claiming goes live with the CityWall online service.", color = CwAccent, fontSize = 12.sp)
            }
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

// --- helpers ---

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** A capital to preview before any permission: the device-locale country, else London. */
private fun sampleCapital(context: Context): Capital {
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
