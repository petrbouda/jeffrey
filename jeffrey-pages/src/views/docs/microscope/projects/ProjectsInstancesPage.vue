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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'instance-sessions', text: 'Instances and Sessions', level: 2 },
  { id: 'instances-column', text: 'Instances and Sessions Columns', level: 2 },
  { id: 'session-drawer', text: 'The Session Drawer', level: 2 }
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
        <p>Instances are browsed on the <strong>Remote</strong> page: pick a project in the Projects column and its instances fill the <strong>Instances</strong> column, the newest one picked, with its recording sessions in the <strong>Sessions</strong> column beside it. Opening a session slides in the <a href="#session-drawer">session drawer</a>.</p>

        <DocsCallout type="info">
          <strong>Remote workspaces only:</strong> Instances appear only for projects inside the workspaces of the configured remote. Locally-uploaded JFR files have no instance concept — they live on the global Recordings page instead.
        </DocsCallout>

        <h2 id="instance-sessions">Instances and Sessions</h2>
        <p>An instance has no status. Its sessions are ordered by their UUIDv7 directory names, whose timestamp is each session's start; the <strong>newest</strong> session is the only one that may still grow, and every older one was ended by a newer one. See <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link> for the details.</p>

        <p>When retention (or a manual delete) removes an instance's last session, the instance's directory goes with it, so an instance simply disappears from the list rather than lingering in an expired state.</p>

        <h2 id="instances-column">Instances and Sessions Columns</h2>
        <p>The <strong>Instances</strong> column lists each instance by its name (the instance's folder) and when it started (when the folder was created, as the share lists it &mdash; not listed on an NFS share, which lists no times), newest first. Microscope shows no counts or aggregates here: no session count, no last data and no storage size. Use the filter box to narrow the list by name; pick an instance to list its sessions.</p>
        <p>The <strong>Sessions</strong> column names an instance's sessions without reading them, ten at a time: its ten newest sessions, and <strong>Load more</strong> under the list reads the next ten older ones. Each entry shows the session's id and start, both from its UUIDv7 folder name, and a <strong>latest</strong> badge on the instance's newest session. A session is a visible folder named with a UUIDv7 &mdash; anything else under the instance is ignored.</p>
        <p>Every column loads one level of the remote's tree when its parent is picked: the instance list carries no sessions, and the session list reads that instance and a page of its sessions by name only. Nothing reloads on its own &mdash; on the Azure file share every read is a round-trip to the share &mdash; until the refresh button in the page header reads the picked path again.</p>
        <p>There are no description files to read: the provisioner writes no marker anywhere on the volume, so every id Microscope shows is a folder name and the page asks the share only for folder listings and sizes. The reads of one listing go out up to eight at a time.</p>

        <h2 id="session-drawer">The Session Drawer</h2>
        <p>Click a session to open it in a drawer over the Instances and Sessions columns; the Workspaces and Projects columns stay in view and clickable beside it. The drawer reads the session on its own, once, when it opens:</p>
        <ul>
          <li><strong>Header</strong> &mdash; the session's id, whether it is the instance's newest (and so may still grow), and its path on the share. The up and down arrows step to the instance's newer and older sessions; <strong>&times;</strong> or <kbd>Esc</kbd> closes the drawer.</li>
          <li><strong>Figures</strong> &mdash; when it started, its last data and its duration, plus its file count and size.</li>
          <li><strong>Files</strong> &mdash; one folder per file type, even for a single file: <strong>JFR Recordings</strong> first, newest chunk first, with rotated files folded under their chunk, then heap dumps, perf counters, logs and other artifacts. Open a folder to see its files under their own names; each file downloads on its own.</li>
          <li><strong>Download session</strong> &mdash; hands the whole session to the download assistant. <strong>Select files</strong> picks some files instead; the chunks picked must be an unbroken run, and a gap greys out <strong>Download selected</strong> and says which chunk is missing.</li>
          <li><strong>Delete</strong> &mdash; removes the session, after a confirmation. The newest session of an instance may still grow, so it cannot be deleted, and a read-only remote offers no delete at all.</li>
        </ul>
        <p>A session that a newer one ended before it wrote any data says so instead of listing files: usually a JVM that failed to start or a restarted container. Its sizes stay as they were read until the drawer's refresh button reads the session again.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
