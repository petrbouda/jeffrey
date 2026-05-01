<template>
  <div class="config-breadcrumbs">
    <div v-for="(item, index) in items" :key="index" class="breadcrumb-entry">
      <i v-if="index > 0" class="bi bi-chevron-right breadcrumb-separator"></i>
      <div class="breadcrumb-item" :class="{ active: item.active }" @click.stop="item.onClick?.()">
        <i :class="'bi ' + item.icon"></i>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface BreadcrumbItem {
  icon: string;
  label: string;
  active?: boolean;
  onClick?: () => void;
}

defineProps<{
  items: BreadcrumbItem[];
}>();
</script>

<style scoped>
.config-breadcrumbs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.breadcrumb-entry {
  display: flex;
  align-items: center;
  gap: 6px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text-muted);
  white-space: nowrap;
  transition: all 0.15s ease-in-out;
}

.breadcrumb-item i {
  font-size: 0.85rem;
  color: var(--color-primary);
  opacity: 0.7;
}

.breadcrumb-item[onclick],
.breadcrumb-item:not(.active) {
  cursor: pointer;
}

.breadcrumb-item:not(.active):hover {
  background: var(--color-primary-light);
  color: var(--color-text);
}

.breadcrumb-item:not(.active):hover i {
  opacity: 1;
}

.breadcrumb-item.active {
  color: var(--color-dark);
  font-weight: 600;
  cursor: default;
  background: var(--color-primary-light);
}

.breadcrumb-item.active i {
  opacity: 1;
}

.breadcrumb-separator {
  font-size: 0.7rem;
  color: var(--color-text-light);
}
</style>
