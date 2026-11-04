## Your own code

Jeffrey never reads this repository, so it is told which code is the application's own: the root
packages of the source tree you are in, passed as `framePrefixes`. Work them out once and reuse them
for every call:

- `Glob` for `**/src/main/java/**/*.java` and `**/src/main/kotlin/**/*.kt` and take the shortest
  package roots shared by the files (usually two or three segments: `com.acme.orders.`). Fall back to
  the `groupId` in `pom.xml` or the `group` in `build.gradle(.kts)`. In a multi-module repository
  where the question is about one service, take that module's packages and the shared ones it uses.
- Keep the trailing dot, so `com.acme.` does not also match `com.acmecorp.`.
- Name them in one line — "your code is `com.acme.orders.`" — so the user can correct you before
  anything depends on it. `code_hotspots` called without them answers with the profile's busiest
  non-library packages as `suggestedFramePrefixes`; one that differs from yours means the profile is
  of another application, or the code was shaded or relocated.

A frame comes back by its runtime name, and source and runtime spell code differently: a lambda body
runs as `Outer#lambda$method$1`, a nested class is `Outer$Inner` at runtime and `Outer.Inner` in
source, a Kotlin top-level function lives on `FileKt`, a Kotlin lambda or coroutine is a class named
after its function (`Outer$method$1`). `code_method` takes the source spelling and lists every runtime
name it merged in `matchedFrames` — read them before you name a line of source for a figure.
