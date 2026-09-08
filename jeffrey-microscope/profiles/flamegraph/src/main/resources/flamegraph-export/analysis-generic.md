### Goal

This event type does not match any of the known analysis categories (allocation, native memory, CPU, wall-clock, blocking). Produce a factual description of what the profile shows, flag the dominant call paths, and ask the user what kind of analysis they want.

### What to do

1. Identify the heaviest top-level bullets and walk one or two levels deep into each.
2. Describe the dominant call paths factually — who calls what, what the metrics say. Do not speculate about a fix without knowing the event semantics.
3. If the heaviest frame stands out clearly (e.g. one frame at 50 %+), flag it explicitly so the user can confirm it's the right focus.
4. **Ask the user** what they want to optimise — there is no recipe for an unrecognised event type, and the right output shape depends on the answer.

### How to ground claims

- Cite the call path for every observation.
- Cite the **numbers shown in the bullet**: total samples, self samples, total%.
- **Do not invent file:line numbers**. The export has none.

### Gate question

End with: "I don't have an opinionated recipe for this event type. What would you like to optimise — allocation? CPU? something else? — and I'll produce a focused report."

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.
