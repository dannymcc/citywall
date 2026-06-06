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

private val FREQ_LABELS = listOf(
    "15 minutes", "30 minutes", "Hourly", "Every 3 hours",
    "Every 6 hours", "Every 12 hours", "Daily",
)
private val FREQ_VALUES = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
private val PALETTE_NAMES = MapWallpaperGenerator.Palette.ALL.map { it.name }

/** The next permission to guide the user toward, one at a time. */
private enum class PermStep { LOCATION, BACKGROUND, NOTIFICATIONS, DONE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CityWallTheme { CityWallScreen() }
        }
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

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewCity by remember { mutableStateOf<String?>(null) }
    var previewIsSample by remember { mutableStateOf(false) }
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
        !hasBackground -> PermStep.BACKGROUND
        !hasNotifications -> PermStep.NOTIFICATIONS
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

    fun generatePreview(useLocation: Boolean) {
        generating = true
        statusMsg = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val fix = if (useLocation) {
                        CityResolver(context).currentCity(useCapital)
                    } else {
                        sampleCapital(context).let { CityFix(it.name, it.lat, it.lon) }
                    } ?: return@withContext Result.failure(IllegalStateException("no-location"))
                    val (w, h) = WallpaperWorker.screenSize(context)
                    val generator = MapWallpaperGenerator(palette = settings.palette)
                    val bmp = WallpaperRepository(context, generator)
                        .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
                    Result.success(Triple(fix.name, bmp, !useLocation))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            generating = false
            result.onSuccess { (name, bmp, isSample) ->
                preview = bmp
                previewCity = name
                previewIsSample = isSample
            }.onFailure {
                statusMsg = if (it.message == "no-location") {
                    "Couldn't get your location — allow access below."
                } else {
                    "Couldn't generate the map: ${it.message}"
                }
            }
        }
    }

    fun setAsWallpaper() {
        val bmp = preview ?: return
        generating = true
        statusMsg = null
        scope.launch {
            withContext(Dispatchers.IO) {
                WallpaperBackup.backupOnce(context) // preserve their original first
                WallpaperWorker.applyWallpaper(context, bmp)
            }
            generating = false
            hasBackup = WallpaperBackup.available(context)
            statusMsg = "Wallpaper set" + (previewCity?.let { " for $it" } ?: "")
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

    // Show what the app does before any permission is requested: generate a sample
    // capital once (cached after the first time, so it's free on later launches).
    LaunchedEffect(Unit) {
        if (preview == null && !hasCoarse) generatePreview(useLocation = false)
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

        HeroPreview(
            preview = preview,
            cityName = previewCity,
            isSample = previewIsSample,
            generating = generating,
        )

        // Primary actions: preview first, then set, then optional restore.
        Button(
            onClick = { generatePreview(useLocation = hasCoarse) },
            enabled = !generating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (preview == null) CwAccent else CwSurface,
                contentColor = if (preview == null) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            ),
        ) {
            Text(
                if (hasCoarse) "Preview my city" else "Preview a sample city",
                fontWeight = FontWeight.SemiBold,
            )
        }

        Button(
            onClick = { setAsWallpaper() },
            enabled = preview != null && !generating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CwAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = CwSurface,
                disabledContentColor = CwMuted,
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
                Text("Set as wallpaper", fontWeight = FontWeight.SemiBold)
            }
        }

        if (hasBackup) {
            OutlinedButton(
                onClick = { restoreWallpaper() },
                enabled = !generating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Restore previous wallpaper", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        statusMsg?.let { Text(it, color = CwMuted, fontSize = 13.sp) }

        // Guided permissions — one step at a time.
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
            )
        }

        SectionLabel("LOOK")
        PickerRow("Palette", PALETTE_NAMES, PALETTE_NAMES.indexOf(paletteName)) { i ->
            paletteName = PALETTE_NAMES[i]
            settings.paletteName = paletteName
            if (preview != null) generatePreview(useLocation = !previewIsSample) // re-preview in new palette
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
        SwitchRow(
            "Capital cities",
            "Map the country's capital instead of your town.",
            useCapital,
        ) { on ->
            useCapital = on
            settings.useCapital = on
        }

        SectionLabel("WORLD MAP")
        SwitchRow(
            "Join the world map",
            "Off keeps everything on this device. On shares the cities you claim.",
            joinWorldMap,
        ) { on ->
            joinWorldMap = on
            settings.joinWorldMap = on
        }
        if (joinWorldMap) {
            Text(
                "Claiming goes live with the CityWall online service.",
                color = CwMuted,
                fontSize = 12.sp,
            )
        }

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
private fun HeroPreview(preview: Bitmap?, cityName: String?, isSample: Boolean, generating: Boolean) {
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

            if (isSample && preview != null) {
                Surface(
                    color = Color(0xCC0E1116),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Text(
                        "SAMPLE",
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
                            if (isSample) "a sample — preview your own next" else "cached on this device",
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
            val faint = Color(0xFF333B49)
            val w = size.width
            val h = size.height
            for (k in 1..5) drawLine(faint, Offset(0f, h * k / 6f), Offset(w, h * k / 6f), 1.5f)
            for (k in 1..3) drawLine(faint, Offset(w * k / 4f, 0f), Offset(w * k / 4f, h), 1.5f)
            drawLine(CwAccent, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 4f, StrokeCap.Round)
        }
        Text("Generating a preview…", color = CwMuted, fontSize = 14.sp)
    }
}

@Composable
private fun PermissionStepCard(step: PermStep, onAction: () -> Unit) {
    val (title, body, cta) = when (step) {
        PermStep.LOCATION -> Triple(
            "Step 1 — Location",
            "To map the town you're actually in, CityWall needs coarse location. That's it — never precise.",
            "Allow location",
        )
        PermStep.BACKGROUND -> Triple(
            "Step 2 — Background location",
            "So the wallpaper can update as you travel, choose “Allow all the time” on the next screen.",
            "Open settings",
        )
        PermStep.NOTIFICATIONS -> Triple(
            "Step 3 — Notifications (optional)",
            "Let CityWall tell you when it sets a new wallpaper. You can skip this.",
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
