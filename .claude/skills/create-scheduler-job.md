# Create Scheduler Job

Add a new job to the Volume Scheduler: a periodic task it runs on the shared recording volume,
once per project of every workspace per run (retention and compression are the two today).
Microscope has no scheduler and no jobs.

## Usage

```
/create-scheduler-job
```

## Workflow

### Step 1: Gather Job Information

Ask the user, with AskUserQuestion:

1. **Job name** — the `JobType` constant and its id (e.g. `LOG_CLEANUP("log-cleanup")`) and the
   class name (`LogCleanupJob`).
2. **What one project visit does** — on the project's sessions and files, through its
   `VolumeProject`.
3. **Settings** — names (kebab-case), types and defaults, e.g. `max-age=7d`.
4. **Default period** and whether it is **enabled by default**.

Every job can be run on demand through `POST /api/v1/jobs/{id}/run`; there is nothing to opt into.

### Step 2: Backend (volume-scheduler)

Read `RetentionJob` and `CompressionJob` first: they are the reference jobs.

1. **Job type** — add the constant, with its id, to
   `volume-scheduler/src/main/java/cafe/jeffrey/volumescheduler/scheduler/JobType.java`.
2. **Job class** — a package of its own, `volume-scheduler/src/main/java/cafe/jeffrey/volumescheduler/{job}/{JobName}Job.java`:
   - implements `Job`; `run()` hands the per-project work to a `ProjectFanOut` built in the
     constructor (`fanOut.forEachProject(...)`) and returns one line saying what the run did.
     The fan-out runs each visit isolated, so one broken project does not abort the run. There is
     no job base class: compose, don't extend;
   - constructor takes `VolumeProjects`, a domain record of its settings (like `RetentionPolicy`:
     no Spring type, validated in its compact constructor) and any collaborator (`Clock` for
     anything time-based — never `Instant.now()`);
   - it logs every line with the project's `ref().logScope()`;
   - it works only on the volume, through `shared/volume`; it never calls Microscope.
3. **Configuration** — add a nested record to
   `volume-scheduler/src/main/java/cafe/jeffrey/volumescheduler/config/VolumeSchedulerProperties.java`,
   `jeffrey.volume-scheduler.{id}.*`, with `enabled` and `period` and its settings, each with its
   `@DefaultValue`, a `schedule()` → `JobSchedule` and a mapping to the job's domain record. Put
   the commented defaults into the reference block of `application.properties`.
4. **Bean** — an `@Bean` for the job in
   `volume-scheduler/src/main/java/cafe/jeffrey/volumescheduler/config/VolumeSchedulerConfiguration.java`,
   and a `ScheduledJob(job, properties.{id}().schedule())` in the `jobScheduler` bean's list. A
   disabled job is listed and runs on demand, but is never scheduled.

### Step 3: REST API

Nothing to add: `GET /api/v1/jobs` lists every job the scheduler holds, and the job's id addresses
it in `/api/v1/jobs/{id}` and in its properties. If the job has settings an operator should see,
add them to `JobResponse` the way the retention settings are. The Volume Scheduler has no UI.

### Step 4: Tests

- `volume-scheduler/src/test/java/cafe/jeffrey/volumescheduler/{job}/{JobName}JobTest.java`,
  modelled on `CompressionJobTest` (Mockito mocks of `VolumeProjects` and `VolumeProject`,
  `@Nested` by scenario, `Clock.fixed`); use `VolumeWriter` from `shared/test` and
  `ProjectVolumeFixture` when the job reads a real session layout.
- Extend `VolumeSchedulerPropertiesTest` for the new defaults and refusals, `JobTypeTest` for the
  id, and `VolumeSchedulerApplicationTest` with a run on demand over a real volume.

### Step 5: Documentation

Document the job and its properties on
`jeffrey-pages/src/views/docs/volume-scheduler/configuration/VolumeSchedulerConfigurationPage.vue`
and in the job lists of `docs/volume-scheduler/VolumeSchedulerOverviewPage.vue`,
`docs/volume-scheduler/VolumeSchedulerArchitecturePage.vue` and the API page.

### Step 6: Verify

```bash
$MVN -q -pl volume-scheduler -am test
```

## Files to Modify

| File | Change |
|---|---|
| `volume-scheduler/.../scheduler/JobType.java` | new constant and id |
| `volume-scheduler/.../{job}/{JobName}Job.java` | new job |
| `volume-scheduler/.../config/VolumeSchedulerProperties.java` | its settings, with defaults |
| `volume-scheduler/.../config/VolumeSchedulerConfiguration.java` | `@Bean`, `ScheduledJob` |
| `volume-scheduler/src/main/resources/application.properties` | commented defaults |
| `volume-scheduler/src/test/.../{job}/{JobName}JobTest.java` | tests |
| `jeffrey-pages/src/views/docs/volume-scheduler/configuration/VolumeSchedulerConfigurationPage.vue` | docs |

## Existing Jobs

- **`RETENTION`** (`retention/RetentionJob`): ages out sessions, caps sessions per instance, trims
  the newest session's old chunks, holds the project under `max-size` and deletes stale instances;
  publishes the storage figures `GET /api/v1/storage` serves.
- **`COMPRESSION`** (`compression/CompressionJob`): LZ4-compresses the closed chunks of each
  instance's two newest sessions (`RecordingCompressor`).
