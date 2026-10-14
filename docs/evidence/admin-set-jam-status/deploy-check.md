# Deploy check after the combined redeploy (14 October 2026)

The user replaced Code.gs, Jams.gs and Post.gs and published a new version of the existing
deployment (docs/migrations/jam-status-script-update.md). The orchestrator ran the three no-write
checks; no passphrase was sent and nothing was written.

1. `GET ?resource=jams&jamStatuses=9` →
   `{"schemaVersion":1,"error":{"code":"invalid_jam_statuses","message":"jamStatuses must be 3 when present"}}`
2. `POST {"action":"deployProbe"}` → `unknown_action`, Known list ends
   `..., publishSetlist, checkPublish, createJam, checkCreateJam, setJamStatus`
3. `GET ?resource=jams&jamStatuses=3` → 2026-07-25 PUBLICADA (13 songs), 2026-10-31 PUBLICADA (12 songs):
   the real upcoming jam is unchanged by the redeploy.
