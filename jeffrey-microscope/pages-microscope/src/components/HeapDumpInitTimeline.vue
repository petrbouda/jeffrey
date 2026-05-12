<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the GNU Affero General Public License v3.
  -->

<template>
  <div class="processing-card">
    <div class="processing-header">
      <div class="processing-title-row">
        <h4>{{ title ?? 'Initializing Heap Dump' }}</h4>
        <div class="processing-overall">
          <span class="overall-elapsed">elapsed {{ totalElapsed }}</span>
          <span class="overall-pill"
            >{{ overallProgress.done }} / {{ overallProgress.total }} ·
            {{ overallProgress.pct }}%</span
          >
        </div>
      </div>
      <p v-if="subtitle ?? defaultSubtitle" class="processing-hint">
        {{ subtitle ?? defaultSubtitle }}
      </p>
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
          <li
            v-for="(stageDef, sIdx) in phase.stages"
            :key="stageDef.id"
            :class="getStep(stageDef.id)?.status"
          >
            <span class="tick">
              <i v-if="getStep(stageDef.id)?.status === 'completed'" class="bi bi-check-lg"></i>
              <span
                v-else-if="getStep(stageDef.id)?.status === 'in_progress'"
                class="phase-spinner"
              ></span>
              <i v-else-if="getStep(stageDef.id)?.status === 'skipped'" class="bi bi-dash"></i>
              <i
                v-else-if="getStep(stageDef.id)?.status === 'on_demand'"
                class="bi bi-clock-history"
              ></i>
              <span v-else>{{ sIdx + 1 }}</span>
            </span>
            <span class="stage-label">{{ stageDef.label }}</span>
            <span class="stage-meta">{{ stageMeta(getStep(stageDef.id)) }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

export interface TimelineStep {
  id: string;
  status: 'pending' | 'in_progress' | 'completed' | 'skipped' | 'on_demand';
  startMs?: number;
  durationMs?: number;
}

interface Phase {
  id: string;
  name: string;
  description: string;
  stages: { id: string; label: string }[];
}

const props = withDefaults(
  defineProps<{
    steps: TimelineStep[];
    /** Epoch millis the parent ticks while a stage is in progress. Ignored otherwise. */
    tickNow: number;
    title?: string;
    subtitle?: string;
    showProgressBar?: boolean;
  }>(),
  { showProgressBar: true }
);

const defaultSubtitle = 'Please wait while we analyze the heap structure...';

/**
 * The canonical pipeline definition (phase grouping + per-stage labels).
 * Source of truth — the backend stores only stage ids + status + duration,
 * the component owns the human-facing labels.
 */
const phases: Phase[] = [
  {
    id: 'indexing',
    name: 'Heap Indexing',
    description: 'Loading and parsing the heap file',
    stages: [
      { id: 'load', label: 'Loading heap dump' },
      { id: 'parse', label: 'Parsing heap structure' },
      { id: 'index', label: 'Building indexes' }
    ]
  },
  {
    id: 'analysis',
    name: 'Memory Analysis',
    description: 'Strings, threads, dominator tree, leaks',
    stages: [
      { id: 'strings', label: 'Analyzing strings' },
      { id: 'threads', label: 'Analyzing threads' },
      { id: 'dominator', label: 'Computing dominator tree' },
      { id: 'biggest', label: 'Finding biggest objects' },
      { id: 'collections', label: 'Analyzing collections' },
      { id: 'leaks', label: 'Detecting leak suspects' }
    ]
  },
  {
    id: 'hotspots',
    name: 'Hotspots & Duplicates',
    description: 'Class loaders, biggest collections, duplicates',
    stages: [
      { id: 'classloaders', label: 'Analyzing class loaders' },
      { id: 'biggest-collections', label: 'Finding biggest collections' },
      { id: 'duplicates', label: 'Detecting duplicate objects' }
    ]
  }
];

const getStep = (id: string): TimelineStep | undefined =>
  props.steps.find(s => s.id === id);

const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

const stageMeta = (step: TimelineStep | undefined): string => {
  if (!step) return '';
  if (step.status === 'completed' && step.durationMs != null) {
    return formatDuration(step.durationMs);
  }
  if (step.status === 'in_progress' && step.startMs != null) {
    return formatDuration(props.tickNow - step.startMs);
  }
  if (step.status === 'skipped') return 'skipped';
  if (step.status === 'on_demand') return 'on demand';
  return 'queued';
};

const isTerminal = (s: TimelineStep): boolean =>
  s.status === 'completed' || s.status === 'skipped' || s.status === 'on_demand';

const phaseStatus = (phase: Phase): 'done' | 'active' | 'pending' => {
  const steps = phase.stages
    .map(s => getStep(s.id))
    .filter((s): s is TimelineStep => s !== undefined);
  if (steps.length === 0) return 'pending';
  if (steps.every(isTerminal)) return 'done';
  if (steps.some(s => s.status === 'in_progress' || s.status === 'completed')) return 'active';
  return 'pending';
};

const phaseProgress = (phase: Phase): number => {
  const steps = phase.stages
    .map(s => getStep(s.id))
    .filter((s): s is TimelineStep => s !== undefined);
  if (steps.length === 0) return 0;
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
    if (s.durationMs != null) ms += s.durationMs;
    else if (s.status === 'in_progress' && s.startMs != null) ms += props.tickNow - s.startMs;
  }
  return formatDuration(ms);
});
</script>

<style scoped>
.processing-card {
  background: white;
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
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

@media (max-width: 900px) {
  .phase-grid {
    grid-template-columns: 1fr;
  }
}

.phase-card {
  background: white;
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
  color: white;
}

.phase-card.active .phase-icon {
  background: var(--color-primary);
  color: white;
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
  color: white;
}

.phase-stages li.in_progress .tick {
  background: var(--color-primary);
  color: white;
}

.phase-stages li.pending .tick {
  background: white;
  border: 1.5px solid var(--color-border);
  color: var(--color-text-light);
}

.phase-stages li.skipped .tick {
  background: var(--color-border);
  color: var(--color-text-light);
}

.phase-stages li.on_demand .tick {
  background: white;
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
  border-top-color: white;
  border-radius: 50%;
  animation: phase-spin 0.8s linear infinite;
}

@keyframes phase-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
