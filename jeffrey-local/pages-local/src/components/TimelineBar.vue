<template>
  <div class="tl-timeline">
    <div class="tl-item">
      <span class="tl-icon-badge tl-icon-badge--success">
        <i class="bi bi-play-fill"></i>
      </span>
      <div class="tl-info">
        <span class="tl-label">Started</span>
        <span class="tl-sub">{{ FormattingService.formatTimestampUTC(createdAt) }}</span>
      </div>
      <span class="tl-main">{{ FormattingService.formatRelativeTime(createdAt) }}</span>
    </div>
    <div class="tl-item">
      <span class="tl-icon-badge tl-icon-badge--danger">
        <i class="bi bi-stop-fill"></i>
      </span>
      <div class="tl-info">
        <span class="tl-label">Finished</span>
        <span v-if="finishedAt" class="tl-sub">{{ FormattingService.formatTimestampUTC(finishedAt) }}</span>
      </div>
      <template v-if="finishedAt">
        <span class="tl-main">{{ FormattingService.formatRelativeTime(finishedAt) }}</span>
      </template>
      <span v-else class="tl-main tl-running">Running...</span>
    </div>
    <div class="tl-item">
      <span class="tl-icon-badge tl-icon-badge--primary">
        <i class="bi bi-clock"></i>
      </span>
      <div class="tl-info">
        <span class="tl-label">Duration</span>
      </div>
      <span class="tl-main">{{ FormattingService.formatDurationInMillis2Units(duration) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import FormattingService from '@/services/FormattingService';

interface Props {
  createdAt: number
  finishedAt?: number | null
  duration: number
}

defineProps<Props>();
</script>

<style scoped>
.tl-timeline {
  display: flex;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.015), transparent);
  border-radius: 0 0 6px 6px;
}

.tl-item {
  flex: 1;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tl-item + .tl-item {
  border-left: 1px solid rgba(0, 0, 0, 0.06);
}

.tl-icon-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  font-size: 0.85rem;
  flex-shrink: 0;
}

.tl-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.tl-icon-badge--success {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success-hover);
}

.tl-icon-badge--danger {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger-hover);
}

.tl-icon-badge--primary {
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.tl-label {
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--color-text-light);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tl-main {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-dark);
  margin-left: auto;
  white-space: nowrap;
}

.tl-running {
  color: var(--color-text-light);
  font-weight: 500;
  font-style: italic;
}

.tl-sub {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .tl-timeline {
    flex-direction: column;
    gap: 4px;
  }

  .tl-item + .tl-item {
    border-left: none;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
    padding-top: 6px;
  }
}
</style>
