# Research notes: background sync

Android recommends deferrable persistent work through [WorkManager][wm]. Apple
provides [background task scheduling][bg] with different execution guarantees.
The shared domain layer should describe intent without promising an exact start
time.

## Working assumptions

- A sync request is idempotent.
- The platform may coalesce multiple requests.
- User-initiated refresh has a separate foreground path.
- Battery and connectivity constraints are platform-owned.

The [architecture decision][adr] should document which guarantees are common
and which remain platform-specific.

[wm]: https://developer.android.com/topic/libraries/architecture/workmanager
[bg]: https://developer.apple.com/documentation/backgroundtasks
[adr]: ../decisions/004-background-sync.md

