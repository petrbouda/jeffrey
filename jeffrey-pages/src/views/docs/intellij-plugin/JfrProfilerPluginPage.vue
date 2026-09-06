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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'install', text: 'Install', level: 2 },
  { id: 'configure', text: 'Point Microscope at It', level: 2 },
  { id: 'differences', text: 'What You Give Up', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const config = `jeffrey.microscope.ide.mode=jfr-profiler-plugin
jeffrey.microscope.ide.base-url=http://localhost:4243`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Java JFR Profiler Plugin"
      icon="bi bi-window-stack"
    />

    <div class="docs-content">
      <p>
        <strong>Java JFR Profiler</strong> is a third-party IntelliJ plugin for opening and
        analyzing Java Flight Recorder files inside the IDE. Microscope can route its
        <em>Open in IDE</em> and <em>View Source</em> requests to it as an alternative to the
        first-party
        <router-link to="/docs/intellij-plugin">Jeffrey Microscope Plugin</router-link>.
      </p>

      <h2 id="install">Install</h2>
      <p>
        Open the plugin page on the JetBrains Marketplace and click <strong>Install to IDE</strong>:
        <a href="https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler" target="_blank" rel="noopener noreferrer">
          plugins.jetbrains.com/plugin/20937-java-jfr-profiler
        </a>.
      </p>

      <h2 id="configure">Point Microscope at It</h2>
      <p>
        Installing the plugin is not enough on its own: Microscope defaults to the first-party
        plugin and discovers it by scanning. This one is a single URL, so it has to be named. Both
        properties are required &mdash; the mode alone leaves the bridge with no address to call.
      </p>
      <DocsCodeBlock :code="config" language="properties" />
      <p>
        The port is the one the JFR Profiler plugin serves on; <code>4243</code> is its default.
        Both are application properties, so the change takes a restart.
      </p>

      <h2 id="differences">What You Give Up</h2>
      <p>
        The two bridges are not equivalent, and the difference is worth knowing before switching:
      </p>
      <ul>
        <li><strong>No window picker.</strong> A single URL means a single IDE. Microscope cannot
          list open projects or bind one to a profile, so the profile-wide IDE control shows
          &ldquo;Connected using JFR Profiler Plugin&rdquo; and nothing to choose.</li>
        <li><strong>No <code>ide_</code> MCP tools.</strong>
          <router-link to="/docs/microscope-mcp/tools#ide"><code>ide_resolve</code></router-link>
          needs an IDE that will locate a frame and report it without opening it, which is a
          capability of the first-party plugin. <code>ide_source</code> and <code>ide_open</code>
          still work.</li>
        <li><strong>No checkout awareness.</strong> Nothing reports the branch or commit, so a
          profile cannot be checked against the code you have open.</li>
        <li><strong>Per-frame gating.</strong> The flamegraph&rsquo;s IDE buttons render disabled
          and enable only once the plugin confirms it has the class, which costs a lookup per frame.</li>
      </ul>

      <DocsCallout type="info" title="The first-party plugin is the default for a reason">
        Unless you are already using Java JFR Profiler for its own sake, the
        <router-link to="/docs/intellij-plugin">Jeffrey Microscope plugin</router-link> needs no
        configuration at all and supports every feature above.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
