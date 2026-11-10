---
type: llm
focus: last_message
---

The "persons dom" recording allocated about 4.1 GB in about 76 s, all of it in TLABs, over 238 types;
byte[] is the largest type (about 1.99 GB, some 48%), then java.util.ArrayList$Itr (about 0.7 GB),
String (about 0.37 GB) and char[] (about 0.24 GB). Charged to the service's own code (jeffrey.testapp)
is only about 13% (some 545 MB): ServerApplication.main (about 215 MB, which is Spring Boot starting up,
not request work), PersonRepository.modifyOpinion (about 140 MB, 3.4%, through String.replaceAll), the
row mapper PersonRepository.lambda$personMapper$0 (about 91 MB, 2.2%, reading the result set) and
PersonController's toJsonTree (about 18 MB). The rest is allocated beneath the service, in the
framework, the JDBC driver and the JVM.

PASS if the reply weighs allocation by bytes and gives a share with what it is a share of (the 4.1 GB
total or the recording), names what allocates the most — the dominant types (byte[] first) or where in
the code — and, for the service's own code, names at least one of modifyOpinion (String.replaceAll),
the person mapper or toJsonTree with a figure, while saying that most of the bytes are allocated outside
the service's own methods. Calling ServerApplication.main a request-path hotspot is a FAIL unless the
reply says it is startup.
FAIL if it ranks by allocation counts instead of bytes, gives figures without a denominator, presents
startup as the main runtime allocator, or says it changed the code.
