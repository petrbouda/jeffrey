---
type: llm
focus: last_message
---

In "persons dom" the 5,097 CPU samples split into: the request threads (Tomcat's workers) about 39%
(1,967), of which the largest part runs the COUNT query (PersonRepository.count, about 716, through the
JDBC driver into SQLite's native code); the JVM's own native threads — the JIT compiler and GC — about
31% (1,594; the container has one CPU core and the JIT compiled for some 33 s); an in-process JFR event
stream about 12% (626); and application startup about 9% (467).

PASS if the reply splits the CPU with figures (shares of the 5,097 samples, or counts) between the
service's work — with the database (the JDBC statements into SQLite's native code, the COUNT query /
PersonRepository.count) named as its largest part — and the JVM's and the profiler's own work (the JIT
compiler, GC, JFR or the profiler), read off the flamegraph by subtree or by self time. FAIL if it names a
single frame as "the" CPU without that split, presents startup, JIT or profiler time as request work, or
gives no figures.
