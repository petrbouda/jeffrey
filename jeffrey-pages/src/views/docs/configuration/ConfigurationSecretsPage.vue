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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

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

const secretsExample = `# AI API Key (Anthropic or OpenAI, depending on jeffrey.ai.provider)
jeffrey.ai.api-key=sk-ant-api03-...`;

const envVarExample = `# Set API key via environment variable
export JEFFREY_AI_API_KEY=sk-ant-api03-...

# Start Jeffrey
java -jar jeffrey.jar`;

const gitignoreExample = `# Jeffrey secrets
secrets.properties
*.secrets
.env`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Secrets"
      icon="bi bi-key"
    />

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
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.ai.api-key</code></td>
            <td>API key for your chosen provider. Anthropic: <a href="https://console.anthropic.com/" target="_blank">console.anthropic.com</a>, OpenAI: <a href="https://platform.openai.com/" target="_blank">platform.openai.com</a></td>
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

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
