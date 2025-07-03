<template>
  <div class="stat-card" :class="variantClass">
    <div class="kpi-icon">
      <i :class="iconClass"></i>
    </div>
    <div class="stat-content">
      <div class="kpi-title">{{ title }}</div>
      <div class="kpi-value">{{ value }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string;
  value: string | number;
  icon: string;
  variant?: 'primary' | 'success' | 'info' | 'danger' | 'warning';
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary'
});

const variantClass = computed(() => `stat-${props.variant}`);
const iconClass = computed(() => `bi bi-${props.icon}`);
</script>

<script lang="ts">
import { computed } from 'vue';
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  padding: 1.25rem;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  border-left: 4px solid transparent;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-primary {
  border-left-color: #4285F4;
}

.stat-success {
  border-left-color: #28a745;
}

.stat-info {
  border-left-color: #17a2b8;
}

.stat-danger {
  border-left-color: #dc3545;
}

.stat-warning {
  border-left-color: #FBBC05;
}

.stat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.kpi-icon {
  font-size: 1.5rem;
  line-height: 1;
  margin-right: 0.75rem;
  flex-shrink: 0;
}

.kpi-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: #777;
  margin-bottom: 0.125rem;
}

.kpi-value {
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
}
</style>