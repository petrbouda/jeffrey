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

const report = `## jdk.ObjectAllocationSample - 5.2 GiB over 2724 samples

1. \`com.example.OrderMapper.toDto\` - total 34.1% (1.8 GiB), self 31.2%
   Reached from OrderService.list through a stream collector; allocating a
   new DTO plus an ArrayList per row.
2. ...

Notes: threshold 1%, weighted by bytes. Frames below 1% rolled into parents.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="jfr-analyst"
      icon="bi bi-fire"
    />

    <div class="docs-content">
      <p>The agent for JFR recordings, installed as <code>microscope:jfr-analyst</code> in Claude Code. <code>analyze-jfr</code>, <code>compare-jfr</code> and <code>optimize-code</code> hand it the reading of a recording and get the findings back.</p>

      <h2 id="what-it-reads">What It Reads</h2>
      <p>One JFR recording, given as a <code>profileId</code> with one question: an event type to graph, a trace operation to work, or the JVM&rsquo;s own views. A comparison adds a <strong>baseline</strong> id: the <code>profileId</code> is the run under examination and the baseline is what it is measured against.</p>
      <p>An open question such as &ldquo;why is this service slow&rdquo; goes through <router-link to="/docs/microscope-mcp/skills"><code>analyze-jfr</code></router-link>, which puts the profile&rsquo;s investigation areas to you and sends the ones you pick to <code>jfr-analyst</code> in one message, so they run at once.</p>
      <DocsCallout type="info" title="Recordings only">
        With no <code>profileId</code>, or handed a heap dump, it says so and stops. Heap dumps are <router-link to="/docs/microscope-mcp/agent/heap-analyst"><code>heap-analyst</code></router-link>&rsquo;s.
      </DocsCallout>

      <h2 id="its-tools">Its Tools</h2>
      <p>Every Jeffrey tool except the <code>heap_</code> family and the writers. Of the <router-link to="/docs/microscope-mcp/clients#what-writes">writers</router-link>, only <code>jvm_autoAnalysis</code> with <code>compute</code> stays open: it fills a cache. Its handbook, in its own definition, is cut from <code>analyze-jfr</code> and <code>compare-jfr</code>.</p>

      <h2 id="what-it-returns">What It Returns</h2>
      <p>The hottest frames with their shares, the slow traces, or the movements between two runs &mdash; findings only, about forty lines at most, each with the Microscope link of the page that shows it:</p>
      <DocsCodeBlock :code="report" language="markdown" />
      <p>Every figure comes from a tool result, and what the recording could not answer is listed under <strong>Not assessed</strong>.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
