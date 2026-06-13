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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'throughput', text: 'Throughput', level: 2 },
  { id: 'peers', text: 'Top Peers', level: 2 },
  { id: 'slowest', text: 'Slowest Operations', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Socket I/O" icon="bi bi-ethernet" />

    <div class="docs-content">
      <p>The Socket I/O page surfaces blocking network I/O — a frequent but easily-missed latency source that doesn't appear in CPU flame graphs. It shows read/write throughput over time, the slowest individual operations, and the busiest peers, attributing wait time to the <code>host:port</code> endpoints your application talks to.</p>

      <DocsCallout type="tip">
        <strong>Reading it:</strong> a peer with high <em>total</em> time but low max is chatty (many small calls — consider batching); a peer with a high <em>max</em> is one slow call — a timeout or a saturated link to a downstream service.
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows total bytes read and written, the socket operation count, and the single slowest operation with its peer.</p>

      <h2 id="throughput">Throughput</h2>
      <p>Bytes read and written per second over socket connections. A read plateau usually means you're waiting on a slow upstream; a write plateau means a slow or backpressured downstream.</p>

      <h2 id="peers">Top Peers</h2>
      <p>Socket endpoints (<code>host:port</code>) ranked by bytes — operation count, bytes (with a share bar), and total/max time per peer — from <code>jdk.SocketRead</code> / <code>jdk.SocketWrite</code>.</p>

      <h2 id="slowest">Slowest Operations</h2>
      <p>The slowest individual reads/writes ordered by duration, with direction, peer, bytes transferred and the blocking thread — the long tail that drives p99 latency.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.SocketRead</code> — host, address, port, bytes read, timeout, end-of-stream and duration (threshold-gated).</li>
        <li><code>jdk.SocketWrite</code> — host, address, port, bytes written and duration (threshold-gated).</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
