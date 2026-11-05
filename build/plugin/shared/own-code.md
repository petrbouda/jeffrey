## Your own code

Jeffrey never reads this repository, so it is told which code is the application's own: the root
packages of the source tree you are in, passed as `framePrefixes`. Work them out once and reuse them
for every call:

- `Glob` for `**/src/main/java/**/*.java` and `**/src/main/kotlin/**/*.kt` and take the shortest
  package roots shared by the files (usually two or three segments: `com.acme.orders.`). Fall back to
  the `groupId` in `pom.xml` or the `group` in `build.gradle(.kts)`. In a multi-module repository
  where the question is about one service, take that module's packages and the shared ones it uses.
- Keep the trailing dot, so `com.acme.` does not also match `com.acmecorp.`.
- Only the source tree's own packages, never a library's or the JDK's (`java.`, `org.springframework.`):
  library and JDK work is already charged to the own method that called it. What a library costs on
  its own is read from `flamegraph_export`.
- Name them in one line — "your code is `com.acme.orders.`" — so the user can correct you before
  anything depends on it. `code_hotspots` called without them answers with the profile's busiest
  non-library packages as `suggestedFramePrefixes`; one that differs from yours means the profile is
  of another application, or the code was shaded or relocated.

A frame comes back by its runtime name, and source and runtime spell code differently: a lambda body
runs as `Outer#lambda$method$1`, a nested class is `Outer$Inner` at runtime and `Outer.Inner` in
source, a Kotlin top-level function lives on `FileKt`, a Kotlin lambda or coroutine is a class named
after its function (`Outer$method$1`). `code_method` takes the source spelling and lists every runtime
name it merged in `matchedFrames` — read them before you name a line of source for a figure. A row of
`code_hotspots` or `code_callSites`, and each of `compare_methods`' `unlistedMovers`, carries its
`reference` in that spelling: pass it on as it is, never the row's runtime `methodName`. A hotspot that
is your own wrapper — a database client, an HTTP helper, a generic executor — is charged everything
below it, so the change is rarely in it: its `calledFrom` names the code of yours in another class that
asked for the cost (the repository method behind a query, the service behind a call), and that is where
a fix lands. Name both. A method `compare_methods` answers `NOT_SEEN` lists
`nearestReferences` — most often the same method with its nested class or package spelled out
(`com.acme.Checkout.PriceCalculator#formatLine` for `Checkout#formatLine`). A `NOT_SEEN` method has
no figures: if the answer's first `nextTools` entry is `compare_methods`, make that call before you
read, report or compare any figure of that method. It respells the methods Jeffrey can match without
doubt. Never stand in an `unlistedMovers` row for a listed method: its `charged` cost leaves out the
own methods it calls, so it hides the very change a diff added below it. With no such entry, the
candidates are ambiguous or live in another class; check their classes in the source before you pass
one yourself.
