<template>
  <div class="detail-breadcrumb" @click="$emit('back')">
    <div class="breadcrumb-tile">
      <i class="bi" :class="icon"></i>
    </div>
    <div class="breadcrumb-stack">
      <span class="breadcrumb-root">{{ rootLabel }}</span>
      <span class="breadcrumb-current">
        <slot></slot>
      </span>
    </div>
    <div class="breadcrumb-back">
      <i class="bi bi-arrow-left"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  rootLabel: string;
  icon?: string;
}

withDefaults(defineProps<Props>(), {
  icon: 'bi-folder2-open'
});

defineEmits<{ back: [] }>();
</script>

<style scoped>
.detail-breadcrumb {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 1.5rem;
  cursor: pointer;
  transition:
    border-color var(--transition-fast, 0.15s) ease,
    background-color var(--transition-fast, 0.15s) ease,
    box-shadow var(--transition-fast, 0.15s) ease;
}

.detail-breadcrumb:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-lighter);
  box-shadow: var(--shadow-md);
}

.detail-breadcrumb:hover .breadcrumb-back {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.breadcrumb-tile {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-white);
  font-size: 1.1rem;
  background: linear-gradient(135deg, var(--color-primary), var(--color-violet));
  box-shadow: var(--shadow-sm);
}

.breadcrumb-stack {
  display: flex;
  flex-direction: column;
  line-height: 1.28;
  min-width: 0;
}

.breadcrumb-root {
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-semibold);
}

.breadcrumb-current {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb-back {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-border);
  background: var(--color-bg-hover);
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all var(--transition-fast, 0.15s) ease;
}

@media (max-width: 768px) {
  .detail-breadcrumb {
    gap: 10px;
    padding: 8px 12px;
  }

  .breadcrumb-tile {
    width: 34px;
    height: 34px;
    font-size: 1rem;
  }
}
</style>
