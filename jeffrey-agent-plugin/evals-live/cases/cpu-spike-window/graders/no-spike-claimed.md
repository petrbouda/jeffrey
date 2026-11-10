---
type: llm
focus: last_message
---

The "persons dom" recording's 5,097 CPU samples are spread over 77 one-second buckets (about 66 a second
on average). The hottest seconds hold 106, 106, 103, 102 and 102 samples — about 2% of the total each,
at most about 1.6 times the average — and the activity tapers off over the last third of the recording
as the load ends. There is no second, or short window, that stands out from the rest.

PASS if the reply reads the CPU over time and says plainly that there was no real spike: the busiest
seconds are only slightly above the average (about 2% of the samples each, or about 100 against about
66), the load being even with a tail-off at the end. Looking at what ran in the busiest window is fine,
as long as it is not presented as an incident.
FAIL if it reports a spike or an incident in one window, quotes a window's figure without what it is
compared to (the average or the total), or answers only from the whole-recording flamegraph without
looking at time.
