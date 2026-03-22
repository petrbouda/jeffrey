<template>
  <div class="dashboard-header mb-3">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <h2 class="dashboard-title">
          <i v-if="icon" :class="`bi bi-${icon} text-primary me-2`"></i>
          {{ title }}
        </h2>
        <p v-if="description" class="text-muted">{{ description }}</p>
      </div>
      <div v-if="$slots.actions">
        <slot name="actions"></slot>
      </div>
    </div>
    <div v-if="$slots.default">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: ''
  }
});
</script>

<style scoped>
.dashboard-header {
  padding: 1rem 0 0.1rem 0;
}

.dashboard-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #111;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
}

.stats-summary {
  display: flex;
  gap: 2rem;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111;
}

.stat-label {
  font-size: 0.85rem;
  color: #777;
}

/* Responsive Adjustments */
@media (max-width: 768px) {
  .stats-summary {
    gap: 1rem;
  }
  
  .stat-value {
    font-size: 1rem;
  }
  
  .stat-label {
    font-size: 0.75rem;
  }
}
</style>