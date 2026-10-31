---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a19242-7d5f-7d93-b7d1-5fd6510bed9c | 01a19242-7d4a-7846-9006-cb0e1cbce553 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T13:59:53.439882Z | PT0S | yes | no |
| 01a19242-79fe-783d-a5a1-11b7711d8ee0 | 01a19242-79f1-7e85-81c6-08b1e2ad1371 | checkout candidate | (quick analysis) | JDK | 2026-10-31T13:59:21.005299Z | PT30.462906S | yes | no |
| 01a19242-75fd-7700-858a-3a8aa32440a1 | 01a19242-75db-739a-8bcc-ba0019540978 | checkout baseline | (quick analysis) | JDK | 2026-10-31T13:58:50.323518Z | PT30.407498S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
