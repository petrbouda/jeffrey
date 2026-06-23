<template>
  <div class="detail-breadcrumb" @click="$emit('back')">
    <span class="breadcrumb-back-btn">
      <i class="bi bi-arrow-left"></i> Back to {{ rootLabel }}
    </span>
    <div class="breadcrumb-divider"></div>
    <div class="breadcrumb-tile">
      <i class="bi" :class="icon"></i>
    </div>
    <div class="breadcrumb-stack">
      <span class="breadcrumb-root">{{ rootLabel }}</span>
      <span class="breadcrumb-current">
        <slot></slot>
      </span>
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
  gap: 12px;
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

/* Prominent "Back to <root>" affordance — fills with brand color on hover. */
.breadcrumb-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-base);
  padding: 6px 12px;
  transition: all var(--transition-fast, 0.15s) ease;
}

.detail-breadcrumb:hover .breadcrumb-back-btn {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.breadcrumb-divider {
  width: 1px;
  align-self: stretch;
  background: var(--color-border);
  margin: 2px 2px;
  flex-shrink: 0;
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

@media (max-width: 768px) {
  .detail-breadcrumb {
    gap: 10px;
    padding: 8px 12px;
  }

  .breadcrumb-back-btn span {
    display: none;
  }

  .breadcrumb-tile {
    width: 34px;
    height: 34px;
    font-size: 1rem;
  }
}
</style>
