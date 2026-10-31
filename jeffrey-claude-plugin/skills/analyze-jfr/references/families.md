# The tool families

Every family a profile is read with, and the tools in each. The skill's routing names the family; this
says what each of its tools answers.

## Which family answers which question

| Family | Tools |
|---|---|
| `profiles_` | `list`, `summary` (identity, features, recorded event types and the top findings in one call — start here), `get`, `features`, `samplerHealth` (whether the samples can be trusted), `link` (the profile in the Jeffrey UI), `viewLink` (one named page) |
| `flamegraph_` | `list` (which event types this profile can graph — call it first), `export` (the call tree as Markdown) |
| `compare_` | `list` (whether two profiles are comparable at all — call it first), `movements` (what moved, ranked), `flamegraph` (the differential call tree) — the `compare-jfr` skill has the workflow |
| `traces_` | `overview`, `operations`, `notifications`, `operationExport`, `slowestTraces`, `traceExport`, `spanFlamegraphExport`, `operationFlamegraphExport`, plus `attributeKeys`/`attributeValues`/`attributeSearch` |
| `jvm_` | `sections` (call it first), `autoAnalysis`, `gc`, `gcDetail`, `safepoints`, `jit`, `threads`, `threadDumps`, `threadDump`, `nativeMemory`, `classLoading`, `exceptions`, `exceptionSites`, `system`, `security`, `container`, `configuration`, `flags` — the machine underneath the application |
| `http_` | `overview`, `endpoint` — HTTP traffic, `SERVER` (served) or `CLIENT` (called out) |
| `jdbc_` | `overview`, `statementGroup`, `pools` — the queries it ran, and the pool in front of them |
| `grpc_` | `overview`, `service`, `traffic` — gRPC latency and message sizes, `SERVER` or `CLIENT` |
| `methodtracing_` | `overview`, `slowest`, `timing` — instrumented method timings (JEP 520) |
| `io_` | `overview`, `endpoints`, `slowest` — socket and file I/O, the waiting a CPU graph cannot see |
| `blocking_` | `overview`, `monitors`, `pinnedThreads` — contended locks, waits, parks, virtual-thread pinning |
| `timeline_` | `hotWindows` (when the samples landed), `zoom` (sub-second, inside one window) |
| `memory_` | `allocations` (by type, not call site), `leakCandidates` (JFR-side, needs no heap dump) |
| `jfr_` | `listTables`, `describeTable`, `describeEventType` (the fields inside one event type), `listEventTypes`, `queryEvents`, `executeQuery`, `getProfileInfo` — raw DuckDB when no purpose-built tool fits; the `jfr-sql` skill has the schema |
| `heap_` | Everything a heap dump answers — the `analyze-heap` skill has the order to run it in |
| `recordings_` | `analyzeFile` (a file not in Jeffrey yet), `analyzeRecording` (uploaded but never analysed), `status` (whether a slow analysis has finished), `list` (the Quick Analysis store) |
