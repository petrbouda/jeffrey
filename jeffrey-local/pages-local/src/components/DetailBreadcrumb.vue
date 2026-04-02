<template>
  <div class="detail-breadcrumb" @click="$emit('back')">
    <div class="breadcrumb-back">
      <i class="bi bi-arrow-left"></i>
    </div>
    <span class="breadcrumb-root">{{ rootLabel }}</span>
    <span class="breadcrumb-sep"><i class="bi bi-chevron-right"></i></span>
    <span class="breadcrumb-current">
      <slot></slot>
    </span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  rootLabel: string;
}

defineProps<Props>();
defineEmits<{ back: [] }>();
</script>

<style scoped>
.detail-breadcrumb {
  background: rgba(94, 100, 255, 0.03);
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: var(--card-border-radius);
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 1.5rem;
  cursor: pointer;
  transition: all var(--transition-fast, 0.15s) ease;
}

.detail-breadcrumb:hover {
  background: rgba(94, 100, 255, 0.06);
  border-color: rgba(94, 100, 255, 0.3);
}

.detail-breadcrumb:hover .breadcrumb-back {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.breadcrumb-back {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid rgba(94, 100, 255, 0.25);
  background: rgba(94, 100, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-size: 0.85rem;
  flex-shrink: 0;
  transition: all var(--transition-fast, 0.15s) ease;
}

.breadcrumb-root {
  font-size: 0.8rem;
  color: var(--color-primary);
  font-weight: 500;
  white-space: nowrap;
  opacity: 0.7;
}

.breadcrumb-sep {
  color: var(--card-border-color);
  font-size: 0.7rem;
}

.breadcrumb-current {
  font-size: 0.9rem;
  color: var(--color-dark);
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .detail-breadcrumb {
    padding: 8px 12px;
    gap: 8px;
  }

  .breadcrumb-root {
    display: none;
  }

  .breadcrumb-sep {
    display: none;
  }

  .breadcrumb-current {
    font-size: 0.8rem;
  }
}
</style>
