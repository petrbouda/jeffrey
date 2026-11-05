<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { computed } from 'vue';
import ReplayCaseList from '@/components/plugin/ReplayCaseList.vue';
import ReplayPlayer from '@/components/plugin/ReplayPlayer.vue';
import ReplayRoute from '@/components/plugin/ReplayRoute.vue';
import ReplayStepCard from '@/components/plugin/ReplayStepCard.vue';
import ReplayTimeline from '@/components/plugin/ReplayTimeline.vue';
import { formatDay, formatUsd, usePluginUsage, type AreaLens, type ReplayTrail } from '@/composables/usePluginUsage';
import { useReplayPlayback } from '@/composables/useReplayPlayback';

// One eval case at a time: pick it on the left, play a run in the middle, follow its route on the right.

const props = defineProps<{ caseIndex: number; runIndex: number; lens: AreaLens }>();

const emit = defineEmits<{
  (e: 'select-case', index: number): void;
  (e: 'select-run', index: number): void;
  (e: 'open-map', trail: ReplayTrail): void;
  (e: 'pick', id: string): void;
}>();

const SUITE_LABELS = { mocked: 'mocked Jeffrey', live: 'live Jeffrey' } as const;

const model = usePluginUsage();
const replays = model.data.replays;

const replay = computed(() => replays[props.caseIndex]);
const run = computed(() => replay.value.runs[props.runIndex] ?? replay.value.runs[0]);
const steps = computed(() => run.value.steps);
const playback = useReplayPlayback(steps);

const area = computed(() => model.areas.get(model.caseArea(replay.value))!);

const runFacts = computed<string[]>(() => {
  const facts: string[] = [SUITE_LABELS[replay.value.suite], formatDay(replay.value.at)];
  if (run.value.turns !== null) {
    facts.push(`${run.value.turns} turns`);
  }
  if (run.value.durationSeconds !== null) {
    facts.push(`${Math.round(run.value.durationSeconds)} s`);
  }
  if (run.value.costUsd !== null) {
    facts.push(formatUsd(run.value.costUsd));
  }
  return facts;
});

const trail = computed(() => model.replayTrail(steps.value.slice(0, playback.position.value + 1)));

function runMark(passed: boolean | null): string {
  if (passed === null) {
    return '';
  }
  return passed ? '✓' : '✗';
}
</script>

<template>
  <div class="replay">
    <aside class="replay-cases">
      <ReplayCaseList :replays="replays" :selected="caseIndex" :lens="lens" @select="emit('select-case', $event)" />
    </aside>

    <section class="replay-main" aria-live="polite">
      <div class="replay-title-row">
        <div>
          <h2 class="replay-title">{{ replay.case }}</h2>
          <p class="replay-facts">
            <span class="replay-area" :class="`replay-area--${area.name}`">{{ area.label }}</span>
            {{ runFacts.join(' · ') }}
          </p>
        </div>
        <div class="replay-runs" role="tablist" aria-label="Runs">
          <button
            v-for="(item, index) in replay.runs"
            :key="item.run"
            type="button"
            role="tab"
            class="replay-run"
            :class="{ 'replay-run--on': item === run, 'replay-run--failed': item.passed === false }"
            :aria-selected="item === run"
            @click="emit('select-run', index)"
          >
            Run {{ item.run }} {{ runMark(item.passed) }}
          </button>
        </div>
      </div>

      <div class="replay-prompt-label">The user asked</div>
      <p class="replay-prompt">{{ replay.prompt.trim() }}</p>
      <div class="replay-graders">
        <span
          v-for="grader in run.graders"
          :key="grader.name"
          class="replay-grader"
          :class="grader.passed ? 'replay-grader--passed' : 'replay-grader--failed'"
          :title="grader.passed ? 'passed' : 'failed'"
        >
          <i class="bi" :class="grader.passed ? 'bi-check-lg' : 'bi-x-lg'" aria-hidden="true"></i>
          {{ grader.name }}
        </span>
      </div>

      <div class="replay-card replay-player">
        <ReplayPlayer
          :position="playback.position.value"
          :count="steps.length"
          :playing="playback.playing.value"
          :at-end="playback.atEnd.value"
          :speed="playback.speed.value"
          :offset-ms="playback.current.value?.t ?? null"
          @toggle="playback.toggle"
          @step="playback.stepBy"
          @seek="playback.seek"
          @speed="playback.speed.value = $event"
        />
      </div>

      <div class="replay-card">
        <ReplayTimeline :run="run" :position="playback.position.value" @seek="playback.seek" />
      </div>
      <p class="replay-note">
        Each row is one call, in order; the time is from the run's first call and playback squeezes the gaps.
        Calls to the tool-search helper are left out.
      </p>
    </section>

    <aside class="replay-side">
      <h3 class="replay-side-title">Where this run goes</h3>
      <ReplayRoute :run="run" :position="playback.position.value" @pick="emit('pick', $event)" />
      <button type="button" class="replay-open-map" @click="emit('open-map', trail)">
        Show it on the full map <i class="bi bi-arrow-right" aria-hidden="true"></i>
      </button>

      <template v-if="playback.current.value">
        <h3 class="replay-side-title">This call</h3>
        <ReplayStepCard :step="playback.current.value" />
      </template>
    </aside>
  </div>
