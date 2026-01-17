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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'ai-api-keys', text: 'AI Provider API Keys', level: 2 },
  { id: 'usage', text: 'Usage', level: 2 },
  { id: 'security', text: 'Security Best Practices', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const secretsExample = `# Anthropic Claude API Key
spring.ai.anthropic.api-key=sk-ant-api03-...

# Or OpenAI API Key (if using OpenAI provider)
# spring.ai.openai.api-key=sk-...`;

const envVarExample = `# Set API key via environment variable
export SPRING_AI_ANTHROPIC_API_KEY=sk-ant-api03-...

# Start Jeffrey
java -jar jeffrey.jar`;

const gitignoreExample = `# Jeffrey secrets
secrets.properties
*.secrets
.env`;
</script>

<template>
  <article class="docs-article">
    <nav class="docs-breadcrumb">
      <router-link to="/docs" class="breadcrumb-item">
        <i class="bi bi-book me-1"></i>Docs
      </router-link>
      <span class="breadcrumb-separator">/</span>
      <router-link to="/docs/configuration/overview" class="breadcrumb-item">Configuration</router-link>
      <span class="breadcrumb-separator">/</span>
      <span class="breadcrumb-item active">Secrets</span>
    </nav>

    <header class="docs-header">
      <div class="header-icon">
        <i class="bi bi-key"></i>
      </div>
      <div class="header-content">
        <h1 class="docs-title">Secrets</h1>
        <p class="docs-subtitle">Sensitive configuration like API keys</p>
      </div>
    </header>

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The <code>secrets.properties</code> file stores sensitive configuration such as API keys.
        This file should never be committed to version control.
      </p>

      <DocsCallout type="warning">
        <strong>Security Warning:</strong> Never commit secrets to version control. Always add
        <code>secrets.properties</code> to your <code>.gitignore</code> file.
      </DocsCallout>

      <h2 id="ai-api-keys">AI Provider API Keys</h2>
      <p>
        API keys for the AI assistant feature. Configure the key matching your chosen provider
        in <router-link to="/docs/configuration/application-properties">application.properties</router-link>.
      </p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Provider</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>spring.ai.anthropic.api-key</code></td>
            <td>Anthropic</td>
            <td>API key for Claude models. Get one at <a href="https://console.anthropic.com/" target="_blank">console.anthropic.com</a></td>
          </tr>
          <tr>
            <td><code>spring.ai.openai.api-key</code></td>
            <td>OpenAI</td>
            <td>API key for GPT models. Get one at <a href="https://platform.openai.com/" target="_blank">platform.openai.com</a></td>
          </tr>
        </tbody>
      </table>

      <h2 id="usage">Usage</h2>

      <h3>Using a secrets.properties File</h3>
      <p>Create a file named <code>secrets.properties</code> in your configuration directory:</p>
      <DocsCodeBlock language="properties" :code="secretsExample" />

      <p>
        Jeffrey automatically imports this file if present. The import is configured in
        <code>application.properties</code> with <code>optional:</code> prefix, so the
        application starts even if the file doesn't exist.
      </p>

      <h3>Using Environment Variables</h3>
      <p>
        For containerized deployments, you can pass API keys as environment variables instead
        of using a properties file:
      </p>
      <DocsCodeBlock language="bash" :code="envVarExample" />

      <h2 id="security">Security Best Practices</h2>

      <h3>Git Ignore</h3>
      <p>Add secrets files to your <code>.gitignore</code>:</p>
      <DocsCodeBlock language="text" :code="gitignoreExample" />

      <h3>Recommendations</h3>
      <ul>
        <li><strong>Use environment variables</strong> in production environments</li>
        <li><strong>Rotate API keys</strong> regularly and when team members leave</li>
        <li><strong>Use separate keys</strong> for development and production</li>
        <li><strong>Consider secret management tools</strong> like HashiCorp Vault or AWS Secrets Manager for enterprise deployments</li>
        <li><strong>Audit API key usage</strong> through your provider's dashboard</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Container Deployments:</strong> In Kubernetes, use Secrets objects to inject
        API keys as environment variables. Never bake secrets into container images.
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
</style>
