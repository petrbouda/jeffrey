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
import DocsArchDiagram from '@/components/docs/DocsArchDiagram.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'role', text: 'Role of the Server', level: 2 },
  { id: 'components', text: 'Components', level: 2 },
  { id: 'grpc-surface', text: 'gRPC Service Surface', level: 2 },
  { id: 'microscope-connection', text: 'How Microscope Connects', level: 2 },
  { id: 'storage', text: 'Storage', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Architecture"
      icon="bi bi-diagram-3"
    />

    <div class="docs-content">
      <p>
        Jeffrey Hub is a headless, multi-workspace recording-collection backend. It catalogs
        what's running (workspaces, projects, instances, recording sessions) in an embedded
        DuckDB and stores the JFR recordings on a shared filesystem. Analysis happens in
        Microscope; Server's job is to collect, organize, and serve.
      </p>

      <h2 id="role">Role of the Server</h2>
      <p>
        Server runs as a long-lived process next to your Java fleet. Async-Profiler agents
        running inside your applications stream JFR chunks to it on every recording loop. The
        agent gets its configuration (event toggles, sampling intervals, output paths) from
        Server's profiler-settings hub, so a single change at the workspace or project scope
        propagates to every agent in that scope.
      </p>

      <DocsCallout type="info">
        <strong>No analysis on Server.</strong> Flame graphs, heap dump
        forensics, and the AI assistant all live in Microscope. Server only collects and serves
        — that keeps expensive profile processing off your production infrastructure and on
        developer machines.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        Server's domain breaks into four layers: domain entities, collection features, gRPC
        services, and storage.
      </p>

      <DocsArchDiagram variant="hub" />

      <p>
        Around the Server, the <router-link to="/docs/provisioner/overview">Jeffrey Provisioner</router-link>
        prepares the Async-Profiler agent's JVM arguments before the application starts, and the
        <router-link to="/docs/agent/overview">Jeffrey Agent</router-link> writes JFR recordings
        to the shared filesystem the Server reads from. The
        <router-link to="/docs/jib">Jeffrey JIB</router-link> module packages the provisioner
        and Agent alongside your application image.
      </p>

      <h2 id="grpc-surface">gRPC Service Surface</h2>
      <p>
        Microscope clients and other server-side tools talk to Server over gRPC. Eight services
        cover the surface:
      </p>

      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-collection"></i> Workspaces</div>
        <div class="service-chip"><i class="bi bi-folder"></i> Projects</div>
        <div class="service-chip"><i class="bi bi-hdd-network"></i> Instances</div>
        <div class="service-chip"><i class="bi bi-cloud-download"></i> Recording Download</div>
        <div class="service-chip"><i class="bi bi-archive"></i> Repository</div>
        <div class="service-chip"><i class="bi bi-broadcast"></i> Event Streaming</div>
        <div class="service-chip"><i class="bi bi-sliders"></i> Profiler Settings</div>
      </div>

      <p>
        Recordings stream on demand from the Server when a client opens them, while live JFR
        events flow over a separate streaming channel for real-time dashboards and incident
        investigations. See the
        <router-link to="/docs/hub/grpc-api">gRPC API reference</router-link> for the full
        service surface.
      </p>

      <h2 id="microscope-connection">How Microscope Connects</h2>
      <p>
        Microscope adds a Server with a single gRPC endpoint and an optional auth token, and
        Microscope handles the rest. From the user's point of view, the connected Server looks
        identical to a local one — same workspaces, projects, instances, sessions — except
        recordings are pulled on demand from the Server when the user opens them. See the
        <router-link to="/docs/microscope/workspaces">Microscope Workspaces</router-link> page
        for the client-side flow.
      </p>

      <h2 id="storage">Storage</h2>
      <p>
        Server stores its catalog (workspaces, projects, instances, sessions, scheduler jobs)
        in a single embedded DuckDB file, and stores the JFR recordings themselves on a shared
        filesystem laid out by workspace and project. There is no managed database, no object
        store, and no per-profile databases. See the
        <router-link to="/docs/hub/storage">Storage</router-link> page for the directory
        layout and design philosophy.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== gRPC service chips ===== */
.service-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 1rem 0 1.25rem;
}

.service-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: #4338ca;
  background: #ede9fe;
  border: 1px solid #c4b5fd;
  border-radius: 6px;
}

.service-chip i { font-size: 0.85rem; }

</style>
