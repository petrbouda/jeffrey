---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a19136-47ae-7e29-af6a-6f22a01af9c4 | 01a19136-479c-722a-923a-de6fb451cc1f | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T09:06:56.046936Z | PT0S | yes | no |
| 01a19136-44f6-7cd5-8be7-18cd6ba6adb2 | 01a19136-44e7-73d4-ad71-c14a83758167 | checkout candidate | (quick analysis) | JDK | 2026-10-31T09:06:23.906187Z | PT30.49395S | yes | no |
| 01a19136-4192-75e9-81e4-7d46ad78e6b8 | 01a19136-4172-771c-8ab6-dde413f31258 | checkout baseline | (quick analysis) | JDK | 2026-10-31T09:05:53.202570Z | PT30.432723S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
