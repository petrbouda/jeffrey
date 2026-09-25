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
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'recording', text: 'Recording Setup', level: 2 },
  { id: 'volume', text: 'Volume Control', level: 2 },
  { id: 'jdk-thresholds', text: 'JDK Event Thresholds', level: 2 },
  { id: 'jmc', text: 'JMC and jfr print Interop', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const recordingCommands = `# Plain JFR at startup
java -XX:StartFlightRecording=filename=app.jfr,settings=profile -jar app.jar

# On demand
jcmd <pid> JFR.start name=jeffrey settings=profile
jcmd <pid> JFR.dump  name=jeffrey filename=app.jfr

# async-profiler: CPU samples + all JFR (and Jeffrey) events in one file —
# the form that unlocks per-span flamegraphs
asprof -d 60 -e cpu --jfrsync default -f app.jfr <pid>`;

const volumeControl = `# Drop hand-written spans shorter than 1 ms
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=1ms

# Cap the emission rate instead of truncating by duration
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceSpanEvent#throttle=500/s

# Keep re-entry nesting but stop recording where re-entered spans ran
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceScopeEvent#enabled=false`;

const jdkThresholds = `# Recording by hand? The JDK events a trace is drawn from need three
# overrides each: a threshold, the rate limit lifted on the I/O ones, and
# enabled= beside them so a threshold is not ignored for a disabled event.
-XX:StartFlightRecording=filename=app.jfr,settings=profile,\
jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,jdk.SocketRead#throttle=1000000/s,\
jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=0ms,jdk.SocketWrite#throttle=1000000/s,\
jdk.FileRead#enabled=true,jdk.FileRead#threshold=0ms,jdk.FileRead#throttle=1000000/s,\
jdk.FileWrite#enabled=true,jdk.FileWrite#threshold=0ms,jdk.FileWrite#throttle=1000000/s,\
jdk.JavaMonitorEnter#enabled=true,jdk.JavaMonitorEnter#threshold=1ms,\
jdk.ThreadPark#enabled=true,jdk.ThreadPark#threshold=1ms`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration"
      icon="bi bi-gear"
    />

    <div class="docs-content">
      <p>The events are recorded by whatever JFR recording is running, and they are enabled by default in any recording — so "configuration" means three things: how you record, how you control volume, and how the JDK's own events get thresholds fine enough for traces. This page is the hand-rolled path; for Provisioner-managed deployments see <router-link to="/docs/tracing/provisioner-hub">Provisioner &amp; Hub</router-link>.</p>

      <h2 id="recording">Recording Setup</h2>

      <DocsCodeBlock :code="recordingCommands" language="bash" />

      <p>No settings-file changes are needed for the <code>jeffrey.*</code> events, and no registration step: JFR auto-registers each event type the first time an instance of its class is created.</p>

      <h2 id="volume">Volume Control</h2>

      <p>A busy application can emit a lot of spans, and every one lands in the JFR chunk. <code>jeffrey.TraceSpan</code> sets no threshold by default — every span is recorded, however short — because acceptable volume is a property of the application, not of the event. Both levers are per recording:</p>

      <DocsCodeBlock :code="volumeControl" language="bash" />

      <table>
        <thead>
          <tr>
            <th>Setting</th>
            <th>Effect</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>threshold</code></td>
            <td>Drops spans shorter than the given duration — <code>threshold=1ms</code> is a reasonable starting point. It costs more than it appears to: dropping a parent leaves its children as orphans (promoted to roots), and dropping a child moves its samples into the parent's <strong>self</strong> time, since a window that was never recorded cannot be subtracted.</td>
          </tr>
          <tr>
            <td><code>throttle</code></td>
            <td>Caps the emission rate (<code>N/s</code>), sampling rather than truncating. Use it when spans are individually meaningful but too numerous.</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning">
        <strong>Never bake thresholds into instrumentation</strong> — a span dropped in code orphans its children in every recording, forever. Thresholds are a per-recording decision, made where the recording is started.
      </DocsCallout>

      <h2 id="jdk-thresholds">JDK Event Thresholds</h2>

      <p>The <router-link to="/docs/tracing/jdk-events">promoted blocking spans</router-link> can only be as fine as the recording, and the stock configuration is far coarser than a trace needs — I/O wants <code>0ms</code> with its rate limit lifted, blocking events <code>1ms</code>:</p>

      <DocsCodeBlock :code="jdkThresholds" language="bash" />

      <DocsCallout type="tip">
        <strong>Provisioner-managed sessions get all of this from one switch.</strong> If your applications are started by the <router-link to="/docs/provisioner">Jeffrey Provisioner</router-link>, it generates these settings for you — see <router-link to="/docs/tracing/provisioner-hub">Provisioner &amp; Hub</router-link>, which also covers how the recording reaches Microscope.
      </DocsCallout>

      <h2 id="jmc">JMC and jfr print Interop</h2>

      <p>The trace events are ordinary JFR events, so <code>jfr print</code> and JDK Mission Control read them like any other: each <code>jeffrey.*</code> event shows its trace, span and parent span id as plain fields. Relating a lock, I/O or exception event to the span it happened in — by thread and time window — is Jeffrey's job; plain JDK tooling shows the spans themselves:</p>

      <DocsCodeBlock code="jfr print --events &quot;jeffrey.*&quot; app.jfr | less" language="bash" />
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
