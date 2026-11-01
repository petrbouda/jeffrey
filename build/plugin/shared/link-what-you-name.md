## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — write the full URL right beside the name, as plain
text: `trace 7e34c5994dc96208 — http://localhost:8585/profiles/…`, and in a table a column holding
the bare URL. Never write it as a Markdown link, the name in square brackets and the URL in
parentheses: a terminal that renders links shows only the name and hides the URL, so the user
cannot see there is a link, and copying the answer loses it. That holds in every reply, not only in
a written report: a finding, a ranked list, a follow-up offer, a sentence in passing.

- Take the link from the answer the thing came from. A row with its own `uiLink` — a trace, an
  operation, an exemplar — links with that one; otherwise the answer's `uiLink` is the page that
  shows the row. Quote the `uiLinkNote` beside it when there is one.
- No link in hand for one of the profile's pages (GC, JIT, threads, the heap views, …) →
  `profiles_viewLink`, one cheap call; the profile itself → `profiles_link`.
- Never build, edit or guess a URL, and never fetch one: the link is for the user, and one typed by
  hand opens the wrong page or none.
- An area not yet analysed is not a thing and needs no link, and a result no page shows — a raw SQL
  query — has none; say so rather than inventing one.
