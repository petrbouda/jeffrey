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
  { id: 'requirements', text: 'Target IDE', level: 2 },
  { id: 'port-range', text: 'Scan Port Range', level: 2 },
  { id: 'trusted-projects', text: 'Trusted Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="IntelliJ Plugin Configuration"
      icon="bi bi-gear"
    />

    <div class="docs-content">
      <p>
        The plugin pairs with Microscope automatically &mdash; there is no port to share and no
        token to copy. The settings below only matter when your environment deviates from the
        IntelliJ defaults. See <router-link to="/docs/intellij-plugin/setup">Setup</router-link>
        to install the plugin first.
      </p>

      <h2 id="requirements">Target IDE</h2>
      <DocsCallout type="info">
        <strong>Target IDE:</strong> IntelliJ IDEA 2025.1+ (<code>since-build = 251</code>).
        The plugin runs on the JetBrains Runtime (Java 21) and is unrelated to the Java version
        you use for the JVM under analysis.
      </DocsCallout>

      <h2 id="port-range">Scan Port Range</h2>
      <p>
        Microscope discovers running IDEs by scanning localhost over a small port range and calling
        <code>/api/jeffrey/instance</code> on each. The default range is 63342&ndash;63362
        (IntelliJ's built-in server defaults). Override it in Microscope's
        <code>application.properties</code> if your IDE binds elsewhere:
      </p>

      <div class="code-block">
        <pre><code>jeffrey.microscope.ide.scan.port-start=63342
jeffrey.microscope.ide.scan.port-end=63362</code></pre>
      </div>

      <p>
        Scanning is lazy and cached: once a window is linked to a profile, the chosen port is reused
        for every jump, and Microscope only re-scans when that port stops responding (IDE restart or
        port reassignment).
      </p>

      <h2 id="trusted-projects">Trusted Projects</h2>
      <p>
        The plugin only exposes <strong>trusted</strong> projects &mdash; untrusted projects never
        show up in Microscope's per-profile target picker. If an expected project is missing from the
        picker, confirm it is open and marked trusted in IntelliJ.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Code Block */
.code-block {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.code-block pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.code-block code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}
</style>
