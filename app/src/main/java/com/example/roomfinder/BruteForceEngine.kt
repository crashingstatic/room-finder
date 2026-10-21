package com.example.roomfinder

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Drives the credential iteration against the portal and accumulates the
 * resilience signals (throttling, lockout, latency, enumeration).
 *
 * Deliberately sequential with a configurable inter-request delay: a serial
 * stream is what makes server-side rate limiting and lockout measurable. The
 * point of the tool is to answer "can an attacker brute-force this?", so the
 * output is the defensive signal, not just a room lookup.
 */
class BruteForceEngine(
    private val config: PortalConfig,
    private val delayMs: Long,
    private val stopOnFirstMatchPerName: Boolean,
    private val probeEnumeration: Boolean,
) {
    private val client = PortalClient(config)

    data class Callbacks(
        val onResult: (AttemptResult) -> Unit,
        val onMatch: (Match) -> Unit,
        val onStats: (RunStats) -> Unit,
        val onLog: (String) -> Unit,
    )

    private var attempts = 0
    private var successes = 0
    private var failures = 0
    private var rateLimited = 0
    private var lockedOut = 0
    private var errors = 0
    private var minLatency = Long.MAX_VALUE
    private var maxLatency = 0L
    private var totalLatency = 0L
    private var enumerationObserved: Boolean? = null

    suspend fun run(lastNames: List<String>, roomRange: RoomRange, cb: Callbacks) {
        val rooms = roomRange.rooms()
        if (lastNames.isEmpty() || rooms.isEmpty()) {
            cb.onLog("Nothing to do: provide at least one last name and a valid room range.")
            return
        }

        val baseline = if (probeEnumeration) establishBaseline(rooms.first(), cb) else null
        cb.onLog("Starting: ${lastNames.size} name(s) x ${rooms.size} room(s) = " +
                "${lastNames.size * rooms.size} max attempts.")

        for (name in lastNames) {
            if (!coroutineContext.isActive) break
            var matchedThisName = false
            for (room in rooms) {
                coroutineContext.ensureActive()

                val result = client.attempt(name, room)
                record(result, baseline)
                cb.onResult(result)
                cb.onStats(snapshot())

                when (result.classification) {
                    Classification.SUCCESS -> {
                        cb.onMatch(Match(name, room))
                        cb.onLog("MATCH  $name -> room $room")
                        matchedThisName = true
                    }
                    Classification.RATE_LIMITED ->
                        cb.onLog("THROTTLED at attempt $attempts (HTTP 429) — portal is rate limiting.")
                    Classification.LOCKED_OUT ->
                        cb.onLog("LOCKOUT signalled at attempt $attempts — portal locked the client out.")
                    Classification.ERROR ->
                        cb.onLog("ERROR  $name/$room: ${result.note}")
                    Classification.FAILURE -> { /* quiet */ }
                }

                if (matchedThisName && stopOnFirstMatchPerName) break
                if (delayMs > 0) delay(delayMs)
            }
        }
        cb.onStats(snapshot())
        cb.onLog(verdict())
    }

    /**
     * Send one request with a random (almost certainly non-existent) last name
     * so later real-name failures can be compared against it. If a real name
     * produces a different failure signature than this garbage name, the portal
     * is leaking valid-name information (user enumeration).
     */
    private suspend fun establishBaseline(room: String, cb: Callbacks): AttemptResult {
        val garbage = "zzq" + Random.nextInt(100000, 999999)
        val probe = client.attempt(garbage, room)
        cb.onLog("Enumeration baseline (garbage name '$garbage'): " +
                "HTTP ${probe.httpStatus}, ${probe.latencyMs}ms, ${probe.classification}")
        if (delayMs > 0) delay(delayMs)
        return probe
    }

    private fun record(r: AttemptResult, baseline: AttemptResult?) {
        attempts++
        when (r.classification) {
            Classification.SUCCESS -> successes++
            Classification.FAILURE -> failures++
            Classification.RATE_LIMITED -> rateLimited++
            Classification.LOCKED_OUT -> lockedOut++
            Classification.ERROR -> errors++
        }
        if (r.latencyMs in 0 until minLatency) minLatency = r.latencyMs
        if (r.latencyMs > maxLatency) maxLatency = r.latencyMs
        totalLatency += r.latencyMs.coerceAtLeast(0)

        // Enumeration: a rejected real-name attempt whose HTTP status differs
        // from the garbage-name baseline means the portal distinguishes them.
        if (baseline != null && r.classification == Classification.FAILURE &&
            r.httpStatus != baseline.httpStatus
        ) {
            enumerationObserved = true
        } else if (baseline != null && enumerationObserved == null &&
            r.classification == Classification.FAILURE
        ) {
            enumerationObserved = false
        }
    }

    private fun snapshot(): RunStats {
        val avg = if (attempts > 0) totalLatency / attempts else 0
        return RunStats(
            attempts = attempts,
            successes = successes,
            failures = failures,
            rateLimited = rateLimited,
            lockedOut = lockedOut,
            errors = errors,
            minLatencyMs = if (minLatency == Long.MAX_VALUE) 0 else minLatency,
            maxLatencyMs = maxLatency,
            avgLatencyMs = avg,
            enumerationObserved = enumerationObserved,
        )
    }

    private fun verdict(): String {
        val sb = StringBuilder("=== Resilience summary ===\n")
        sb.append("Attempts: $attempts | matches: $successes | failures: $failures | ")
        sb.append("throttled: $rateLimited | lockouts: $lockedOut | errors: $errors\n")
        when {
            rateLimited == 0 && lockedOut == 0 ->
                sb.append("WEAK: no throttling or lockout observed across $attempts attempts. " +
                        "Brute force is unmitigated — add rate limiting / lockout.\n")
            lockedOut > 0 ->
                sb.append("Lockout kicked in — brute force is partially mitigated. " +
                        "Verify the threshold is low enough.\n")
            else ->
                sb.append("Throttling observed — verify it is strict enough to make " +
                        "full enumeration impractical.\n")
        }
        when (enumerationObserved) {
            true -> sb.append("User enumeration POSSIBLE: failed real names respond " +
                    "differently than unknown names. Return identical responses for all failures.")
            false -> sb.append("No obvious user enumeration in failure responses.")
            null -> sb.append("Enumeration not probed (enable the probe to test).")
        }
        return sb.toString()
    }
}
