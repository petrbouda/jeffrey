---
paths:
  - "jeffrey-hub/core-hub/**/project/repository/**"
  - "jeffrey-hub/core-hub/**/grpc/**"
  - "jeffrey-hub/core-hub/**/scheduler/**"
  - "jeffrey-hub/hub-model/**"
  - "jeffrey-microscope/hub-client/**"
  - "jeffrey-microscope/grpc-client/**"
  - "jeffrey-microscope/recordings-core/**"
---

## Hub repository and file-download rules

Invariants of how the hub stores, lists and serves a session's files. Each is a rule that was
once broken; the "why" says what went wrong. Do not re-derive these from the code.

### One domain model per side
- Hub (`jeffrey-hub/hub-model`, `cafe.jeffrey.hub.model`) and Microscope (`jeffrey-microscope/microscope-model`) each map the proto in `shared/hub-api` onto their **own** records (`ProtoMappers` on the hub, `ClientProtoMappers`/`RepositoryClient` in `hub-client`). Fourteen records exist once per side on purpose (`RecordingSession`, `RepositoryFile`, `RecordingStatus`, `WorkspaceInfo`, `ProjectInfo`, …). `ModuleBoundaryTest` in `core-hub` and `core-microscope` fails on any import across the line.
- `shared/common` holds utilities and hub↔provisioner contract types only (`RemoteProject*`, `ProfilerSettings*`, `RepositoryType`, `JeffreyLayout`, `ChunkWindow`). Never put a domain record there.
- `RecordingSession.openRecording()` (which chunk the profiler still holds open) must answer alike on both sides; the same three cases are pinned in each side's `RecordingSessionTest`.

### Session status is a fact of the row
- `FilesystemRepositoryStorage.statusOf` is `finishedAt == null ? ACTIVE : FINISHED`. Never derive it from the session's position in the project's listing — with two instances per project the older live session read as FINISHED and had the file its profiler was writing compressed and deleted (`everyUnfinishedSessionIsLiveWhateverItsPosition` pins it).
- Listing order is `FilesystemRepositoryStorage.NEWEST_BY_NAME`, a constant, presentation only. Do not reintroduce a `FileInfoProcessor` strategy.

### The hub reports whether a file is a recording and nothing more
- `HubManagedFile` has exactly two constants, `JFR` and `JFR_LZ4`, because those are the only files the hub *does* anything to (timestamp from its own name via `TimestampResolver.RECORDING_NAME`, compression to LZ4). A type may be compressed exactly when it reads its timestamp from its own name; anything else (`app.pprof.lz4`) would change id and lose its original.
- `HubManagedFile.of(name)` is `Optional`; a file it is empty for (log, heap dump, pprof, `.jfr.N~` scratch) is listed and served as it lies: whole name as id, filesystem timestamp, `is_recording=false`. A recording's id drops its extension so one id names it and the archive it becomes.
- `core-hub` must not depend on `recording-storage-api` (Microscope's `ManagedFile`/`FileCategory`). Microscope classifies names itself in `RepositoryClient.toFileResponse` and `RepositoryFiles`.
- Repository statistics is **one number** (bytes a project occupies). No per-kind buckets, no volume capacity (the RWX volume's `FileStore` never reports the PVC size), no `hs_err` detection in the session detector.

### A download is one file, and the hub does not care what kind
- One RPC, `DownloadFile(sessionId, fileId)` (`file_download_service.proto`, `FileDownloadGrpcService`, `FileStreamClient.streamFile`) over one `RepositoryStorage.file(sessionId, fileId)`. What the caller does with the bytes is decided from the **listing** (`RepositoryFile.isRecordingFile()`, `RepositoryFiles.isArtifact`), never from which endpoint answered. Do not add a per-category download path.
- The hub refuses, each **named with its reason**: a file the session does not hold, the chunk still being written (`RecordingSession.isOpen`), one no longer on disk (`FileVanishedException` — resolved once more because the compression job may have rewritten it), and one that is empty. `.jfr.N~` is served like any file; Microscope declines to ask for it.
- **The name travels with the bytes**: `DataChunk.filename` on the first chunk; the receiver writes what arrived (`FileStreamClient.TransferredFile`, which reduces the name to one path element in its compact constructor).

### A download is an unbroken run of chunks
- Files are downloaded one at a time, in parallel, and kept as several files; nothing joins them. The recording carries one start/end across its files, so a skipped chunk leaves a silent hole. `RemoteRecordingsDownloadManager` refuses a gapped selection and an id the session does not hold; `hubs_download` refuses naming the missing chunk; the UI mirrors it in `chunkSelection.ts`. The predicate is `ChunkWindow.Selection.contiguous()` in `shared/common` — do not add a fourth copy. A window selection cannot be gapped; only `fileIds`/checkbox picks need checking.

### Compression belongs to the compression job only
- Never compress on a read path: it once rewrote the repository mid-read and the client wrote LZ4 bytes under a `.jfr` name.
- `Compression.compress` writes to `.<name>.<uuid>.tmp` beside the target and renames — an archive appears whole or not at all. Everything downstream (delete-on-archive-exists, `file()` handing over the archive) rests on it. Whether a type is an archive is `HubManagedFile.isArchive()`, not the extension.

### Protos ship together
- Hub and Microscope are one release: a removed field's number is reused and survivors renumbered, no `reserved`. If versions ever diverge, flip this rule.

### Nothing on the hub reads a recording
- No JFR parser, no replay stream, no event-activity scan, no session-environment reader (`SessionEnvironmentReader` lives in `hub-client` and parses in Microscope). Pull the chunks covering a window into Microscope instead. Never add MCP to the hub.

### Scheduler
- No descriptor hierarchy, no `JobContext`: a job reads its params from its `JobConfig` at construction and `execute()` takes nothing. Every job bean lives in `configuration/SchedulerConfiguration`; `JobLocks` is the one per-job lock the tick and `ManualJobRunner` both take.
