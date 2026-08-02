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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'setup', text: 'Setup', level: 2 },
  { id: 'how-a-run-works', text: 'How a Run Works', level: 2 },
  { id: 'grounding', text: 'Grounding and Severity', level: 2 },
  { id: 'patches', text: 'Patches', level: 2 },
  { id: 'privacy', text: 'What Leaves Your Machine', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Advisor" icon="bi bi-lightbulb" />

    <div class="docs-content">
      <p>
        The Advisor answers the question a flame graph cannot: <strong>what should I change in my
        code?</strong> For one profile and one sample event type, it hands the AI the call tree Jeffrey
        measured plus read-only access to your source folder, and asks it to map the hottest frames to
        real files and propose specific, minimal changes.
      </p>

      <p>
        Everything the model produces is grounded before it is stored. Cited frames are resolved against
        the measured call tree and cited paths against your working copy. Severity is then computed from
        what survived — so a finding the model invented cannot raise a profile's priority.
      </p>

      <h2 id="overview">Overview</h2>

      <p>The Advisor is a top-level mode in the profile detail page, with three pages:</p>

      <ul>
        <li><strong>Overview</strong> — the landing page: set the source folder inline, launch the run, and watch its phased, timed timeline. The finished timeline is kept and re-shown on return.</li>
        <li><strong>Findings</strong> — a separate page for the recommendations report, with the claim list above it.</li>
        <li><strong>Patches</strong> — the proposed code changes, one per event type, each shown as a unified diff with the measured cost of what it removes.</li>
      </ul>

      <p>
        It analyses four profile groups, each folding the event types that answer one question — the
        first present in a recording wins:
      </p>

      <ul>
        <li><strong>CPU</strong> — <code>jdk.ExecutionSample</code></li>
        <li><strong>Wall-Clock</strong> — <code>profiler.WallClockSample</code></li>
        <li><strong>Allocation</strong> — <code>jdk.ObjectAllocationSample</code>, or the older TLAB pair</li>
        <li><strong>Blocking</strong> — <code>jdk.JavaMonitorEnter</code>, <code>jdk.JavaMonitorWait</code>, <code>jdk.ThreadPark</code></li>
      </ul>

      <h2 id="setup">Setup</h2>

      <p>Two things are required:</p>

      <ol>
        <li>
          <strong>An AI provider.</strong> Configure one under Settings → AI Configuration (Claude,
          ChatGPT, Ollama, or the Claude Code CLI in headless mode). Until then the Advisor is greyed
          out, and it lights up as soon as a provider is selected — no restart.
        </li>
        <li>
          <strong>A source folder.</strong> On the Advisor Overview, point the profile at an absolute
          path on the machine running Microscope. Microscope reads your working copy in place: nothing
          is cloned, no credentials are stored, and the folder is never written to or deleted.
        </li>
      </ol>

      <DocsCallout type="info" title="Per profile">
        The source folder is stored per profile, so it works for any profile — including a
        locally-uploaded Quick Analysis recording, which has no project.
      </DocsCallout>

      <h2 id="how-a-run-works">How a Run Works</h2>

      <p>
        The Overview page is the landing: set a source folder and press <strong>Run Advisor</strong>. One
        launch analyzes <strong>every event type at once</strong> — CPU, Wall-Clock, Allocation, Blocking
        — shown as a processing timeline with a <strong>phase card per type</strong>. Each type moves
        through the same five <strong>timed steps</strong>, and the types drain a few at a time through a
        shared ceiling so a single run cannot flood the AI provider:
      </p>

      <ol>
        <li><strong>Prompt</strong> — the flamegraph markdown is built from the profile database and cached, along with the flattened call tree behind it.</li>
        <li><strong>Source</strong> — the configured folder is validated and its commit read.</li>
        <li><strong>Review</strong> — the model explores your source with four read-only tools: <code>listFiles</code>, <code>glob</code>, <code>readFile</code> and <code>grep</code>.</li>
        <li><strong>Verify</strong> — the model's answer is checked: every cited frame is resolved against the measured call tree and severity is computed before anything is stored.</li>
        <li><strong>Patch</strong> — the proposed diff is repaired so it applies cleanly, and the files it touches are checked against the folder. A type where the model proposed no code change finishes this step with nothing to build.</li>
      </ol>

      <p>
        The steps are also where the run's time is accounted for: Review is the only one that calls the
        AI, so it dominates, while Prompt, Verify and Patch are local work measured in milliseconds.
      </p>

      <p>
        When it finishes, the timeline is <strong>kept</strong> — with each step's measured time — and
        re-shown whenever you return to the Overview, the same way the Heap Dump keeps its last
        initialization. The recommendations themselves live on the separate <strong>Findings</strong> page.
      </p>

      <p>
        The prompt is cached per event type, so re-running skips straight to the model. Changing the
        prompt-detail threshold under Settings → Advisor regenerates it, since that setting only reaches
        the model through the markdown.
      </p>

      <p>
        <strong>Clear results</strong> on the Overview throws all of it away — every report, its claims,
        the kept timeline, and the cached prompts — leaving the profile exactly as if the Advisor had
        never run. It is the counterpart of the Heap Dump's <em>Clear Cache</em>, and the way to get rid
        of a report generated against the wrong source folder, or of results for event types a later run
        no longer covers. Because the cached prompts go too, the next run rebuilds each profile summary
        from the call tree before it reaches the model.
      </p>

      <DocsCallout type="warning" title="Re-running costs a fresh analysis">
        Clearing cannot be undone, and regenerating means one AI analysis per event type. A run already
        in flight is not affected — clear it after the run finishes, or cancel first.
      </DocsCallout>

      <h2 id="grounding">Grounding and Severity</h2>

      <p>
        The model is asked to list, machine-readably, the frame each recommendation rests on. Every
        citation is resolved against the measured call tree — forgivingly about notation
        (<code>RateTable#lookup</code>, <code>com/acme/RateTable.lookup</code> and
        <code>RateTable.lookup</code> all resolve to the same measured frame) but never inventing a
        match for a frame that was never sampled.
      </p>

      <p>
        A claim that does not resolve is <strong>shown, clearly marked as unverified, and excluded from
        severity</strong> — not silently dropped. Hiding the model's mistake would make an unverifiable
        report indistinguishable from a verified one.
      </p>

      <p>
        Severity is Jeffrey's, not the model's: it is computed from the dominant grounded frame's
        measured <em>self</em> share — 20% or more is CRITICAL, 10% HIGH, 3% MEDIUM, otherwise LOW.
        Self share rather than total, because total is dominated by orchestration frames near 100%.
      </p>

      <h2 id="patches">Patches</h2>

      <p>
        When the model proposes a concrete edit, it is stored as a unified diff and shown on the
        <strong>Patches</strong> page — one patch per event type, ranked by severity and then by the
        measured cost of the heaviest frame it touches. Each is rendered with the grounded claims it
        rests on above it, and can be copied or saved as a <code>.patch</code> file.
      </p>

      <p>
        The figure in the diff gutter is a <em>citation</em>, not a per-line profile. A line carries a
        share only when it is a cited hotspot that resolved against the measured call tree; every other
        line is left blank rather than shaded a plausible colour, because Jeffrey measures per method,
        not per line. A type that produced a report without proposing an edit is named on the page
        instead of silently missing from it.
      </p>

      <h2 id="privacy">What Leaves Your Machine</h2>

      <p>
        The model sees the flamegraph markdown, the deterministic findings Jeffrey derived from it, and
        whatever source it reads through the four tools. Those tools are sandboxed to the configured
        folder — every path is checked twice, once lexically and once against its real location, so a
        symlink pointing out of the tree resolves to nothing. There are no write, delete or execute
        tools.
      </p>

      <p>
        If you would rather nothing left the machine at all, select the Claude Code CLI provider and run
        against a local model, or use the flamegraph <em>Copy for AI</em> button and paste the prompt
        wherever you like.
      </p>

      <DocsNavFooter />
    </div>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
