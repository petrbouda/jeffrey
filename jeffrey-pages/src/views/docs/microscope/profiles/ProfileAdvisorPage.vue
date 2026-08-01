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
  { id: 'patch-verification', text: 'Patch Verification', level: 2 },
  { id: 'regression', text: 'Regression', level: 2 },
  { id: 'fleet-patterns', text: 'Fleet Patterns', level: 2 },
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
        Everything the model produces is checked before it is stored. Cited frames are resolved against
        the measured call tree, cited paths against your working copy, and the patch against
        <code>git apply</code>. Severity is then computed from what survived — so a finding the model
        invented cannot raise a profile's priority.
      </p>

      <h2 id="overview">Overview</h2>

      <p>The Advisor is a top-level mode in the profile detail page, with five sections:</p>

      <ul>
        <li><strong>Findings</strong> — the recommendations report, with the claim list above it.</li>
        <li><strong>Patch &amp; Verification</strong> — the proposed unified diff and how far it was verified.</li>
        <li><strong>Regression</strong> — which frames moved against a baseline profile.</li>
        <li><strong>Fleet Patterns</strong> — hotspots recurring across projects.</li>
        <li><strong>Source &amp; Settings</strong> — where your code is, and how far verification goes.</li>
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
          <strong>A source folder.</strong> Under Advisor → Source &amp; Settings, point the project at
          an absolute path on the machine running Microscope. Microscope reads your working copy in
          place: nothing is cloned, no credentials are stored, and the folder is never written to or
          deleted.
        </li>
      </ol>

      <DocsCallout type="info" title="Per project, not per profile">
        The source folder belongs to the project, so every profile of the same service shares it. A
        Quick Analysis profile has no project and therefore no source folder to configure.
      </DocsCallout>

      <h2 id="how-a-run-works">How a Run Works</h2>

      <p>
        Generation is asynchronous — a run takes minutes — and the page shows the same stage timeline
        heap-dump initialization uses, with a live clock on the stage in flight and a real duration on
        each stage that has finished:
      </p>

      <ol>
        <li><strong>Building the profile summary</strong> — the flamegraph markdown is built from the profile database and cached, along with the flattened call tree behind it.</li>
        <li><strong>Locating the source folder</strong> — the configured folder is validated and its commit read.</li>
        <li><strong>Reading your source</strong> — the model explores your source with four read-only tools: <code>listFiles</code>, <code>glob</code>, <code>readFile</code> and <code>grep</code>.</li>
        <li><strong>Checking claims and patch</strong> — claims are grounded, the patch is checked, severity is computed.</li>
        <li><strong>Storing findings</strong> — the recommendation, its claims and its token cost are written to the profile database.</li>
      </ol>

      <p>
        A run is filed per event type: at most one at a time for the same profile group, plus a small
        global ceiling, so simultaneous requests wait for a slot instead of degrading together. A run
        that is waiting shows as running with no stage started yet.
      </p>

      <p>
        The prompt is cached per event type, so re-running skips straight to the model. Changing the
        prompt-detail threshold regenerates it, since that setting only reaches the model through the
        markdown.
      </p>

      <p>
        When a run reaches a terminal state its stage timings are written to the profile database, so
        reopening the page later still shows how long each stage took. Progress of a run
        <em>in flight</em> lives only in memory: if Microscope restarts mid-run the work died with the
        process, and the page reports the run as gone rather than pretending it is still going.
      </p>

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

      <h2 id="patch-verification">Patch Verification</h2>

      <p>The patch climbs a ladder, and each level runs only if the one below it passed:</p>

      <ul>
        <li><strong>Applies cleanly</strong> — <code>git apply --check -p1</code>. Works out of the box for a git checkout.</li>
        <li><strong>Module compiles</strong> — your configured compile command.</li>
        <li><strong>Tests pass</strong> — your configured test command.</li>
      </ul>

      <p>
        A level that cannot run reports <strong>skipped</strong>, kept distinct from passed. The compile
        and test commands are blank by default: running your build is a decision only you can make, and
        Jeffrey will not guess at <code>mvn</code> or <code>gradle</code> on your behalf.
      </p>

      <DocsCallout type="warning" title="Applying the patch">
        Jeffrey never writes to your working copy. The diff is shown and can be copied; applying it is
        yours to do.
      </DocsCallout>

      <h2 id="regression">Regression</h2>

      <p>
        Pick a baseline with the <strong>Secondary Profile</strong> selector and the Regression page
        reports which frames moved. No model is involved — it is arithmetic over the two cached call
        trees, so it is exact, instant and free. Frames moving less than the configured threshold are
        hidden, because below it you are looking at sampling noise.
      </p>

      <h2 id="fleet-patterns">Fleet Patterns</h2>

      <p>
        Grounded claims are also indexed across profiles, so the Advisor can answer a question no single
        recording can: which frames are hot in more than one project. When the same frame costs several
        services time, the fix usually belongs where that frame lives rather than in each caller — a
        different piece of work with a different owner. Only grounded claims are rolled up.
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
