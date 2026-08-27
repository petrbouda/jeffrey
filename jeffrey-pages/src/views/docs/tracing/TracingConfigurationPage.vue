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
  { id: 'provisioner', text: 'Provisioner Integration', level: 2 },
  { id: 'jmc', text: 'JMC and jfr print Interop', level: 2 },
  { id: 'testing', text: 'Testing Your Instrumentation', level: 2 }
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

const jdkThresholds = `-XX:StartFlightRecording=filename=app.jfr,settings=profile,\\
jdk.SocketRead#threshold=1ms,jdk.SocketWrite#threshold=1ms,\\
jdk.FileRead#threshold=1ms,jdk.FileWrite#threshold=1ms,\\
jdk.JavaMonitorEnter#threshold=1ms,jdk.ThreadPark#threshold=1ms`;

const provisionerConfig = `# provisioner.conf (HOCON)
tracing {
  enabled = true
  # Optional: your own JFR settings list for the thresholds side-recording;
  # "none" opts out of the side-recording while leaving method tracing on
  # jfr-event-settings = "jdk.SocketRead#threshold=1ms,..."
}`;

const testExample = `import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;

@Test
void checkoutTracesAssembleCorrectly() throws IOException {
    // JfrRecordings starts an in-process recording, runs the body, and
    // returns the recorded events — no files, no fixtures.
    List<RecordedEvent> events = JfrRecordings.all(
            List.of("jeffrey.TraceSpan", "jeffrey.JdbcQuery"),
            () -> service.checkout("a-1"));

    SpansAssert.assertThat(events)
            .hasNoUntracedSpans()                     // nothing committed with commit()
            .hasNoOrphanedSpans()                     // every parent id resolves
            .hasSpanCount(4)
            .hasSpan("order.checkout").isRoot()
                    .hasKind("SERVER")
                    .hasNoError()
            .and()
            .hasSpan("UserMapper.selectById")
                    .nestedUnder("order.checkout")
                    .hasEventType("jeffrey.JdbcQuery")
            .and()
            .hasSpanNameCardinalityAtMost(10);        // catches ids leaking into names
}

@Test
void failedChargeMarksTheSpan() throws IOException {
    List<RecordedEvent> events = JfrRecordings.all("jeffrey.TraceSpan", () -> {
        assertThrows(CardDeclinedException.class, () -> service.charge("a-1"));
    });

    SpansAssert.assertThat(events)
            .hasSpan("order.charge")
            .hasStatus("ERROR")
            .hasErrorType(CardDeclinedException.class);
}`;

const testDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events-test</artifactId>
    <version><!-- latest release --></version>
    <scope>test</scope>
</dependency>`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration &amp; Testing"
      icon="bi bi-gear"
    />

    <div class="docs-content">
      <p>The events are recorded by whatever JFR recording is running, and they are enabled by default in any recording — so "configuration" means three things: how you record, how you control volume, and how the JDK's own events get thresholds fine enough for traces. Plus: asserting on spans in your own tests, so instrumentation regressions fail CI instead of blank dashboards.</p>

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

      <p>The <router-link to="/docs/tracing/jdk-events">promoted blocking spans</router-link> can only be as fine as the recording: the JDK events default to 10–20&nbsp;ms thresholds. For request-grade traces, lower them:</p>

      <DocsCodeBlock :code="jdkThresholds" language="bash" />

      <h2 id="provisioner">Provisioner Integration</h2>

      <p>Sessions bootstrapped by the <router-link to="/docs/provisioner">Jeffrey Provisioner</router-link> get all of this from one switch:</p>

      <DocsCodeBlock :code="provisionerConfig" language="text" filename="provisioner.conf" />

      <p>With <code>tracing.enabled = true</code> the Provisioner (a) passes <code>tracing.enabled=true</code> to the <router-link to="/docs/tracing/traced-annotation">Jeffrey Agent</router-link> so <code>@Traced</code> methods are woven, and (b) starts a <em>second</em> JFR recording (<code>jeffrey-tracing-thresholds</code>) that exists purely to lower the JDK blocking-event thresholds — JFR takes the most verbose setting across active recordings, so the side-recording tightens thresholds without touching your main recording's configuration. <code>tracing.jfr-event-settings</code> overrides the built-in list; <code>none</code> drops the side-recording while leaving method tracing on. See the <router-link to="/docs/provisioner/configuration">Provisioner configuration reference</router-link> for the full syntax.</p>

      <h2 id="jmc">JMC and jfr print Interop</h2>

      <p>The trace fields are declared <code>@Contextual</code> (Java 25), which does nothing for Jeffrey's own analysis — but it makes <code>jfr print</code> and JDK Mission Control display the trace and span ids <em>beside every event that occurred inside the span</em>: lock events, I/O, exceptions. A recording instrumented for Jeffrey is therefore more readable in plain JDK tooling too:</p>

      <DocsCodeBlock code="jfr print --events &quot;jeffrey.*&quot; app.jfr | less" language="bash" />

      <h2 id="testing">Testing Your Instrumentation</h2>

      <p><code>jeffrey-events-test</code> is the executable form of the <router-link to="/docs/tracing/getting-started">verification checklist</router-link> — assertions over the spans in a recording, dependency-free (plain <code>AssertionError</code>, works under JUnit, TestNG or neither):</p>

      <DocsCodeBlock :code="testDependency" language="xml" />
      <DocsCodeBlock :code="testExample" language="java" />

      <p>The toolkit:</p>

      <table>
        <thead>
          <tr>
            <th>Class</th>
            <th>What it does</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>JfrRecordings</code></td>
            <td><code>all(eventType(s), body)</code> / <code>single(eventType, body)</code> — records an in-process JFR recording around the body and returns the events</td>
          </tr>
          <tr>
            <td><code>RecordedSpan</code></td>
            <td>The span view over a <code>RecordedEvent</code>: <code>isSpan</code> (structural — any event with a <code>spanId</code> field), <code>from(events)</code>, <code>isRoot()</code>, <code>isTraced()</code></td>
          </tr>
          <tr>
            <td><code>SpansAssert</code></td>
            <td><code>assertThat(events)</code> — <code>hasSpan</code>, <code>hasNoSpan</code>, <code>hasSpanCount</code>, <code>hasNoUntracedSpans</code>, <code>hasNoOrphanedSpans</code>, <code>hasSpanNameCardinalityAtMost</code></td>
          </tr>
          <tr>
            <td><code>SpanAssert</code></td>
            <td>Per-span: <code>isRoot</code>, <code>nestedUnder</code>, <code>inSameTraceAs</code>, <code>isTraced</code>, <code>hasKind</code>, <code>hasStatus</code>, <code>hasErrorType</code>, <code>hasNoError</code>, <code>hasEventType</code>, chained with <code>and()</code></td>
          </tr>
        </tbody>
      </table>

      <p>Every failure names what was actually recorded — the useful question when instrumentation is wrong is never "did it fail" but "what did it emit instead". The three assertions worth having in every service's test suite: <code>hasNoUntracedSpans()</code> (catches <code>commit()</code>-instead-of-<code>commitSpan()</code>), <code>hasNoOrphanedSpans()</code> (catches executor boundaries crossed without <code>fork</code>), and <code>hasSpanNameCardinalityAtMost(n)</code> (catches ids leaking into span names).</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
