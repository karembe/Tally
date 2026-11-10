# Golden vectors

Shared by the Java encoder tests and the Go verifier (phase 4). Never edit by hand; a change means a new encoder VERSION.

`event-1` input:

| field | value |
|---|---|
| seq | 1 |
| transfer_id | 01890000-0000-7000-8000-000000000001 |
| from | 00000000-0000-7000-8000-000000000978 |
| to | 01890000-0000-7000-8000-000000000002 |
| amount | 12345 |
| currency | EUR |
| created_at | 2026-01-01T00:00:00.123456Z |

- `event-1.hex`: `CanonicalEncoder.encode` output, lowercase hex.
- `event-1.sha256`: `SHA-256(32 zero bytes || payload)`, the hash of a genesis event.
