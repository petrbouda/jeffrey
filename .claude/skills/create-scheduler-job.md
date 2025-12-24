# Create Scheduler Job

Create a new Scheduler job in the Jeffrey codebase with all required backend and frontend boilerplate.

## Usage

```
/create-scheduler-job
```

## Workflow

When this skill is invoked, follow these steps:

### Step 1: Gather Job Information

Ask the user for the following information using AskUserQuestion:

1. **Job Name**: The name for the new job (e.g., "DataCleanup", "MetricsExporter")
2. **Job Scope**: One of:
    - `PROJECT` - Job operates on individual projects (extends RepositoryProjectJob)
    - `GLOBAL` - Job operates globally across workspaces (extends WorkspaceJob)
    - `INTERNAL` - Internal system job (implements Job directly)
3. **Description**: Brief description of what the job does
4. **Parameters**: Does the job need configuration parameters? (yes/no)
5. **Allow Multiple Instances**: Can multiple instances of this job exist? (default: no)

### Step 2: Generate Backend Files

Based on the gathered information, create/modify these files:

#### 2.1 Add JobType Enum Value

**File:** `service/common-model/src/main/java/pbouda/jeffrey/common/model/job/JobType.java`

Add the new enum value with appropriate Group:

```java
MY_NEW_JOB(Group.PROJECT),  // or GLOBAL, INTERNAL
```

#### 2.2 Create Job Descriptor

**File:** `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/{JobName}JobDescriptor.java`

Use this template:

```java
/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.scheduler.job.descriptor;

import pbouda.jeffrey.common.model.job.JobType;

import java.util.Map;

/**
 * Job descriptor for {description}.
 */
public record {JobName}

        JobDescriptor(/* parameters if needed */)
        implements JobDescriptor

        < {
            JobName
        }

        JobDescriptor>{

        // If has parameters, add static factory:
        // public static {JobName}JobDescriptor of(Map<String, String> params) {
        //     return new {JobName}JobDescriptor(params.get("paramName"));
        // }

        @Override
        public Map<String, String> params() {
            return Map.of();  // Add params if needed
        }

        @Override
        public JobType type() {
            return JobType. {
                JOB_TYPE_ENUM
            } ;
        }

        // Override if multiple instances allowed:
        // @Override
        // public boolean allowMulti() {
        //     return true;
        // }
}
```

#### 2.3 Update JobDescriptor Sealed Interface

**File:** `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/JobDescriptor.java`

Add the new descriptor to the permits clause:

```java
public sealed interface JobDescriptor<T extends JobDescriptor<T>>
        permits ...,
        {JobName}

JobDescriptor {
```

#### 2.4 Update JobDescriptorFactory

**File:** `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/JobDescriptorFactory.java`

Add case in the switch statement:

```java
case{JOB_TYPE_ENUM}->
        new{JobName}

JobDescriptor();  // or .of(params) if has parameters
```

#### 2.5 Create Job Implementation

**File:** `service/core/src/main/java/pbouda/jeffrey/scheduler/job/{JobName}Job.java`

For PROJECT scope (extends RepositoryProjectJob):

```java
/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 * ... license header ...
 */

package pbouda.jeffrey.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.{JobName}JobDescriptor;

import java.time.Duration;

/**
 * {description}
 */
public class {JobName}Job extends RepositoryProjectJob

        < {
            JobName
        }

        JobDescriptor>{

        private static final Logger LOG = LoggerFactory.getLogger({JobName}Job.class);

        private final Duration period;

        public {
            JobName
        }

        Job(
                WorkspacesManager workspacesManager,
                SchedulerManager schedulerManager,
                RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
                JobDescriptorFactory jobDescriptorFactory,
                Duration period) {

            super(workspacesManager, schedulerManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
            this.period = period;
        }

        @Override
        protected void executeOnRepository(
                ProjectManager manager,
                RemoteRepositoryStorage remoteRepositoryStorage, {
            JobName
        }

        JobDescriptor jobDescriptor){

        String projectName = manager.info().name();
        LOG.

        debug("Executing {JobName} job: project='{}'",projectName);

        // TODO: Implement job logic here
    }

        @Override
        public Duration period() {
            return period;
        }

        @Override
        public JobType jobType() {
            return JobType. {
                JOB_TYPE_ENUM
            } ;
        }
}
```

For GLOBAL scope (extends WorkspaceJob):

