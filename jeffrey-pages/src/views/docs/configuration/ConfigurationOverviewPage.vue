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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'zero-configuration', text: 'Zero Configuration', level: 2 },
  { id: 'configuration-precedence', text: 'Configuration Precedence', level: 2 },
  { id: 'overriding-properties', text: 'Overriding Properties', level: 2 },
  { id: 'external-configuration', text: 'External Configuration Files', level: 2 },
  { id: 'configuration-files', text: 'Configuration Files Reference', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const envVarExample = `# Override server port
export SERVER_PORT=9090

# Override Jeffrey home directory
export JEFFREY_HOME_DIR=/data/jeffrey

# Start Jeffrey
java -jar jeffrey.jar`;

const cmdLineExample = `# Override server port
java -jar jeffrey.jar --server.port=9090

# Override home directory
java -jar jeffrey.jar -Djeffrey.home.dir=/data/jeffrey

# Multiple overrides
java -jar jeffrey.jar --server.port=9090 -Djeffrey.home.dir=/data/jeffrey`;

const configLocationExample = `# Use a specific configuration file
java -jar jeffrey.jar --spring.config.location=file:/mnt/config/application.properties

# Use multiple locations (comma-separated)
java -jar jeffrey.jar --spring.config.location=file:/mnt/config/,classpath:/

# Add additional location (keeps defaults)
java -jar jeffrey.jar --spring.config.additional-location=file:/mnt/config/`;

const configImportExample = `# Import additional properties file
spring.config.import=optional:file:/path/to/custom.properties

# Import from classpath
spring.config.import=optional:classpath:custom.properties

# Import multiple files
spring.config.import=optional:file:/path/to/secrets.properties,optional:file:/path/to/advanced.properties`;
</script>

<template>
  <article class="docs-article">
    <nav class="docs-breadcrumb">
      <router-link to="/docs" class="breadcrumb-item">
        <i class="bi bi-book me-1"></i>Docs
      </router-link>
      <span class="breadcrumb-separator">/</span>
      <span class="breadcrumb-item">Configuration</span>
      <span class="breadcrumb-separator">/</span>
      <span class="breadcrumb-item active">Overview</span>
    </nav>

    <header class="docs-header">
      <div class="header-icon">
        <i class="bi bi-gear"></i>
      </div>
      <div class="header-content">
        <h1 class="docs-title">Configuration Overview</h1>
        <p class="docs-subtitle">Configure Jeffrey to match your environment and requirements</p>
      </div>
    </header>

    <div class="docs-content">
      <h2 id="zero-configuration">Zero Configuration</h2>
      <p>
        Jeffrey is designed to work out of the box with sensible defaults. You can start Jeffrey without any
        configuration files, and it will use built-in defaults for all settings.
      </p>

      <DocsCallout type="info">
        <strong>Ready to Run:</strong> All configuration properties have code defaults. Jeffrey starts
        without any configuration files, using <code>~/.jeffrey</code> as the home directory and
        port <code>8080</code> for the web interface.
      </DocsCallout>

      <h2 id="configuration-precedence">Configuration Precedence</h2>
      <p>
        Jeffrey follows Spring Boot's configuration precedence. Properties are resolved in this order
        (later sources override earlier ones):
      </p>

      <ol>
        <li><strong>Code Defaults</strong> - Built-in default values in the application code</li>
        <li><strong>application.properties</strong> - Properties file in the classpath or working directory</li>
        <li><strong>Environment Variables</strong> - System environment variables (e.g., <code>SERVER_PORT</code>)</li>
        <li><strong>Command-line Arguments</strong> - Arguments passed at startup (e.g., <code>--server.port=9090</code>)</li>
      </ol>

      <h2 id="overriding-properties">Overriding Properties</h2>

      <h3>Using Environment Variables</h3>
      <p>
        Environment variables follow a naming convention: replace dots with underscores and use uppercase.
        For example, <code>server.port</code> becomes <code>SERVER_PORT</code>.
      </p>
      <DocsCodeBlock language="bash" :code="envVarExample" />

      <h3>Using Command-line Arguments</h3>
      <p>
        Properties can be passed as command-line arguments using <code>--property=value</code> or
        <code>-Dproperty=value</code> syntax.
      </p>
      <DocsCodeBlock language="bash" :code="cmdLineExample" />

      <h2 id="external-configuration">External Configuration Files</h2>

      <h3>Using --spring.config.location</h3>
      <p>
        You can specify an external configuration file location at startup. This is useful for
        containerized deployments or when you want to keep configuration outside the application.
      </p>
      <DocsCodeBlock language="bash" :code="configLocationExample" />

      <h3>Using spring.config.import</h3>
      <p>
        You can import additional configuration files from within your <code>application.properties</code>.
        The <code>optional:</code> prefix means the application won't fail if the file doesn't exist.
      </p>
      <DocsCodeBlock language="properties" :code="configImportExample" />

      <h2 id="configuration-files">Configuration Files Reference</h2>
      <p>
        Jeffrey organizes configuration into three logical files. All properties have code defaults,
        so these files are optional:
      </p>

      <div class="config-files-grid">
        <router-link to="/docs/configuration/application-properties" class="config-file-card">
          <div class="card-icon">
            <i class="bi bi-file-earmark-text"></i>
          </div>
          <div class="card-content">
            <h4>application.properties</h4>
            <p>Essential settings like server port, home directory, and AI configuration.</p>
          </div>
        </router-link>

        <router-link to="/docs/configuration/advanced-properties" class="config-file-card">
          <div class="card-icon">
            <i class="bi bi-sliders"></i>
          </div>
          <div class="card-content">
            <h4>advanced.properties</h4>
            <p>Tuning options for jobs, database, logging, and other advanced settings.</p>
          </div>
        </router-link>

        <router-link to="/docs/configuration/secrets" class="config-file-card">
          <div class="card-icon">
            <i class="bi bi-key"></i>
          </div>
          <div class="card-content">
            <h4>secrets.properties</h4>
            <p>Sensitive data like API keys. Should not be committed to version control.</p>
          </div>
        </router-link>
      </div>
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

.config-files-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
  margin-top: 1.5rem;
}

.config-file-card {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.config-file-card:hover {
  border-color: #0d6efd;
  box-shadow: 0 4px 12px rgba(13, 110, 253, 0.15);
  transform: translateY(-2px);
}

.config-file-card .card-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0d6efd 0%, #0a58ca 100%);
  border-radius: 10px;
  color: white;
  font-size: 1.25rem;
}

.config-file-card .card-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #212529;
}

.config-file-card .card-content p {
  margin: 0;
  font-size: 0.875rem;
  color: #6c757d;
  line-height: 1.5;
}
</style>
