<template>
  <div class="dual-panel" :class="{ 'dual-panel-embedded': embedded }">
    <div v-if="title" class="dual-panel-header">
      <i v-if="icon" :class="`bi bi-${icon} me-2`"></i>
      <h4>{{ title }}</h4>
    </div>
    <div class="dual-panel-grid" :class="{ 'single-mode': !rightTitle }">
      <!-- Left column -->
      <div class="dual-panel-section">
        <div class="section-subtitle">{{ leftTitle }}</div>
        <slot name="left" />
      </div>

      <template v-if="rightTitle">
        <div class="dual-panel-divider"></div>

        <!-- Right column -->
        <div class="dual-panel-section">
          <div class="section-subtitle">{{ rightTitle }}</div>
          <slot name="right" />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  icon?: string;
  leftTitle: string;
  rightTitle?: string;
  embedded?: boolean;
}>();
</script>

<style scoped>
.dual-panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-lg);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.dual-panel-embedded {
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.dual-panel-header {
  display: flex;
  align-items: center;
  padding: 0.85rem 1.25rem;
  border-bottom: 1px solid var(--color-border);
}

.dual-panel-header h4 {
  margin: 0;
  color: var(--color-dark);
  font-size: 0.95rem;
  font-weight: 600;
}

.dual-panel-header i {
  color: var(--color-primary);
  opacity: 0.7;
}

.dual-panel-grid {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: start;
}

.dual-panel-grid.single-mode {
  grid-template-columns: 1fr 1fr;
}

.dual-panel-divider {
  width: 1px;
  background: var(--color-border);
  align-self: stretch;
}

.dual-panel-section {
  padding: 1rem 1.25rem;
}

.section-subtitle {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--color-text-muted);
  margin-bottom: 0.75rem;
}

@media (max-width: 992px) {
  .dual-panel-grid {
    grid-template-columns: 1fr;
  }

  .dual-panel-divider {
    width: auto;
    height: 1px;
    align-self: auto;
  }
}
</style>
