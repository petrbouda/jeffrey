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
  { id: 'tls', text: 'TLS Handshakes', level: 2 },
  { id: 'certificates', text: 'Certificates', level: 2 },
  { id: 'deserialization', text: 'Deserialization', level: 2 },
  { id: 'providers', text: 'Crypto Providers', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Security & TLS" icon="bi bi-shield-lock" />

    <div class="docs-content">
      <p>The Security &amp; TLS page (the last item in the Analysis section) aggregates the JDK security JFR events for both security-posture review and performance investigation. All of these are instant events, so there is no per-operation latency — the value is in the breakdowns, flags, and counts. The page shows an empty state when no security events are present.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip shows total TLS handshakes (and distinct peers), certificates observed (and how many were flagged), and deserialization events (and how many were rejected).</p>

      <DocsCallout type="tip">
        <strong>Where to start:</strong> open <em>Certificates</em> for posture (weak keys, deprecated signatures, expiry) and <em>TLS Handshakes</em> for protocol/cipher hygiene and connection churn.
      </DocsCallout>

      <h2 id="tls">TLS Handshakes</h2>
      <p>Handshakes per second over time (<code>jdk.TLSHandshake</code>), plus breakdowns by protocol version (legacy TLS 1.0/1.1 are flagged), cipher suite, and peer (<code>host:port</code>). Sustained high handshake rates point to connection churn or missing session resumption — a latency and CPU cost.</p>

      <h2 id="certificates">Certificates</h2>
      <p>Every X.509 certificate observed (<code>jdk.X509Certificate</code>), deduplicated by id and annotated with validation counts (<code>jdk.X509Validation</code>). Each row is flagged server-side for a <strong>weak key</strong> (&lt; 2048-bit RSA / &lt; 256-bit EC), a <strong>weak signature</strong> (MD5/SHA-1), and <strong>expired</strong> or <strong>expiring-soon</strong> status (validity compared to the recording's end time).</p>

      <h2 id="deserialization">Deserialization</h2>
      <p>Java deserialization activity (<code>jdk.Deserialization</code>): a summary of total events, how many had a filter configured, how many were rejected, and how many threw; plus the top deserialized types by total bytes read (with max bytes and max graph depth). Unfiltered or oversized deserialization is both a security risk and a performance concern.</p>

      <h2 id="providers">Crypto Providers</h2>
      <p>JCA provider/service-type/algorithm usage counts (<code>jdk.SecurityProviderService</code>) — confirm which providers and algorithms were actually exercised and spot unexpected or weak algorithm usage.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every security event the JDK emits.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
