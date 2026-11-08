package lu.andreasbraun.gpstoolbox.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lu.andreasbraun.gpstoolbox.data.Agnss
import lu.andreasbraun.gpstoolbox.data.Prefs
import lu.andreasbraun.gpstoolbox.engine.GnssEngine
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.tools.CompassScreen
import lu.andreasbraun.gpstoolbox.ui.tools.LevelScreen
import lu.andreasbraun.gpstoolbox.ui.tools.RawLogScreen
import lu.andreasbraun.gpstoolbox.ui.tools.SensorsScreen
import lu.andreasbraun.gpstoolbox.ui.tools.SettingsScreen
import lu.andreasbraun.gpstoolbox.ui.tools.TrackScreen
import lu.andreasbraun.gpstoolbox.ui.tools.WaypointsScreen

enum class Tab(val label: String, val icon: ImageVector) {
    NOW("Now", Ic.now),
    SKY("Sky", Ic.sky),
    ACCURACY("Accuracy", Ic.accuracy),
    TOOLS("Tools", Ic.tools),
}

enum class Tool { COMPASS, LEVEL, WAYPOINTS, TRACK, LOG, SENSORS, SETTINGS }

@Composable
fun AppRoot() {
    val gnss by GnssEngine.state.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = tool == null && tab != Tab.NOW) { tab = Tab.NOW }
    BackHandler(enabled = tool != null) { tool = null }

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(containerColor = C.Deep, tonalElevation = 0.dp) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            tool = null
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Accent,
                            selectedTextColor = C.Accent,
                            indicatorColor = C.Accent.copy(alpha = 0.14f),
                            unselectedIconColor = C.Muted,
                            unselectedTextColor = C.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val openTool = tool
            when {
                !gnss.permission -> PermissionScreen()
                openTool != null -> ToolHost(openTool) { tool = null }
                else -> when (tab) {
                    Tab.NOW -> NowScreen(onOpenSettings = { tool = Tool.SETTINGS })
                    Tab.SKY -> SkyScreen()
                    Tab.ACCURACY -> AccuracyScreen()
                    Tab.TOOLS -> ToolsScreen(onOpen = { tool = it })
                }
            }
        }
    }
}

@Composable
private fun ToolHost(tool: Tool, onBack: () -> Unit) {
    when (tool) {
        Tool.COMPASS -> CompassScreen(onBack)
        Tool.LEVEL -> LevelScreen(onBack)
        Tool.WAYPOINTS -> WaypointsScreen(onBack)
        Tool.TRACK -> TrackScreen(onBack)
        Tool.LOG -> RawLogScreen(onBack)
        Tool.SENSORS -> SensorsScreen(onBack)
        Tool.SETTINGS -> SettingsScreen(onBack)
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        GnssEngine.refresh()
        Agnss.schedule(context)
    }
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Ic.sky, contentDescription = null, tint = C.Accent, modifier = Modifier.size(56.dp))
        Text("Precise location needed", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.Text, textAlign = TextAlign.Center)
        Text(
            "GPS Toolbox reads position and satellite data from your phone's GNSS receiver. " +
                "The app has no internet access, so nothing leaves your phone.",
            fontSize = 15.sp, color = C.TextSoft, textAlign = TextAlign.Center,
        )
        if (asked) {
            Text(
                "Choose \"Precise\" when Android asks. If the dialog no longer appears, enable precise location in the app settings.",
                fontSize = 13.sp, color = C.Amber, textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton("Allow location", onClick = { launcher.launch(permissions) }, modifier = Modifier.weight(1f))
            if (asked) {
                ActionButton(
                    "App settings",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun LocationOffBanner() {
    val context = LocalContext.current
    Panel(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text("Location is turned off", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Amber)
        Text("Turn on location to receive satellites.", fontSize = 13.sp, color = C.TextSoft)
        ActionButton(
            "Open location settings", primary = false, height = 44.dp,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}
