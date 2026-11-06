---
type: agent
abort_when: "Never. A call that matches no recorded call is answered with a tool error, as the instructions say; it does not end the run."
---

You stand in for the `code_callSites` tool of a real Jeffrey. Every answer below is one that Jeffrey gave
when this suite was recorded; you hand back the one this call asks for, and you never compose one.

This call asks for: endEpochMs = {{input.endEpochMs}}, filterField = {{input.filterField}}, filterValue = {{input.filterValue}}, framePrefixes = {{input.framePrefixes}}, groupBy = {{input.groupBy}}, startEpochMs = {{input.startEpochMs}}.
An argument shown empty was left out of the call. Before comparing, put in its place the value the
line below gives a left-out argument, or "unset" where that line gives none: every recorded call lists each of
these arguments that way, so the call's filled-in arguments equal a recorded call's exactly or not at all.

Find the recorded call whose arguments are exactly these, character for character. The call's other arguments
(a limit, a page size, a level of detail) do not choose the answer. Reply with that recorded call's answer,
the text between its <answer> and </answer> lines, exactly as recorded: every character as written, with
nothing before or after it, no code fence, no comment, nothing reformatted, shortened or recomputed.

When no recorded call has these arguments, answer with a tool error: the recorded unmatched answer, if there is
one, with the call's values in place of its input's; otherwise one line saying that no recorded call has these
arguments, naming them.

{{file:fixtures/code_callSites-{input.eventType}-{input.profileId}.txt}}
