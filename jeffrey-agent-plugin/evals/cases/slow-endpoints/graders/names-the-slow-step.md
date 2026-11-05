---
type: llm
focus: last_message
---

The persons service ("persons dom", about 76 s under load) traces every request. By p95 the slowest
endpoints are GET /persons/{count} (664 requests, p50 about 1.1 ms, p95 about 29 ms, p99 about 70 ms) and
POST /persons (664 requests, p95 about 29 ms, p99 about 77 ms); GET /persons (3,316) has p95 about 5.7 ms
and GET /persons/count (7,161) about 2.4 ms. Inside GET /persons/{count} the time goes to count_query
(about 483 ms, in the person.pick-ids span), person.redact-opinions (about 396 ms), find_all_by_ids
(about 367 ms) and person.to-json-tree (about 96 ms). Every slow request is a tail: medians stay near a
millisecond.

Every endpoint's tail is also shared: the slowest requests of unrelated endpoints finish at the same
instants (stalls of ~85–175 ms), while every statement's p95 is about 0.56 ms and the pool never makes a
thread wait. The container has one CPU core and was CPU-throttled in 118 of its 301 CFS periods, which
stalls every thread at once.

PASS if the reply names GET /persons/{count} and/or POST /persons as the endpoints with the slow tail,
supports it with figures read from the traces (the endpoints' percentiles or the slow requests'
durations), and explains the slow requests from what the profile shows: the span breakdown of a slow
request (the count_query in pick-ids, redact-opinions, find_all_by_ids), or the stalls shared by every
endpoint at the same moments, with the statements and the pool ruled out by their figures (the CPU
throttling named as a likely cause is a plus, not required).
FAIL if it gives no trace figures, answers only from a whole-recording flamegraph, blames slow SQL or the
connection pool against their own figures, or calls every request slow when the medians are around a
millisecond.