```java
public class {JobName}Job extends WorkspaceJob

< {
    JobName
}

JobDescriptor>{
        // Similar structure but extends WorkspaceJob
        // Override executeOnWorkspace() instead
        }
```

#### 2.6 Register Spring Bean

**File:** `service/core/src/main/java/pbouda/jeffrey/configuration/JobsConfiguration.java`

Add bean method:

```java

@Bean
public Job {
    jobName
}

Job(
        @Value("${jeffrey.job.{job-name-kebab}.period:}") Duration jobPeriod) {
    return new {
        JobName
    } Job(
            liveWorkspacesManager,
            schedulerManager,
            repositoryStorageFactory,
            jobDescriptorFactory,
            jobPeriod == null ? defaultPeriod : jobPeriod);
}
```

**Job Period Configuration:**

Each job supports a configurable execution period via Spring properties:

- **Property name**: `jeffrey.job.{job-name-kebab}.period`
- **Format**: ISO-8601 duration (e.g., `PT1M` for 1 minute, `PT5M` for 5 minutes, `PT1H` for 1 hour)
- **Default**: Falls back to `jeffrey.job.default.period` (default: 1 minute) if not specified

Example configuration in `application.properties`:

```properties
# Global default period for all jobs
jeffrey.job.default.period=PT1M
# Override period for specific job
jeffrey.job.my-custom-job.period=PT5M
```

#### 2.7 For PROJECT Jobs: Add to Default Job Definitions

If the job should be auto-configured for new projects, add it to the default configuration files:

**File:** `service/core/src/main/resources/job-definitions/default-job-definitions.json`

Add a new job definition entry:

```json
{
  "id": "default-{job-name-kebab}",
  "type": "{JOB_TYPE_ENUM}",
  "params": {
    // Add job parameters if needed
  }
}
```

**File:** `service/core/src/main/resources/project-templates/default-project-templates.json`

Add the job ID to the `jobDefinitions` array in the appropriate templates:

- `default-projects-synchronizer-template` - for projects synced from workspace
- `default-project-template` - for manually created projects

```json
{
  "id": "default-projects-synchronizer-template",
  "jobDefinitions": [
    "default-repository-session-cleaner",
    "default-repository-recording-cleaner",
    "default-repository-jfr-compression",
    "default-{job-name-kebab}"
    // Add your job here
  ]
}
```

#### 2.8 For GLOBAL Jobs: Update GlobalJobsInitializer

**File:** `service/core/src/main/java/pbouda/jeffrey/appinitializer/GlobalJobsInitializer.java`

Add in `onApplicationEvent()`:

```java
boolean {
    jobName
}

Create =environment.

getProperty(
        "jeffrey.job.{job-name-kebab}.create-if-not-exists",Boolean .class, false);
if({jobName}Create){
        schedulerManager.

create(new {
    JobName
}

JobDescriptor());
        }
```

#### 2.9 For GLOBAL Jobs: Add Application Properties

**File:** `service/core/src/main/resources/application.properties`

Add properties for auto-creation and period:

```properties
# The following properties are used to configure the {job-name} job
# It is a global job that {description}
jeffrey.job.{job-name-kebab}.create-if-not-exists=true
jeffrey.job.{job-name-kebab}.period=5m
```

### Step 3: Generate Frontend Files

#### 3.1 Add to Frontend JobType

**File:** `pages/src/services/model/JobType.ts`

Add the new job type constant.

#### 3.2 For GLOBAL Jobs: Create Plugin

**File:** `pages/src/services/scheduler/plugins/{JobName}Plugin.ts`

```typescript
/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 * ... license header ...
 */

import {
    BaseJobTypePlugin,
    type JobCardMetadata,
    type JobValidationResult,
    type JobCreationParams
} from './JobTypePlugin';
import {JobName}

Modal
from
'@/components/scheduler/modal/{JobName}Modal.vue';
import type JobInfo from '@/services/model/JobInfo';

export class {
    JobName
}

Plugin
extends
BaseJobTypePlugin
{
    readonly
    jobType = '{JOB_TYPE_ENUM}';

    readonly
    cardMetadata: JobCardMetadata = {
        jobType: '{JOB_TYPE_ENUM}',
        title: '{Job Title}',
        description: '{description}',
        icon: 'bi-gear',  // Choose appropriate Bootstrap icon
        iconColor: 'text-primary',
        iconBg: 'bg-primary-soft'
    };

    readonly
    modalComponent = {JobName}
    Modal;

    async
    validateJobCreation(params
:
    JobCreationParams
):
    Promise < JobValidationResult > {
        const errors
:
    string[] = [];
    // Add validation logic
    return {isValid: errors.length === 0, errors};
}

    buildJobCreationParams(formData
:
    any
):
    JobCreationParams
    {
        return {...formData};
    }
}
```

