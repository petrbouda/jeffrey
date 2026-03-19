<template>
  <div class="tl-timeline">
    <div class="tl-item">
      <div class="tl-value">
        <i class="bi bi-play-fill tl-icon text-success"></i>
        <span class="tl-label">Started</span>
      </div>
      <span class="tl-main">{{ FormattingService.formatRelativeTime(createdAt) }}</span>
      <span class="tl-sub">{{ FormattingService.formatTimestampUTC(createdAt) }}</span>
    </div>
    <div class="tl-item">
      <div class="tl-value">
        <i class="bi bi-stop-fill tl-icon text-danger"></i>
        <span class="tl-label">Finished</span>
      </div>
      <template v-if="finishedAt">
        <span class="tl-main">{{ FormattingService.formatRelativeTime(finishedAt) }}</span>
        <span class="tl-sub">{{ FormattingService.formatTimestampUTC(finishedAt) }}</span>
      </template>
      <span v-else class="tl-main text-muted">Running...</span>
    </div>
    <div class="tl-item">
      <div class="tl-value">
        <i class="bi bi-clock tl-icon text-primary"></i>
        <span class="tl-label">Duration</span>
      </div>
      <span class="tl-main">
        {{ FormattingService.formatDurationInMillis2Units(duration) }}
      </span>
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
  margin-top: 0;
  padding-top: 0;
  background: rgba(0, 0, 0, 0.01);
  border-radius: 0 0 6px 6px;
}

.tl-item {
  flex: 1;
  padding: 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tl-item + .tl-item {
  border-left: 1px solid rgba(0, 0, 0, 0.06);
}

.tl-value {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tl-icon {
  font-size: 0.7rem;
}

.tl-label {
  font-size: 0.65rem;
  font-weight: 600;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.tl-main {
  font-size: 0.78rem;
  font-weight: 500;
  color: #374151;
}

.tl-sub {
  font-size: 0.68rem;
  color: #9ca3af;
  opacity: 0.8;
}

@media (max-width: 768px) {
  .tl-timeline {
    flex-direction: column;
    gap: 4px;
  }

  .tl-item + .tl-item {
    border-left: none;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
    padding-top: 4px;
  }
}
</style>
