---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a191c0-1ead-7acd-8332-7cc1292945ad | 01a191c0-1e9e-7034-8673-c01b4367ffc7 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T11:37:29.517722Z | PT0S | yes | no |
| 01a191c0-1bfe-719c-9aa4-92e7ab3cb7e0 | 01a191c0-1bf0-7aac-9349-631c1fa5c320 | checkout candidate | (quick analysis) | JDK | 2026-10-31T11:36:57.399511Z | PT30.499511S | yes | no |
| 01a191c0-18b5-7f0f-b052-2a34a3e75a92 | 01a191c0-1893-7963-b8f0-298ee3339b17 | checkout baseline | (quick analysis) | JDK | 2026-10-31T11:36:26.701542Z | PT30.420636S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
