---
paths:
  - "jeffrey-microscope/profiles/recording-parser/**"
  - "jeffrey-microscope/profiles/profile-management/**"
  - "jeffrey-microscope/recordings-core/**"
  - "jeffrey-microscope/recording-storage-api/**"
---

## Recording parsing and storage rules

### A recording is however many files it arrived as — they are never joined
- `RecordingSources` carries them; `RecordingEventParser`, `RecordingInformationParser`, `ProfileInitializer` and `ProfileDataInitializer.startAutoAnalysis` all take it. Each file is a self-contained JFR read independently into one `EventWriter`; the only thing spanning the set is the window, `min(start)`/`max(end)` over all chunk headers (`JfrParser.buildRecordingInfo`).
- Splitting a file into chunks only **manufactures** parse units: `SourceParseMode` splits while there are fewer than `availableProcessors() * 2` files, otherwise parses files as they lie. A `.jfr.lz4` is always split (`EventStream` cannot open one; the split decompresses in the same pass).
- Each source gets its **own** scratch directory — chunk files are named by position, and a shared directory would have the second source overwrite the first's `chunk_0` silently.
- Sources expand on virtual threads; their units parse on the bulk pool. Never share the two pools (an expander would block a bulk thread waiting on parses queued behind it).
- Auto-analysis (JMC rules) starts **before** the parse, reads the recording file only, and is joined by the warming stage — a profile that answers at all answers with its findings. Do not add a poll for it.

### Files are stored flat under `<recordingId>-<name>`
- The prefix keeps the directory unique across recordings; it is **not** part of the name. `recording_files.filename` holds the name without it, `RecordingsCoreManagerImpl.storagePath` is the one place that joins the two, and `StoredFile` carries name beside path. Reading the name back off the storage path yields a doubled prefix that finds nothing (analysis reports missing, deletion leaves it, `IdeRecordingLookup` reads every recording as never imported).
- `storagePath` (in `recordings-core`) writes the convention; `RecordingFileLookup` (in `profile-management`, used by auto-analysis) reads it back. Neither module depends on the other, so the two agree by hand — change one and change the other. The reader matches on `<recordingId>-`, separator included, or `rec-1` claims `rec-10`'s files, and prefers a `FileCategory.RECORDING` file over an artifact so JMC rules do not run over the GC log sitting beside the recording.

### Microscope's `ManagedFile` is the only type that knows a heap dump from a log
- `recording-storage-api` (`ManagedFile`, `FileCategory`, `RecordingFile`, `Recording`) carries description, extension, matcher and category. The hub cannot see it and must not depend on it.
