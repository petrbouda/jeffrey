<template>
  <span class="badge" :class="[sizeClass, variantClass, keyValueClass, uppercaseClass, props.class]">
    <template v-if="isKeyValueMode">
      <i v-if="icon" :class="icon" class="badge-icon"></i>
      <span class="badge-key">{{ keyLabel }}:</span>
      <span class="badge-value">{{ value }}</span>
    </template>
    <template v-else>
      <i v-if="icon" :class="icon" style="width: 0.5rem; height: 0.5rem; margin-right: 5px" class="badge-icon"></i>
      {{ value }}
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type BadgeSize = 'xxs' | 'xs' | 's' | 'm' | 'l' | 'xl';
type BadgeVariant = 'primary' | 'info' | 'secondary' | 'success' | 'warning' | 'danger' | 'light' | 'dark' | 'blue' | 'green' | 'orange' | 'red' | 'purple' | 'grey' | 'pink' | 'yellow' | 'cyan' | 'indigo' | 'teal' | 'lime' | 'brown';

interface Props {
  value?: string | number;
  keyLabel?: string;
  size?: BadgeSize;
  variant?: BadgeVariant;
  icon?: string;
  class?: string;
  uppercase?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  size: 'm',
  variant: 'primary',
  uppercase: true
});

// Determine if this is a key-value badge
const isKeyValueMode = computed(() => props.keyLabel !== undefined);

const sizeClass = computed(() => `badge-${props.size}`);
const variantClass = computed(() => `badge-${props.variant}`);
const keyValueClass = computed(() => isKeyValueMode.value ? 'badge-key-value' : '');
const uppercaseClass = computed(() => props.uppercase ? '' : 'badge-no-uppercase');
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
  vertical-align: middle;
}

/* Size variants */
.badge-xxs {
  padding: 0.15rem 0.3rem;
  font-size: 0.6rem;
  min-height: 0.8rem;
  line-height: 1;
}

.badge-xs {
  padding: 0.2rem 0.4rem;
  font-size: 0.65rem;
  min-height: 1rem;
  line-height: 1.2;
}

.badge-s {
  padding: 0.25rem 0.5rem;
  font-size: 0.7rem;
  min-height: 1.25rem;
  line-height: 1.2;
  padding-top: 0.3rem;
  padding-bottom: 0.2rem;
}

.badge-m {
  padding: 0.3rem 0.6rem;
  font-size: 0.75rem;
  min-height: 1.5rem;
  line-height: 1.2;
}

.badge-l {
  padding: 0.375rem 0.625rem;
  font-size: 0.8rem;
  min-height: 2rem;
  line-height: 1.2;
}

.badge-xl {
  padding: 0.5rem 0.75rem;
  font-size: 0.9rem;
  min-height: 2.5rem;
  line-height: 1.2;
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

/* Badge variants - matching MetricsList styles */
.badge-primary {
  background: #e2e7fd;
  border: 1px solid #9ba8ff;
  color: #1565c0;
}

.badge-info {
  background: #ddf2f6;
  border: 1px solid #7dd3e8;
  color: #00838f;
}

.badge-secondary {
  background: #e7eafd;
  border: 1px solid #9ba3d4;
  color: #6c757d;
}

.badge-success {
  background-color: var(--color-success-light, #e8f5e8);
  border-color: var(--color-success, #2e7d32);
  color: var(--color-success, #2e7d32);
}

.badge-success .badge-value {
  color: var(--color-success, #2e7d32);
}

.badge-warning {
  background-color: var(--color-warning-light, #fff8e1);
  border-color: var(--color-warning, #f57c00);
  color: var(--color-warning, #f57c00);
}

.badge-warning .badge-value {
  color: var(--color-warning, #f57c00);
}

.badge-danger {
  background-color: var(--color-danger-light, #ffebee);
  border-color: var(--color-danger, #d32f2f);
  color: var(--color-danger, #d32f2f);
}

.badge-danger .badge-value {
  color: var(--color-danger, #d32f2f);
}

/* Key-value badge styling - matching original MetricsList */
.badge-key {
  color: var(--color-text-muted, #6c757d);
  font-weight: 500;
}

.badge-value {
  color: var(--color-dark, #212529);
  font-weight: 600;
}

/* Override default badge styles for key-value mode */
.badge.badge-key-value {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background-color: var(--color-light, #f8f9fa);
  border-radius: 0.25rem;
  border: 1px solid var(--color-border-light, #dee2e6);
  text-transform: none;
  font-weight: 500;
  justify-content: flex-start;
}

/* Size-specific overrides for key-value badges */
.badge.badge-key-value.badge-xxs {
  padding: 0.15rem 0.3rem;
  font-size: 0.6rem;
  gap: 0.1rem;
}

.badge.badge-key-value.badge-xs {
  padding: 0.2rem 0.4rem;
  font-size: 0.65rem;
  gap: 0.15rem;
}

.badge.badge-key-value.badge-s {
  padding: 0.25rem 0.5rem;
  font-size: 0.7rem;
  gap: 0.2rem;
}

.badge.badge-key-value.badge-m {
  padding: 0.3rem 0.6rem;
  font-size: 0.75rem;
  gap: 0.25rem;
}

.badge.badge-key-value.badge-l {
  padding: 0.375rem 0.625rem;
  font-size: 0.8rem;
  gap: 0.3rem;
}

.badge.badge-key-value.badge-xl {
  padding: 0.5rem 0.75rem;
  font-size: 0.9rem;
  gap: 0.35rem;
}

/* Variant overrides for key-value badges */
.badge.badge-key-value.badge-success {
  background-color: var(--color-success-light, #e8f5e8);
  border-color: var(--color-success, #2e7d32);
}

.badge.badge-key-value.badge-success .badge-value {
  color: var(--color-success, #2e7d32);
}

.badge.badge-key-value.badge-warning {
  background-color: var(--color-warning-light, #fff8e1);
  border-color: var(--color-warning, #f57c00);
}

.badge.badge-key-value.badge-warning .badge-value {
  color: var(--color-warning, #f57c00);
}

.badge.badge-key-value.badge-danger {
  background-color: var(--color-danger-light, #ffebee);
  border-color: var(--color-danger, #d32f2f);
}

.badge.badge-key-value.badge-danger .badge-value {
  color: var(--color-danger, #d32f2f);
}

.badge.badge-key-value.badge-primary {
  background: #e2e7fd;
  border: 1px solid #9ba8ff;
}

.badge.badge-key-value.badge-info {
  background: #ddf2f6;
  border: 1px solid #7dd3e8;
}

.badge.badge-key-value.badge-secondary {
  background: #e7eafd;
  border: 1px solid #9ba3d4;
}

/* Icon styling */
.badge-icon {
  margin-right: 0.25rem;
  font-size: 0.5em;
}

/* Size-specific icon adjustments */
.badge-xxs .badge-icon {
  margin-right: 0.1rem;
  font-size: 0.6em;
}

.badge-xs .badge-icon {
  margin-right: 0.15rem;
  font-size: 0.65em;
}

.badge-s .badge-icon {
  margin-right: 0.2rem;
  font-size: 0.7em;
}

.badge-m .badge-icon {
  margin-right: 0.25rem;
  font-size: 0.75em;
}

.badge-l .badge-icon {
  margin-right: 0.3rem;
  font-size: 0.8em;
}

.badge-xl .badge-icon {
  margin-right: 0.35rem;
  font-size: 0.9em;
}

/* Uppercase override */
.badge.badge-no-uppercase {
  text-transform: none;
}

</style>
