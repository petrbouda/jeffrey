---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a191e4-0e90-7c2b-a27c-67382c632b50 | 01a191e4-0e7f-7ecf-bc1c-4622f87d0461 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T12:16:44.688814Z | PT0S | yes | no |
| 01a191e4-0bcb-782d-90e9-27653514268f | 01a191e4-0bbf-7009-9464-cf12a3216f98 | checkout candidate | (quick analysis) | JDK | 2026-10-31T12:16:12.466836Z | PT30.470749S | yes | no |
| 01a191e4-080c-7e31-992b-5121062a1b20 | 01a191e4-07ec-7efa-bf3e-9632f6a1b310 | checkout baseline | (quick analysis) | JDK | 2026-10-31T12:15:41.680913Z | PT30.492652S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
