<template>
  <div class="configure-command">
    <div class="command-input-section">
      <div class="command-input-area">
        <label class="command-label">Profiler Command</label>
        <textarea
          v-model="localCommand"
          class="command-textarea"
          rows="4"
          @input="$emit('update:modelValue', localCommand)"
        ></textarea>
        <div class="command-help">
          You can manually enter your command or use the visual builder to construct it step by step.
        </div>
        <div class="command-help">
        Example: -agentpath:/path/to/libasyncProfiler.so=start,event=ctimer,wall=10ms,loop=15m,file=profile.jfr
        </div>
      </div>

      <div class="command-actions">
        <button
          type="button"
          class="btn-open-builder"
          @click="$emit('open-builder')"
        >
          <i class="bi bi-ui-checks"></i>
          Open Builder
        </button>
        <button
          type="button"
          class="btn-clear-command"
          @click="clearCommand"
          :disabled="!localCommand.trim()"
        >
          <i class="bi bi-x-circle"></i>
          Clear
        </button>
        <button
          type="button"
          class="btn-next-step"
          @click="$emit('accept-command', localCommand)"
          :disabled="!localCommand.trim()"
        >
          Next: Apply Configuration
          <i class="bi bi-arrow-right"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

interface Props {
  modelValue: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'open-builder': [];
  'accept-command': [command: string];
  'clear': [];
}>();

const localCommand = ref(props.modelValue);

// Watch for external changes to modelValue
watch(() => props.modelValue, (newValue) => {
  localCommand.value = newValue;
});

const clearCommand = () => {
  localCommand.value = '';
  emit('update:modelValue', '');
  emit('clear');
};
</script>

<style scoped>
.configure-command {
  width: 100%;
}

.command-input-section {
  margin-top: 20px;
}

.command-input-area {
  margin-bottom: 20px;
}

.command-label {
  font-size: 0.8rem;
  font-weight: 700;
  color: #6b7280;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.command-label::before {
  content: '';
  width: 3px;
  height: 14px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  border-radius: 2px;
}

.command-textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  font-size: 0.85rem;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  background: #ffffff;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: #374151;
  resize: vertical;
}

.command-textarea:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.command-help {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 6px;
  font-style: italic;
}

.command-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.btn-open-builder,
.btn-clear-command,
.btn-next-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
}

.btn-open-builder {
  background: linear-gradient(135deg, #faf5ff, #f3e8ff);
  border: 1px solid rgba(147, 51, 234, 0.3);
  color: #581c87;
}

.btn-open-builder:hover {
  background: linear-gradient(135deg, #9333ea, #7c3aed);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(147, 51, 234, 0.3);
}

.btn-clear-command {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #dc2626;
}

.btn-clear-command:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.3);
}

.btn-clear-command:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-next-step {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
}

.btn-next-step:hover:not(:disabled) {
  background: linear-gradient(135deg, #4c52ff, #3f46ff);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.4);
}

.btn-next-step:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
