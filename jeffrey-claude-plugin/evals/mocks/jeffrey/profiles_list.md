---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a19334-f651-798c-8817-2a68246b6ced | 01a19334-f63e-7e0b-99a5-e4676afd49cd | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T18:24:44.113643Z | PT0S | yes | no |
| 01a19334-f2e8-7e26-909f-6374da10c563 | 01a19334-f2db-79f4-b7cb-1bb5b2f09f7d | checkout candidate | (quick analysis) | JDK | 2026-10-31T18:24:11.648246Z | PT30.47555S | yes | no |
| 01a19334-eede-702a-8f73-06a758aeb429 | 01a19334-eebc-7dd7-a5ac-64517bc68d80 | checkout baseline | (quick analysis) | JDK | 2026-10-31T18:23:40.942111Z | PT30.422815S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8590/recordings
