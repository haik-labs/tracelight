# Public Lens

A source-grounded Android app for reviewing a person's public professional footprint. Public Lens uses Firebase AI Logic with Google Search grounding to identify possible matches first, then creates a structured deep report only after the user confirms a candidate and unlocks the report.

## Prototype boundaries

- Candidate discovery is deliberately compact and limited to five results to control latency and token use.
- Deep reports deduplicate sources, reference them by ID, and separate verified, conflicting, and uncertain claims.
- Exact addresses, personal phone numbers, private email addresses, relatives, credentials, live location, and sensitive-trait inferences are excluded.
- The current purchase button is a test unlock; production still requires Google Play Billing and server-side entitlement verification.

## Build

```bash
./gradlew test assembleDebug
```
