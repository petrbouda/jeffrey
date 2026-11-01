## Implement fixes

You are usually running inside the repository of the application that was profiled, so a finding
can become a change to its code. When it can, offer that as an option labelled exactly
**Implement fixes** — not "I can propose a change" in passing — beside the follow-ups that close the
answer. In Claude Code close with `AskUserQuestion` whenever this option is on offer, a named area's
answer included: the follow-ups, **Implement fixes**, and "Stop here". Elsewhere, one line that
starts "Implement fixes:". Offer it, never take it: nothing is edited until the user picks it.

Offer it only when both hold, or it is noise — and when they do not, leave it out without a word:
no "Implement fixes: not on offer", no sentence on why there is nothing to fix.

- the working directory is a repository — a `.git` there (`Glob`), or `git rev-parse
  --show-toplevel` answering;
- at least one finding names something that repository holds — the class of the hot frame, the
  throw site, the configuration file (`logback.xml`, `application.yml`, a pool's settings): `Grep`
  or `Glob` for it finds a file there, not only JDK, framework or library code.

Then say how the code relates to the profile in the option's description: `profiles_get`'s
`recordingCommit` against `git rev-parse HEAD` — "profiled at abc1234, HEAD is def5678" when they
differ, "commit not recorded" when `recordingCommit` is null. That qualifies the offer; it never
removes it.

The option's description names what it would change, at most two findings by place
(say, logback.xml's caller data, or the pricing class's line formatter). When the user asks what else there
is, or the analysis winds down, the ledger's **Covered** findings that pass the same check become
one multi-select **Implement fixes for:** question.

Picked, it hands over to the `advise-jfr` skill with what this conversation already established —
the profile id, each finding's frame or setting, its figures and its link — so it starts at reading
the source rather than choosing evidence again, and keeps its own gate: the proposed change per
finding first, then which to apply, and only the accepted ones are edited. A setting (a JVM flag, a
pool size) is proposed as a value to change, not as an edit to code. After the build and the tests,
offer **Re-profile and compare** — `profile-run` records the changed code, `compare-jfr` puts it
against this profile — so the fix is measured rather than assumed.
