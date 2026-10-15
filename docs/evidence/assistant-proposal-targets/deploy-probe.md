# Deploy probe — assistant-proposal-targets (15 October 2026)

The user redeployed `Post` per `docs/migrations/assistant-proposal-targets-script-update.md`. Checked without the
passphrase and without writing:

- POST `{"action":"deployProbe"}` → `unknown_action`; the known-actions message ends with
  `checkJamEdit, checkFutureSetlistWrite`.
- Read-only `GET ?resource=jams&jamStatuses=3` → unchanged: 2026-07-25 PUBLICADA, 13 songs (the BORRADOR jams of
  31/10 and 13/11 are omitted from the anonymous read, as D-26 requires).
- `local.properties` md5 unchanged (569913ae…). The optional `checkFutureSetlistWrite` (needs the passphrase) was not run.
