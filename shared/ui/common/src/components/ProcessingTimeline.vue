<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  A generic staged-processing timeline: phase cards, each with timed step rows, plus an overall progress
  header. The phase grouping and step labels are supplied by the caller through `phases`, so the same
  component renders the Heap Dump initialization, the Advisor run, and any future staged feature. The
  backend stores only step id/status/duration; this component owns the layout.
-->
<template>
  <div class="processing-card">
    <div class="processing-header">
      <div class="processing-title-row">
        <h4>{{ title ?? 'Processing' }}</h4>
        <div class="processing-overall">
          <span class="overall-elapsed">elapsed {{ totalElapsed }}</span>
          <span class="overall-pill"
            >{{ overallProgress.done }} / {{ overallProgress.total }} ·
            {{ overallProgress.pct }}%</span
          >
        </div>
      </div>
      <p v-if="subtitle" class="processing-hint">{{ subtitle }}</p>
    </div>

    <div v-if="showProgressBar" class="overall-bar">
      <div class="overall-bar-fill" :style="{ width: overallProgress.pct + '%' }"></div>
    </div>

    <div class="phase-grid">
      <div
        v-for="(phase, phaseIdx) in phases"
        :key="phase.id"
        class="phase-card"
        :class="phaseStatus(phase)"
      >
        <div class="phase-icon">
          <i v-if="phaseStatus(phase) === 'done'" class="bi bi-check-lg"></i>
          <span v-else>{{ phaseIdx + 1 }}</span>
        </div>
        <div class="phase-name">{{ phase.name }}</div>
        <div class="phase-desc">{{ phase.description }}</div>
        <div class="phase-bar">
          <div class="phase-bar-fill" :style="{ width: phaseProgress(phase) + '%' }"></div>
        </div>
        <ul class="phase-stages">
          <template v-for="(stepDef, sIdx) in phase.steps" :key="stepDef.id">
            <li
              :class="[
                getStep(stepDef.id)?.status,
                { expandable: hasSubPhases(getStep(stepDef.id)) },
                { expanded: hasSubPhases(getStep(stepDef.id)) && isExpanded(stepDef.id) }
              ]"
              @click="hasSubPhases(getStep(stepDef.id)) && toggleExpanded(stepDef.id)"
            >
              <span class="stage-chevron" v-if="hasSubPhases(getStep(stepDef.id))">
                <i class="bi bi-chevron-right"></i>
              </span>
              <span class="stage-chevron stage-chevron-placeholder" v-else></span>
              <span class="tick">
                <i v-if="getStep(stepDef.id)?.status === 'completed'" class="bi bi-check-lg"></i>
                <span
                  v-else-if="getStep(stepDef.id)?.status === 'in_progress'"
                  class="phase-spinner"
                ></span>
                <i v-else-if="getStep(stepDef.id)?.status === 'skipped'" class="bi bi-dash"></i>
                <i
                  v-else-if="getStep(stepDef.id)?.status === 'on_demand'"
                  class="bi bi-clock-history"
                ></i>
                <i v-else-if="getStep(stepDef.id)?.status === 'failed'" class="bi bi-x-lg"></i>
                <span v-else>{{ sIdx + 1 }}</span>
              </span>
              <span class="stage-label">{{ stepDef.label }}</span>
              <span class="stage-meta">{{ stageMeta(getStep(stepDef.id)) }}</span>
            </li>
            <li
              v-if="hasSubPhases(getStep(stepDef.id)) && isExpanded(stepDef.id)"
              class="subphase-block"
            >
              <ul class="subphase-list">
                <li
                  v-for="sub in getStep(stepDef.id)!.subPhases"
                  :key="sub.name"
                  class="subphase-row"
                >
                  <span
                    class="subphase-row-fill"
                    :style="{
                      width: subPhasePercent(sub, getStep(stepDef.id)!.subPhases!) + '%'
                    }"
                  ></span>
                  <span class="subphase-name">{{ sub.name }}</span>
                  <span v-if="sub.note" class="subphase-note">{{ sub.note }}</span>
                  <span class="subphase-time">{{
                    FormattingService.formatDurationMillisCompact(sub.durationMs)
                  }}</span>
                </li>
              </ul>
            </li>
          </template>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import FormattingService from '@shared/services/FormattingService';
import type { SubPhaseTiming, TimelinePhase, TimelineStep } from '@shared/types/processing';

const props = withDefaults(
  defineProps<{
    /** The phase grouping + per-step labels — the only feature-specific input. */
    phases: TimelinePhase[];
    steps: TimelineStep[];
    /** Epoch millis the parent ticks while a step is in progress. Ignored otherwise. */
    tickNow: number;
    title?: string;
    subtitle?: string;
    showProgressBar?: boolean;
  }>(),
  { showProgressBar: true }
);

