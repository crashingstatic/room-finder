package com.example.roomfinder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    // --- Config inputs (bound to the UI) ---
    var url by mutableStateOf("")
    var method by mutableStateOf(HttpMethod.POST)
    var lastNameField by mutableStateOf("lastname")
    var roomField by mutableStateOf("room")
    var extraFields by mutableStateOf("")
    var contentType by mutableStateOf(ContentType.FORM_URLENCODED)
    var successMarker by mutableStateOf("")
    var failureMarker by mutableStateOf("")

    var lastNamesText by mutableStateOf("")
    var roomStart by mutableStateOf("100")
    var roomEnd by mutableStateOf("150")
    var padWidth by mutableStateOf("0")

    var delayMs by mutableStateOf("250")
    var stopOnFirstMatchPerName by mutableStateOf(true)
    var probeEnumeration by mutableStateOf(true)

    // --- Run state (observed by the UI) ---
    var running by mutableStateOf(false)
        private set
    var stats by mutableStateOf(RunStats())
        private set

    val log = mutableStateListOf<String>()
    val matches = mutableStateListOf<Match>()

    private var job: Job? = null

    fun parseLastNames(): List<String> =
        lastNamesText.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun start() {
        if (running) return
        log.clear()
        matches.clear()
        stats = RunStats()

        val config = PortalConfig(
            url = url.trim(),
            method = method,
            lastNameField = lastNameField.trim().ifBlank { "lastname" },
            roomField = roomField.trim().ifBlank { "room" },
            extraFields = extraFields.trim(),
            contentType = contentType,
            successMarker = successMarker,
            failureMarker = failureMarker,
        )
        if (config.url.isBlank()) {
            log.add("Set the portal URL first.")
            return
        }

        val range = RoomRange(
            start = roomStart.toIntOrNull() ?: 0,
            end = roomEnd.toIntOrNull() ?: -1,
            padWidth = padWidth.toIntOrNull() ?: 0,
        )
        val names = parseLastNames()

        val engine = BruteForceEngine(
            config = config,
            delayMs = delayMs.toLongOrNull() ?: 0L,
            stopOnFirstMatchPerName = stopOnFirstMatchPerName,
            probeEnumeration = probeEnumeration,
        )

        running = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.run(
                    names,
                    range,
                    BruteForceEngine.Callbacks(
                        onResult = { /* per-attempt detail intentionally not logged to avoid flooding */ },
                        onMatch = { m -> matches.add(m) },
                        onStats = { s -> stats = s },
                        onLog = { line -> log.add(line) },
                    ),
                )
            } catch (e: Exception) {
                log.add("Run ended: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        running = false
        log.add("Stopped by user.")
    }
}
