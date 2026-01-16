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
  { id: 'config-file', text: 'Configuration File', level: 2 },
  { id: 'configuration-options', text: 'Configuration Options', level: 2 },
  { id: 'features', text: 'Features', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const minimalConfig = `jeffrey-home = "/opt/jeffrey"
workspace-id = "production"
project-name = "my-service"`;

const fullConfig = `jeffrey-home = "/opt/jeffrey"
profiler-path = "/opt/async-profiler/libasyncProfiler.so"
workspace-id = "production"
project-name = "my-service"
project-label = "My Service"
attributes { cluster = "blue", namespace = "production" }

perf-counters { enabled = true }
heap-dump { enabled = true, type = "crash" }
jvm-logging {
  enabled = true
  command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log::filecount=3,filesize=5m"
}
messaging { enabled = true, max-age = "24h" }
jdk-java-options { enabled = true, additional-options = "-Xmx2g -Xms2g" }`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">CLI</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Configuration</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-gear"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Configuration</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey CLI uses <strong>HOCON</strong> (Human-Optimized Config Object Notation) for configuration. HOCON is a superset of JSON with more readable syntax.</p>

        <h2 id="config-file">Configuration File</h2>

        <h3>Minimal Configuration</h3>
        <p>The minimum required settings to start profiling:</p>
        <DocsCodeBlock
          language="hocon"
          :code="minimalConfig"
        />

        <h3>Full Configuration</h3>
        <p>A complete configuration with all features enabled:</p>
        <DocsCodeBlock
          language="hocon"
          :code="fullConfig"
        />

        <DocsCallout type="tip">
          <strong>Environment variables:</strong> HOCON supports environment variable substitution. Use <code>${VAR}</code> for required variables or <code>${?VAR}</code> for optional ones.
        </DocsCallout>

        <h2 id="configuration-options">Configuration Options</h2>
        <table>
          <thead>
            <tr>
              <th>Option</th>
              <th>Required</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>jeffrey-home</code></td>
              <td>Yes</td>
              <td>Base directory for Jeffrey data</td>
            </tr>
            <tr>
              <td><code>workspace-id</code></td>
              <td>Yes</td>
              <td>Workspace identifier (e.g., "production", "staging")</td>
            </tr>
            <tr>
              <td><code>project-name</code></td>
              <td>Yes</td>
              <td>Project name for organizing recordings</td>
            </tr>
            <tr>
              <td><code>profiler-path</code></td>
              <td>No</td>
              <td>Path to libasyncProfiler.so</td>
            </tr>
            <tr>
              <td><code>project-label</code></td>
              <td>No</td>
              <td>Human-readable project label</td>
            </tr>
            <tr>
              <td><code>attributes</code></td>
              <td>No</td>
              <td>Custom key-value metadata (e.g., cluster, namespace)</td>
            </tr>
          </tbody>
        </table>

        <h2 id="features">Features</h2>
        <p>Jeffrey CLI can enable additional features that capture more diagnostic data alongside JFR recordings:</p>

        <div class="features-grid">
          <div class="feature-card perf">
            <div class="feature-icon"><i class="bi bi-speedometer2"></i></div>
            <h4>Perf Counters</h4>
            <p>Captures JVM performance data via <code>-XX:+UsePerfData</code>. Provides low-level metrics about JVM internals.</p>
            <code>perf-counters { enabled = true }</code>
          </div>
          <div class="feature-card heap">
            <div class="feature-icon"><i class="bi bi-memory"></i></div>
            <h4>Heap Dump</h4>
            <p>Automatic heap dumps on OutOfMemoryError or JVM crash. Compressed with gzip for efficient storage.</p>
            <code>heap-dump { enabled = true; type = "crash" }</code>
          </div>
          <div class="feature-card logging">
            <div class="feature-icon"><i class="bi bi-file-text"></i></div>
            <h4>JVM Logging</h4>
            <p>Structured JVM diagnostic logging including GC events, JIT compilation, and JFR activity.</p>
            <code>jvm-logging { enabled = true }</code>
          </div>
          <div class="feature-card messaging">
            <div class="feature-icon"><i class="bi bi-broadcast"></i></div>
            <h4>Messaging</h4>
            <p>Real-time JFR event streaming for live monitoring. Events are stored in a streaming repository.</p>
            <code>messaging { enabled = true, max-age = "24h" }</code>
          </div>
          <div class="feature-card jdk-options">
            <div class="feature-icon"><i class="bi bi-gear-wide-connected"></i></div>
            <h4>JDK Java Options</h4>
            <p>Sets <code>JDK_JAVA_OPTIONS</code> environment variable with all JVM flags. The JVM picks this up automatically.</p>
            <code>jdk-java-options { enabled = true, additional-options = "-Xmx2g" }</code>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Path placeholders:</strong> Use <code>&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;</code> in configuration values to reference the session directory path. This is replaced at runtime with the actual path.
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

/* Features Grid */
.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.feature-card {
  padding: 1.25rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.feature-card .feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: #fff;
  margin-bottom: 0.75rem;
}

.feature-card h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.feature-card p {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

.feature-card code {
  display: block;
  font-size: 0.75rem;
  background: #f8fafc;
  padding: 0.5rem;
  border-radius: 4px;
  color: #6c757d;
}

/* Feature card themes */
.feature-card.perf .feature-icon {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
}

.feature-card.heap .feature-icon {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
}

.feature-card.logging .feature-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.feature-card.messaging .feature-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.feature-card.jdk-options .feature-icon {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
}

/* Responsive */
@media (max-width: 768px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
