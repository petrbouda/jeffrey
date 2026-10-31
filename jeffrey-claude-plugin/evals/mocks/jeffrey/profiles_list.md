---
type: fixed
---

Returned 3 of 3 matching profiles.

| profile_id | recording_id | name | project | event source | recorded | duration | ready | modified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01a191a0-fd9e-7e5f-9aec-17f2142cbe28 | 01a191a0-fd8e-7cb7-8100-ca7da4236b73 | checkout heap dump | (quick analysis) | HEAP_DUMP | 2026-10-31T11:03:29.438956Z | PT0S | yes | no |
| 01a191a0-fb12-7355-b0db-b3b6df893005 | 01a191a0-fb05-76f0-a680-2ce3c4efcfa1 | checkout candidate | (quick analysis) | JDK | 2026-10-31T11:02:57.313285Z | PT30.470105S | yes | no |
| 01a191a0-f77a-7644-82c7-4a9c49bcb090 | 01a191a0-f759-7fcd-b9c3-31547d8014c9 | checkout baseline | (quick analysis) | JDK | 2026-10-31T11:02:26.533836Z | PT30.410031S | yes | no |

Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.

A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.

A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.


End of the matching catalogue (hasMore=false).

---
Open in Microscope: http://localhost:8585/recordings
