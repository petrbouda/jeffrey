<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<!--
  A button with its main action on the left and a ▾ on the right that opens a menu of the rest.
  The menu's items come from the default slot, which gets `close` to call after an item runs; give
  them the `split-button__item` class (`split-button__item--danger` for a destructive one) and use
  `split-button__label` / `split-button__divider` to group them.
-->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

export type SplitButtonTone = 'primary' | 'success' | 'danger';

withDefaults(
  defineProps<{
    label: string;
    /** A Bootstrap icon class shown before the label, e.g. `bi-play-fill`. */
    icon?: string;
    tone?: SplitButtonTone;
    disabled?: boolean;
    /** The accessible name of the ▾ toggle. */
    menuLabel?: string;
  }>(),
  {
    icon: undefined,
    tone: 'primary',
    disabled: false,
    menuLabel: 'More actions'
  }
);

const emit = defineEmits<{
  (e: 'click'): void;
}>();

const rootRef = ref<HTMLElement | null>(null);
const open = ref(false);

const close = () => {
  open.value = false;
};

const closeOnOutsideClick = (event: MouseEvent) => {
  const target = event.target as Node | null;
  if (open.value && rootRef.value && target && !rootRef.value.contains(target)) {
    close();
  }
};

const closeOnEscape = (event: KeyboardEvent) => {
  if (open.value && event.key === 'Escape') {
    close();
  }
};

onMounted(() => {
  document.addEventListener('mousedown', closeOnOutsideClick);
  document.addEventListener('keydown', closeOnEscape);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeOnOutsideClick);
  document.removeEventListener('keydown', closeOnEscape);
});
</script>

<template>
  <div ref="rootRef" class="split-button" :class="`split-button--${tone}`" @click.stop>
    <button class="split-button__main" type="button" :disabled="disabled" @click="emit('click')">
      <i v-if="icon" class="bi" :class="icon"></i>
      {{ label }}
    </button>
    <button
      class="split-button__toggle"
      type="button"
      :disabled="disabled"
      :aria-label="menuLabel"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="open = !open"
    >
      <i class="bi" :class="open ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
    </button>
    <div v-if="open" class="split-button__menu" role="menu">
      <slot :close="close" />
    </div>
  </div>
</template>

<style scoped>
.split-button {
  --split-accent: var(--color-primary);
  --split-text: var(--color-primary);
  --split-bg: var(--color-white);
  position: relative;
  display: inline-flex;
  align-items: stretch;
  flex-shrink: 0;
  border: 1px solid var(--split-accent);
  border-radius: var(--radius-base);
  background: var(--split-bg);
}

.split-button--success {
  --split-accent: var(--color-success);
  --split-text: var(--color-green-text);
  --split-bg: var(--color-green-bg);
}

.split-button--danger {
  --split-accent: var(--color-danger);
  --split-text: var(--color-danger);
  --split-bg: var(--color-danger-light);
}

.split-button__main,
.split-button__toggle {
  border: 0;
  background: transparent;
  color: var(--split-text);
  font-size: 0.75rem;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.split-button__main {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  padding: var(--spacing-1) var(--spacing-3);
  border-radius: var(--radius-base) 0 0 var(--radius-base);
}

.split-button__toggle {
  padding: 0 var(--spacing-2);
  border-left: 1px solid var(--split-accent);
  border-radius: 0 var(--radius-base) var(--radius-base) 0;
  font-size: 0.65rem;
}

.split-button__main:hover:not(:disabled),
.split-button__toggle:hover:not(:disabled),
.split-button__toggle[aria-expanded='true'] {
  background: color-mix(in srgb, var(--split-accent) 12%, transparent);
}

.split-button__main:disabled,
.split-button__toggle:disabled {
  cursor: progress;
  opacity: 0.7;
}

.split-button__menu {
  position: absolute;
  top: calc(100% + var(--spacing-1));
  right: 0;
  z-index: var(--z-dropdown);
  min-width: 220px;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  padding: var(--spacing-1);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-lg);
}

.split-button__menu :slotted(.split-button__item) {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  width: 100%;
  padding: var(--spacing-2) var(--spacing-3);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-dark);
  font-size: 0.75rem;
  text-align: left;
  cursor: pointer;
}

.split-button__menu :slotted(.split-button__item:hover) {
  background: var(--color-bg-hover-alt);
}

.split-button__menu :slotted(.split-button__item--danger) {
  color: var(--color-danger);
}

.split-button__menu :slotted(.split-button__label) {
  padding: var(--spacing-2) var(--spacing-3) var(--spacing-1);
  font-size: 0.6rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.split-button__menu :slotted(.split-button__divider) {
  height: 1px;
  margin: var(--spacing-1) 0;
  background: var(--color-border);
}
</style>
