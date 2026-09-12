# Microscope MCP Phase 4 workflows

The approved audit's Phase 4 adds five workflows to the existing MCP endpoint. This implementation extends the Phase 3 branch while PR #294 is open. It keeps existing tools and text callers working, uses the existing profile leases, pipeline registry and replay RPC, and adds no protocol task transport or infinite subscription.

## Bounded Hub queries

A read-only Hub tool accepts a full session reference, selected event types, optional start/end bounds, and bounded row/UTF-8 byte limits. It returns complete event rows with the applied filters, source identity, counts and termination reason. RPC deadlines and cancellation bound work, including when a server stalls. Session lookup must be scoped on the Hub by workspace/project/session; corrupt or skipped input must not be reported as complete. Extend the replay protocol additively and retain existing UI callers.

## Unified operations

Expose a consistent operation identity and status/cancel interface for recording import, Hub download and heap preparation. Reuse the schedulers and registries which own the work. IDs identify attempts, so an old cancellation cannot affect a retry. Status distinguishes queued/running/cancel_requested/completed/failed/cancelled and reports real progress and explicit retry instructions. Cancellation remains requested until the worker exits; an interrupt-ignoring worker keeps its deduplication slot. Do not claim rollback of completed side effects. Retention and process-restart limits are explicit.

## Evidence and comparison quality

Add a tool-backed evidence resource using the existing finding models: schema version, profile and recording identities, build, applied filters, units, denominators, findings, truncation and capability gaps. A bounded response preserves complete records and reports omissions. Extend comparison with actual recording duration, event overlap, available sampling settings and recording-quality evidence. Do not manufacture a workload denominator from sample count; report per-operation normalization unavailable unless the recording establishes one.

## Diagnostics

A read-only diagnostic resource reports the actual build, selected/effective families, profile readiness counts, bounded Hub reachability including deadline failures, and per-tool call counts, errors, latency and output sizes. Metric keys come only from advertised tools; arguments, results, credentials and raw environment values are never retained. Hub diagnostics obey the existing Hub access gate. JSON output remains bounded and reports omissions.

## Delivery

Work in an isolated Phase 4 worktree. Keep the original checkout and Phase 3 PR unchanged. Regression tests cover real public endpoints and controlled asynchronous/RPC behavior. Run Maven serially, update corresponding Jeffrey Pages documentation, and leave implementation uncommitted until requested.
