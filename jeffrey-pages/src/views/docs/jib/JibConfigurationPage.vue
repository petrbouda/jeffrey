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
  { id: 'properties', text: 'Properties', level: 2 },
  { id: 'build-time-vs-runtime', text: 'Build-time vs Runtime', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="JIB Configuration"
        icon="bi bi-gear"
      />

      <div class="docs-content">
        <p>Every field has a sensible default &mdash; you only need to set a property when you want
          to override it. Non-null values are baked as image-level ENV defaults; Kubernetes
          pod-level env vars still override them at runtime. See
          <router-link to="/docs/jib/setup">Build Setup</router-link> for where these properties are
          declared in Gradle and Maven.</p>

        <h2 id="properties">Properties</h2>
        <div class="config-table">
          <table>
            <thead>
              <tr><th>Property</th><th>Image ENV</th><th>Default</th><th>Purpose</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><code>enabled</code><br><span class="prop-type">boolean</span></td>
                <td>&mdash;</td>
                <td><code>true</code></td>
                <td><strong>Build-time</strong> gate. If <code>false</code>, the extension is a no-op &mdash; no wrapper layer, no entrypoint rewrite, plain JIB output.</td>
              </tr>
              <tr>
                <td><code>jeffreyHome</code></td>
                <td><code>JEFFREY_HOME</code></td>
                <td>&mdash; <span class="prop-type">(must be set)</span></td>
                <td>Shared-volume root. Wrapper resolves the provisioner at <code>&lt;home&gt;/libs/current/provisioner-&lt;arch&gt;</code>. If neither this nor <code>provisionerPath</code> is set, the wrapper warns and falls through &mdash; the container still starts, just without profiling.</td>
              </tr>
              <tr>
                <td><code>baseConfig</code></td>
                <td><code>JEFFREY_BASE_CONFIG</code></td>
                <td><code>/jeffrey/jeffrey-base.conf</code></td>
                <td>Path to the base HOCON config inside the container.</td>
              </tr>
              <tr>
                <td><code>overrideConfig</code></td>
                <td><code>JEFFREY_OVERRIDE_CONFIG</code></td>
                <td><code>/jeffrey/jeffrey-overrides.conf</code></td>
                <td>Path to per-service override HOCON. The wrapper only passes it to <code>provisioner init</code> if the file actually exists, so it's effectively optional at runtime.</td>
              </tr>
              <tr>
                <td><code>provisionerPath</code></td>
                <td><code>JEFFREY_PROVISIONER_PATH</code></td>
                <td>derived from <code>jeffreyHome</code></td>
                <td>Explicit provisioner binary path. Bypasses the <code>&lt;home&gt;/libs/current/*</code> resolution when you bundle the provisioner into your image yourself.</td>
              </tr>
              <tr>
                <td><code>argFile</code></td>
                <td><code>JEFFREY_ARG_FILE</code></td>
                <td><code>/tmp/jvm.args</code></td>
                <td>Argfile path. Must match the HOCON <code>arg-file</code> key.</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h2 id="build-time-vs-runtime">Build-time vs Runtime</h2>
        <p>There are two layers of control. <code>enabled</code> is a <strong>build-time</strong> gate
          evaluated by the extension when the image is assembled &mdash; setting it to <code>false</code>
          produces a plain JIB image with no wrapper at all. The remaining properties become image-level
          <code>ENV</code> defaults that the entrypoint wrapper reads at container start, and every one of
          them can be overridden at runtime by a pod-level environment variable of the same name.</p>

        <DocsCallout type="info">
          <strong>Runtime kill switch.</strong> Independently of the build-time <code>enabled</code> gate,
          you can disable profiling on an already-built image by setting
          <code>JEFFREY_ENABLED=false</code> (or <code>0</code>/<code>no</code>/<code>off</code>) on the
          pod &mdash; the wrapper then <code>exec</code>s the original <code>java</code> command verbatim.
          See <router-link to="/docs/jib">Overview &rarr; Runtime Kill Switch</router-link>.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Config table */
.config-table {
  margin: 1.5rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.config-table table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}

.config-table th,
.config-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.875rem;
  color: #374151;
  vertical-align: top;
}

.config-table thead th {
  background: #f8fafc;
  font-weight: 600;
  color: #1f2937;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.config-table tbody tr:last-child td {
  border-bottom: none;
}

.config-table code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.82rem;
  padding: 0.05rem 0.35rem;
  background: #f1f5f9;
  border-radius: 4px;
  color: #1e293b;
}

.config-table .prop-type {
  display: inline-block;
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: #64748b;
}
</style>
