<template>
  <div v-if="command" class="command-display" @click="copy" title="Click to copy command">
    <div class="command-display-header">
      <slot name="header-left">
        <div class="command-display-label">
          <i class="bi bi-terminal-fill"></i>
          <span>{{ label }}</span>
          <span
            v-if="level"
            class="settings-level-badge"
            :class="'settings-level-' + level.toLowerCase()"
          >
            {{ level }}
          </span>
        </div>
      </slot>
      <div class="command-display-actions">
        <slot name="header-actions">
          <button
            v-if="deletable"
            class="command-display-btn command-display-btn--danger"
            @click.stop="$emit('delete')"
            :disabled="deleting"
            title="Delete settings"
          >
            <span v-if="deleting" class="spinner-border spinner-border-sm" role="status"></span>
            <template v-else><i class="bi bi-trash"></i></template>
          </button>
        </slot>
        <i class="bi bi-clipboard command-display-copy"></i>
      </div>
    </div>
    <div class="command-display-content">
      {{ command }}
    </div>
  </div>
  <div v-else class="command-display command-display--empty">
    <div class="command-display-header">
      <slot name="header-left">
        <div class="command-display-label">
          <i class="bi bi-terminal-fill"></i>
          <span>{{ emptyMessage }}</span>
        </div>
      </slot>
    </div>
    <div v-if="$slots.content" class="command-display-body">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import ToastService from '@/services/ToastService';

interface Props {
  command: string | null;
  label?: string;
  level?: string | null;
  deletable?: boolean;
  deleting?: boolean;
  emptyMessage?: string;
}

const props = withDefaults(defineProps<Props>(), {
  label: 'Active Settings',
  level: null,
  deletable: false,
  deleting: false,
  emptyMessage: 'No settings configured'
});

defineEmits<{
  delete: [];
}>();

async function copy() {
  if (props.command) {
    try {
      await navigator.clipboard.writeText(props.command);
      ToastService.success('Copied!', 'Command copied to clipboard');
    } catch (error) {
      console.error('Failed to copy:', error);
      ToastService.error('Copy Failed', 'Could not copy to clipboard');
    }
  }
}
</script>

<style scoped>
.command-display {
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s ease-in-out;
  width: 100%;
  box-sizing: border-box;
}

.command-display:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

.command-display--empty {
  cursor: default;
}

.command-display--empty:hover {
  border-color: var(--color-border);
  box-shadow: none;
}

.command-display-header {
  background: var(--color-bg-hover);
  border-bottom: 1px solid var(--color-border);
  padding: 8px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.command-display--empty .command-display-header {
  border-bottom: none;
}

.command-display-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-text);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.command-display-label i {
  color: var(--color-primary);
}

.command-display-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.command-display-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: var(--bs-white);
  border: 1px solid var(--color-border);
  border-radius: var(--bs-border-radius-sm);
  color: var(--color-text-muted);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.command-display-btn:hover:not(:disabled) {
  background: var(--color-bg-hover);
}

.command-display-btn--danger {
  color: var(--color-danger);
  border-color: rgba(230, 55, 87, 0.2);
}

.command-display-btn--danger:hover:not(:disabled) {
  background: rgba(230, 55, 87, 0.08);
  border-color: rgba(230, 55, 87, 0.3);
}

.command-display-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.command-display-copy {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  opacity: 0.5;
  transition: all 0.15s ease-in-out;
}

.command-display:hover .command-display-copy {
  opacity: 1;
  color: var(--color-primary);
}

.command-display-content {
  padding: 12px 14px;
  font-size: var(--font-size-sm);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-weight: 500;
  color: var(--color-text);
  overflow-wrap: anywhere;
  word-break: break-all;
  line-height: 1.6;
}

.command-display-body {
  padding: 14px;
}

/* Settings Level Badge */
.settings-level-badge {
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 2px 7px;
  border-radius: var(--bs-border-radius-sm);
}

.settings-level-global {
  background: rgba(107, 114, 128, 0.1);
  color: var(--color-text);
}

.settings-level-workspace {
  background: rgba(57, 175, 209, 0.1);
  color: var(--color-info);
}

.settings-level-project {
  background: rgba(0, 210, 122, 0.1);
  color: var(--color-emerald);
}

.settings-level-none {
  background: rgba(107, 114, 128, 0.1);
  color: var(--color-text-muted);
}
</style>
