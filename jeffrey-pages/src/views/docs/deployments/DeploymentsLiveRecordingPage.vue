<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'architecture', text: 'Architecture', level: 2 },
  { id: 'jeffrey-configuration', text: 'Jeffrey Configuration', level: 2 },
  { id: 'target-application', text: 'Target Application Setup', level: 2 },
  { id: 'workflow', text: 'Workflow', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const jeffreyConfig = `# application.properties for Jeffrey

server.port=8585
jeffrey.home.dir=/data/jeffrey

# Enable scheduler for automatic recording collection
jeffrey.job.scheduler.enabled=true
jeffrey.job.default.period=1m

# Recording storage - where recordings are collected
jeffrey.project.recording-storage.path=\${jeffrey.home.dir}/recordings

# Global profiler settings for target applications
jeffrey.profiler.global-settings.create-if-not-exists=true
jeffrey.profiler.global-settings.command=-agentpath:<<JEFFREY_PROFILER_PATH>>=start,alloc,lock,event=ctimer,loop=15m,chunksize=5m,file=<<JEFFREY_CURRENT_SESSION>>/profile-%t.jfr`;

const cliConfig = `# jeffrey-init.conf for target application

jeffrey-home = "/data/jeffrey"
profiler-path = "/opt/async-profiler/libasyncProfiler.so"

workspace-id = "production"
project-name = "my-service"
project-label = "My Service"

perf-counters { enabled = true }
heap-dump { enabled = true, type = "crash" }

jdk-java-options {
  enabled = true
  additional-options = "-Xmx2g -Xms2g"
}

attributes {
  env = "production"
  cluster = "blue"
}`;

const targetStartup = `# Initialize profiling and start application
eval "$(java -jar jeffrey-cli.jar init /path/to/jeffrey-init.conf)"
java -jar my-application.jar`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Deployments</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Live with Recording Collection</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-broadcast-pin"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Live with Recording Collection</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Deploy Jeffrey in <strong>Live mode</strong> to continuously collect recordings from profiled applications. This setup is ideal for production environments where you want ongoing profiling data.</p>

        <h2 id="overview">Overview</h2>
        <p>In this deployment model:</p>
        <ul>
          <li><strong>Jeffrey</strong> runs as a central server collecting and analyzing recordings</li>
          <li><strong>Target applications</strong> use Jeffrey CLI to write recordings to a shared storage</li>
          <li><strong>Scheduler</strong> automatically discovers and imports new recordings</li>
        </ul>

        <DocsCallout type="info">
          <strong>Live Workspace:</strong> A Live workspace monitors a directory for new recording sessions and automatically imports them as they appear.
        </DocsCallout>

        <h2 id="architecture">Architecture</h2>
        <div class="architecture-diagram">
          <pre><code>┌─────────────────────┐     ┌─────────────────────┐
│  Target App 1       │     │  Target App 2       │
│  + Jeffrey CLI      │     │  + Jeffrey CLI      │
└─────────┬───────────┘     └─────────┬───────────┘
          │                           │
          │    writes recordings      │
          ▼                           ▼
┌─────────────────────────────────────────────────┐
│              Shared Storage (NFS/PVC)           │
│  /recordings/workspace/project/session/*.jfr    │
└─────────────────────────────────────────────────┘
                      ▲
                      │ monitors & imports
                      │
┌─────────────────────┴───────────────────────────┐
│                   Jeffrey                       │
│  - Live Workspace configured                    │
│  - Scheduler polls for new sessions             │
│  - Auto-creates profiles from recordings        │
└─────────────────────────────────────────────────┘</code></pre>
        </div>

        <h2 id="jeffrey-configuration">Jeffrey Configuration</h2>
        <p>Configure Jeffrey to monitor the recording storage directory:</p>
        <DocsCodeBlock
          language="properties"
          :code="jeffreyConfig"
        />

        <h3>Key Settings</h3>
        <ul>
          <li><strong>Scheduler</strong> - Must be enabled to poll for new recordings</li>
          <li><strong>Recording storage</strong> - Path where target applications write their recordings</li>
          <li><strong>Profiler settings</strong> - Default settings synced to target applications</li>
        </ul>

        <h2 id="target-application">Target Application Setup</h2>
        <p>Each application that should be profiled needs Jeffrey CLI configuration:</p>
        <DocsCodeBlock
          language="hocon"
          :code="cliConfig"
        />

        <h3>Starting the Application</h3>
        <DocsCodeBlock
          language="bash"
          :code="targetStartup"
        />

        <DocsCallout type="tip">
          <strong>Container deployment:</strong> In Kubernetes or Docker, mount the shared storage volume to both Jeffrey and target application containers. See the <router-link to="/docs/deployments/kubernetes">Kubernetes deployment guide</router-link> for a complete example.
        </DocsCallout>

        <h2 id="workflow">Workflow</h2>
        <ol>
          <li><strong>Application starts</strong> - Jeffrey CLI creates session directory and configures profiling</li>
          <li><strong>Recordings generated</strong> - Async-Profiler writes JFR chunks to the session directory</li>
          <li><strong>Jeffrey discovers</strong> - Scheduler detects new session via <code>.session-info.json</code></li>
          <li><strong>Profile created</strong> - Jeffrey imports recordings and creates analyzable profiles</li>
          <li><strong>Analysis available</strong> - Flamegraphs, timeseries, and other features ready to use</li>
        </ol>

        <DocsCallout type="warning">
          <strong>Storage considerations:</strong> Ensure sufficient disk space for recordings. Configure session cleanup in the Scheduler to automatically remove old sessions.
        </DocsCallout>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.architecture-diagram {
  margin: 1.5rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.architecture-diagram pre {
  margin: 0;
  padding: 1.5rem;
  background: #1e293b;
  overflow-x: auto;
}

.architecture-diagram code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8rem;
  color: #e2e8f0;
  line-height: 1.4;
}
</style>
