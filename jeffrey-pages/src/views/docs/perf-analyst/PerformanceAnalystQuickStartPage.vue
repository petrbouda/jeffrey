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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'run', text: '1. Run Performance Analyst', level: 2 },
  { id: 'configure-ai', text: '2. Configure an AI Provider', level: 2 },
  { id: 'connect-hub', text: '3. Connect a Hub', level: 2 },
  { id: 'attach-repo', text: '4. Attach a Repository', level: 2 },
  { id: 'generate', text: '5. Generate a Recommendation', level: 2 }
];

const dockerCmd = `docker run -it --rm \\
  -p 8080:8080 \\
  -v "$HOME/.jeffrey-performance-analyst:/root/.jeffrey-performance-analyst" \\
  petrbouda/jeffrey-performance-analyst:latest`;

const jarCmd = `java -jar performance-analyst.jar`;

const aiProps = `# Choose one: claude | chatgpt | ollama | claude-code
jeffrey.performance-analyst.ai.provider=claude
jeffrey.performance-analyst.ai.model=claude-opus-4-8
jeffrey.performance-analyst.ai.max-tokens=4096
# API key lives in secrets.properties (gitignored)
jeffrey.performance-analyst.ai.api-key=\${ANTHROPIC_API_KEY}`;

const claudeCodeProps = `jeffrey.performance-analyst.ai.provider=claude-code
jeffrey.performance-analyst.ai.cli-path=claude
jeffrey.performance-analyst.ai.timeout-seconds=600
jeffrey.performance-analyst.ai.mcp-url=http://127.0.0.1:8080/api/internal/mcp/claude-code`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Quick Start"
      icon="bi bi-rocket-takeoff"
    />

    <div class="docs-content">
      <p>
        Get from zero to your first recommendation: run Performance Analyst, point it at an AI provider,
        connect a Jeffrey Hub, attach the source repository, and generate a recommendation.
      </p>

      <DocsCallout type="info" title="Prerequisites">
        A running <router-link to="/docs/hub">Jeffrey Hub</router-link> with at least one recording, and
        either an AI provider API key or the Claude Code CLI installed locally.
      </DocsCallout>

      <h2 id="run">1. Run Performance Analyst</h2>
      <p>The container is the quickest path. It exposes the UI on port 8080 and persists data to your home directory:</p>
      <DocsCodeBlock :code="dockerCmd" language="bash" />
      <p>Or run the standalone JAR if you build from source:</p>
      <DocsCodeBlock :code="jarCmd" language="bash" />
      <p>Then open <code>http://localhost:8080</code>.</p>

      <h2 id="configure-ai">2. Configure an AI Provider</h2>
      <p>
        Recommendations need an AI backend. Set the provider properties (typically in
        <code>application.properties</code>, with the key in <code>secrets.properties</code>):
      </p>
      <DocsCodeBlock :code="aiProps" language="properties" filename="application.properties" />
      <p>
        Prefer to use your existing Claude subscription with no API key? Use the <strong>Claude Code</strong>
        provider — it drives the CLI headlessly and reaches the repository tools through the built-in MCP
        server:
      </p>
      <DocsCodeBlock :code="claudeCodeProps" language="properties" filename="application.properties" />

      <h2 id="connect-hub">3. Connect a Hub</h2>
      <p>
        In the UI, register your Jeffrey Hub by its address, then browse its workspaces and projects and
        download a recording into a local project. See
        <router-link to="/docs/perf-analyst/hub-connection">Hub Connection</router-link> for the full flow.
      </p>

      <h2 id="attach-repo">4. Attach a Repository</h2>
      <p>
        Open the project's settings and add its <strong>version control system</strong> — the GitHub or
        GitLab repository URL plus credentials. The credentials are encrypted at rest; the AI clones this
        repo read-only when generating a recommendation.
      </p>

      <h2 id="generate">5. Generate a Recommendation</h2>
      <p>
        Pick a downloaded recording and an event type (CPU, allocation, locks, …) and start a recommendation.
        Performance Analyst builds the flamegraph prompt, clones the repo, and runs the model with its
        read-only tools. When it finishes you get:
      </p>
      <ul class="result-list">
        <li><i class="bi bi-bar-chart-steps"></i> A <strong>severity</strong> grade — CRITICAL / HIGH / MEDIUM / LOW.</li>
        <li><i class="bi bi-card-text"></i> A markdown <strong>explanation</strong> mapping the hotspot to specific code.</li>
        <li><i class="bi bi-file-earmark-diff"></i> An optional <strong>unified-diff patch</strong> you can apply directly.</li>
      </ul>
      <p>
        Findings across all projects are ranked by severity in the <strong>Overview</strong>, so the
        highest-impact issues surface first.
      </p>

      <DocsCallout type="tip">
        The flamegraph prompt for a recording + event type is cached. Re-running a recommendation (for
        example after changing the AI model) skips re-parsing the JFR and goes straight to the model.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.result-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 1.25rem;
  display: grid;
  gap: 0.4rem;
}

.result-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.9rem;
  color: #334155;
  line-height: 1.5;
}

.result-list li i {
  color: #059669;
  font-size: 1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}
</style>
