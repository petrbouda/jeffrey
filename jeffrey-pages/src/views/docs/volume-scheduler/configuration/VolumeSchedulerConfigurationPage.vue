<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'server', text: 'Server', level: 2 },
  { id: 'volume', text: 'Volume', level: 2 },
  { id: 'retention', text: 'Retention', level: 2 },
  { id: 'compression', text: 'Compression', level: 2 },
  { id: 'environment-variables', text: 'Environment Variables', level: 2 },
  { id: 'run-on-demand', text: 'Running a Job on Demand', level: 2 },
  { id: 'logging', text: 'Logging', level: 2 },
  { id: 'storage', text: 'Project/Recording Storage', level: 2 },
  { id: 'workspace-discovery', text: 'Workspace Discovery', level: 2 },
  { id: 'session-order', text: 'Session Order', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const propertiesExample = `# The directory that holds workspaces/ (required)
jeffrey.volume-scheduler.volume.path=/mnt/jeffrey

# Retention, every 5 minutes
jeffrey.volume-scheduler.retention.enabled=true
jeffrey.volume-scheduler.retention.period=5m
jeffrey.volume-scheduler.retention.session-retention=7d
jeffrey.volume-scheduler.retention.max-sessions=10
jeffrey.volume-scheduler.retention.newest-session-file-retention=7d
jeffrey.volume-scheduler.retention.max-size=20GB
jeffrey.volume-scheduler.retention.instance-retention=14d

# LZ4 compression of finished JFR chunks, every minute
jeffrey.volume-scheduler.compression.enabled=true
jeffrey.volume-scheduler.compression.period=1m`;

const envExample = `JEFFREY_VOLUMESCHEDULER_VOLUME_PATH=/mnt/jeffrey
JEFFREY_VOLUMESCHEDULER_RETENTION_MAXSIZE=50GB
JEFFREY_VOLUMESCHEDULER_RETENTION_SESSIONRETENTION=3d
JEFFREY_VOLUMESCHEDULER_COMPRESSION_PERIOD=30s`;

const traceToFileExample = `# The volume scheduler's container in its Deployment:
# TRACE into a rolling file, INFO on the console
containers:
  - name: volume-scheduler
    image: petrbouda/volume-scheduler:<version>
    args:
      - -Xmx64m
      - --logging.level.cafe.jeffrey=TRACE
      - --logging.threshold.console=INFO
      - --logging.file.name=/var/log/volume-scheduler.log`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration"
      icon="bi bi-gear"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The Volume Scheduler is configured with Spring Boot properties in three groups under
        <code>jeffrey.volume-scheduler.*</code>: the <a href="#volume">volume</a> it maintains, the
        <a href="#retention">retention</a> job and the <a href="#compression">compression</a> job.
        Every property has a default except <code>jeffrey.volume-scheduler.volume.path</code>, which is
        required. The volume scheduler has no database: the shared volume is its only state.
      </p>
      <p>
        Its Docker image (<code>petrbouda/volume-scheduler:&lt;version&gt;</code>) is a GraalVM native
        image, the binary <code>/app/volume-scheduler</code>, and takes the properties as environment
        variables (<code>JEFFREY_VOLUMESCHEDULER_VOLUME_PATH</code>), as <code>--key=value</code>
        arguments, or as a mounted file named by <code>SPRING_CONFIG_ADDITIONAL_LOCATION</code>. See
        <router-link to="/docs/deployment/helm-chart#native-image">Native Image</router-link>. To build
        it yourself, run <code>docker build -f docker/Dockerfile-volume-scheduler .</code> from the
        repository root, or build the binary alone with
        <code>mvn -pl build/build-volume-scheduler-native -am -Pnative package</code>.
      </p>

      <DocsCodeBlock
        language="properties"
        :code="propertiesExample"
      />

      <DocsCallout type="info">
        Configuration is read once at startup and is not stored anywhere — changing a period or a
        retention window requires a restart. A value that does not bind (a non-positive size, count or
        duration, or a missing volume path) fails the startup with a message naming the property.
      </DocsCallout>

      <h2 id="server">Server</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>server.port</code></td>
            <td><code>8080</code></td>
            <td>HTTP port for the <router-link to="/docs/volume-scheduler/api">REST API</router-link> and the actuator health probes (<code>/actuator/health/liveness</code>, <code>/actuator/health/readiness</code>)</td>
          </tr>
        </tbody>
      </table>

      <h2 id="volume">Volume</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.volume-scheduler.volume.path</code></td>
            <td><em>required</em></td>
            <td>
              The directory that holds <code>workspaces/</code> — where the shared recording volume is
              mounted, e.g. <code>/mnt/jeffrey</code>. Startup fails if it is missing or not a
              directory, so an unmounted volume is caught instead of silently maintaining an empty
              directory; a missing <code>workspaces/</code> underneath only means there are no projects
              yet. Env var: <code>JEFFREY_VOLUMESCHEDULER_VOLUME_PATH</code>
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="retention">Retention</h2>
      <p>
        The <code>retention</code> job runs one pass per project over a single listing of its sessions.
        Its rules apply in this order:
      </p>
      <ol>
        <li>deletes sessions older than <code>session-retention</code> — a session is aged by the start of its newest recording;</li>
        <li>keeps at most <code>max-sessions</code> sessions per instance, deleting the oldest beyond the cap even before their window expires — a consecutive run of failed (empty) sessions counts as one, and the instance's newest session is never taken;</li>
        <li>trims recordings older than <code>newest-session-file-retention</code> out of each instance's newest session;</li>
        <li>holds the project under <code>max-size</code>, freeing the oldest sessions first;</li>
        <li>deletes instances created longer ago than <code>instance-retention</code> that hold no session, with their directories.</li>
      </ol>
      <p>
        The newest recording of an instance's newest session — the one the JVM may still be writing — is
        never deleted. What is left is the project's figure in
        <router-link to="/docs/volume-scheduler/api#storage"><code>GET /api/v1/storage</code></router-link>.
        The first pass runs at startup.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.enabled</code></td>
            <td><code>true</code></td>
            <td>Whether the job runs on its schedule. Off, nothing on the volume is retained or cleaned up, though the job can still be <a href="#run-on-demand">run by hand</a>, and <code>GET /api/v1/storage</code> stays unmeasured until it is</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.period</code></td>
            <td><code>5m</code></td>
            <td>The fixed delay between the end of one pass and the start of the next</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.session-retention</code></td>
            <td><code>7d</code></td>
            <td>How old a session may get, measured from its newest recording's start</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.max-sessions</code></td>
            <td><code>10</code></td>
            <td>Sessions kept per instance; a run of failed (empty) sessions counts as one</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.newest-session-file-retention</code></td>
            <td><code>7d</code></td>
            <td>How old a recording in an instance's newest session may get before it is trimmed; the newest recording is never trimmed</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.max-size</code></td>
            <td><code>20GB</code></td>
            <td>The disk budget of each project; the oldest sessions go first once it is exceeded. Binary multiples: <code>20GB</code> is 20 GiB</td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.retention.instance-retention</code></td>
            <td><code>14d</code></td>
            <td>How long an instance without sessions is kept after it was created</td>
          </tr>
        </tbody>
      </table>
      <p>
        Durations accept Spring's notation — <code>500ms</code>, <code>30s</code>, <code>5m</code>,
        <code>1h</code>, <code>7d</code> — or ISO-8601 such as <code>PT5M</code>. Sizes accept
        <code>B</code>, <code>KB</code>, <code>MB</code>, <code>GB</code> and <code>TB</code>, each a
        multiple of 1024.
      </p>

      <h2 id="compression">Compression</h2>
      <p>
        The <code>compression</code> job LZ4-compresses the finished <code>.jfr</code> chunks of each
        instance's two newest sessions, never the newest recording of the newest session. Each chunk is
        written to a scratch file next to it and renamed to its <code>.lz4</code> name atomically, then the
        original is deleted. A <code>.jfr</code> newer than its same-named <code>.lz4</code> archive is a
        name collision, not a leftover of a finished compression, so the job keeps it and logs a warning.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.volume-scheduler.compression.enabled</code></td>
            <td><code>true</code></td>
            <td>Whether the job runs on its schedule; it can still be <a href="#run-on-demand">run by hand</a></td>
          </tr>
          <tr>
            <td><code>jeffrey.volume-scheduler.compression.period</code></td>
            <td><code>1m</code></td>
            <td>The fixed delay between the end of one run and the start of the next</td>
          </tr>
        </tbody>
      </table>

      <h2 id="environment-variables">Environment Variables</h2>
      <p>
        Every property works as an environment variable in the form Spring's relaxed binding reads:
        upper case, dots as underscores, dashes dropped.
      </p>
      <DocsCodeBlock
        language="bash"
        :code="envExample"
      />

      <h2 id="run-on-demand">Running a Job on Demand</h2>
      <p>
        Either job can be run by hand with
        <router-link to="/docs/volume-scheduler/api#run-job"><code>POST /api/v1/jobs/{job}/run</code></router-link>,
        whether it is enabled or not. The run is synchronous: it waits for a scheduled run of the same job
        that is still in flight, then runs and answers with what it did. A manual retention run also
        refreshes the storage figures instead of waiting out its period.
        <router-link to="/docs/volume-scheduler/api#jobs"><code>GET /api/v1/jobs</code></router-link> shows
        each job's id, whether it is enabled, its period and, for retention, its settings.
      </p>

      <h2 id="logging">Logging</h2>
      <p>
        The <code>logging.*</code> properties below are <strong>Spring Boot's own, not Jeffrey's</strong> —
        Jeffrey ships no <code>logback-spring.xml</code>, so Boot configures Logback itself and each one
        behaves exactly as the Spring Boot reference documents it. They are listed here because they are the
        supported way to configure the volume scheduler's logging, and their defaults are Boot's. The console
        is the only appender until <code>logging.file.name</code> is set.
      </p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>logging.level.cafe.jeffrey</code></td>
            <td><code>INFO</code></td>
            <td>Log level for Jeffrey classes</td>
          </tr>
          <tr>
            <td><code>logging.file.name</code></td>
            <td><em>none</em></td>
            <td>Write a rolling log file as well as the console. Because Boot owns the Logback setup, this one property is enough to add the file appender</td>
          </tr>
          <tr>
            <td><code>logging.threshold.console</code></td>
            <td><code>TRACE</code></td>
            <td>Floor for the console appender. Set it to <code>INFO</code> to keep the console readable while the file takes everything</td>
          </tr>
          <tr>
            <td><code>logging.threshold.file</code></td>
            <td><code>TRACE</code></td>
            <td>The same floor, for the file appender</td>
          </tr>
          <tr>
            <td><code>logging.logback.rollingpolicy.max-file-size</code></td>
            <td><code>10MB</code></td>
            <td>Roll the log file over once it reaches this size</td>
          </tr>
          <tr>
            <td><code>logging.logback.rollingpolicy.total-size-cap</code></td>
            <td><code>0B</code> (unlimited)</td>
            <td>Total size the archived files may take before the oldest are deleted</td>
          </tr>
          <tr>
            <td><code>logging.logback.rollingpolicy.max-history</code></td>
            <td><code>7</code></td>
            <td>How many archived log files to keep</td>
          </tr>
        </tbody>
      </table>

      <h3>Tracing into a file without flooding the console</h3>
      <p>
        The three levers combine into one recipe: raise the <em>level</em> to <code>TRACE</code>, give
        the file a name, and hold the <em>console</em> threshold at <code>INFO</code>. The native binary
        takes them as <code>--key=value</code> arguments, after any runtime option of the native image
        itself such as the <code>-Xmx64m</code> heap cap:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="traceToFileExample"
      />

      <DocsCallout type="info">
        Keep the log file off the shared volume: a path such as <code>/var/log</code> inside the container
        (or an <code>emptyDir</code> mounted there) leaves the volume holding only what the profiled
        applications wrote.
      </DocsCallout>

      <h2 id="storage">Project/Recording Storage</h2>
      <p>
        There is no property for this. Recordings are not copied into a directory of the volume
        scheduler's own: they stay where the Provisioner and the application wrote them, under the
        workspace tree on the shared volume —
        <code>&lt;volume.path&gt;/workspaces/&lt;workspace-ref-id&gt;/&lt;project&gt;/&lt;instance&gt;/&lt;session&gt;/</code>.
        The jobs retain and compress those files in place, listing the directories on every run rather
        than keeping a copy of them; see
        <router-link to="/docs/tracing/provisioner-volume">Provisioner &amp; Volume</router-link> for the layout.
      </p>

      <h2 id="workspace-discovery">Workspace Discovery</h2>
      <p>
        There is no discovery job and no index: every run lists the workspace directories afresh, so a
        newly provisioned project, instance or session is picked up on the job's next run, and the folder
        names are the state. A workspace's id is its directory name (the reference id the Provisioner's
        <code>project.workspace-ref-id</code> names); a project's id is its directory name (the
        Provisioner's <code>project.name</code>); an instance's id is its directory name
        (<code>instance-name</code>, <code>HOSTNAME</code> by default); a session's id is its directory
        name, a UUIDv7 whose timestamp is the session's start. There are no marker files anywhere on the
        volume: a project or an instance is any visible (non-dot) child directory, a session a visible
        directory named with a UUIDv7, and anything else under an instance is ignored.
      </p>
      <p>
        Every visible directory at the volume's root is a workspace, named by its folder — its reference
        id; there is no separate display name. The one exception is the filesystem's own
        <code>lost+found</code>; Microscope and the volume scheduler read the same set. The
        volume scheduler creates none: a workspace appears when the Provisioner writes it, the first time
        it provisions a project into that workspace (its <code>project.workspace-ref-id</code>,
        <code>$default</code> when unset). There is no property for this.
      </p>
      <p>
        The volume scheduler does no profile analysis and serves no recordings: every flamegraph and trace
        is computed in Jeffrey Microscope, which reads the volume through its connector.
      </p>

      <h2 id="session-order">Session Order</h2>
      <p>
        Nothing to configure: the volume scheduler keeps no session state. An instance's sessions are
        ordered by their UUIDv7 directory names, the newest one is the only one that may still grow, and
        its newest recording is never compressed or deleted; see
        <router-link to="/docs/connectors/recording-sessions/lifecycle">Session Lifecycle</router-link>.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
