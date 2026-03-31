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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'technologies-hub', text: 'Technologies Hub', level: 2 },
  { id: 'http-server', text: 'HTTP Server', level: 2 },
  { id: 'http-client', text: 'HTTP Client', level: 2 },
  { id: 'grpc-server', text: 'gRPC Server', level: 2 },
  { id: 'grpc-client', text: 'gRPC Client', level: 2 },
  { id: 'database', text: 'Database', level: 2 },
  { id: 'method-tracing', text: 'Method Tracing', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Technologies Section"
        icon="bi bi-layers"
      />

      <div class="docs-content">
        <p>The Technologies section provides <strong>application-specific analysis</strong> focused on how your code interacts with external systems - HTTP services, gRPC services, databases, and method tracing for performance-critical paths.</p>

        <h2 id="overview">Overview</h2>
        <p>While the JVM Internals section focuses on the runtime, the Technologies section focuses on your application's behavior:</p>
        <ul>
          <li><strong>HTTP Server</strong> - Inbound request handling and endpoint performance</li>
          <li><strong>HTTP Client</strong> - Outbound calls to external HTTP services</li>
          <li><strong>gRPC Server</strong> - Inbound gRPC service call analysis</li>
          <li><strong>gRPC Client</strong> - Outbound gRPC call analysis</li>
          <li><strong>Database</strong> - SQL statement execution and connection pool behavior</li>
          <li><strong>Method Tracing</strong> - Method-level instrumentation for detailed timing</li>
        </ul>

        <DocsCallout type="warning">
          <strong>Event requirements:</strong> Technologies features require Jeffrey JFR Events or compatible custom events to be enabled during recording. Features without the required events will appear disabled in the hub. See <router-link to="/docs/jeffrey-jfr-events/overview">Jeffrey JFR Events</router-link> for setup instructions.
        </DocsCallout>

        <h2 id="technologies-hub">Technologies Hub</h2>
        <p>The Technologies Hub is the entry point that displays all available technology dashboards as cards. Each card shows the technology name and a brief description. Cards are automatically disabled when no matching events are found in the recording.</p>

        <p>An integration info strip at the top of the hub explains how to enable these dashboards. Add the <code>cafe.jeffrey-analyst:jeffrey-events</code> dependency to your application to emit the required JFR events &mdash; dashboards activate automatically when matching events are detected in the recording.</p>

        <h2 id="http-server">HTTP Server</h2>
        <p>HTTP Server analysis shows how your application handles incoming requests:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            High-level metrics including total requests, response times (max, P99, P95), success rate with error breakdown, and data transferred. Includes an endpoint list and distribution charts for status codes and HTTP methods.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            Response time and request count trends over the recording duration. Visualize traffic patterns and identify latency spikes at specific points in time.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            Status code and HTTP method distribution charts showing the breakdown of responses across your endpoints.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Requests">
            Table of the slowest individual HTTP requests sorted by response time. Drill into specific slow requests to understand what caused the delay.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-share" title="Endpoint Details">
            Drill down into individual endpoints to see detailed timing breakdowns, status code distributions, and request patterns.
          </DocsFeatureItem>
        </div>

        <h2 id="http-client">HTTP Client</h2>
        <p>HTTP Client analysis tracks outbound requests to external services:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            Aggregated metrics for all outbound HTTP calls showing target hosts, latency distributions, and failure rates. Identify slow external dependencies affecting your application.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            Outbound request trends over time showing response times and call counts to external services.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            Status code and target distribution for outbound calls.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Requests">
            Slowest outbound HTTP calls sorted by response time. Essential for identifying which external dependencies cause latency.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-share" title="Endpoint Details">
            Per-target analysis of outbound calls including connection times, response times, and retry patterns.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="tip">
          <strong>Dependency analysis:</strong> HTTP Client metrics help identify which external services are causing latency in your application. Look for high P99 times or elevated error rates.
        </DocsCallout>

        <h2 id="grpc-server">gRPC Server</h2>
        <p>gRPC Server analysis shows how your application handles incoming gRPC calls:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            High-level gRPC metrics including total calls, response times (max, P99, P95), success rate, and data transferred. Lists all gRPC services with their call counts and performance metrics.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            Response time and call count trends over the recording duration for gRPC services.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            gRPC status code distribution showing the breakdown of OK, error, and other status codes across your services.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Calls">
            Individual gRPC calls sorted by response time. Examine the slowest service calls to understand latency causes.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-list-columns-reverse" title="Services">
            Detailed per-service view with method-level breakdown. Each service shows its methods with individual stats, timeseries, distribution, and slowest calls in a tabbed interface.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-arrow-left-right" title="Traffic">
            Data transfer analysis showing average and maximum request/response payload sizes. Identify services with unexpectedly large payloads.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up-arrow" title="Size Timeseries">
            Payload size trends over time showing how request and response sizes change during the recording.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-bar-chart" title="Size Distribution">
            Distribution of payload sizes across all gRPC calls.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-sort-down" title="Largest Calls">
            Individual gRPC calls sorted by payload size to identify the largest messages in your service communication.
          </DocsFeatureItem>
        </div>

        <h2 id="grpc-client">gRPC Client</h2>
        <p>gRPC Client analysis tracks outbound gRPC calls to other services:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            Aggregated metrics for all outbound gRPC calls showing target services, latency distributions, and failure rates.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            Outbound gRPC call trends over time.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            Status code distribution for outbound gRPC calls.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Calls">
            Slowest outbound gRPC calls sorted by response time.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-list-columns-reverse" title="Services">
            Per-service breakdown of outbound gRPC calls with method-level details.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-arrow-left-right" title="Traffic">
            Payload size analysis for outbound gRPC communication.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Server vs Client:</strong> gRPC Server and Client dashboards share the same sub-pages but show different data. Server shows inbound calls to your services, Client shows outbound calls to other services.
        </DocsCallout>

        <h2 id="database">Database</h2>
        <p>Database analysis covers SQL execution and connection pool behavior:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            Summary of all SQL statement executions including total time, execution counts, average duration, distribution charts, and a list of the slowest statements.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            SQL execution time and statement count trends over the recording duration. Identify periods of high database activity.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            Operation type (SELECT, INSERT, UPDATE, DELETE) and statement group distribution charts.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Statements">
            Individual SQL executions sorted by duration. Click to view the full SQL text.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-collection" title="Statement Groups">
            SQL statements grouped by normalized query pattern. Similar queries with different parameters are aggregated together, making it easy to identify problematic query patterns.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-diagram-3" title="Connection Pools">
            Connection pool metrics showing active connections, wait times, and pool utilization. Identify connection exhaustion or misconfigured pool sizes.
          </DocsFeatureItem>
        </div>

        <h2 id="method-tracing">Method Tracing</h2>
        <p>Method tracing provides detailed timing for instrumented code paths:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-speedometer2" title="Overview">
            Summary of all traced method invocations showing total invocations, unique methods, total duration, and response time percentiles. Includes distribution charts for top methods by invocations and by duration.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up" title="Timeseries">
            Method invocation count and total duration trends over the recording duration.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-pie-chart" title="Distribution">
            Top methods by invocation count and by total duration distribution charts.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-fire" title="Flamegraph">
            Flamegraph visualization built from trace data rather than CPU samples. Shows actual method execution times with full call hierarchy, enabling precise latency analysis.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Traces">
            Individual trace executions sorted by duration. Examine the slowest requests or operations to understand what caused the delay.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-layers" title="Cumulated Traces">
            Aggregated trace patterns showing cumulative time across all invocations. Identifies methods that may be fast individually but consume significant time due to high call frequency.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Tracing vs Sampling:</strong> Tracing shows actual method execution times (wall-clock), not statistical samples. This enables precise latency analysis but requires instrumentation to be configured in your application.
        </DocsCallout>

        <p>The Technologies section features complement JVM Internals by focusing on your code's external interactions. Use both sections together for complete application analysis.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
