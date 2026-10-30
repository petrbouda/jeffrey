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
  A drawer that slides in from the right over part of its container rather than over the page:
  no backdrop, nothing behind it blocked, so what it leaves uncovered stays usable. The container
  must be positioned (`position: relative`); `left` says where the drawer's edge stops, so a
  column browser keeps its first columns in view. Esc closes it unless a modal is open on top.

  Usage:
    <div style="position: relative">
      …columns…
      <PanelDrawer v-model:show="open" left="30rem" aria-label="Session">
        <template #header>…</template>
        …body…
      </PanelDrawer>
    </div>
-->

<template>
  <Transition name="panel-drawer-slide">
    <aside v-if="show" class="panel-drawer" :style="{ left }" :aria-label="ariaLabel">
      <header v-if="$slots.header" class="panel-drawer-head">
        <slot name="header"></slot>
      </header>
      <div class="panel-drawer-body">
        <slot></slot>
      </div>
    </aside>
  </Transition>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';

const props = withDefaults(
  defineProps<{
    show: boolean;
    /** Where the drawer's left edge stops inside its container, as a CSS length. */
    left?: string;
    ariaLabel?: string;
  }>(),
  {
    left: '0',
    ariaLabel: undefined
  }
);

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
}>();

/** A Bootstrap modal is open over the page: its own Esc handling comes first. */
const MODAL_OPEN_SELECTOR = '.modal.show';

const onKeydown = (event: KeyboardEvent) => {
  if (!props.show || event.key !== 'Escape' || event.defaultPrevented) {
    return;
  }
  if (document.querySelector(MODAL_OPEN_SELECTOR)) {
    return;
  }
  emit('update:show', false);
};

onMounted(() => {
  window.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
});
</script>

<style scoped>
.panel-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
}

.panel-drawer-head {
  flex: none;
  padding: var(--spacing-3) var(--spacing-4);
  border-bottom: 1px solid var(--color-border);
}

.panel-drawer-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.panel-drawer-slide-enter-active,
.panel-drawer-slide-leave-active {
  transition:
    transform 0.18s ease-out,
    opacity 0.18s ease-out;
}

.panel-drawer-slide-enter-from,
.panel-drawer-slide-leave-to {
  transform: translateX(2.5rem);
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .panel-drawer-slide-enter-active,
  .panel-drawer-slide-leave-active {
    transition: none;
  }
}
</style>
