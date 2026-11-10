---
type: llm
focus: last_message
---

Against "persons direct" (4,628 CPU samples), "persons dom" took 5,097. The differential tree shows the
extra time coming in through the request path: DispatcherServlet into PersonController, where
PersonController.getRandomPerson (GET /persons) grew by about 225 samples (108 to 333) and now calls
InefficientPersonService.getRandomPerson, new in this build (about 319 samples), and
PersonController.getNPersons (GET /persons/{count}) grew by about 59 and calls
InefficientPersonService.getNPersons, also new (about 126). Underneath, the time is the extra COUNT
query (PersonRepository.count through the JDBC driver into SQLite) that the new service runs before
each random pick.

PASS if the reply follows the extra CPU down a call path — PersonController into the new
InefficientPersonService (getRandomPerson and/or getNPersons) — with the samples it gained (a delta, or
both sides' figures), and names what runs underneath (the COUNT query, PersonRepository.count, JDBC or
SQLite). It may note limits of a one-run comparison.
FAIL if it names only frames without figures, puts the extra time in the JSON tree or the JFR stream
as the main cause, or reads CPU samples as request latency.