**File:** `pages/src/services/scheduler/pluginSetup.ts`

Add import and registration:

```typescript
import {

{
    JobName
}
Plugin
}
from
'./plugins/{JobName}Plugin';

// In setupJobPlugins():
const {jobName}
Plugin = new {JobName}
Plugin();
jobPluginRegistry.registerPlugin({jobName}
Plugin
)
;
```

#### 3.3 For PROJECT Jobs: Update SchedulerList.vue

**File:** `pages/src/views/projects/detail/SchedulerList.vue`

1. Add import for modal component
2. Add modal visibility ref
3. Add existence check ref
4. Add JobCard in template
5. Add case to handleCreateJob switch
6. Add case to getJobDisplayInfo switch
7. Update checkForExistingJobs
8. Add modal component in template

#### 3.4 Create Modal Component

**File:** `pages/src/components/scheduler/modal/{JobName}Modal.vue`

Create a modal component for job configuration with appropriate form fields.

### Step 4: Summary

After generating all files, provide a summary:

- List all created/modified files
- Remind to run `mvn clean compile` to verify backend
- Remind to run `npm run build` in pages/ to verify frontend
- Note any TODO items in generated code

## File Locations Reference

| Component               | Path                                                                                           |
|-------------------------|------------------------------------------------------------------------------------------------|
| JobType enum (backend)  | `service/common-model/src/main/java/pbouda/jeffrey/common/model/job/JobType.java`              |
| Job descriptor          | `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/`                          |
| JobDescriptor interface | `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/JobDescriptor.java`        |
| JobDescriptorFactory    | `service/core/src/main/java/pbouda/jeffrey/scheduler/job/descriptor/JobDescriptorFactory.java` |
| Job implementation      | `service/core/src/main/java/pbouda/jeffrey/scheduler/job/`                                     |
| JobsConfiguration       | `service/core/src/main/java/pbouda/jeffrey/configuration/JobsConfiguration.java`               |
| GlobalJobsInitializer   | `service/core/src/main/java/pbouda/jeffrey/appinitializer/GlobalJobsInitializer.java`          |
| Application properties  | `service/core/src/main/resources/application.properties`                                       |
| Default job definitions | `service/core/src/main/resources/job-definitions/default-job-definitions.json`                 |
| Project templates       | `service/core/src/main/resources/project-templates/default-project-templates.json`             |
| JobType enum (frontend) | `pages/src/services/model/JobType.ts`                                                          |
| Job plugins             | `pages/src/services/scheduler/plugins/`                                                        |
| Plugin setup            | `pages/src/services/scheduler/pluginSetup.ts`                                                  |
| GlobalSchedulerView     | `pages/src/views/global/GlobalSchedulerView.vue`                                               |
| SchedulerList           | `pages/src/views/projects/detail/SchedulerList.vue`                                            |
| Modal components        | `pages/src/components/scheduler/modal/`                                                        |

## Example Existing Jobs

| Job                                       | Type                                       | Scope   | Description                                      |
|-------------------------------------------|--------------------------------------------|---------|--------------------------------------------------|
| RepositoryCompressionProjectJob           | REPOSITORY_JFR_COMPRESSION                 | PROJECT | Compresses JFR files using LZ4                   |
| RepositorySessionCleanerProjectJob        | REPOSITORY_SESSION_CLEANER                 | PROJECT | Removes old repository sessions                  |
| ProjectRecordingStorageSynchronizerJob    | PROJECT_RECORDING_STORAGE_SYNCHRONIZER     | PROJECT | Syncs recordings between storage and database    |
| ProjectsSynchronizerJob                   | PROJECTS_SYNCHRONIZER                      | GLOBAL  | Syncs projects from workspace directories        |
| WorkspaceProfilerSettingsSynchronizerJob  | WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER   | GLOBAL  | Syncs profiler settings                          |
| OrphanedProjectRecordingStorageCleanerJob | ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER | GLOBAL  | Removes orphaned projects from recording storage |
