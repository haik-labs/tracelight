# TraceLight

A privacy-first Android prototype for auditing your **own** public digital footprint. TraceLight presents a polished, consent-led flow from profile entry through an illustrative scan, paywall preview, and actionable report.

## Prototype boundaries

- Results and payment are mocked locally; no personal data leaves the device.
- The app does not identify arbitrary people or reveal sensitive data.
- A production release must add real consent verification, billing, source attribution, deletion controls, and legal/privacy review.

## Build

```bash
./gradlew test assembleDebug
```
