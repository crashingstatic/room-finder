package com.example.roomfinder

/**
 * Configuration for the portal under test. Everything is parameterized so the
 * tool can be pointed at your own captive portal's login endpoint without
 * hard-coding any particular vendor's request shape.
 */
data class PortalConfig(
    val url: String = "",
    val method: HttpMethod = HttpMethod.POST,
    /** Form/query field name that carries the guest last name. */
    val lastNameField: String = "lastname",
    /** Form/query field name that carries the room number. */
    val roomField: String = "room",
    /** Extra static fields sent on every request, e.g. "submit=Login&terms=1". */
    val extraFields: String = "",
    /** Content type for POST bodies. */
    val contentType: ContentType = ContentType.FORM_URLENCODED,
    /** Substring whose PRESENCE means authentication succeeded (optional). */
    val successMarker: String = "",
    /** Substring whose PRESENCE means authentication failed (optional). */
    val failureMarker: String = "",
    /**
     * If no markers are set, treat any 2xx/3xx as success and 4xx as failure.
     * When markers are set they take precedence.
     */
    val treatRedirectAsSuccess: Boolean = true,
)

enum class HttpMethod { GET, POST }

enum class ContentType(val mime: String) {
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    JSON("application/json"),
}

/** Defines the room numbers to iterate, e.g. 100..130 zero-padded to width 3. */
data class RoomRange(
    val start: Int = 100,
    val end: Int = 150,
    /** Zero-pad width; 0 means no padding. */
    val padWidth: Int = 0,
) {
    fun rooms(): List<String> {
        if (end < start) return emptyList()
        return (start..end).map { n ->
            if (padWidth > 0) n.toString().padStart(padWidth, '0') else n.toString()
        }
    }
}

data class AttemptResult(
    val lastName: String,
    val room: String,
    val httpStatus: Int,
    val latencyMs: Long,
    val classification: Classification,
    val note: String = "",
)

enum class Classification {
    SUCCESS,        // credentials accepted
    FAILURE,        // credentials rejected
    RATE_LIMITED,   // 429 / throttling signal from server
    LOCKED_OUT,     // server indicates lockout
    ERROR,          // network/transport error
}

/** A name->room match discovered during the run. */
data class Match(val lastName: String, val room: String)

/** Aggregate signals that matter for a brute-force resilience assessment. */
data class RunStats(
    val attempts: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val rateLimited: Int = 0,
    val lockedOut: Int = 0,
    val errors: Int = 0,
    val minLatencyMs: Long = 0,
    val maxLatencyMs: Long = 0,
    val avgLatencyMs: Long = 0,
    /**
     * Enumeration signal: do responses for a bad last name differ from
     * responses for a valid last name + bad room? If so the portal leaks
     * which names are real. Null until enough data is seen.
     */
    val enumerationObserved: Boolean? = null,
)
