package br.com.teasomagora

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private val measurement by viewModels<MeasurementViewModel>()
    private var pendingPermission by mutableStateOf<Point?>(null)
    private var pendingRepeatBoth by mutableStateOf(false)

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val point = pendingPermission
        pendingPermission = null
        val repeatBoth = pendingRepeatBoth
        pendingRepeatBoth = false
        if (granted && repeatBoth) measurement.repeatBoth()
        else if (granted && point != null) measurement.start(point)
        if (!granted) measurement.permissionDenied()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences(newBase).localize(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPermission = savedInstanceState?.getString("permissionPoint")?.let(Point::valueOf)
        pendingRepeatBoth = savedInstanceState?.getBoolean("pendingRepeatBoth") ?: false
        val preferences = AppPreferences(this)

        setContent {
            var theme by remember { mutableStateOf(preferences.theme) }
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            val dark = when (theme) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x80151C23.toInt()) { dark },
                )
            }
            val measurementScroll = rememberScrollState()
            val settingsScroll = rememberScrollState()
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            fun notify(message: Int) {
                scope.launch { snackbar.showSnackbar(getString(message)) }
            }

            BackHandler(enabled = settingsOpen || measurement.active != null) {
                if (settingsOpen) settingsOpen = false else measurement.cancel()
            }
            MaterialTheme(colorScheme = if (dark) darkColors else lightColors) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(if (settingsOpen) settingsScroll else measurementScroll)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                modifier = Modifier.heightIn(min = 48.dp),
                                enabled = measurement.active == null && pendingPermission == null,
                                onClick = { settingsOpen = !settingsOpen },
                            ) {
                                Text(stringResource(if (settingsOpen) R.string.back else R.string.settings_title))
                            }
                        }

                        if (settingsOpen) {
                            PreferencesContent(
                                theme = theme,
                                language = preferences.language,
                                onTheme = { preferences.theme = it; theme = it },
                                onLanguage = {
                                    if (it != preferences.language) {
                                        preferences.language = it
                                        recreate()
                                    }
                                },
                                onSponsors = {
                                    if (!launchExternal(Intent(Intent.ACTION_VIEW, Uri.parse(SPONSORS_URL)))) {
                                        notify(R.string.link_error)
                                    }
                                },
                                onPix = {
                                    val clipboard = getSystemService(ClipboardManager::class.java)
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Pix", PIX_KEY))
                                        notify(R.string.pix_copied)
                                    } else notify(R.string.copy_error)
                                },
                                onContact = {
                                    if (!launchExternal(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT")))) {
                                        notify(R.string.email_error)
                                    }
                                },
                            )
                        } else {
                            MeasurementContent(
                                state = measurement,
                                permissionPending = pendingPermission != null,
                                onMeasure = ::requestMeasurement,
                                onRepeatBoth = ::requestRepeatBoth,
                                onSettings = {
                                    launchExternal(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("permissionPoint", pendingPermission?.name)
        outState.putBoolean("pendingRepeatBoth", pendingRepeatBoth)
    }

    override fun onStop() {
        measurement.cancel()
        super.onStop()
    }

    private fun requestMeasurement(point: Point) {
        if (pendingPermission != null || measurement.active != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            measurement.start(point)
        } else {
            pendingPermission = point
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestRepeatBoth() {
        if (pendingPermission != null || measurement.active != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            measurement.repeatBoth()
        } else {
            pendingPermission = Point.A
            pendingRepeatBoth = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchExternal(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val SPONSORS_URL = "https://github.com/sponsors/alunoadsnoite"
        const val PIX_KEY = "81063e93-0c1b-4ddb-8a16-b9be68a75d4e"
        const val CONTACT = "valdenor.sa@proton.me"
    }
}

private val lightColors = lightColorScheme(
    primary = Color(0xFF075C56),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6EBE4),
    onPrimaryContainer = Color(0xFF002722),
    background = Color(0xFFFAF8F4),
    surface = Color(0xFFFAF8F4),
    onSurface = Color(0xFF131B2C),
)
private val darkColors = darkColorScheme(
    primary = Color(0xFF95D8CC),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF164D46),
    onPrimaryContainer = Color(0xFFD5F4EE),
    background = Color(0xFF151C23),
    surface = Color(0xFF151C23),
    onSurface = Color(0xFFE9EEF2),
)

@Composable
private fun MeasurementContent(
    state: MeasurementViewModel,
    permissionPending: Boolean,
    onMeasure: (Point) -> Unit,
    onRepeatBoth: () -> Unit,
    onSettings: () -> Unit,
) {
    val pointA = state.pointA
    val pointB = state.pointB
    val active = state.active
    val labelA = state.labelA.trim().ifEmpty { stringResource(R.string.point_a) }
    val labelB = state.labelB.trim().ifEmpty { stringResource(R.string.point_b) }
    val comparison = aggregateComparisons(state.rounds)

    SectionHeading(stringResource(R.string.title))
    if (comparison == null) {
        Text(stringResource(R.string.introduction))
    } else if (active == null) {
        val result = when (comparison) {
            Comparison.A_QUIETER -> stringResource(R.string.less_at_point, labelA)
            Comparison.B_QUIETER -> stringResource(R.string.less_at_point, labelB)
            Comparison.INCONCLUSIVE -> stringResource(R.string.inconclusive)
            Comparison.INCONSISTENT -> stringResource(R.string.inconsistent)
            Comparison.DIFFERENT_INPUT -> stringResource(R.string.mismatch)
        }
        Card(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(result, Modifier.padding(18.dp), style = MaterialTheme.typography.titleLarge)
        }
        when (comparison) {
            Comparison.INCONCLUSIVE -> Text(stringResource(R.string.inconclusive_detail))
            Comparison.INCONSISTENT -> Text(stringResource(R.string.inconsistent_detail))
            else -> Unit
        }
        if (comparison == Comparison.INCONCLUSIVE || comparison == Comparison.INCONSISTENT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onMeasure(Point.A) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.repeat_a)) }
                OutlinedButton(
                    onClick = { onMeasure(Point.B) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.repeat_b)) }
            }
            OutlinedButton(
                onClick = onRepeatBoth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.repeat_both)) }
        }
    }

    if (active != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val activeLabel = if (active == Point.A) labelA else labelB
            Text(stringResource(R.string.measuring, activeLabel), Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            })
            LinearProgressIndicator(
                progress = { 1f - state.secondsLeft.toFloat() / MEASUREMENT_SECONDS },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.seconds_left, state.secondsLeft))
            Text(stringResource(R.string.measurement_guidance))
            OutlinedButton(onClick = state::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }

    state.message?.let { message ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(message),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
            )
            if (message == R.string.permission_denied) {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
            }
        }
    }

    val enabled = active == null && !permissionPending
    val relativeLevels = if (active == null && pointA != null && pointB != null &&
        (comparison == Comparison.A_QUIETER || comparison == Comparison.B_QUIETER)
    ) {
        val louder = maxOf(pointA.level, pointB.level)
        fun relative(reading: SoundReading) = 10.0.pow((reading.level - louder) / 20).toFloat()
        relative(pointA) to relative(pointB)
    } else null

    PointCard(
        point = Point.A,
        pointLabel = labelA,
        labelValue = state.labelA,
        reading = pointA,
        enabled = enabled,
        canEditLabel = active == null,
        primary = pointA == null,
        relativeLevel = relativeLevels?.first,
        onMeasure = onMeasure,
        onLabelChange = { state.setLabel(Point.A, it) },
    )
    PointCard(
        point = Point.B,
        pointLabel = labelB,
        labelValue = state.labelB,
        reading = pointB,
        enabled = enabled && pointA != null,
        canEditLabel = active == null,
        primary = pointA != null && pointB == null,
        relativeLevel = relativeLevels?.second,
        onMeasure = onMeasure,
        onLabelChange = { state.setLabel(Point.B, it) },
    )

    if (comparison != null) Text(stringResource(R.string.comparison_detail), style = MaterialTheme.typography.bodyMedium)
    if (pointA != null || pointB != null) {
        TextButton(onClick = state::reset, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset))
        }
    } else if (active == null) {
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PointCard(
    point: Point,
    pointLabel: String,
    labelValue: String,
    reading: SoundReading?,
    enabled: Boolean,
    canEditLabel: Boolean,
    primary: Boolean,
    relativeLevel: Float?,
    onMeasure: (Point) -> Unit,
    onLabelChange: (String) -> Unit,
) {
    val action = when (point) {
        Point.A -> if (reading == null) R.string.start_a else R.string.retry_a
        Point.B -> if (reading == null) R.string.start_b else R.string.retry_b
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(pointLabel, large = false)
            OutlinedTextField(
                value = labelValue,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.point_label, stringResource(point.label))) },
                placeholder = { Text(stringResource(point.label)) },
                singleLine = true,
                enabled = canEditLabel,
                modifier = Modifier.fillMaxWidth(),
            )
            val label = when {
                relativeLevel == 1f -> R.string.higher_intensity
                relativeLevel != null -> R.string.lower_intensity
                reading == null -> R.string.waiting
                else -> R.string.measured
            }
            Text(stringResource(label))
            if (relativeLevel != null) {
                LinearProgressIndicator(
                    progress = { relativeLevel },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
            }
            val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            if (primary) {
                Button(onClick = { onMeasure(point) }, enabled = enabled, modifier = modifier) {
                    Text(stringResource(action))
                }
            } else {
                OutlinedButton(onClick = { onMeasure(point) }, enabled = enabled, modifier = modifier) {
                    Text(stringResource(action))
                }
            }
        }
    }
}

