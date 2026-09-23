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
import DocsArchDiagram from '@/components/docs/DocsArchDiagram.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'role', text: 'Role of the Hub', level: 2 },
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
        Microscope; the hub's job is to collect, organize, and serve.
      </p>

      <h2 id="role">Role of the Hub</h2>
      <p>
        Hub runs as a long-lived process next to your Java fleet. Async-Profiler running
        inside your applications writes JFR chunks to the shared volume on every recording loop;
        the hub discovers them there, catalogues them in place and serves them over gRPC. The
        profiler gets its configuration (event toggles, sampling intervals, output paths) from the
        provisioner that started the JVM — its <code>profiler-command</code> setting or the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable.
      </p>

      <DocsCallout type="info">
        <strong>Full profile analysis stays in Microscope.</strong> Flame graphs and heap dump
        forensics and the MCP endpoint run in Microscope. Hub never reads a recording itself — it
        serves the files, and <router-link to="/docs/microscope-mcp/tools#hubs">Microscope’s Hub tools</router-link>
        pull them in for analysis.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        The hub's domain breaks into four layers: domain entities, collection features, gRPC
        services, and storage.
      </p>

      <DocsArchDiagram variant="hub" />

      <p>
        Around the hub, the <router-link to="/docs/provisioner/overview">Jeffrey Provisioner</router-link>
        prepares the Async-Profiler agent's JVM arguments before the application starts, and the
        profiled application writes JFR recordings to the shared filesystem the hub reads from. The
        <router-link to="/docs/jib">Jeffrey JIB</router-link> module packages the provisioner
        and async-profiler into your application image.
      </p>

      <h2 id="grpc-surface">gRPC Service Surface</h2>
      <p>
        Microscope clients and other server-side tools talk to the hub over gRPC. Five services
        cover the surface:
      </p>

      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-collection"></i> Workspaces</div>
        <div class="service-chip"><i class="bi bi-folder"></i> Projects</div>
        <div class="service-chip"><i class="bi bi-hdd-network"></i> Instances</div>
        <div class="service-chip"><i class="bi bi-cloud-download"></i> Recording Download</div>
        <div class="service-chip"><i class="bi bi-archive"></i> Repository</div>
      </div>

      <p>
        A session's files stream on demand from the Hub when a client asks for them — every chunk,
        or only the ones covering a window — and the client assembles the recording. See the
        <router-link to="/docs/hub/grpc-api">gRPC API reference</router-link> for the full
        service surface.
      </p>

      <h2 id="microscope-connection">How Microscope Connects</h2>
      <p>
        Microscope adds a hub with a single gRPC endpoint and an optional auth token, and
        Microscope handles the rest. From the user's point of view, the connected hub looks
        identical to a local one — same workspaces, projects, instances, sessions — except
        recordings are pulled on demand from the hub when the user opens them. See the
        <router-link to="/docs/microscope/workspaces">Microscope Workspaces</router-link> page
        for the client-side flow.
      </p>

      <h2 id="storage">Storage</h2>
      <p>
        Hub stores its catalog (workspaces, projects, instances, sessions)
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
