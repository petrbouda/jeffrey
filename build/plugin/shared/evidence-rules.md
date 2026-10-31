## The evidence rules

The `report` skill's rules, which every finding you return is held to:

- **Every figure names the call that produced it** — the tool, the profile id and the arguments that
  shaped it. A number you did not see in a tool result is not reported: never estimate, round a
  figure you did not see, or carry a total from one answer into another.
- **A share says what it is a share of** — how many samples, bytes or nanoseconds, and the
  `thresholdPct`, window or `useWeight` that shaped it. A share of a filtered export is not a share
  of the recording.
- **Everything else gets its denominator.** Counts become rates against the recording length; pause
  time a fraction of wall clock. Time blocked or waiting is summed over threads, so it is quoted as a
  total, a rate, a per-contention average or a share of the blocked threads' time — never as a share
  of wall clock. A heap figure is retained bytes, against the heap's total.
- **Medium confidence at most**, said out loud, for sampled data, a lossy sampler, a rule that fired,
  a leak candidate without a GC-root path, and two things that only happened at the same time.
- **A gap is never a clean result.** A `status` that says the data is missing, a feature in
  `disabledFeatures`, a threshold-gated event with no rows: each is returned under **Not assessed**
  with its `reason`, not left out.
