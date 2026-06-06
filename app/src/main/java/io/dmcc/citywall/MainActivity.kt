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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CityWallTheme {
                CityWallScreen()
            }
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
    var cityName by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    // Re-check permissions whenever we come back to the foreground (e.g. from Settings).
    var permRefresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keyed on permRefresh so they re-evaluate after returning from the system dialog
    // or the Settings screen.
    val hasCoarse = remember(permRefresh) {
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val hasBackground = remember(permRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    val fgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permRefresh++ }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permRefresh++ }

    fun update() {
        generating = true
        statusMsg = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val fix = CityResolver(context).currentCity(useCapital)
                        ?: return@withContext Result.failure(IllegalStateException("no-location"))
                    val (w, h) = WallpaperWorker.screenSize(context)
                    val generator = MapWallpaperGenerator(palette = settings.palette)
                    val bmp = WallpaperRepository(context, generator)
                        .getOrCreate(fix.name, fix.lat, fix.lon, w, h)
                    WallpaperWorker.applyWallpaper(context, bmp)
                    Result.success(fix.name to bmp)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            generating = false
            result.onSuccess { (name, bmp) ->
                preview = bmp
                cityName = name
                statusMsg = "Wallpaper set for $name"
            }.onFailure {
                statusMsg = if (it.message == "no-location") {
                    "Couldn't get your location — grant access below."
                } else {
                    "Couldn't update: ${it.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Wordmark()

        HeroPreview(preview = preview, cityName = cityName, generating = generating)

        if (!hasCoarse || !hasBackground) {
            PermissionCard(
                hasCoarse = hasCoarse,
                onGrant = {
                    when {
                        !hasCoarse -> fgLauncher.launch(foregroundPermissions())
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                            openAppSettings(context) // background can't be granted from a dialog
                        else ->
                            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )
        }

        Button(
            onClick = { update() },
            enabled = hasCoarse && !generating,
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
                Text("Generating…", fontWeight = FontWeight.SemiBold)
            } else {
                Text("Set as wallpaper", fontWeight = FontWeight.SemiBold)
            }
        }

        statusMsg?.let { Text(it, color = CwMuted, fontSize = 13.sp) }

        SectionLabel("LOOK")
        PickerRow("Palette", PALETTE_NAMES, PALETTE_NAMES.indexOf(paletteName)) { i ->
            paletteName = PALETTE_NAMES[i]
            settings.paletteName = paletteName
        }

        SectionLabel("UPDATES")
        PickerRow("Every", FREQ_LABELS, FREQ_VALUES.indexOf(freqMinutes)) { i ->
            freqMinutes = FREQ_VALUES[i]
            settings.updateMinutes = freqMinutes
            if (autoUpdate) WallpaperScheduler.enablePeriodic(context) // apply new interval
        }
        SwitchRow(
            "Auto-update",
            "Refresh the wallpaper in the background.",
            autoUpdate,
        ) { on ->
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
private fun Wordmark() {
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
}

@Composable
private fun HeroPreview(preview: Bitmap?, cityName: String?, generating: Boolean) {
    Surface(
        color = MapSlate,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Current wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlaceholderMap()
            }

            if (cityName != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            cityName,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("cached on this device", color = CwMuted, fontSize = 12.sp)
                    }
                }
            }

            if (generating) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CwAccent,
                )
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
            for (k in 1..5) {
                val y = h * k / 6f
                drawLine(faint, Offset(0f, y), Offset(w, y), 1.5f)
            }
            for (k in 1..3) {
                val x = w * k / 4f
                drawLine(faint, Offset(x, 0f), Offset(x, h), 1.5f)
            }
            drawLine(CwAccent, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 4f, StrokeCap.Round)
        }
        Text("Tap “Set as wallpaper”", color = CwMuted, fontSize = 14.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MonoLabel,
        color = CwMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PickerRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
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
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(i)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
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
private fun PermissionCard(hasCoarse: Boolean, onGrant: () -> Unit) {
    Surface(color = CwAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (!hasCoarse) "Location access needed" else "Allow background updates",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (!hasCoarse) {
                    "CityWall maps the town you're in. Coarse location only — never precise."
                } else {
                    "Choose “Allow all the time” so the wallpaper can refresh on its own."
                },
                color = CwMuted,
                fontSize = 13.sp,
            )
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CwAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (!hasCoarse) "Grant access" else "Open settings")
            }
        }
    }
}

// --- helpers ---

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun foregroundPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
}.toTypedArray()

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
