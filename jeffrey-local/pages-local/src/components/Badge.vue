<template>
  <span
    class="badge"
    :class="[
      sizeClass,
      variantClass,
      keyValueClass,
      uppercaseClass,
      { 'badge-borderless': borderless },
      props.class
    ]"
  >
    <template v-if="isKeyValueMode">
      <i v-if="icon" :class="icon" class="badge-icon"></i>
      <span class="badge-key">{{ keyLabel }}:</span>
      <span class="badge-value">{{ value }}</span>
    </template>
    <template v-else>
      <i
        v-if="icon"
        :class="icon"
        style="width: 0.5rem; height: 0.5rem; margin-right: 5px"
        class="badge-icon"
      ></i>
      {{ value }}
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Size, Variant } from '@/types/ui.ts';

interface Props {
  value?: string | number;
  keyLabel?: string;
  size?: Size;
  variant?: Variant;
  icon?: string;
  class?: string;
  uppercase?: boolean;
  borderless?: boolean;
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
const keyValueClass = computed(() => (isKeyValueMode.value ? 'badge-key-value' : ''));
const uppercaseClass = computed(() => (props.uppercase ? '' : 'badge-no-uppercase'));
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
  background-color: var(--color-neutral-lightest);
  color: var(--color-neutral-text);
  border-color: var(--color-neutral-border);
}

.badge-dark {
  background-color: var(--color-dark-bg);
  color: var(--bs-white);
  border-color: var(--color-dark-border);
}

/* Color variants */
.badge-blue {
  background-color: var(--color-info-bg);
  color: var(--color-info-text);
  border-color: var(--color-info-border);
}

.badge-green {
  background-color: var(--color-green-bg);
  color: var(--color-green-text);
  border-color: var(--color-green-border);
}

.badge-orange {
  background-color: var(--color-orange-bg);
  color: var(--color-orange-text);
  border-color: var(--color-orange-border);
}

.badge-red {
  background-color: var(--color-red-bg);
  color: var(--color-red-text);
  border-color: var(--color-red-border);
}

.badge-purple {
  background-color: var(--color-purple-bg);
  color: var(--color-purple-text);
  border-color: var(--color-purple-border);
}

.badge-violet {
  background-color: var(--color-violet-lightest-bg);
  color: var(--color-violet-text);
  border-color: var(--color-violet-border-medium);
}

.badge-grey {
  background-color: var(--color-grey-bg);
  color: var(--color-grey-text);
  border-color: var(--color-grey-border);
}

.badge-pink {
  background-color: var(--color-pink-bg);
  color: var(--color-pink-text);
  border-color: var(--color-pink-border);
}

.badge-yellow {
  background-color: var(--color-amber-bg);
  color: var(--color-yellow-text);
  border-color: var(--color-yellow-border);
}

.badge-cyan {
  background-color: var(--color-cyan-bg);
  color: var(--color-cyan-text);
  border-color: var(--color-cyan-border);
}

.badge-indigo {
  background-color: var(--color-indigo-bg);
  color: var(--color-indigo-text);
  border-color: var(--color-indigo-border);
}

.badge-teal {
  background-color: var(--color-teal-bg);
  color: var(--color-teal-text);
  border-color: var(--color-teal-border);
}

.badge-lime {
  background-color: var(--color-lime-bg);
  color: var(--color-lime-text);
  border-color: var(--color-lime-border);
}

.badge-brown {
  background-color: var(--color-brown-bg);
  color: var(--color-brown-text);
  border-color: var(--color-brown-border);
}

/* Status variants */
.badge-status-active {
  background-color: var(--color-amber-light);
  color: var(--color-amber-highlight);
}

.badge-status-finished {
  background-color: var(--color-success-100);
  color: var(--color-emerald);
}

.badge-status-blocked {
  background-color: var(--color-light);
  color: var(--color-text-muted);
}

.badge-status-unknown {
  background-color: var(--color-light);
  color: var(--color-text-muted);
}

/* Badge variants - matching MetricsList styles */
.badge-primary {
  background: var(--color-primary-bg);
  border: 1px solid var(--color-primary-border);
  color: var(--color-info-text);
}

.badge-info {
  background: var(--color-info-bg-alt);
  border: 1px solid var(--color-info-border-alt);
  color: var(--color-info-text-dark);
}

.badge-secondary {
  background: var(--color-secondary-bg);
  border: 1px solid var(--color-secondary-border);
  color: var(--color-text-muted);
}

.badge-success {
  background-color: var(--color-success-light);
  border-color: var(--color-success);
  color: var(--color-success);
}

.badge-success .badge-value {
  color: var(--color-success);
}

.badge-warning {
  background-color: var(--color-warning-light);
  border-color: var(--color-warning);
  color: var(--color-warning);
}

.badge-warning .badge-value {
  color: var(--color-warning);
}

.badge-danger {
  background-color: var(--color-danger-light);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.badge-danger .badge-value {
  color: var(--color-danger);
}

/* Key-value badge styling - matching original MetricsList */
.badge-key {
  color: var(--color-text-muted);
  font-weight: 500;
}

.badge-value {
  color: var(--color-dark);
  font-weight: 600;
}

/* Override default badge styles for key-value mode */
.badge.badge-key-value {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background-color: var(--color-light);
  border-radius: 0.25rem;
  border: 1px solid var(--color-border-light);
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
  background-color: var(--color-success-light);
  border-color: var(--color-success);
}

.badge.badge-key-value.badge-success .badge-value {
  color: var(--color-success);
}

.badge.badge-key-value.badge-warning {
  background-color: var(--color-warning-light);
  border-color: var(--color-warning);
}

.badge.badge-key-value.badge-warning .badge-value {
  color: var(--color-warning);
}

.badge.badge-key-value.badge-danger {
  background-color: var(--color-danger-light);
  border-color: var(--color-danger);
}

.badge.badge-key-value.badge-danger .badge-value {
  color: var(--color-danger);
}

.badge.badge-key-value.badge-primary {
  background: var(--color-primary-bg);
  border: 1px solid var(--color-primary-border);
}

.badge.badge-key-value.badge-info {
  background: var(--color-info-bg-alt);
  border: 1px solid var(--color-info-border-alt);
}

.badge.badge-key-value.badge-secondary {
  background: var(--color-secondary-bg);
  border: 1px solid var(--color-secondary-border);
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

/* Borderless override */
.badge.badge-borderless {
  border-color: transparent !important;
}

/* Uppercase override */
.badge.badge-no-uppercase {
  text-transform: none;
}
</style>
