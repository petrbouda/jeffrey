---
type: llm
focus: last_message
---

"persons dom" holds 57,373 wall-clock samples against 5,097 CPU samples. About a third of the wall clock
is idle (19,055 samples; 38,318 left when idle threads are excluded), and of the rest the service's own
methods carry under 2% — the request threads' time is spent below them, in the frameworks, the JDBC
driver and SQLite, and in socket I/O. The container is also limited to one CPU core and was throttled in
118 of 301 CFS periods (about 2.7 s parked), which shows as waiting rather than CPU.

PASS if the reply reads the wall clock (not only the CPU), separates idle waiting from the requests'
time (states the idle share, or reads the wall clock with idle excluded), and names where the request
threads spend it with a figure — the database statements, the socket I/O, or the CPU throttling.
FAIL if it answers from CPU samples alone, or counts idle pool threads as the requests' waiting.
