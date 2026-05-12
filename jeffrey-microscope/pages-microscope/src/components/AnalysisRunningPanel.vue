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
        <h4>{{ title }}</h4>
        <span class="overall-pill">elapsed {{ formattedElapsed }}</span>
      </div>
      <p v-if="subtitle" class="processing-hint">{{ subtitle }}</p>
    </div>

    <div class="overall-bar">
      <div class="overall-bar-fill-indeterminate"></div>
    </div>

    <div class="stage-row">
      <span class="tick"><span class="phase-spinner"></span></span>
      <span class="stage-label">{{ currentStage }}</span>
      <span class="stage-meta">{{ formattedElapsed }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{
  title: string;
  subtitle?: string;
  currentStage: string;
  /** Epoch millis the run started — drives the live counter. */
  startedAt: number;
}>();

const now = ref(Date.now());
let interval: ReturnType<typeof setInterval> | null = null;

const start = () => {
  if (interval !== null) return;
  now.value = Date.now();
  interval = setInterval(() => {
    now.value = Date.now();
  }, 250);
};

const stop = () => {
  if (interval !== null) {
    clearInterval(interval);
    interval = null;
  }
};

onMounted(start);
onUnmounted(stop);
// If parent flips startedAt (new run), restart the tick.
watch(() => props.startedAt, () => {
  stop();
  start();
});

const formattedElapsed = computed(() => {
  const ms = Math.max(0, now.value - props.startedAt);
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
});
</script>

<style scoped>
.processing-card {
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 2rem 2rem 2.5rem;
  max-width: 1300px;
  margin: 0 auto;
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

/* Indeterminate progress bar */
.overall-bar {
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  margin: 0 0 1.5rem;
  position: relative;
}

.overall-bar-fill-indeterminate {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 40%;
  background: linear-gradient(90deg, var(--color-success), var(--color-primary));
  border-radius: 2px;
  animation: indeterminate-slide 1.6s ease-in-out infinite;
}

@keyframes indeterminate-slide {
  0% {
    left: -40%;
  }
  100% {
    left: 100%;
  }
}

/* Stage row */
.stage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 16px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.tick {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stage-label {
  flex: 1;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-primary);
}

.stage-meta {
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.phase-spinner {
  width: 10px;
  height: 10px;
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
