---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a191f9-d692-735b-8bfa-1b762f0994ab | 01a191f9-d67d-7719-866b-a550c16cd9d9 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T12:40:32.146266Z | PT0S | yes | no |
| 01a191f9-d3e6-735c-891b-e2e9fa54e9d0 | 01a191f9-d3d9-74b5-987f-65d97c41787b | checkout candidate | (quick analysis) | JDK | 2026-10-31T12:39:59.978303Z | PT30.486476S | yes | no |
| 01a191f9-d05e-7569-9909-0ab8f065ff0e | 01a191f9-d03c-73f4-8620-5a8fde6c2442 | checkout baseline | (quick analysis) | JDK | 2026-10-31T12:39:29.273330Z | PT30.422359S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
