<template>
  <GenericModal v-model:show="showModal" title="Add Jeffrey Server" size="md" @close="reset">
    <div class="form-group mb-3">
      <label class="form-label">Server Name <span class="text-danger">*</span></label>
      <input
        v-model="form.name"
        type="text"
        class="form-control"
        placeholder="e.g. production"
        :disabled="loading"
      />
      <small class="text-muted">Display name shown in the server switcher.</small>
    </div>

    <div class="row g-2 mb-3">
      <div class="col-8">
        <label class="form-label">Hostname <span class="text-danger">*</span></label>
        <input
          v-model="form.hostname"
          type="text"
          class="form-control"
          placeholder="grpc.example.com"
          :disabled="loading"
        />
      </div>
      <div class="col-4">
        <label class="form-label">Port <span class="text-danger">*</span></label>
        <input
          v-model.number="form.port"
          type="number"
          class="form-control"
          placeholder="9090"
          min="1"
          max="65535"
          :disabled="loading"
        />
      </div>
    </div>

    <div class="form-check mb-3">
      <input
        id="plaintext-check"
        v-model="form.plaintext"
        type="checkbox"
        class="form-check-input"
        :disabled="loading"
      />
      <label for="plaintext-check" class="form-check-label">
        Plaintext (h2c, no TLS)
      </label>
      <div class="form-text">
        Enable for in-cluster Service DNS or trusted-LAN setups. Default is TLS.
      </div>
    </div>

    <div v-if="error" class="alert alert-danger" role="alert">
      <i class="bi bi-exclamation-triangle me-2"></i>{{ error }}
    </div>

    <template #footer>
      <button class="btn btn-secondary" :disabled="loading" @click="close">Cancel</button>
      <button class="btn btn-primary" :disabled="!isValid || loading" @click="submit">
        <span v-if="loading" class="spinner-border spinner-border-sm me-2" role="status"></span>
        Add Server
      </button>
    </template>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import GenericModal from '@/components/GenericModal.vue';
import RemoteServerClient from '@/services/api/RemoteServerClient';
import ToastService from '@/services/ToastService';

const props = defineProps<{ show: boolean }>();
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'server-added'): void;
}>();

const showModal = computed({
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
