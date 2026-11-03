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
- `JfrParser`'s `Path` methods read a `.jfr.lz4` through a decompressing stream (`JfrParser.read`); read as it lies an archive has no chunk header and once came back as a recording with no chunks — no span, source `JDK` — rather than failing. `iterateChunks(Path, BiConsumer<FileChannel, …>)` hands out a channel, which only a plain file has, so it refuses an archive by name. A file can hold two chains of chunks that overlap in time (`jfrsync`: the JDK's and async-profiler's, their event types numbered differently); the span is the earliest start to the latest end, never the chunks' durations added up. `PersonsRecording` (`shared/test`) is such a file, with its figures pinned.
- Splitting a file into chunks only **manufactures** parse units: `SourceParseMode` splits while there are fewer than `availableProcessors() * 2` files, otherwise parses files as they lie. A `.jfr.lz4` is always split (`EventStream` cannot open one; the split decompresses in the same pass).
- Each source gets its **own** scratch directory — chunk files are named by position, and a shared directory would have the second source overwrite the first's `chunk_0` silently.
- Sources expand on virtual threads; their units parse on the bulk pool. Never share the two pools (an expander would block a bulk thread waiting on parses queued behind it).
- Auto-analysis (JMC rules) starts **before** the parse, reads the recording file only, and is joined by the warming stage — a profile that answers at all answers with its findings. Do not add a poll for it.
- Auto-analysis loads a recording **one chain at a time** and merges the collections, the JDK's first (`RecordingChains`, `AutoAnalysisDataProvider`). In a `jfrsync` file both chains define `jdk.ActiveSetting` and `jdk.ActiveRecording` with different fields; loaded together, JMC's parallel chunk loading registers whichever definition comes first under the name and renames the other (`jdk.ActiveSetting<uuid>`), so the rules that read the recording's settings (Method Profiling, Allocated Classes, Threads Allocating, TLAB Allocation Ratio, GC Freed Ratio, GC Pressure) judged the same file differently from one evaluation to the next. Do not load the chains together again, and keep the merge: a rule run per chain loses the ones that need both (context switches), and keeping one chain's settings loses the rules that read the other's. The JDK's chain goes first because some rules take the first recording's facts (its start, its length) — met after async-profiler's, the CPU-load rules find too few samples. A file one profiler wrote is loaded as it lies; only a mixed file is cut into chunks, in a scratch directory. `AutoAnalysisDataProviderRealisticTest` holds every answer alike, again and however the run is handed over, and every settings-reading rule answering.

### Files are stored flat under `<recordingId>-<name>`
- The prefix keeps the directory unique across recordings; it is **not** part of the name. `recording_files.filename` holds the name without it, `RecordingStorageLayout` (in `recording-storage-api`) is the one place that joins the two, and `StoredFile` carries name beside path. Reading the name back off the storage path yields a doubled prefix that finds nothing (analysis reports missing, deletion leaves it, `IdeRecordingLookup` reads every recording as never imported).
- Never concatenate the prefix yourself: `recordings-core` writes files, `ProfileRecordingsManager` and `RecordingFileLookup` read them back, and all of them go through `RecordingStorageLayout`. Readers start from the recording's `recording_files` rows and never list the directory to find a recording's files by name.
- `RecordingFileLookup` (auto-analysis on demand, a `@Bean` in `ProfileAnalysisConfiguration`) returns **every** JFR file of the recording, the same set the import ran the rules over, and nothing else: no heap dump, pprof, OTLP or log. If any of them is gone from disk it returns none, as the import does. Anything it returns makes `canGenerate()` answer true, so a non-JFR answer advertises an analysis that can only fail.

### Microscope's `ManagedFile` is the only type that knows a heap dump from a log
- `recording-storage-api` (`ManagedFile`, `FileCategory`, `RecordingFile`, `Recording`) carries description, extension, matcher and category. The hub cannot see it and must not depend on it.
