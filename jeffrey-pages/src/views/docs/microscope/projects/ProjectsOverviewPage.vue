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
  { id: 'what-are-projects', text: 'What are Projects?', level: 2 },
  { id: 'browsing-projects', text: 'Browsing a Project', level: 2 },
  { id: 'deleting-projects', text: 'Deleting a Project', level: 2 },
  { id: 'creating-projects', text: 'Creating Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Projects"
      icon="bi bi-kanban"
    />

    <div class="docs-content">
      <p>A <strong>project</strong> is a folder inside a workspace that groups everything related to a single application or service — its JVM instances and recording sessions.</p>

      <h2 id="what-are-projects">What are Projects?</h2>
      <p>You typically create one project per application or environment so the data stays organised:</p>
      <ul>
        <li>One project per microservice in your architecture.</li>
        <li>One project per environment — <em>dev</em>, <em>staging</em>, <em>production</em>.</li>
        <li>One project per profiling scenario — <em>perf-test</em>, <em>baseline</em>, <em>incident-2026-04</em>.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Just want to analyse a JFR file?</strong> Skip projects entirely — drop the file on the global
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page.
        Projects are for staying connected to live applications through the remote (the Azure file share that holds the recording volume).
      </DocsCallout>

      <h2 id="browsing-projects">Browsing a Project</h2>
      <p>
        A project has no page of its own: it is browsed on the
        <router-link to="/docs/microscope/workspaces">Remote</router-link> page, whose columns follow
        the share's folders &mdash; <strong>Workspaces</strong>, <strong>Projects</strong>,
        <strong>Instances</strong>, <strong>Sessions</strong>. Pick a workspace and its projects are
        listed with their names and the folder's creation date; a workspace holding a single project
        opens it straight away. Pick a project and its
        <router-link to="/docs/microscope/projects/instances">instances</router-link> are listed, with
        the newest one picked and its sessions listed beside it. The Recordings and Profiler Builder
        tabs stay in reach the whole time.
      </p>
      <p>
        The page's address follows what is picked
        (<code>/remote?workspaceId=…&amp;projectId=…&amp;instanceId=…&amp;sessionId=…</code>), so a link
        opens the same project, instance or session. The address of the former project pages
        (<code>/remote/workspaces/&lt;workspace&gt;/projects/&lt;project&gt;/…</code>) still works and
        leads to the same place on the Remote page.
      </p>

      <DocsCallout type="info">
        <strong>Where did "Profiles", "Recordings", and "Repository" go?</strong>
        Profiles and recordings live globally on the
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page, regardless of project.
        A session's files and sizes are shown when the session is opened; see
        <router-link to="/docs/microscope/projects/instances">Instances</router-link>.
      </DocsCallout>

      <h2 id="deleting-projects">Deleting a Project</h2>
      <p>
        A project's name is its folder on the share, given by the provisioner, and it is also the
        project's id, so a project cannot be renamed. The picked project carries a
        <strong>delete</strong> button in the Projects column; a confirmation step protects against
        accidents. Deleting is final: the remote removes the project's directory on the workspace
        volume, and there is no restore. A read-only remote shows no delete button.
      </p>

      <h2 id="creating-projects">Creating Projects</h2>
      <p>
        Projects are not created in Microscope. A project appears when a JVM started by the
        <router-link to="/docs/provisioner/overview">Jeffrey Provisioner</router-link> creates its folder &mdash;
        named by the project <code>name</code> the provisioner was configured with, which is also the project's id:
        the remote is read from the shared filesystem on every request, so the next time
        Microscope syncs with the remote the project shows up under its workspace, together with
        its instances and recording sessions.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

</style>
