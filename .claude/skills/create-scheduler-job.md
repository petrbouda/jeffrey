# Create Scheduler Job

Add a new scheduler job to Jeffrey Hub: a periodic task the hub runs on the shared recording
volume, once per project of every workspace per tick (retention and compression are the two today).
Microscope has no scheduler and no jobs.

## Usage

```
/create-scheduler-job
```

## Workflow

### Step 1: Gather Job Information

Ask the user, with AskUserQuestion:

1. **Job name** — the `JobType` constant (`UPPER_SNAKE`, e.g. `REPOSITORY_LOG_CLEANUP`) and the
   class name (`RepositoryLogCleanupProjectJob`).
2. **What one project visit does**, and whether it works on the project's repository storage
   (sessions and files: extend `RepositoryProjectJob`) or on the project as a whole (extend `ProjectJob`).
3. **Parameters** — names (kebab-case), types and defaults, e.g. `max-age=7d`.
4. **Default period** and whether it is **enabled by default**.
5. **Manual run** — whether an operator may run it now from the Scheduler page (`ManuallyTriggerable`).

### Step 2: Backend (jeffrey-hub)

Read `ProjectRetentionJob` and `RepositoryCompressionProjectJob` first: they are the reference jobs.

1. **Job type** — add the constant to
   `jeffrey-hub/hub-model/src/main/java/cafe/jeffrey/hub/model/job/JobType.java`.
2. **Job class** — `jeffrey-hub/core-hub/src/main/java/cafe/jeffrey/hub/core/scheduler/job/{JobName}ProjectJob.java`:
   - extends `RepositoryProjectJob` (implement `executeOnRepository(ProjectManager, RepositoryStorage)`)
     or `ProjectJob` (implement `execute(ProjectManager)`). A job that walks the projects its own way
     extends the package-private `FanOutJob` directly, as `ProjectRetentionJob` does. `FanOutJob`
     runs each visit isolated (`visit(scope, body)`), so one broken project does not abort the tick;
   - constructor takes `WorkspacesManager`, the job's `JobConfig` and any collaborator (`Clock` for
     anything time-based — never `Instant.now()`); read `config.period()` and parse every
     `config.params()` entry into a typed field there, with each param name a `private static final`;
   - `period()` returns the configured period, `jobType()` the new constant;
   - for a manual run, also implement `ManuallyTriggerable.runManually()`, returning one line that
     says what the run did. Nothing else names the job: the REST layer and the UI pick it up from
     the interface.
   - The job works only on the volume, through `shared/volume`; it never calls Microscope.
3. **Bean** — add an `@Bean` method to
   `jeffrey-hub/core-hub/src/main/java/cafe/jeffrey/hub/core/configuration/SchedulerConfiguration.java`
   passing `config(JobType.{JOB})`. The scheduler, `ManualJobRunner` and `JobRegistry` collect every
   `Job` bean themselves; a disabled job is still built and listed, but never scheduled.
4. **Defaults** — add the job to `jeffrey-hub/core-hub/src/main/resources/scheduler-defaults.properties`:
   ```properties
   jeffrey.hub.scheduler.jobs.{job-key}.enabled=true
   jeffrey.hub.scheduler.jobs.{job-key}.period=5m
   jeffrey.hub.scheduler.jobs.{job-key}.params.{param}={default}
   ```
   `{job-key}` is the `JobType` constant in kebab-case (`REPOSITORY_LOG_CLEANUP` → `repository-log-cleanup`).
   `application.properties` overrides these; `SchedulerJobsProperties` merges the two.

### Step 3: Frontend (jeffrey-hub/pages-hub)

1. Extend the `JobTypeName` union in `jeffrey-hub/pages-hub/src/services/api/model/JobView.ts`
   (same order as the enum).
2. In `jeffrey-hub/pages-hub/src/views/server/SchedulerView.vue`, add the job to `displayNames`,
   `descriptions` and `icons` — they are `Record<JobTypeName, …>`, so the build fails until all three
   have it. The table, its period, params and the Run control come from the backend's `JobView`;
   there is no per-job markup and no modal to add.

### Step 4: Tests

- `jeffrey-hub/core-hub/src/test/java/cafe/jeffrey/hub/core/scheduler/job/{JobName}ProjectJobTest.java`,
  modelled on `RepositoryCompressionProjectJobTest` (Mockito, `@Nested` by scenario, `Clock.fixed`);
  use `VolumeWriter` from `shared/test` when the job reads a real session layout.
- Extend `SchedulerJobsPropertiesTest` if the defaults or param parsing are new in kind, and
  `ManualJobRunnerTest` for a manually triggerable job.

### Step 5: Documentation

Document the job and its properties on `jeffrey-pages/src/views/docs/hub/configuration/HubConfigurationPage.vue`
(the `jeffrey.hub.scheduler.jobs` section) and in the job list of `docs/hub/HubOverviewPage.vue` /
`docs/hub/HubArchitectureOverviewPage.vue`.

### Step 6: Verify

```bash
$MVN -q -pl jeffrey-hub/core-hub -am test
cd jeffrey-hub/pages-hub && npm run build
```

## Files to Modify

| File | Change |
|---|---|
| `jeffrey-hub/hub-model/.../model/job/JobType.java` | new constant |
| `jeffrey-hub/core-hub/.../scheduler/job/{JobName}ProjectJob.java` | new job |
| `jeffrey-hub/core-hub/.../configuration/SchedulerConfiguration.java` | `@Bean` |
| `jeffrey-hub/core-hub/src/main/resources/scheduler-defaults.properties` | enabled, period, params |
| `jeffrey-hub/pages-hub/src/services/api/model/JobView.ts` | `JobTypeName` |
| `jeffrey-hub/pages-hub/src/views/server/SchedulerView.vue` | name, description, icon |
| `jeffrey-hub/core-hub/src/test/.../scheduler/job/{JobName}ProjectJobTest.java` | tests |
| `jeffrey-pages/src/views/docs/hub/configuration/HubConfigurationPage.vue` | docs |

## Existing Jobs

- **`PROJECT_RETENTION`** (`ProjectRetentionJob`): ages out sessions, caps sessions per instance, trims
  the newest session's old chunks, holds the project under `max-size` and deletes stale instances;
  refreshes the storage dashboard. Manually triggerable.
- **`REPOSITORY_JFR_COMPRESSION`** (`RepositoryCompressionProjectJob`, a `RepositoryProjectJob`):
  LZ4-compresses the closed chunks of each instance's two newest sessions.
