<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsArchDiagram from '@/components/docs/DocsArchDiagram.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'path', text: 'How a Traced Recording Travels', level: 2 },
  { id: 'switch', text: 'Turning Tracing On', level: 2 },
  { id: 'thresholds', text: 'What the Provisioner Emits', level: 2 },
  { id: 'second-recording', text: 'Why a Second Recording', level: 2 },
  { id: 'layout', text: 'What Lands on the Volume', level: 2 },
  { id: 'hub', text: 'What the Hub Does With It', level: 2 },
  { id: 'download', text: 'Getting the Recording into Microscope', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const provisionerConfig = `# provisioner.conf (HOCON)
tracing {
  enabled = true
  # Optional: your own JFR settings list for the thresholds side-recording;
  # "none" opts out of the side-recording while leaving method tracing on
  # jfr-event-settings = "jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,..."
}`;

const generatedOptions = `# Written into the @argfile the entrypoint hands to the JVM
-javaagent:/opt/jeffrey/jeffrey-agent.jar=heartbeat.dir=<session>/.heartbeat,tracing.enabled=true,app...
-XX:FlightRecorderOptions=repository=<session>/streaming-repo
-XX:StartFlightRecording:name=jeffrey-tracing-thresholds,maxage=30m,<event settings below>`;

const thresholds = `-XX:StartFlightRecording:name=jeffrey-tracing-thresholds,maxage=30m,\\
jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,jdk.SocketRead#throttle=1000000/s,\\
jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=0ms,jdk.SocketWrite#throttle=1000000/s,\\
jdk.FileRead#enabled=true,jdk.FileRead#threshold=0ms,jdk.FileRead#throttle=1000000/s,\\
jdk.FileWrite#enabled=true,jdk.FileWrite#threshold=0ms,jdk.FileWrite#throttle=1000000/s,\\
jdk.FileForce#enabled=true,jdk.FileForce#threshold=0ms,\\
jdk.JavaMonitorEnter#enabled=true,jdk.JavaMonitorEnter#threshold=1ms,\\
jdk.JavaMonitorWait#enabled=true,jdk.JavaMonitorWait#threshold=1ms,\\
jdk.ThreadPark#enabled=true,jdk.ThreadPark#threshold=1ms,\\
jdk.ThreadSleep#enabled=true,jdk.ThreadSleep#threshold=1ms,\\
jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=1ms,\\
jdk.ZAllocationStall#enabled=true,jdk.ZAllocationStall#threshold=0ms`;

const sessionLayout = `<workspaces>/<workspace-ref-id>/
├── .pending/<timestamp>_<uuid>        # provisioner-declared work, for the hub to notice
├── .settings/settings-<timestamp>.json # hub-pushed profiler settings, read on the next run
└── <project-name>/<instance-id>/<session-id>/
    ├── profile-<timestamp>.jfr        # the durable chunks — traces live in these
    ├── streaming-repo/                # live JFR repository the hub tails
    ├── .heartbeat/heartbeat           # epoch millis, rewritten every 5s
    ├── .heartbeat/finished            # clean-exit marker, written on shutdown
    └── .session-info.json`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Provisioner &amp; Hub"
      icon="bi bi-hdd-network"
    />

    <div class="docs-content">
      <p>Everything else in this guide assumes you can already open a recording. This page is about how a traced recording gets from a JVM in a cluster to a waterfall on your machine — what the <router-link to="/docs/provisioner">Provisioner</router-link> switches on, what lands on the shared volume, what the <router-link to="/docs/hub">Hub</router-link> does with it, and how Microscope pulls it down. If you record locally and open the file yourself, none of this applies: see <router-link to="/docs/tracing/configuration">Configuration &amp; Testing</router-link> instead.</p>

      <h2 id="path">How a Traced Recording Travels</h2>

      <DocsArchDiagram variant="tracing" />

      <p>Two things about this picture are easy to get wrong, and both matter when a workspace comes up empty:</p>

      <ul>
        <li><strong>The Provisioner never talks to the Hub.</strong> It is a short-lived CLI that runs before <code>main()</code>, writes the session tree and an <code>@argfile</code>, and exits. Its only channel to the Hub is the shared volume — there is no event transport in between.</li>
        <li><strong>The Hub polls; nothing pushes to it.</strong> A reconciler drains <code>.pending/</code> every 5&nbsp;seconds, and a detector reads <code>.heartbeat/</code> every 30&nbsp;seconds. A session appearing a few seconds after the app starts is the system working as designed.</li>
      </ul>

      <h2 id="switch">Turning Tracing On</h2>

      <p>One switch in the Provisioner config covers the whole traced setup:</p>

      <DocsCodeBlock :code="provisionerConfig" language="text" filename="provisioner.conf" />

      <p>With <code>tracing.enabled = true</code> the Provisioner adds these to the JVM options it generates:</p>

      <DocsCodeBlock :code="generatedOptions" language="bash" />

      <p>That is: <code>tracing.enabled=true</code> on the <router-link to="/docs/tracing/traced-annotation">Jeffrey Agent</router-link> so <code>@Traced</code> methods are woven, and a second JFR recording carrying the event thresholds a trace is read at. The <code>jeffrey.*</code> events your instrumentation emits need no configuration at all — they are on by default in any recording.</p>

      <h2 id="thresholds">What the Provisioner Emits</h2>

      <p>The <router-link to="/docs/tracing/jdk-events">promoted blocking spans</router-link> can only be as fine as the recording, and the stock configuration is far coarser than a trace needs. This is the exact option the Provisioner generates:</p>

      <DocsCodeBlock :code="thresholds" language="bash" />

      <p>Three separate things are overridden per event, and all three are load-bearing:</p>

      <ul>
        <li><strong><code>threshold</code></strong> — I/O events are recorded from <code>0ms</code>, so no socket or file wait is too short to become a bar; the blocking events (locks, <code>Object.wait</code>, park, sleep, pinning) are recorded from <code>1ms</code>, which keeps volume sane without losing anything a reader looks for.</li>
        <li><strong><code>throttle</code></strong> — Java&nbsp;25's <code>default.jfc</code> rate-limits socket and file I/O to <strong>100 events a second</strong>, and a threshold does not lift that. Without the lift, a busy JVM silently drops the very events a trace hangs its leaf spans on. It must be a numeric rate rather than <code>off</code>: JFR resolves <code>throttle</code> to the highest <em>parseable</em> rate across active recordings, and <code>off</code> parses as no rate at all.</li>
        <li><strong><code>enabled</code></strong> — named beside every threshold, because a threshold alone is ignored for an event a custom profiler configuration had switched off.</li>
      </ul>

      <DocsCallout type="info">
        <code>jdk.FileForce</code> and <code>jdk.ZAllocationStall</code> deliberately carry no <code>throttle</code> — naming one they do not have costs a JFR warning at startup.
      </DocsCallout>

      <h2 id="second-recording">Why a Second Recording</h2>

      <p>The obvious approach would be to put these settings in the profiler's own recording. The Provisioner does not, because the profiler's configuration arrives from whichever source won — the CLI, the Hub, or the built-in default — so rewriting it would have to be done three times over.</p>

      <p>A second recording sidesteps that entirely: <strong>JFR resolves every setting to the most verbose value across all active recordings</strong>, so this one lowers the thresholds for the profiler's recording as well, without either knowing about the other. The events land in the same repository chunks, which is what puts them in both places they are read from — the dumped <code>.jfr</code> files and the stream the Hub follows.</p>

      <p>Two details follow from that design. It names no <code>settings=</code> on purpose — that would leave it on the JVM's default configuration, the same one the profiler uses, changing nothing; naming <code>settings=none</code> to keep it bare is what a reader expects and is exactly wrong, because it drops the event settings spelled out beside it. And <code>maxage=30m</code> bounds how long the recording pins repository chunks.</p>

      <p><code>tracing.jfr-event-settings</code> replaces the built-in list; setting it to <code>none</code> drops this recording while leaving method tracing on.</p>

      <h2 id="layout">What Lands on the Volume</h2>

      <DocsCodeBlock :code="sessionLayout" language="text" />

      <p>The Provisioner creates the tree and writes the marker files, then appends a pointer file to <code>.pending/</code> — an index, not a queue, carrying a path rather than a copy of the state. The application fills the session directory as it runs: async-profiler dumps a <code>profile-&lt;timestamp&gt;.jfr</code> chunk on its loop interval, JFR keeps live chunks in <code>streaming-repo/</code>, and the Jeffrey Agent rewrites the heartbeat every 5&nbsp;seconds.</p>

      <h2 id="hub">What the Hub Does With It</h2>

      <table>
        <thead>
          <tr>
            <th>Job</th>
            <th>Cadence</th>
            <th>What it does</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Workspace reconciler</td>
            <td>5&nbsp;s</td>
            <td>Drains <code>.pending/</code>, reads the marker JSONs, and materializes projects, instances and sessions into the Hub's catalog. Strictly additive; an entry is deleted only after it succeeds.</td>
          </tr>
          <tr>
            <td>Session-finished detector</td>
            <td>30&nbsp;s</td>
            <td>Reads <code>.heartbeat/</code>. A <code>finished</code> marker ends the session deterministically; a stale heartbeat ends it at that timestamp. A present <code>hs_err</code> file raises a JVM-crash notification and auto-retains the session.</td>
          </tr>
          <tr>
            <td>Compression &amp; retention</td>
            <td>15&nbsp;m and up</td>
            <td>LZ4-compresses finished recordings, and enforces session, recording, quota and instance retention.</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning">
        <strong>The Hub never analyses a recording.</strong> It catalogs, compresses, retains and serves. Every flamegraph, trace, Guardian check and heap-dump view is computed in Microscope — so the traces in this guide are derived on your machine, from bytes the Hub only ever stored.
      </DocsCallout>

      <h2 id="download">Getting the Recording into Microscope</h2>

      <p>Microscope reaches the Hub over gRPC, and that is the only network hop in the whole picture:</p>

      <ul>
        <li><strong>Catalog</strong> — <code>RepositoryService</code> lists sessions and their files, so you can see what a project recorded without downloading anything.</li>
        <li><strong>Bytes</strong> — <code>RecordingDownloadService</code> streams a merged recording, a single file, or an artifact back in 64&nbsp;KB chunks.</li>
        <li><strong>Live and replay</strong> — <code>EventStreamingService</code> tails <code>streaming-repo/</code> for a running session, or replays a finished one.</li>
      </ul>

      <p>Downloaded bytes land under <code>~/.jeffrey-microscope/recordings/</code>, and initializing a profile parses them once into a per-profile DuckDB at <code>~/.jeffrey-microscope/profiles/&lt;profile-id&gt;/profile-data.db</code>. The trace tables described in <router-link to="/docs/tracing/analysis">Analyzing Traces</router-link> are derived at that moment — which is why <router-link to="/docs/tracing/jdk-events">promoted blocking spans</router-link> and <router-link to="/docs/tracing/gc-safepoints">GC context</router-link> apply retroactively to recordings made before those features existed.</p>

      <h2 id="pitfalls">Pitfalls</h2>

      <table>
        <thead>
          <tr>
            <th>Symptom</th>
            <th>Cause</th>
            <th>Fix</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Traces present, but every span is a bare bar with no I/O or lock children</td>
            <td>The thresholds side-recording never started</td>
            <td>Check <code>tracing.enabled = true</code> and that <code>tracing.jfr-event-settings</code> is not <code>none</code></td>
          </tr>
          <tr>
            <td>Socket spans appear under light load and vanish under heavy load</td>
            <td>The <code>default.jfc</code> 100/s rate limit, not lifted</td>
            <td>Keep the <code>#throttle</code> settings; a custom event list that drops them reintroduces the cap</td>
          </tr>
          <tr>
            <td><code>@Traced</code> methods produce no spans, everything else is fine</td>
            <td>Agent weaving is off or unavailable</td>
            <td>Java 25+, <code>tracing.enabled=true</code>, and <code>jeffrey-events</code> on the class's own loader — see <router-link to="/docs/tracing/traced-annotation">@Traced &amp; the Agent</router-link></td>
          </tr>
          <tr>
            <td>The session never appears in the Hub</td>
            <td>The Hub is not mounting the same volume path, or <code>.pending/</code> is unreadable</td>
            <td>The reconciler runs every 5&nbsp;s and is additive — if nothing arrives, the volume is the thing to check, not the app</td>
          </tr>
          <tr>
            <td>The session never ends</td>
            <td>No heartbeat file — the agent was not attached, or <code>heartbeat.dir</code> points elsewhere</td>
            <td>The Provisioner sets <code>heartbeat.dir</code>; a hand-rolled agent argument has to match the session path</td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
