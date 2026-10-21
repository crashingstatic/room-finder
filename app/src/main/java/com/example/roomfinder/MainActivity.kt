package com.example.roomfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(vm)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(vm: MainViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Room Finder — portal resilience test") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Authorized testing only. Point this at a portal you own/operate, " +
                    "using test data, to measure brute-force resistance.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionCard("Target") {
                OutlinedTextField(
                    value = vm.url, onValueChange = { vm.url = it },
                    label = { Text("Login URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Method: ", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = vm.method == HttpMethod.POST,
                        onClick = { vm.method = HttpMethod.POST }, label = { Text("POST") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = vm.method == HttpMethod.GET,
                        onClick = { vm.method = HttpMethod.GET }, label = { Text("GET") },
                    )
                }
                Row {
                    OutlinedTextField(
                        value = vm.lastNameField, onValueChange = { vm.lastNameField = it },
                        label = { Text("Last-name field") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = vm.roomField, onValueChange = { vm.roomField = it },
                        label = { Text("Room field") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = vm.extraFields, onValueChange = { vm.extraFields = it },
                    label = { Text("Extra fields (a=1&b=2)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.method == HttpMethod.POST) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Body: ", style = MaterialTheme.typography.labelLarge)
                        FilterChip(
                            selected = vm.contentType == ContentType.FORM_URLENCODED,
                            onClick = { vm.contentType = ContentType.FORM_URLENCODED },
                            label = { Text("form") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = vm.contentType == ContentType.JSON,
                            onClick = { vm.contentType = ContentType.JSON },
                            label = { Text("json") },
                        )
                    }
                }
                OutlinedTextField(
                    value = vm.successMarker, onValueChange = { vm.successMarker = it },
                    label = { Text("Success marker (optional substring)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.failureMarker, onValueChange = { vm.failureMarker = it },
                    label = { Text("Failure marker (optional substring)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Candidates") {
                OutlinedTextField(
                    value = vm.lastNamesText, onValueChange = { vm.lastNamesText = it },
                    label = { Text("Last names (one per line or comma-separated)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Row {
                    NumberField("Room start", vm.roomStart, { vm.roomStart = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    NumberField("Room end", vm.roomEnd, { vm.roomEnd = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    NumberField("Pad width", vm.padWidth, { vm.padWidth = it }, Modifier.weight(1f))
                }
            }

            SectionCard("Run options") {
                NumberField("Delay between requests (ms)", vm.delayMs, { vm.delayMs = it },
                    Modifier.fillMaxWidth())
                ToggleRow("Stop at first match per name", vm.stopOnFirstMatchPerName) {
                    vm.stopOnFirstMatchPerName = it
                }
                ToggleRow("Probe for user enumeration", vm.probeEnumeration) {
                    vm.probeEnumeration = it
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.start() }, enabled = !vm.running,
                    modifier = Modifier.weight(1f),
                ) { Text(if (vm.running) "Running…" else "Start test") }
                OutlinedButton(
                    onClick = { vm.stop() }, enabled = vm.running,
                    modifier = Modifier.weight(1f),
                ) { Text("Stop") }
            }

            StatsCard(vm.stats)

            if (vm.matches.isNotEmpty()) {
                SectionCard("Matches (${vm.matches.size})") {
                    vm.matches.forEach { m ->
                        Text("${m.lastName}  →  room ${m.room}",
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }

            SectionCard("Log") {
                if (vm.log.isEmpty()) {
                    Text("No output yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    vm.log.takeLast(200).forEach { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatsCard(s: RunStats) {
    SectionCard("Signals") {
        Text("Attempts: ${s.attempts}", fontFamily = FontFamily.Monospace)
        Text("Matches: ${s.successes}   Failures: ${s.failures}", fontFamily = FontFamily.Monospace)
        Text("Throttled (429): ${s.rateLimited}   Lockouts: ${s.lockedOut}   Errors: ${s.errors}",
            fontFamily = FontFamily.Monospace)
        Divider()
        Text("Latency ms  min:${s.minLatencyMs} avg:${s.avgLatencyMs} max:${s.maxLatencyMs}",
            fontFamily = FontFamily.Monospace)
        val enum = when (s.enumerationObserved) {
            true -> "POSSIBLE — failures differ by name"
            false -> "not observed"
            null -> "n/a"
        }
        Text("User enumeration: $enum", fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
