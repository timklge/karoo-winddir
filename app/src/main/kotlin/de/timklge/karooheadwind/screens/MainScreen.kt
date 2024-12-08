package de.timklge.karooheadwind.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.getGpsCoordinateFlow
import de.timklge.karooheadwind.saveSettings
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamStats
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class WindUnit(val id: String, val label: String, val unitDisplay: String){
    KILOMETERS_PER_HOUR("kmh", "Kilometers (km/h)", "km/h"),
    METERS_PER_SECOND("ms", "Meters (m/s)", "m/s"),
    MILES_PER_HOUR("mph", "Miles (mph)", "mph"),
    KNOTS("kn", "Knots (kn)", "kn")
}

enum class PrecipitationUnit(val id: String, val label: String){
    MILLIMETERS("mm", "Millimeters (mm)"),
    INCH("inch", "Inch")
}

@Serializable
data class HeadwindSettings(
    val windUnit: WindUnit = WindUnit.KILOMETERS_PER_HOUR,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MILLIMETERS,
    val welcomeDialogAccepted: Boolean = false,
    val showWindspeedOverlay: Boolean = true
){
    companion object {
        val defaultSettings = Json.encodeToString(HeadwindSettings())
    }
}

@Serializable
data class HeadwindStats(
    val lastSuccessfulWeatherRequest: Long? = null,
    val lastSuccessfulWeatherPosition: GpsCoordinates? = null,
    val failedWeatherRequest: Long? = null,
){
    companion object {
        val defaultStats = Json.encodeToString(HeadwindStats())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    var selectedWindUnit by remember { mutableStateOf(WindUnit.KILOMETERS_PER_HOUR) }
    var selectedPrecipitationUnit by remember { mutableStateOf(PrecipitationUnit.MILLIMETERS) }
    var welcomeDialogVisible by remember { mutableStateOf(false) }
    var showWindspeedOverlay by remember { mutableStateOf(false) }

    val stats by ctx.streamStats().collectAsState(HeadwindStats())
    val location by karooSystem.getGpsCoordinateFlow().collectAsState(initial = null)

    var savedDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            selectedWindUnit = settings.windUnit
            selectedPrecipitationUnit = settings.precipitationUnit
            welcomeDialogVisible = !settings.welcomeDialogAccepted
            showWindspeedOverlay = settings.showWindspeedOverlay
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text("WindDir") })
        Column(modifier = Modifier
            .padding(5.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            val windSpeedUnitDropdownOptions = WindUnit.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
            val windSpeedUnitInitialSelection = windSpeedUnitDropdownOptions.find { option -> option.id == selectedWindUnit.id }!!
            Dropdown(label = "Wind Speed Unit", options = windSpeedUnitDropdownOptions, initialSelection = windSpeedUnitInitialSelection) { selectedOption ->
                selectedWindUnit = WindUnit.entries.find { unit -> unit.id == selectedOption.id }!!
            }

            val precipitationUnitDropdownOptions = PrecipitationUnit.entries.toList().map { unit -> DropdownOption(unit.id, unit.label) }
            val precipitationUnitInitialSelection = precipitationUnitDropdownOptions.find { option -> option.id == selectedPrecipitationUnit.id }!!
            Dropdown(label = "Precipitation Unit", options = precipitationUnitDropdownOptions, initialSelection = precipitationUnitInitialSelection) { selectedOption ->
                selectedPrecipitationUnit = PrecipitationUnit.entries.find { unit -> unit.id == selectedOption.id }!!
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showWindspeedOverlay, onCheckedChange = { showWindspeedOverlay = it})
                Spacer(modifier = Modifier.width(10.dp))
                Text("Show headwind speed on arrow")
            }

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {
                    val newSettings = HeadwindSettings(windUnit = selectedWindUnit, precipitationUnit = selectedPrecipitationUnit, welcomeDialogAccepted = true, showWindspeedOverlay = showWindspeedOverlay)

                    coroutineScope.launch {
                        saveSettings(ctx, newSettings)
                        savedDialogVisible = true
                    }
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

            if (!karooConnected){
                Text(modifier = Modifier.padding(5.dp), text = "Could not read device status. Is your Karoo updated?")
            }

            val lastPosition = location?.let { l -> stats.lastSuccessfulWeatherPosition?.distanceTo(l) }
            val lastPositionDistanceStr = lastPosition?.let { dist -> " (${dist.roundToInt()} km away)" } ?: ""

            if (stats.failedWeatherRequest != null && (stats.lastSuccessfulWeatherRequest == null || stats.failedWeatherRequest!! > stats.lastSuccessfulWeatherRequest!!)){
                val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(stats.failedWeatherRequest!!), ZoneOffset.systemDefault()).toLocalTime().truncatedTo(
                    ChronoUnit.SECONDS)

                val successStr = if(lastPosition != null) " Last data received at ${localTime}${lastPositionDistanceStr}." else ""
                Text(modifier = Modifier.padding(5.dp), text = "Failed to update weather data; last try at ${localTime}.${successStr}")
            } else if(stats.lastSuccessfulWeatherRequest != null){
                val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(stats.lastSuccessfulWeatherRequest!!), ZoneOffset.systemDefault()).toLocalTime().truncatedTo(
                    ChronoUnit.SECONDS)

                Text(modifier = Modifier.padding(5.dp), text = "Last weather data received at ${localTime}${lastPositionDistanceStr}")
            } else {
                Text(modifier = Modifier.padding(5.dp), text = "No weather data received yet, waiting for GPS fix...")
            }
        }
    }

    if (savedDialogVisible){
        AlertDialog(onDismissRequest = { savedDialogVisible = false },
            confirmButton = { Button(onClick = {
                savedDialogVisible = false
            }) { Text("OK") } },
            text = { Text("Settings saved successfully.") }
        )
    }

    if (welcomeDialogVisible){
        AlertDialog(onDismissRequest = { },
            confirmButton = { Button(onClick = {
                coroutineScope.launch {
                    saveSettings(ctx, HeadwindSettings(windUnit = selectedWindUnit, precipitationUnit = selectedPrecipitationUnit, welcomeDialogAccepted = true))
                }
            }) { Text("OK") } },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Welcome to karoo-headwind!")

                    Spacer(Modifier.padding(10.dp))

                    Text("You can add headwind direction and other fields to your data pages in your profile settings.")

                    Spacer(Modifier.padding(10.dp))

                    Text("Please note that this app periodically fetches data from the Open-Meteo API to know the current weather at your approximate location.")
                }
            }
        )
    }
}