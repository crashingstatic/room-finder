# Room Finder — captive-portal brute-force resilience tester

An Android app for **authorized** testing of a captive portal you own or
operate. It drives last-name × room-number login attempts against your portal
and reports the signals that tell you whether an attacker could brute-force it:
throttling, lockout, response latency, and user-enumeration leakage.

> Use this only against a portal you are authorized to test, with test data.
> It is an assessment tool: the useful output is the **resilience summary**,
> not a list of real guests' rooms.

## What it measures

- **Matches** — which `(last name, room)` pairs the portal accepts.
- **Throttling** — HTTP 429 / rate-limit responses, counted and flagged.
- **Lockout** — body text indicating the client was locked out.
- **Latency** — min/avg/max per attempt (timing-based defenses show here).
- **User enumeration** — whether a *known-bad* last name gets a different
  response than a real last name with a wrong room. If so, the portal leaks
  which names are valid guests, which is itself a finding.

The end-of-run summary grades the portal (e.g. "WEAK: no throttling observed").

## Configuring it for your portal

Everything about the request is parameterized on the **Target** screen:

| Field | Meaning |
|-------|---------|
| Login URL | The endpoint the portal's form submits to |
| Method | `POST` (default) or `GET` |
| Last-name field / Room field | The form field names the portal expects |
| Extra fields | Static params sent every request, e.g. `submit=Login&accept=1` |
| Body | `form` (urlencoded) or `json` for POST |
| Success/Failure marker | Optional response substrings used to classify an attempt. If both are blank, classification falls back to HTTP status (2xx/3xx = success, 4xx = failure). |

To find these, open your portal's login page, submit once with your browser's
devtools Network tab open, and copy the request URL, method, and field names.

## Run options

- **Delay between requests (ms)** — keep this serial + delayed so server-side
  throttling is actually measurable. The engine runs sequentially by design.
- **Stop at first match per name** — stop scanning rooms for a name once found.
- **Probe for user enumeration** — sends one garbage-name baseline request first.

## Build

Open in Android Studio (Giraffe+), or from the command line:

```
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`.

Requires JDK 17 and the Android SDK (compileSdk 34). `minSdk` is 24.

## Notes / limitations

- Cleartext traffic is enabled in the manifest because captive portals are
  frequently plain HTTP; remove `usesCleartextTraffic` if yours is HTTPS-only.
- Redirects are not auto-followed so a 3xx can be read as its own signal.
- The engine is intentionally single-threaded; concurrency would obscure the
  throttling measurement this tool exists to produce.