@Composable
private fun PreferencesContent(
    theme: ThemeChoice,
    language: AppLanguage,
    onTheme: (ThemeChoice) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onSponsors: () -> Unit,
    onPix: () -> Unit,
    onContact: () -> Unit,
) {
    SectionHeading(stringResource(R.string.settings_title))
    SectionHeading(stringResource(R.string.theme), large = false)
    Column(Modifier.selectableGroup()) {
        ThemeChoice.entries.forEach {
            PreferenceChoice(stringResource(it.label), theme == it) { onTheme(it) }
        }
    }
    HorizontalDivider()
    SectionHeading(stringResource(R.string.language), large = false)
    Column(Modifier.selectableGroup()) {
        AppLanguage.entries.forEach {
            PreferenceChoice(it.label, language == it) { onLanguage(it) }
        }
    }
    HorizontalDivider()
    SectionHeading(stringResource(R.string.support), large = false)
    Text(stringResource(R.string.support_detail))
    OutlinedButton(onClick = onSponsors, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.sponsors))
    }
    OutlinedButton(onClick = onPix, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.copy_pix))
    }
    HorizontalDivider()
    SectionHeading(stringResource(R.string.contact), large = false)
    Text(stringResource(R.string.contact_email))
    OutlinedButton(onClick = onContact, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.write_email))
    }
}

@Composable
private fun PreferenceChoice(text: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = checked, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = checked, onClick = null)
        Text(text)
    }
}

@Composable
private fun SectionHeading(title: String, large: Boolean = true) {
    Text(title, Modifier.semantics { heading() }, style = if (large) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.titleLarge
    })
}
