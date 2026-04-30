<template>
  <LeftDrawer
    v-model:show="showDrawer"
    title="Add Jeffrey Server"
    icon="bi-hdd-network"
    @update:show="reset"
    @submit="submit"
  >
    <div class="drawer-section">
      <div class="drawer-section-label">
        <i class="bi bi-bookmark-fill"></i>
        Identity
      </div>

      <div class="field-group">
        <label class="field-label">
          Server Name
          <span class="field-required">*</span>
        </label>
        <div class="field-wrap" :class="{ 'is-disabled': loading }">
          <input
            v-model="form.name"
            type="text"
            class="field-input"
            placeholder="e.g. production"
            :disabled="loading"
          />
        </div>
        <div class="field-hint">Display name shown in the server switcher.</div>
      </div>
    </div>

    <div class="drawer-section">
      <div class="drawer-section-label">
        <i class="bi bi-router"></i>
        Connection
      </div>

      <div class="field-row">
        <div class="field-group">
          <label class="field-label">
            Hostname
            <span class="field-required">*</span>
          </label>
          <div class="field-wrap" :class="{ 'is-disabled': loading }">
            <input
              v-model="form.hostname"
              type="text"
              class="field-input is-mono"
              placeholder="grpc.example.com"
              :disabled="loading"
            />
          </div>
        </div>

        <div class="field-group">
          <label class="field-label">
            Port
            <span class="field-required">*</span>
          </label>
          <div class="field-wrap" :class="{ 'is-disabled': loading }">
            <input
              v-model.number="form.port"
              type="number"
              class="field-input is-mono"
              placeholder="9090"
              min="1"
              max="65535"
              :disabled="loading"
            />
          </div>
        </div>
      </div>

      <label class="setting-row">
        <div class="setting-row-text">
          <div class="setting-row-title">Plaintext (h2c, no TLS)</div>
          <div class="setting-row-sub">
            Enable for in-cluster Service DNS or trusted-LAN setups. Default is TLS.
          </div>
        </div>
        <span class="toggle-switch">
          <input
            v-model="form.plaintext"
            type="checkbox"
            class="toggle-input"
            :disabled="loading"
          />
          <span class="toggle-slider"></span>
        </span>
      </label>
    </div>

    <div v-if="error" class="field-alert" role="alert">
      <i class="bi bi-exclamation-triangle"></i>
      <span>{{ error }}</span>
    </div>

    <template #footer>
      <button class="btn btn-secondary" :disabled="loading" @click="close">Cancel</button>
      <button class="btn btn-primary" :disabled="!isValid || loading" @click="submit">
        <span v-if="loading" class="spinner-border spinner-border-sm me-2" role="status"></span>
        Add Server
      </button>
    </template>
  </LeftDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import LeftDrawer from '@/components/LeftDrawer.vue';
import RemoteServerClient from '@/services/api/RemoteServerClient';
import ToastService from '@/services/ToastService';
import '@/styles/shared-components.css';

const props = defineProps<{ show: boolean }>();
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'server-added'): void;
}>();

const showDrawer = computed({
  get: () => props.show,
  set: v => emit('update:show', v),
});

const defaultForm = () => ({
  name: '',
  hostname: '',
  port: 9090,
  plaintext: false,
});

const form = ref(defaultForm());
const loading = ref(false);
const error = ref<string | null>(null);

const isValid = computed(() =>
  form.value.name.trim().length > 0 &&
  form.value.hostname.trim().length > 0 &&
  form.value.port >= 1 &&
  form.value.port <= 65535,
);

const client = new RemoteServerClient();

watch(
  () => props.show,
  open => {
    if (open) reset();
  },
);

const reset = () => {
  form.value = defaultForm();
  error.value = null;
  loading.value = false;
};

const close = () => {
  emit('update:show', false);
};

const submit = async () => {
  if (!isValid.value) return;
  loading.value = true;
  error.value = null;
  try {
    await client.add({
      name: form.value.name.trim(),
      hostname: form.value.hostname.trim(),
      port: form.value.port,
      plaintext: form.value.plaintext,
    });
    ToastService.success('Server Added', 'Connected to Jeffrey server.');
    emit('server-added');
    emit('update:show', false);
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? e?.message ?? 'Failed to add server';
  } finally {
    loading.value = false;
  }
};
</script>
