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

const headings = [
  { id: 'marketplace', text: 'From the JetBrains Marketplace', level: 2 },
  { id: 'from-source', text: 'From Source (development)', level: 2 },
  { id: 'verify', text: 'Verify', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="IntelliJ Plugin Setup"
      icon="bi bi-download"
    />

    <div class="docs-content">
      <p>
        Install the <strong>Jeffrey Microscope</strong> plugin into IntelliJ IDEA, then verify the
        built-in-server port Microscope will discover. For what the plugin does once installed, see
        <router-link to="/docs/intellij-plugin">Overview</router-link>.
      </p>

      <h2 id="marketplace">From the JetBrains Marketplace</h2>
      <ol>
        <li>In IntelliJ: <strong>Settings → Plugins → Marketplace</strong> and search for
          <strong>Jeffrey Microscope</strong>.</li>
        <li>Click <strong>Install</strong> and restart the IDE when prompted.</li>
      </ol>
      <p>
        Or open the plugin page directly:
        <a href="https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope" target="_blank" rel="noopener noreferrer">
          plugins.jetbrains.com/plugin/31963-jeffrey-microscope
        </a> and click <strong>Install to IDE</strong>.
      </p>

      <h2 id="from-source">From Source (development)</h2>
      <ol>
        <li>Build the plugin from the Jeffrey repo — it's a standalone Gradle project, not part of
          the Maven reactor:
          <pre><code>cd jeffrey-intellij-plugin
./gradlew buildPlugin</code></pre>
          The output is <code>build/distributions/jeffrey-intellij-plugin-&lt;version&gt;.zip</code>.
        </li>
        <li>In IntelliJ: <strong>Settings → Plugins → ⚙ → Install Plugin from Disk…</strong>,
          select the zip, and restart the IDE.</li>
      </ol>

      <h2 id="verify">Verify</h2>
      <p>
        Open <strong>Settings → Tools → Jeffrey Microscope Plugin</strong> — this view shows the
        bound built-in-server port that Microscope's discovery scan will land on, plus the active
        plugin version. If that port falls outside the default 63342&ndash;63362 range, adjust
        Microscope's scan range as described in
        <router-link to="/docs/intellij-plugin/configuration">Configuration</router-link>.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
