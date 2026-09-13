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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();
onMounted(() => setHeadings([
  { id: 'connect', text: 'Connect', level: 2 },
  { id: 'scan', text: 'Find event activity', level: 2 },
  { id: 'coverage', text: 'Coverage and limits', level: 2 }
]));
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Hub MCP" icon="bi bi-plug" />
    <div class="docs-content">
      <p>Find busy periods before downloading recordings. The Hub reads its finished JFR files and counts
        events by time bucket and type. Only compact summaries leave the Hub; Microscope is not required.</p>
      <h2 id="connect">Connect</h2>
      <p>Set <code>jeffrey.hub.mcp.enabled=true</code> and connect your MCP client to
        <code>http://localhost:8080/api/mcp</code>, using your configured Hub HTTP port.</p>
      <p>The endpoint is disabled by default. Native clients without an <code>Origin</code> header are supported;
        browser-origin requests are rejected. For remote access, set <code>jeffrey.hub.mcp.allowed-hosts</code>
        to the comma-separated trusted Hub hostnames and provide authentication through your reverse proxy.
        The hostname check does not authenticate callers.</p>
      <h2 id="scan">Find event activity</h2>
      <p>Call <code>hub_eventActivity</code> with <code>workspaceId</code>, <code>projectId</code>,
        <code>sessionId</code>, <code>startTime</code> and <code>endTime</code>. Times are epoch milliseconds
        in UTC; start is inclusive and end is exclusive. Optional <code>bucketSeconds</code> defaults to 300,
        giving 288 five-minute buckets for a day. Optional <code>eventTypes</code> is a comma-separated list
        of up to 16 exact names, such as <code>jdk.GarbageCollection</code>; omit it to count all event types.</p>
      <p>The call starts a background scan and returns a <code>scanId</code>. Poll
        <code>hub_activityStatus</code> with that ID. Set <code>order="events"</code> for the busiest intervals,
        <code>order="types"</code> for the greatest variety of observed event types, or <code>order="time"</code>
        for chronological order. At most 20 bucket details and 10 type details per bucket are returned;
        omission counts are explicit and totals include the omitted details. <code>limit</code> can lower
        the number of returned buckets. Output sizing may lower it further.</p>
      <p>Each start call creates a new scan. Poll its ID instead of starting it again. Use
        <code>hub_activityCancel(scanId)</code> to stop it. Cancellation remains <code>cancel_requested</code>
        until the reader and cleanup finish. Existing recordings are unchanged.</p>
      <h2 id="coverage">Coverage and limits</h2>
      <p>The scan has no total-event cap and keeps counters rather than raw events. Memory is bounded to
        at most 288 buckets and 512 distinct types. A type-capacity error leaves partial counts and asks
        for a narrower event-type filter. Two scans can run concurrently; further scans queue.
        At most 16 scans are kept, with completed results retained for up to one hour and evicted earlier
        when capacity is needed. Restarting the Hub forgets them.</p>
      <p>A finished scan can still be incomplete: check <code>complete</code>, <code>coverageKnown</code>,
        <code>sourceErrors</code> and <code>error</code>. Running or incomplete counts are lower bounds, so
        their rankings may change. Coverage includes only finished files visible at scan start; overlapping
        files can count the same event more than once. Counts describe recorded activity and do not
        establish equivalent application workloads.</p>
      <p>Only event types actually observed in the selected window contribute to its distinct-type count.
        An absent type may mean no events occurred or that recording settings excluded it.</p>
    </div>
    <DocsNavFooter />
  </article>
</template>
