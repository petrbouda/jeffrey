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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'keys', text: 'Secret Keys', level: 2 },
  { id: 'in-app', text: 'In-App Settings (Recommended)', level: 2 },
  { id: 'env-vars', text: 'Environment Variables', level: 2 },
  { id: 'cli-arg', text: 'Command-Line Argument', level: 2 },
  { id: 'security', text: 'Security Best Practices', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const envVarExample = `# Equivalent property: jeffrey.microscope.ai.api-key
export JEFFREY_MICROSCOPE_AI_API_KEY=sk-ant-api03-...

java -jar microscope.jar`;

const cliArgExample = `java -jar microscope.jar \\
  --jeffrey.microscope.ai.api-key=sk-ant-api03-...`;
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
        Jeffrey Microscope only has one kind of secret today — the AI provider's API key.
        It can be supplied through any standard Spring Boot configuration source, or
        managed inside the Microscope UI where it is encrypted at rest in the embedded DuckDB.
      </p>

      <DocsCallout type="info">
        <strong>Microscope-only:</strong> Jeffrey Server has no concept of API keys. Secrets only
        apply when the AI features are enabled in Microscope.
      </DocsCallout>

      <h2 id="keys">Secret Keys</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.ai.api-key</code></td>
            <td>
              API key for your chosen AI provider. Anthropic:
              <a href="https://console.anthropic.com/" target="_blank">console.anthropic.com</a>.
              OpenAI:
              <a href="https://platform.openai.com/" target="_blank">platform.openai.com</a>.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="in-app">In-App Settings (Recommended)</h2>
      <p>
        Open Microscope and use the in-app Settings to enter the API key. The value is encrypted
        before being stored in the embedded DuckDB database, then transparently decrypted and
        injected into the Spring environment on the next startup. This avoids leaking the key into
        process listings, environment dumps, or shell history.
      </p>

      <h2 id="env-vars">Environment Variables</h2>
      <p>
        Spring Boot relaxed binding maps every property to an uppercase, underscore-separated
        environment variable. This is the simplest option for containers and CI:
      </p>
      <DocsCodeBlock language="bash" :code="envVarExample" />

      <h2 id="cli-arg">Command-Line Argument</h2>
      <p>
        Useful for one-off testing — note that the value will be visible to anyone who can list
        processes on the host:
      </p>
      <DocsCodeBlock language="bash" :code="cliArgExample" />

      <h2 id="security">Security Best Practices</h2>
      <ul>
        <li><strong>Prefer the in-app Settings or env vars</strong> — never check API keys into <code>application.properties</code> in version control.</li>
        <li><strong>Use a Kubernetes Secret</strong> mounted as an env var when running in a cluster; never bake the key into a container image.</li>
        <li><strong>Rotate keys regularly</strong> and immediately when team members leave.</li>
        <li><strong>Use separate keys</strong> for development and production so quotas and audit logs stay distinct.</li>
        <li><strong>Audit usage</strong> through your provider's dashboard.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Container deployments:</strong> mount the key as an env var via a Kubernetes
        Secret or Docker secret. Then either point Microscope's home directory at a persistent
        volume (so the in-app Settings save survives restarts) or rely on the env var on each start.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
