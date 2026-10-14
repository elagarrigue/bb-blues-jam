# Deploy probe after the user's redeploy (14 October 2026)

The user pasted the new `Post.gs` and published a new version of the existing deployment. The
orchestrator then POSTed `{"action":"deployProbe"}` with no passphrase (no write possible: an unknown
action is answered before the guard). Answer:

```
{"schemaVersion":1,"error":{"code":"unknown_action","message":"Unknown or missing action. Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong, checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot, addExtraParticipant, removeExtraParticipant, moveSong, checkSetlistMove, publishSetlist, checkPublish, createJam, checkCreateJam"}}
```

`createJam` and `checkCreateJam` are deployed. `checkCreateJam` and `createJam` were not called.
