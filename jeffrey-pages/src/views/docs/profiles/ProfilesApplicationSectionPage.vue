<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  { id: 'http-server', text: 'HTTP Server', level: 2 },
  { id: 'http-client', text: 'HTTP Client', level: 2 },
  { id: 'database', text: 'Database', level: 2 },
  { id: 'tracing', text: 'Tracing', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Application Section"
        icon="bi bi-layers"
      />

      <div class="docs-content">
        <p>The Application section provides <strong>application-specific analysis</strong> focused on how your code interacts with external systems - HTTP services, databases, and method tracing for performance-critical paths.</p>

        <h2 id="overview">Overview</h2>
        <p>While the JVM Internals section focuses on the runtime, the Application section focuses on your application's behavior:</p>
        <ul>
          <li><strong>HTTP Server</strong> - Inbound request handling and endpoint performance</li>
          <li><strong>HTTP Client</strong> - Outbound calls to external services</li>
          <li><strong>Database</strong> - SQL statement execution and connection pool behavior</li>
          <li><strong>Tracing</strong> - Method-level instrumentation for detailed timing</li>
        </ul>

        <DocsCallout type="warning">
          <strong>Event requirements:</strong> Application Mode features require Jeffrey JFR Events or compatible custom events to be enabled during recording. Features without the required events will appear disabled in the sidebar. See <router-link to="/docs/jeffrey-jfr-events/overview">Jeffrey JFR Events</router-link> for setup instructions.
        </DocsCallout>

        <h2 id="http-server">HTTP Server</h2>
        <p>HTTP Server analysis shows how your application handles incoming requests:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            High-level metrics for all server endpoints including request counts, response time distributions, and error rates. Quickly identify which endpoints receive the most traffic and which have performance issues.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-share" title="Endpoint Details">
            Drill down into individual endpoints to see detailed timing breakdowns, status code distributions, and request patterns. Useful for investigating specific API performance issues.
          </DocsFeatureItem>
        </div>

        <table>
          <thead>
            <tr>
              <th>Metric</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Request Count</td>
              <td>Total number of requests per endpoint</td>
            </tr>
            <tr>
              <td>P50/P95/P99 Latency</td>
              <td>Response time percentiles</td>
            </tr>
            <tr>
              <td>Error Rate</td>
              <td>Percentage of 4xx/5xx responses</td>
            </tr>
            <tr>
              <td>Throughput</td>
              <td>Requests per second over time</td>
            </tr>
          </tbody>
        </table>

        <h2 id="http-client">HTTP Client</h2>
        <p>HTTP Client analysis tracks outbound requests to external services:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            Aggregated metrics for all outbound HTTP calls showing target hosts, latency distributions, and failure rates. Identify slow external dependencies affecting your application.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-share" title="Endpoint Details">
            Per-target analysis of outbound calls including connection times, response times, and retry patterns. Essential for understanding third-party service impact on your application.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="tip">
          <strong>Dependency analysis:</strong> HTTP Client metrics help identify which external services are causing latency in your application. Look for high P99 times or elevated error rates.
        </DocsCallout>

        <h2 id="database">Database</h2>
        <p>Database analysis covers SQL execution and connection pool behavior:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Statements Overview">
            Summary of all SQL statement executions including total time, execution counts, and average duration. Quickly find which queries consume the most database time.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-collection" title="Statement Groups">
            SQL statements grouped by normalized query pattern. Similar queries with different parameters are aggregated together, making it easy to identify problematic query patterns regardless of parameter values.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-diagram-3" title="Connection Pools">
            Connection pool metrics showing active connections, wait times, and pool utilization. Identify connection exhaustion or misconfigured pool sizes.
          </DocsFeatureItem>
        </div>

        <table>
          <thead>
            <tr>
              <th>Metric</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Execution Count</td>
              <td>Number of times the statement was executed</td>
            </tr>
            <tr>
              <td>Total Duration</td>
              <td>Cumulative execution time across all calls</td>
            </tr>
            <tr>
              <td>Avg Duration</td>
              <td>Average time per execution</td>
            </tr>
            <tr>
              <td>Max Duration</td>
              <td>Slowest individual execution</td>
            </tr>
          </tbody>
        </table>

        <h2 id="tracing">Tracing</h2>
        <p>Method tracing provides detailed timing for instrumented code paths:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-speedometer2" title="Overview">
            Summary of all traced method invocations showing call counts, total time, and average duration. Get a high-level view of where time is spent in instrumented code.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-fire" title="Flamegraph">
            Flamegraph visualization built from trace data rather than CPU samples. Shows actual method execution times with full call hierarchy, enabling precise latency analysis.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-hourglass-split" title="Slowest Traces">
            Individual trace executions sorted by duration. Examine the slowest requests or operations to understand what caused the delay - useful for investigating specific incidents.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-layers" title="Cumulated Traces">
            Aggregated trace patterns showing cumulative time across all invocations. Identifies methods that may be fast individually but consume significant time due to high call frequency.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Tracing vs Sampling:</strong> Tracing shows actual method execution times (wall-clock), not statistical samples. This enables precise latency analysis but requires instrumentation to be configured in your application.
        </DocsCallout>

        <p>The Application section features complement JVM Internals by focusing on your code's external interactions. Use both sections together for complete application analysis.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
