---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a19118-a121-78ce-860d-aac8feadce6d | 01a19118-a10e-7b87-819c-04d13733ddd2 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T08:34:32.865583Z | PT0S | yes | no |
| 01a19118-9e8f-7971-a4dd-730ded87e153 | 01a19118-9e81-7d45-b0cd-ac11806e85c1 | checkout candidate | (quick analysis) | JDK | 2026-10-31T08:34:00.735532Z | PT30.5621S | yes | no |
| 01a19118-9b4b-7f8a-83f7-c8b7e66d2d6b | 01a19118-9b2b-7ede-91ee-4f53aef04e5a | checkout baseline | (quick analysis) | JDK | 2026-10-31T08:33:30.065681Z | PT30.403039S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8586/recordings