</template>

<style scoped>
.replay {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr) 340px;
  min-height: 720px;
}

.replay-cases {
  position: sticky;
  top: var(--site-header-height);
  align-self: start;
  max-height: calc(100vh - var(--site-header-height));
  overflow-y: auto;
  padding: 14px 12px 24px;
  border-right: 1px solid var(--site-line);
  background: var(--site-surface);
}

.replay-main {
  min-width: 0;
  padding: 24px 28px 40px;
}

.replay-side {
  padding: 24px 20px 40px;
  border-left: 1px solid var(--site-line);
  background: var(--site-surface);
}

.replay-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 20px;
}

.replay-title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.01em;
  word-break: break-word;
}

.replay-facts {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--site-faint);
}

.replay-area {
  display: inline-block;
  margin-right: 6px;
  padding: 0 8px;
  border-radius: var(--ap-radius-pill);
  background: var(--ap-hover);
  color: var(--site-muted);
  font-size: 12px;
  font-weight: 600;
}

.replay-area--jfr {
  background: var(--ap-jfr-band);
  color: var(--ap-jfr);
}

.replay-area--heap {
  background: var(--ap-heap-band);
  color: var(--ap-heap);
}

.replay-runs {
  display: inline-flex;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  overflow: hidden;
}

.replay-run {
  padding: 6px 12px;
  border: 0;
  background: var(--site-surface);
  color: var(--site-muted);
  font-size: 13px;
  cursor: pointer;
}

.replay-run + .replay-run {
  border-left: 1px solid var(--site-line-strong);
}

.replay-run--failed {
  color: var(--ap-no);
}

.replay-run--on {
  background: var(--site-ink);
  color: var(--site-surface);
}

.replay-prompt-label,
.replay-side-title {
  margin: 20px 0 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--site-faint);
}

.replay-side-title:first-child {
  margin-top: 0;
}

.replay-prompt {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--site-line);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  font-family: var(--site-font-mono);
  font-size: 12.5px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.replay-graders {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0 16px;
}

.replay-grader {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--ap-radius-pill);
  background: var(--site-surface);
  font-size: 12px;
}

.replay-grader--passed {
  border-color: var(--ap-ok-line);
  color: var(--ap-ok);
}

.replay-grader--failed {
  border-color: var(--ap-no-line);
  color: var(--ap-no);
}

.replay-card {
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
  overflow: hidden;
}

.replay-player {
  padding: 12px 14px;
  margin-bottom: 14px;
  overflow: visible;
}

.replay-note {
  margin: 10px 2px 0;
  font-size: 12.5px;
  color: var(--site-faint);
}

.replay-open-map {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 22px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--site-ember);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.replay-open-map:hover {
  color: var(--site-flame);
}

@media (max-width: 1199px) {
  .replay {
    grid-template-columns: 250px minmax(0, 1fr);
  }

  .replay-side {
    grid-column: 2;
    border-left: 0;
    border-top: 1px solid var(--site-line);
    padding: 22px 28px 40px;
  }
}

@media (max-width: 859px) {
  .replay {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
  }

  .replay-cases {
    position: static;
    max-height: none;
    padding: 14px 16px;
    border-right: 0;
    border-bottom: 1px solid var(--site-line);
  }

  .replay-main {
    padding: 18px 16px 24px;
  }

  .replay-side {
    grid-column: 1;
    padding: 20px 16px 32px;
  }
}
</style>