const getStep = (id: string): TimelineStep | undefined => props.steps.find(s => s.id === id);

// Sub-phase accordion state — set of step ids currently expanded.
const expandedSteps = ref<Set<string>>(new Set());
const isExpanded = (id: string): boolean => expandedSteps.value.has(id);
const toggleExpanded = (id: string) => {
  const next = new Set(expandedSteps.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  expandedSteps.value = next;
};

const hasSubPhases = (step: TimelineStep | undefined): boolean =>
  !!step && Array.isArray(step.subPhases) && step.subPhases.length > 0;

/** Bar width % vs. the largest sub-phase in the set. Floored at 2% so a near-zero sub-phase still shows. */
const subPhasePercent = (sub: SubPhaseTiming, all: SubPhaseTiming[]): number => {
  const total = all.reduce((s, p) => s + Math.max(0, p.durationMs), 0);
  if (total <= 0) {
    return 0;
  }
  return Math.max(2, Math.round((sub.durationMs / total) * 100));
};

const stageMeta = (step: TimelineStep | undefined): string => {
  if (!step) {
    return '';
  }
  if (step.status === 'completed' && step.durationMs != null) {
    return FormattingService.formatDurationMillisCompact(step.durationMs);
  }
  if (step.status === 'in_progress' && step.startMs != null) {
    return FormattingService.formatDurationMillisCompact(props.tickNow - step.startMs);
  }
  if (step.status === 'failed') {
    return 'failed';
  }
  if (step.status === 'skipped') {
    return 'skipped';
  }
  if (step.status === 'on_demand') {
    return 'on demand';
  }
  return 'queued';
};

const isTerminal = (s: TimelineStep): boolean =>
  s.status === 'completed' || s.status === 'skipped' || s.status === 'on_demand' || s.status === 'failed';

const phaseStatus = (phase: TimelinePhase): 'done' | 'active' | 'pending' => {
  const steps = phase.steps
    .map(s => getStep(s.id))
    .filter((s): s is TimelineStep => s !== undefined);
  if (steps.length === 0) {
    return 'pending';
  }
  if (steps.every(isTerminal)) {
    return 'done';
  }
  if (steps.some(s => s.status === 'in_progress' || s.status === 'completed')) {
    return 'active';
  }
  return 'pending';
};

const phaseProgress = (phase: TimelinePhase): number => {
  const steps = phase.steps
    .map(s => getStep(s.id))
    .filter((s): s is TimelineStep => s !== undefined);
  if (steps.length === 0) {
    return 0;
  }
  const done = steps.filter(isTerminal).length;
  return (done / steps.length) * 100;
};

const overallProgress = computed(() => {
  const all = props.steps;
  const done = all.filter(isTerminal).length;
  const total = all.length;
  return {
    done,
    total,
    pct: total > 0 ? Math.round((done / total) * 100) : 0
  };
});

const totalElapsed = computed(() => {
  let ms = 0;
  for (const s of props.steps) {
    if (s.durationMs != null) {
      ms += s.durationMs;
    } else if (s.status === 'in_progress' && s.startMs != null) {
      ms += props.tickNow - s.startMs;
    }
  }
  return FormattingService.formatDurationMillisCompact(ms);
});
</script>

<style scoped>
.processing-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 2rem 2rem 2.5rem;
  width: 100%;
}

.processing-header {
  margin-bottom: 1.25rem;
}

.processing-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0.4rem;
}

.processing-header h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  margin: 0;
}

.processing-overall {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.overall-elapsed {
  font-variant-numeric: tabular-nums;
}

.overall-pill {
  background: var(--color-primary-light);
  color: var(--color-primary);
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.74rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.processing-hint {
  font-size: 0.8125rem;
  color: var(--color-text-light);
  margin: 0;
}

.overall-bar {
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  margin: 0 0 1.5rem;
}

.overall-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-success), var(--color-primary));
  transition: width 0.3s ease;
}

.phase-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 16px;
}

.phase-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
}

.phase-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.phase-card.done {
  border-color: var(--color-success);
  background: var(--color-success-light);
}

.phase-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 700;
  margin-bottom: 10px;
}

.phase-card.done .phase-icon {
  background: var(--color-success);
  color: var(--color-white);
}

.phase-card.active .phase-icon {
  background: var(--color-primary);
  color: var(--color-white);
}

.phase-card.pending .phase-icon {
  background: var(--color-border);
  color: var(--color-text-light);
}

