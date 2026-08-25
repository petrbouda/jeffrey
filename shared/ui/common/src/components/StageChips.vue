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
  Staged progress at list density: one chip per group of steps, ticked, spinning, dashed for skipped
  or red for failed, with the group's time beside its label.

  The compact counterpart to ProcessingTimeline, for places that show several runs at once and have
  a single line to do it in — a list of recordings importing together, where a timeline per row would
  be unreadable and a dialog per row unopenable. Both take the same TimelineStep[] and agree on what
  a group's status is, because both ask timelineStatus.
-->
<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import type { TimelineStep } from '@shared/types/processing';
import {
  type GroupStatus,
  groupElapsedMs,
  groupStatus,
  resolveSteps
} from '@shared/services/timelineStatus';

/** One chip: a label and the step ids it stands for. */
export interface StageChipGroup {
  id: string;
  label: string;
  stepIds: string[];
}

const props = defineProps<{
  groups: StageChipGroup[];
  steps: TimelineStep[];
  /** Epoch millis the parent ticks while a group is in progress. Ignored otherwise. */
  tickNow: number;
}>();

const statusOf = (group: StageChipGroup): GroupStatus => groupStatus(group.stepIds, props.steps);

/**
 * A group every one of whose steps was skipped reads as skipped rather than as done — "this
 * recording had no traces" is worth saying, and a tick would say the opposite.
 */
const isSkipped = (group: StageChipGroup): boolean => {
  const resolved = resolveSteps(group.stepIds, props.steps);
  return resolved.length > 0 && resolved.every(step => step.status === 'skipped');
};

const chipClass = (group: StageChipGroup): string => {
  if (isSkipped(group)) {
    return 'stage-chip--skipped';
  }
  return `stage-chip--${statusOf(group)}`;
};

/**
 * The group's time: how long it took, or how long it has been running. Skipped and not-yet-started
 * groups have no time to show — a zero would read as "instant" rather than "never ran".
 */
const timeOf = (group: StageChipGroup): string | null => {
  const status = statusOf(group);
  if (status === 'pending' || isSkipped(group)) {
    return null;
  }
  const elapsed = groupElapsedMs(group.stepIds, props.steps, props.tickNow);
  if (elapsed <= 0) {
    return null;
  }
  return FormattingService.formatDurationMillisCompact(elapsed);
};

const titleOf = (group: StageChipGroup): string => {
  if (isSkipped(group)) {
    return `${group.label}: not needed for this recording`;
  }
  switch (statusOf(group)) {
    case 'done':
      return `${group.label}: finished`;
    case 'active':
      return `${group.label}: running`;
    case 'failed':
      return `${group.label}: failed`;
    default:
      return `${group.label}: waiting`;
  }
};
</script>

<template>
  <div class="stage-chips">
    <template v-for="(group, index) in groups" :key="group.id">
      <span v-if="index > 0" class="stage-chips__arrow" aria-hidden="true">›</span>
      <span class="stage-chip" :class="chipClass(group)" :title="titleOf(group)">
        <span class="stage-chip__mark">
          <i v-if="isSkipped(group)" class="bi bi-dash"></i>
          <i v-else-if="statusOf(group) === 'done'" class="bi bi-check-lg"></i>
          <i v-else-if="statusOf(group) === 'failed'" class="bi bi-x-lg"></i>
          <span v-else-if="statusOf(group) === 'active'" class="stage-chip__spinner"></span>
          <i v-else class="bi bi-dot"></i>
        </span>
        <span class="stage-chip__label">{{ group.label }}</span>
        <span v-if="timeOf(group)" class="stage-chip__time">{{ timeOf(group) }}</span>
      </span>
    </template>
  </div>
</template>

<style scoped>
.stage-chips {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  flex-wrap: wrap;
}

.stage-chips__arrow {
  color: var(--color-text-muted);
  font-size: 0.65rem;
  line-height: 1;
}

.stage-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: 0.65rem;
  font-weight: 500;
  line-height: 1.4;
  padding: 0.1rem 0.45rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.stage-chip--done {
  border-color: var(--color-success);
  background: var(--color-success-light);
  color: var(--color-success);
}

.stage-chip--active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.stage-chip--failed {
  border-color: var(--color-danger);
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.stage-chip--skipped {
  border-style: dashed;
}

.stage-chip__mark {
  display: inline-flex;
  align-items: center;
  font-size: 0.6rem;
}

.stage-chip__time {
  font-family: var(--font-family-monospace);
  font-size: 0.6rem;
  font-variant-numeric: tabular-nums;
  opacity: 0.85;
}

.stage-chip__spinner {
  width: 0.5rem;
  height: 0.5rem;
  border: 1.5px solid currentColor;
  border-right-color: transparent;
  border-radius: var(--radius-circle);
  animation: stage-chip-spin 0.7s linear infinite;
}

@keyframes stage-chip-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stage-chip__spinner {
    animation: none;
  }
}
</style>
