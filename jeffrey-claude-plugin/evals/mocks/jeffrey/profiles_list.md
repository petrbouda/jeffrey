---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a18f01-3ae2-724b-a47d-71ecab1d1566 | 01a18f01-3ad1-74d9-94b7-0118ac1b5b28 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-30T22:49:44.930204Z | PT0S | yes | no |
| 01a18f01-3837-7780-801b-7b52cb546b67 | 01a18f01-3828-7884-818c-2aae19809b3d | checkout candidate | (quick analysis) | JDK | 2026-10-30T22:49:12.698403Z | PT30.557946S | yes | no |
| 01a18f01-34b6-7531-a0a2-eb6fcb82eda4 | 01a18f01-3492-7013-8700-fa51e999fd51 | checkout baseline | (quick analysis) | JDK | 2026-10-30T22:48:41.942795Z | PT30.459869S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8586/recordings
