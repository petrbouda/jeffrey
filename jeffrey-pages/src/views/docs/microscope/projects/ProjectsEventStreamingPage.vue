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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'live-stream', text: 'Live Stream', level: 2 },
  { id: 'replay-stream', text: 'Replay Stream', level: 2 },
  { id: 'available-events', text: 'Available Events', level: 2 },
  { id: 'live-vs-replay', text: 'Live vs Replay', level: 2 },
  { id: 'workspace-availability', text: 'Workspace Availability', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Event Streaming"
      icon="bi bi-broadcast"
    />

    <div class="docs-content">
      <p>Event Streaming lets you inspect JFR events from a project's recording sessions. <strong>Live Stream</strong> subscribes to events as the JVM emits them; <strong>Replay Stream</strong> reads historical events from the dumped recording files of a past session. Both pages deliver events to the browser via Server-Sent Events (SSE).</p>

      <DocsCallout type="info">
        <strong>Server Connection Required:</strong> Both Live Stream and Replay Stream operate on project instances and their sessions, so a workspace connected to a jeffrey-server instance is required.
      </DocsCallout>

      <h2 id="how-it-works">How It Works</h2>
      <p>Sessions belong to project instances. Live Stream opens a subscription against the session's <em>streaming repository</em> on the remote JVM — events are micro-batched on JFR's flush cycle (~1 second). Replay Stream reads the session's already-dumped <code>.jfr</code> / <code>.jfr.lz4</code> files. In both cases, batches travel from Jeffrey Server to Jeffrey Microscope over gRPC and are forwarded to the browser over SSE.</p>

      <div class="flow-diagram">
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-cpu"></i></div>
          <div class="flow-label">JVM writes events to streaming repository (live) or dump files (replay)</div>
        </div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-cloud"></i></div>
          <div class="flow-label">Jeffrey Server reads and batches events via gRPC</div>
        </div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-pc-display"></i></div>
          <div class="flow-label">Jeffrey Microscope bridges to SSE for browser delivery</div>
        </div>
      </div>

      <h2 id="live-stream">Live Stream</h2>
      <p>Subscribe to JFR events from one or more running sessions in real time. The stream stays open until you disconnect — it never completes on its own.</p>

      <p>Three inline cards — <strong>Sessions</strong>, <strong>Event Types</strong>, <strong>Buffer</strong> — each click-to-edit in place. Deep-linking from the Instances timeline prefills the Sessions card and auto-opens the Events card.</p>

      <div class="config-steps">
        <div class="config-step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4>Sessions</h4>
            <p>Select one or more active sessions, grouped by instance and searchable by ID. While disconnected, a session chip's <strong>×</strong> removes it without reopening the picker.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4>Event Types</h4>
            <p>Pick from curated JFR categories or enter custom event names.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4>Buffer</h4>
            <p>Rolling buffer size: 500, 1000, 5000, 10,000, or custom.</p>
          </div>
        </div>
      </div>

      <p>While connected, a status strip shows the total events received, the number of batches delivered, and the timestamp of the last batch. Session chips are color-coded, and each table row's left border matches its source session. If the subscription for one session fails, that session is marked with a warning icon while the other sessions keep streaming — <strong>failures are isolated per session</strong>.</p>

      <DocsCallout type="warning">
        <strong>CPU profiling events not available in Live Stream:</strong> Events like <code>jdk.ExecutionSample</code> collected by async-profiler are merged into the JFR recording at dump time and do not appear in the live streaming repository. Use Replay Stream to inspect them after the session is dumped.
      </DocsCallout>

      <h2 id="replay-stream">Replay Stream</h2>
      <p>Read historical events from the dumped recording files of a single past session. Unlike Live Stream, a replay completes when the selected time window has been fully read.</p>

      <p>Three inline cards — <strong>Session</strong>, <strong>Event Types</strong>, <strong>Time Range</strong> — each click-to-edit in place. Deep-linking from the Instances timeline prefills the Session card with the default range (<em>Beginning → Latest</em>) and auto-opens the Events card.</p>

      <div class="config-steps">
        <div class="config-step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4>Session</h4>
            <p>Select a single session. Replay reads the <code>.jfr</code> / <code>.jfr.lz4</code> files dumped for it.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4>Event Types</h4>
            <p>Same picker as Live Stream.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4>Time Range</h4>
            <p><strong>From</strong>: <em>Beginning</em> or <em>Custom</em> datetime. <strong>To</strong>: <em>Latest</em> or <em>Custom</em> datetime — if both custom, <em>From</em> must precede <em>To</em>. A third row controls the event <strong>Buffer</strong> (same presets as Live Stream).</p>
          </div>
        </div>
      </div>

      <p>When replay finishes, the page switches to a <em>Replay Complete</em> state and the Start Replay button becomes available again so you can run another replay with different parameters. Errors surface as a toast notification; the configuration is preserved so you can adjust and retry.</p>

      <DocsCallout type="warning">
        <strong>Dumped files required:</strong> Replay fails if the selected session has no dumped recording files yet. If you only have an active session with no dumps, use Live Stream instead.
      </DocsCallout>

      <h2 id="available-events">Available Events</h2>
      <p>The following categories are surfaced by the built-in event-type picker. Live Stream is limited to events that the JVM commits to its streaming repository in real time (JVM statistics, runtime events, container metrics, Jeffrey instrumentation). Replay Stream can surface any event type present in the dumped recording files.</p>

      <div class="event-categories">
        <div class="event-category">
          <h4><i class="bi bi-cpu"></i> JVM Statistics</h4>
          <p><code>jdk.CPULoad</code>, <code>jdk.ThreadCPULoad</code>, <code>jdk.ClassLoadingStatistics</code>, <code>jdk.JavaThreadStatistics</code>, <code>jdk.CompilerStatistics</code>, <code>jdk.ResidentSetSize</code></p>
        </div>
        <div class="event-category">
          <h4><i class="bi bi-exclamation-triangle"></i> Runtime Events</h4>
          <p><code>jdk.JavaExceptionThrow</code>, <code>jdk.Deoptimization</code>, <code>jdk.ThreadSleep</code>, <code>jdk.FileRead</code>, <code>jdk.FileWrite</code>, <code>jdk.SocketRead</code>, <code>jdk.SocketWrite</code></p>
        </div>
        <div class="event-category">
          <h4><i class="bi bi-box"></i> Container Metrics</h4>
          <p><code>jdk.ContainerCPUUsage</code>, <code>jdk.ContainerMemoryUsage</code>, <code>jdk.ContainerCPUThrottling</code>, <code>jdk.ContainerIOUsage</code></p>
        </div>
        <div class="event-category">
          <h4><i class="bi bi-lightning"></i> Jeffrey Events</h4>
          <p><code>jeffrey.JdbcQuery</code>, <code>jeffrey.GrpcServerExchange</code>, <code>jeffrey.HttpServerExchange</code>, <code>jeffrey.JdbcPoolStatistics</code> and more</p>
        </div>
      </div>

      <h2 id="live-vs-replay">Live vs Replay</h2>
      <table class="comparison-table">
        <thead>
          <tr>
            <th>Aspect</th>
            <th>Live Stream</th>
            <th>Replay Stream</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Session selection</td>
            <td>One or more (multi-select)</td>
            <td>Exactly one</td>
          </tr>
          <tr>
            <td>Time range</td>
            <td>Not applicable</td>
            <td>Configurable start and end</td>
          </tr>
          <tr>
            <td>Data source</td>
            <td>Live JFR streaming repository</td>
            <td>Dumped <code>.jfr</code> / <code>.jfr.lz4</code> files</td>
          </tr>
          <tr>
            <td>Batch cadence</td>
            <td>~1 s (JFR flush cycle)</td>
            <td>Variable (file read speed)</td>
          </tr>
          <tr>
            <td>Completion</td>
            <td>Never (open until Disconnect)</td>
            <td>Finishes when window is fully read</td>
          </tr>
          <tr>
            <td>Failure isolation</td>
            <td>Per session — others keep streaming</td>
            <td>Whole replay stops on error</td>
          </tr>
          <tr>
            <td>Async-profiler CPU samples</td>
            <td>Not available</td>
            <td>Available (merged at dump time)</td>
          </tr>
        </tbody>
      </table>

      <h2 id="workspace-availability">Workspace Availability</h2>
      <p>Both Live Stream and Replay Stream require a workspace connected to a jeffrey-server instance, because they operate on project instances and their sessions. Workspaces that do not expose instances will not show the Event Streaming sidebar items.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Flow Diagram */
.flow-diagram {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  text-align: center;
  max-width: 180px;
}

.flow-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.flow-icon i {
  font-size: 1.2rem;
  color: #5e64ff;
}

.flow-label {
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.3;
}

.flow-arrow {
  color: #94a3b8;
  font-size: 1.25rem;
}

/* Config Steps */
.config-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.config-step {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 50%;
  background: #5e64ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
}

.step-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.step-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

/* Event Categories */
.event-categories {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.event-category {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.event-category h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.event-category h4 i {
  color: #5e64ff;
}

.event-category p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.5;
}

.event-category code {
  font-size: 0.75rem;
}

@media (max-width: 768px) {
  .flow-diagram {
    flex-direction: column;
  }
  .flow-arrow i {
    transform: rotate(90deg);
  }
  .event-categories {
    grid-template-columns: 1fr;
  }
}
</style>
