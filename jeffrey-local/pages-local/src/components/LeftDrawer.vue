<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="show" class="drawer-backdrop" @click="$emit('update:show', false)"></div>
    </Transition>
    <Transition name="drawer-slide">
      <aside v-if="show" class="drawer" role="dialog" :aria-label="title">
        <header class="drawer-head">
          <div class="drawer-title">
            <i v-if="icon" :class="['bi', icon, 'text-primary', 'me-2']"></i>
            <span>{{ title }}</span>
          </div>
          <button
            class="drawer-close"
            type="button"
            aria-label="Close"
            @click="$emit('update:show', false)"
          >
            <i class="bi bi-x-lg"></i>
          </button>
        </header>

        <div class="drawer-body">
          <slot></slot>
        </div>

        <footer v-if="$slots.footer" class="drawer-foot">
          <slot name="footer"></slot>
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue';

const props = defineProps<{
  show: boolean;
  title: string;
  icon?: string;
}>();

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'submit'): void;
}>();

const onKeydown = (event: KeyboardEvent) => {
  if (!props.show) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('update:show', false);
    return;
  }
  if (event.key === 'Enter') {
    const target = event.target as HTMLElement | null;
    const tag = target?.tagName;
    if (tag === 'TEXTAREA' || tag === 'BUTTON' || tag === 'SELECT') return;
    if (target?.isContentEditable) return;
    event.preventDefault();
    emit('submit');
  }
};

watch(
  () => props.show,
  open => {
    if (open) {
      window.addEventListener('keydown', onKeydown);
    } else {
      window.removeEventListener('keydown', onKeydown);
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
});
</script>

<style scoped>
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(11, 23, 39, 0.4);
  backdrop-filter: blur(2px);
  z-index: 90;
}

.drawer {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 100%;
  max-width: 460px;
  background: var(--color-white);
  box-shadow: 8px 0 24px rgba(0, 0, 0, 0.12);
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.drawer-head {
  padding: var(--spacing-5);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.drawer-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  display: flex;
  align-items: center;
}

.drawer-close {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 1.2rem;
  padding: 4px 8px;
  border-radius: var(--radius-base);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
}

.drawer-close:hover {
  background: var(--color-light);
  color: var(--color-dark);
}

.drawer-body {
  padding: var(--spacing-5);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
  flex: 1;
  overflow-y: auto;
}

.drawer-foot {
  padding: var(--spacing-4) var(--spacing-5);
  border-top: 1px solid var(--color-border-light);
  background: var(--color-light);
  display: flex;
  gap: var(--spacing-3);
  justify-content: flex-end;
}

/* Animations */
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.18s ease-out;
}
.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.22s ease-out;
}
.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(-100%);
}
</style>
