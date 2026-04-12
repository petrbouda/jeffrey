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
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'available-events', text: 'Available Events', level: 2 },
  { id: 'time-range', text: 'Time Range', level: 2 },
  { id: 'replay-stream', text: 'Replay Stream', level: 2 },
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
      <p>Event Streaming lets you subscribe to <strong>live JFR events</strong> from a remote session's streaming repository. Events are delivered in real-time via Server-Sent Events (SSE) as they are committed by the JVM's JFR infrastructure.</p>

      <DocsCallout type="info">
        <strong>Server Connection Required:</strong> Live Stream requires a workspace connected to a jeffrey-server instance with active recording sessions.
      </DocsCallout>

      <h2 id="how-it-works">How It Works</h2>
      <p>Jeffrey Server opens the JFR streaming repository of a running session using the JDK's <code>EventStream.openRepository()</code> API. Events are micro-batched on JFR's flush cycle (~1 second) and streamed to your browser via gRPC (server) and SSE (browser).</p>

      <div class="flow-diagram">
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-cpu"></i></div>
          <div class="flow-label">JVM writes events to streaming repository</div>
        </div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-cloud"></i></div>
          <div class="flow-label">Jeffrey Server reads and batches events via gRPC</div>
        </div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-step">
          <div class="flow-icon"><i class="bi bi-pc-display"></i></div>
          <div class="flow-label">Jeffrey Local bridges to SSE for browser delivery</div>
        </div>
      </div>

      <h2 id="configuration">Configuration</h2>
      <p>The streaming configuration wizard has three steps:</p>

      <div class="config-steps">
        <div class="config-step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4>Session</h4>
            <p>Select an active recording session from one of the project's running instances. Only sessions with a streaming repository are available.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4>Event Types</h4>
            <p>Choose which JFR event types to subscribe to. Events are organized by category: CPU, GC, memory, threading, I/O, and Jeffrey instrumentation events.</p>
          </div>
        </div>
        <div class="config-step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4>Time Range</h4>
            <p>Set the start time (<em>From Beginning</em>, <em>Now</em>, or <em>Custom</em>), end time (<em>Now</em> or <em>Custom</em>), and optionally enable <strong>Continuous streaming</strong> to keep the stream open for new events.</p>
          </div>
        </div>
      </div>

      <h2 id="available-events">Available Events</h2>
      <p>The streaming repository contains events committed by the JVM's JFR infrastructure in real-time. These include periodic JVM statistics, runtime events, and Jeffrey's custom instrumentation events.</p>

      <DocsCallout type="warning">
        <strong>CPU profiling events not available:</strong> Events like <code>jdk.ExecutionSample</code> collected by async-profiler are merged into the JFR recording at dump time and do not appear in the live streaming repository.
      </DocsCallout>

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

      <h2 id="time-range">Time Range</h2>
      <p>The time range controls which events are delivered:</p>
      <ul>
        <li><strong>From Beginning:</strong> Replay all events available in the streaming repository, then optionally continue with new events</li>
        <li><strong>From Now:</strong> Only receive events generated after subscription</li>
        <li><strong>Custom:</strong> Specify exact start and/or end timestamps</li>
        <li><strong>Continuous:</strong> Keep the stream open after the initial replay, receiving new events as they arrive</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Bounded vs Continuous:</strong> Without continuous mode, the stream automatically closes when it reaches the end time. With continuous mode enabled, the stream stays open and delivers new events indefinitely until you disconnect.
      </DocsCallout>

      <h2 id="replay-stream">Replay Stream</h2>
      <p>While Live Stream subscribes to events from a running session, <strong>Replay Stream</strong> reads historical JFR events from already dumped recording files. This lets you browse past events without a live connection to the server.</p>

      <p>Replay Stream works with any recording that has been merged into the project's Recordings section. Select a recording, choose event types, and replay the events as if they were streaming in real-time.</p>

      <DocsCallout type="tip">
        <strong>Live vs Replay:</strong> Use Live Stream to monitor running applications in real-time. Use Replay Stream to investigate historical events from completed recordings — no active session required.
      </DocsCallout>

      <h2 id="workspace-availability">Workspace Availability</h2>
      <p><strong>Live Stream</strong> requires a workspace connected to a jeffrey-server instance. The project must have at least one active recording session with a JFR streaming repository.</p>
      <p><strong>Replay Stream</strong> is available in all workspace types, as it reads from local recording files.</p>
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
