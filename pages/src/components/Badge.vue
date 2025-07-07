<template>
  <span class="badge" :class="[sizeClass, variantClass]">
    {{ value }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type BadgeSize = 'xs' | 'sm' | 'md' | 'lg';
type BadgeVariant = 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info' | 'light' | 'dark' | 'blue' | 'green' | 'orange' | 'red' | 'purple' | 'grey' | 'pink' | 'yellow' | 'cyan' | 'indigo' | 'teal' | 'lime' | 'brown';

interface Props {
  value: string | number;
  size?: BadgeSize;
  variant?: BadgeVariant;
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  variant: 'primary'
});

const sizeClass = computed(() => `badge-${props.size}`);
const variantClass = computed(() => `badge-${props.variant}`);
</script>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  text-transform: uppercase;
  border-radius: 5px;
  border: 1px solid transparent;
  flex-shrink: 0;
}

/* Size variants */
.badge-xs {
  padding: 0.4em 0.8em;
  font-size: 0.75rem;
  font-weight: 500;
  min-width: 3rem;
}

.badge-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.7rem;
  min-height: 1.5rem;
}

.badge-md {
  padding: 0.375rem 0.625rem;
  font-size: 0.8rem;
  min-height: 2rem;
}

.badge-lg {
  padding: 0.5rem 0.75rem;
  font-size: 0.9rem;
  min-height: 2.5rem;
}

/* Color variants */
.badge-primary {
  background-color: #e3f2fd;
  color: #1565c0;
  border-color: #bbdefb;
}

.badge-secondary {
  background-color: #f5f5f5;
  color: #6c757d;
  border-color: #dee2e6;
}

.badge-success {
  background-color: #e8f5e8;
  color: #2e7d32;
  border-color: #c8e6c9;
}

.badge-danger {
  background-color: #ffebee;
  color: #d32f2f;
  border-color: #ffcdd2;
}

.badge-warning {
  background-color: #fff8e1;
  color: #f57c00;
  border-color: #ffecb3;
}

.badge-info {
  background-color: #e0f7fa;
  color: #00838f;
  border-color: #b2ebf2;
}

.badge-light {
  background-color: #fafafa;
  color: #212529;
  border-color: #e0e0e0;
}

.badge-dark {
  background-color: #424242;
  color: #ffffff;
  border-color: #616161;
}

/* Color variants */
.badge-blue {
  background-color: #e3f2fd;
  color: #1565c0;
  border-color: #90caf9;
}

.badge-green {
  background-color: #e8f5e8;
  color: #2e7d32;
  border-color: #81c784;
}

.badge-orange {
  background-color: #fff3e0;
  color: #ef6c00;
  border-color: #ffb74d;
}

.badge-red {
  background-color: #ffebee;
  color: #c62828;
  border-color: #ef5350;
}

.badge-purple {
  background-color: #f3e5f5;
  color: #7b1fa2;
  border-color: #ba68c8;
}

.badge-grey {
  background-color: #f5f5f5;
  color: #424242;
  border-color: #bdbdbd;
}

.badge-pink {
  background-color: #fce4ec;
  color: #c2185b;
  border-color: #f48fb1;
}

.badge-yellow {
  background-color: #fffde7;
  color: #f57f17;
  border-color: #fff176;
}

.badge-cyan {
  background-color: #e0f7fa;
  color: #00695c;
  border-color: #4dd0e1;
}

.badge-indigo {
  background-color: #e8eaf6;
  color: #3f51b5;
  border-color: #9fa8da;
}

.badge-teal {
  background-color: #e0f2f1;
  color: #00695c;
  border-color: #4db6ac;
}

.badge-lime {
  background-color: #f9fbe7;
  color: #689f38;
  border-color: #aed581;
}

.badge-brown {
  background-color: #efebe9;
  color: #5d4037;
  border-color: #a1887f;
}
</style>
