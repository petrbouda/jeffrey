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
  { id: 'docket', text: 'The Docket', level: 2 },
  { id: 'setup', text: 'Setup', level: 2 },
  { id: 'project-folders', text: 'Project Folders', level: 3 },
  { id: 'how-a-run-works', text: 'How a Run Works', level: 2 },
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
        A run produces three artifacts per event type — the prompt that was sent, the recommendation
        the model wrote, and the proposed patch — and each gets its own page, and its own step on the
        run timeline. A patch is attempted for every recommendation, so a type without one means the
        model found no concrete edit worth proposing, not that it was skipped.
      </p>

      <h2 id="overview">Overview</h2>

      <p>The Advisor is a top-level mode in the profile detail page, with four pages:</p>

      <ul>
        <li><strong>Overview</strong> — the landing page: set the source folder inline, launch the run, and watch its phased, timed timeline. The finished timeline is kept and re-shown on return.</li>
        <li><strong>Prompts</strong> — the exact message sent to the model for each event type, shown verbatim and copyable.</li>
        <li><strong>Recommendations</strong> — a separate page for the recommendation the model wrote.</li>
        <li><strong>Patches</strong> — the proposed code changes, one per event type, each shown as a unified diff.</li>
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

      <h2 id="docket">The Docket</h2>

      <p>
        Prompts, Recommendations and Patches all open with the same row of cards, always in the order
        CPU → Wall-Clock → Allocation → Blocking, so a group sits in the same place on every page and
        every profile. Each card carries that group's own figures: on Prompts the sample count and the
        size of the cached message; on Recommendations a badge marking the groups that produced a
        patch; on Patches the size of the proposed diff in files and lines.
      </p>

      <p>
        A group the recording carries no samples for keeps its place, ruled out with a hatch rather than
        dropped from the row. It is still selectable: picking it explains what is missing and shows the
        profiler flag that captures it — <code>event=ctimer</code>, <code>wall=10ms</code>,
        <code>alloc=512k</code> or <code>lock=10ms</code>. Only the groups a recording actually carries
        are analysed, and the Overview's "what it will analyze" list shows those alone.
      </p>

      <h2 id="setup">Setup</h2>

      <p>Two things are required:</p>

      <ol>
        <li>
          <strong>An AI provider.</strong> Configure one under Settings → AI Configuration (Claude,
          ChatGPT, Ollama, or the Claude Code CLI in headless mode). Until then the Advisor is greyed
          out, and it lights up as soon as a provider is selected — no restart.
        </li>
        <li>
          <strong>A source folder.</strong> On the Advisor Overview, choose one of your saved project
          folders — or type an absolute path of your own — on the machine running Microscope.
          Microscope reads your working copy in place: nothing is cloned, no credentials are stored,
          and the folder is never written to or deleted. It is checked once when you press Run — a
          path that is relative, missing, not a directory or not readable refuses the launch and says
          which, rather than starting a run that fails minutes later.
        </li>
      </ol>

      <DocsCallout type="info" title="Per profile">
        The source folder is stored per profile, so it works for any profile — including a
        locally-uploaded Quick Analysis recording, which has no project.
      </DocsCallout>

      <h3 id="project-folders">Project Folders</h3>

      <p>
        A working copy you profile often does not have to be typed out every time. Under
        <strong>Settings → Advisor → Project folders</strong> you keep a list of folders on this
        machine, each under a name you choose — the name is yours alone, tied to no workspace,
        project or hub, and it is what you recognise the folder by. Every entry is checked against
        the filesystem and listed as <em>Found</em> or <em>Not found</em>, so a checkout that moved
        shows up here rather than at the moment you launch a run.
      </p>

      <p>
        The Advisor Overview's <strong>Source folder</strong> control is that list: it opens with a
        filter, shows each folder by name with its path underneath, and marks a folder whose path no
        longer resolves as <em>Not found</em> — those cannot be picked, since a run against them
        could only fail. <strong>Custom path…</strong> at the bottom returns the plain field for a
        one-off path, which is also what the control offers while no folder is saved yet. A
        <strong>Manage folders</strong> link beside the field opens Settings → Advisor, so the list
        can be edited without losing your place.
      </p>

      <h2 id="how-a-run-works">How a Run Works</h2>

      <p>
        The Overview page is the landing: set a source folder and press <strong>Run Advisor</strong>. One
        launch analyzes <strong>every event type at once</strong> — CPU, Wall-Clock, Allocation, Blocking
        — shown as a processing timeline with a <strong>phase card per type</strong>. Each type moves
        through the same three <strong>timed steps</strong> — one per artifact it produces — and the
        types drain a few at a time through a shared ceiling so a single run cannot flood the AI
        provider:
      </p>

      <ol>
        <li><strong>Prompt</strong> — the complete user message is composed from the profile database and cached, so the run sends it verbatim and the Prompt page can show exactly what was asked.</li>
        <li><strong>Recommendation</strong> — the model explores your source with four read-only tools: <code>listFiles</code>, <code>glob</code>, <code>readFile</code> and <code>grep</code>, and its answer is split into the recommendations and the diff.</li>
        <li><strong>Patch</strong> — the diff the previous step produced is repaired so it applies cleanly, and the files it touches are checked against the folder. A type where the model proposed no code change finishes this step with nothing to build.</li>
      </ol>

      <p>
        The steps are also where the run's time is accounted for: Recommendation is the only one that
        calls the AI, so it dominates, while Prompt and Patch are local work measured in milliseconds.
        Validating the source folder is not among them — it belongs to the whole launch rather than to
        one event type, so it happens once before any type starts.
      </p>

      <p>
        When it finishes, the timeline is <strong>kept</strong> — with each step's measured time — and
        re-shown whenever you return to the Overview, the same way the Heap Dump keeps its last
        initialization. The recommendation itself lives on the separate <strong>Recommendations</strong> page.
      </p>

      <p>
        The prompt is cached per event type, so re-running skips straight to the model. Changing the
        prompt-detail threshold under Settings → Advisor regenerates it, since that setting only reaches
        the model through the markdown.
      </p>

      <p>
        <strong>Clear results</strong> on the Overview throws all of it away — every recommendation, its patch,
        the kept timeline, and the cached prompts — leaving the profile exactly as if the Advisor had
        never run. It is the counterpart of the Heap Dump's <em>Clear Cache</em>, and the way to get rid
        of a recommendation generated against the wrong source folder, or of results for event types a later run
        no longer covers. Because the cached prompts go too, the next run rebuilds each profile summary
        from the call tree before it reaches the model.
      </p>

      <DocsCallout type="warning" title="Re-running costs a fresh analysis">
        Clearing cannot be undone, and regenerating means one AI analysis per event type. A run already
        in flight is not affected — clear it after the run finishes, or cancel first.
      </DocsCallout>

      <h2 id="patches">Patches</h2>

      <p>
        When the model proposes a concrete edit, it is stored as a unified diff and shown on the
        <strong>Patches</strong> page — one patch per event type, listed in the docket's fixed order with
        the size of each diff on its card. Each is rendered as a unified diff with line numbers, and can
        be copied or saved as a <code>.patch</code> file that <code>git apply -p1</code> accepts.
      </p>

      <p>
        The diff is repaired before it is stored: models reliably miscount hunk headers, so those are
        recomputed, and a payload with no hunk at all is discarded rather than offered behind a button
        that would hand you a file that cannot apply. A type that produced a recommendation without proposing an
        edit is named on the page instead of silently missing from it.
      </p>

      <h2 id="privacy">What Leaves Your Machine</h2>

      <p>
        The model sees the flamegraph markdown and whatever source it reads through the four tools. Those tools are sandboxed to the configured
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