.phase-name {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  margin-bottom: 2px;
}

.phase-desc {
  font-size: 0.74rem;
  color: var(--color-text-muted);
  margin-bottom: 14px;
}

.phase-bar {
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 14px;
}

.phase-bar-fill {
  height: 100%;
  transition: width 0.3s ease;
}

.phase-card.done .phase-bar-fill {
  background: var(--color-success);
}

.phase-card.active .phase-bar-fill {
  background: var(--color-primary);
}

.phase-card.pending .phase-bar-fill {
  background: var(--color-border);
}

.phase-stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.phase-stages li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.84rem;
  color: var(--color-text-muted);
}

.phase-stages li.completed {
  color: var(--color-text);
}

.phase-stages li.in_progress {
  color: var(--color-primary);
  font-weight: 600;
}

.phase-stages li.pending,
.phase-stages li.skipped {
  color: var(--color-text-light);
}

.phase-stages li.failed {
  color: var(--color-danger);
  font-weight: 600;
}

.phase-stages li.on_demand {
  color: var(--color-text);
}

.phase-stages .stage-label {
  flex: 1;
}

.phase-stages .stage-meta {
  font-size: 0.74rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.phase-stages li.in_progress .stage-meta {
  color: var(--color-primary);
  font-weight: 500;
}

.phase-stages li.pending .stage-meta,
.phase-stages li.skipped .stage-meta {
  color: var(--color-text-light);
}

.phase-stages li.failed .stage-meta {
  color: var(--color-danger);
}

.phase-stages li.on_demand .stage-meta {
  color: var(--color-primary);
  font-style: italic;
}

.phase-stages .tick {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
}

.phase-stages li.completed .tick {
  background: var(--color-success);
  color: var(--color-white);
}

.phase-stages li.in_progress .tick {
  background: var(--color-primary);
  color: var(--color-white);
}

.phase-stages li.pending .tick {
  background: var(--color-bg-card);
  border: 1.5px solid var(--color-border);
  color: var(--color-text-light);
}

.phase-stages li.skipped .tick {
  background: var(--color-border);
  color: var(--color-text-light);
}

.phase-stages li.failed .tick {
  background: var(--color-danger);
  color: var(--color-white);
}

.phase-stages li.on_demand .tick {
  background: var(--color-bg-card);
  border: 1.5px solid var(--color-primary);
  color: var(--color-primary);
}

.phase-stages .tick i {
  font-size: 0.7rem;
  line-height: 1;
}

.phase-spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid rgba(255, 255, 255, 0.4);
  border-top-color: var(--color-white);
  border-radius: 50%;
  animation: phase-spin 0.8s linear infinite;
}

@keyframes phase-spin {
  to {
    transform: rotate(360deg);
  }
}

/* Sub-phase accordion */

.phase-stages li.expandable {
  cursor: pointer;
  border-radius: 4px;
  padding: 2px 4px;
  margin: -2px -4px;
}

.phase-stages li.expandable:hover {
  background: var(--color-primary-light);
}

.phase-stages li.expanded {
  background: var(--color-primary-light);
}

.stage-chevron {
  width: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  transition: transform 0.15s ease;
  flex-shrink: 0;
}

.phase-stages li.expanded .stage-chevron {
  transform: rotate(90deg);
  color: var(--color-primary);
}

.stage-chevron-placeholder {
  visibility: hidden;
}

.phase-stages li.subphase-block {
  display: block;
  padding: 6px 0 4px 0;
  margin: 0 -6px 0 28px;
  background: transparent;
  cursor: default;
}

.subphase-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.subphase-row {
  position: relative;
  display: flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 4px;
  overflow: hidden;
  font-size: 0.76rem;
}

.subphase-row-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: var(--color-primary-light);
  border-radius: 4px;
  transition: width 0.25s ease;
  pointer-events: none;
}

.subphase-name {
  position: relative;
  flex: 1;
  z-index: 1;
  color: var(--color-text);
  font-family: var(--font-family-monospace);
  font-size: 0.74rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.subphase-note {
  position: relative;
  z-index: 1;
  font-size: 0.66rem;
  color: var(--color-text-muted);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 3px;
  padding: 1px 6px;
  margin-right: 8px;
  white-space: nowrap;
  font-weight: 500;
}

.subphase-time {
  position: relative;
  z-index: 1;
  font-variant-numeric: tabular-nums;
  font-size: 0.76rem;
  color: var(--color-text);
  font-weight: 500;
}

@media (prefers-reduced-motion: reduce) {
  .phase-spinner {
    animation: none;
  }
}
</style>
