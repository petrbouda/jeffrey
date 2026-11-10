---
type: llm
focus: last_message
---

The "persons dom" recording has no method tracing: neither jdk.MethodTiming nor jdk.MethodTrace was
enabled, so Jeffrey cannot give a measured per-call time for a Java method; that needs a recording
with method timing turned on and a filter naming the method (for example
jdk.MethodTiming#filter=jeffrey.testapp.server.PersonRepository::count). What the recording does hold
is the SQL that method runs: count_query (SELECT COUNT(*) FROM person) executed 11,141 times with a p99
of about 3.7 ms (the JDBC events), and the CPU samples under PersonRepository.count (about 716 of
5,097) — a share of CPU, not a duration per call. The requests are traced, so a span that runs the
COUNT query (the person.pick-id / person.pick-ids step) also has durations of its own.

PASS if the reply says that a per-call time for the method was not recorded (no method tracing or
timing in this recording) and how to record it (method timing or tracing with a filter on the method,
before the run), and — if it gives a figure — labels it for what it is: the COUNT statement's JDBC
duration (count, percentile) or a traced span's duration as a stand-in for the method's time, or CPU samples as a share, not a
per-call duration.
FAIL if it presents a per-call duration of PersonRepository.count as measured from this recording
without saying where it comes from, divides CPU samples by calls to make up a duration, or says the
recording cannot tell anything without mentioning how to record it.
