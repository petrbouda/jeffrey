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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'instance-sessions', text: 'Instances and Sessions', level: 2 },
  { id: 'instances-tab', text: 'Instances Tab', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Instances"
        icon="bi bi-hdd-stack"
      />

      <div class="docs-content">
        <p><strong>Instances</strong> represent individual JVM processes connected to a project. When an application starts through the Jeffrey Provisioner it gets an instance folder in the project &mdash; named by <code>instance-name</code>, <code>HOSTNAME</code> by default, and that folder name is the instance's id &mdash; and starts producing recording sessions.</p>

        <h2 id="overview">Overview</h2>
        <p>The project page opens on its <strong>Instances</strong> tab:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-grid" title="Instances">
            All instances registered in the project in one table, with the project's storage size — the bytes every session of the project occupies on the remote. Only the Jeffrey Hub&rsquo;s retention pass measures that figure, and Microscope does not connect to the hub, so on the Azure file share the size reads as not measured; the hub&rsquo;s own <router-link to="/docs/hub/architecture#web-ui">Storage page</router-link> shows it.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Remote workspaces only:</strong> Instances appear only for projects inside the workspaces of the configured remote. Locally-uploaded JFR files have no instance concept — they live on the global Recordings page instead.
        </DocsCallout>

        <h2 id="instance-sessions">Instances and Sessions</h2>
        <p>An instance has no status. Its sessions are ordered by their UUIDv7 directory names, whose timestamp is each session's start; the <strong>newest</strong> session is the only one that may still grow, and every older one was ended by a newer one. See <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link> for the details.</p>

        <p>When retention (or a manual delete) removes an instance's last session, the instance's directory goes with it, so an instance simply disappears from the list rather than lingering in an expired state.</p>

        <h2 id="instances-tab">Instances Tab</h2>
        <p>Instances are listed in a table with two columns, <strong>Instance</strong> (its name, the instance's folder) and <strong>Started</strong> (when the instance's folder was created, as the share lists it — blank on an NFS share, which lists no times), newest first. Microscope shows no counts or aggregates here: no session count and no last data. Use the search box to filter by name; click a row to open the instance's page with its sessions.</p>
        <p>The right end of the tab row carries a one-line summary of the project: its instances and, when measured, its storage size. Click it to open the full breakdown:</p>
        <ul>
          <li><strong>Instances</strong> — total instances and the first instance.</li>
          <li><strong>Storage</strong> — the bytes the project occupies on the remote. Opening the tab never walks the project's files for it, and only the Jeffrey Hub's retention pass measures it, so on the Azure file share the breakdown says the project is not measured; see the hub's Storage page for the figure.</li>
        </ul>
        <p>Every view here loads one level of the remote's tree: the instance list carries no sessions, and an instance's page reads that instance and a page of its sessions by name only.</p>
        <p>Nothing reloads on its own. On the Azure file share every read is a round-trip to the share, so an instance's page reads the instance once when it opens, every session starts collapsed, and a recording session's sizes stay as they were read until <strong>Refresh</strong> in the page header reads them again.</p>
        <p>An instance's page lists its sessions by name only, ten at a time: its ten newest sessions, and <strong>Load more</strong> under the list reads the next ten older ones. Each session card shows the session's id and start, both from its UUIDv7 folder name, and whether it is the instance's newest session; its files, size, last data and duration load when the card is expanded. A session is a visible folder named with a UUIDv7 &mdash; anything else under the instance is ignored. There is no failed-session band on the page.</p>
        <p>There are no description files to read: the provisioner writes no marker anywhere on the volume, so every id Microscope shows is a folder name and a page asks the share only for folder listings and sizes. The reads of one page go out up to eight at a time.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
