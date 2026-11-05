<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'what-it-reads', text: 'What It Reads', level: 2 },
  { id: 'its-tools', text: 'Its Tools', level: 2 },
  { id: 'what-it-returns', text: 'What It Returns', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const report = `## What holds the memory - 1.4 GiB live

1. \`com.example.audit.AuditLog\` - retained 812 MiB, 1 instance (shallow 32 B)
   GC-root path: static AuditLog.INSTANCE -> entries -> ArrayList -> Object[]
   An in-memory log that is appended to and never trimmed.
   Link: <uiLink>
2. ...

Notes: dominator tree built for this; leak suspects computed.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="heap-analyst"
      icon="bi bi-memory"
    />

    <div class="docs-content">
      <p>The agent for heap dumps, installed as <code>microscope:heap-analyst</code> in Claude Code. <code>analyze-heap</code> hands it a dump and gets the findings back.</p>

      <h2 id="what-it-reads">What It Reads</h2>
      <p>One heap dump, given as a <code>profileId</code> with one question: what is using the memory, what is leaking, why the heap grows across redeploys, or where the waste is. It works the whole route: a histogram names a class, the dominator tree says what retains it, and a GC-root path says why it is still reachable.</p>
      <DocsCallout type="info" title="Heap dumps only">
        With no <code>profileId</code>, or handed a JFR recording, it says so and stops. Recordings are <router-link to="/docs/microscope-mcp/agent/jfr-analyst"><code>jfr-analyst</code></router-link>&rsquo;s. A dump you name by its path is imported in your session first; the agent does not import.
      </DocsCallout>

      <h2 id="its-tools">Its Tools</h2>
      <p><code>heap_*</code>, <code>profiles_*</code> and <code>operations_status</code>. It builds a missing dominator tree or report with <code>heap_prepare</code>, and <code>heap_oql</code> with <code>includeRetainedSize</code> stays open; both only fill a cache. Its handbook, in its own definition, is cut from <code>analyze-heap</code> and <code>heap-sql</code>.</p>

      <h2 id="what-it-returns">What It Returns</h2>
      <p>For each finding the class name, the retained bytes and the GC-root path &mdash; findings only, about forty lines at most:</p>
      <DocsCodeBlock :code="report" language="markdown" />
      <p>Every figure comes from a tool result, and a report that was never computed is listed under <strong>Not assessed</strong>, not as an empty ranking.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
