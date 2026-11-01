## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — make the name a Markdown link marked with `↗`:
`[trace 7e34c5994dc96208 ↗](<its uiLink>)`, and in a table a column of `[↗ open](<its uiLink>)`.
Never write the bare URL. The `↗` is what makes it a link the user sees: a terminal that renders
links shows the text alone, clickable, with the URL behind it, and an unmarked name reads as plain
words. That holds in every reply, not only in a written report: a finding, a ranked list, a
follow-up offer, a sentence in passing.

- Take the link from the answer the thing came from. A row with its own `uiLink` — a trace, an
  operation, an exemplar — links with that one; otherwise the answer's `uiLink` is the page that
  shows the row. Quote the `uiLinkNote` beside it when there is one.
- No link in hand for one of the profile's pages (GC, JIT, threads, the heap views, …) →
  `profiles_viewLink`, one cheap call; the profile itself → `profiles_link`.
- Never build, edit or guess a URL, and never fetch one: the link is for the user, and one typed by
  hand opens the wrong page or none.
- An area not yet analysed is not a thing and needs no link, and a result no page shows — a raw SQL
  query — has none; say so rather than inventing one.
