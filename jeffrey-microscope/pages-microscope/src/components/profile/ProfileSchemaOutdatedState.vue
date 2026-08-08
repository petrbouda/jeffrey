<template>
  <div class="schema-outdated-state">
    <div class="state-icon">
      <i class="bi bi-database-exclamation"></i>
    </div>
    <h2 class="state-title">This profile is from an older Jeffrey version</h2>
    <p v-if="!recordingMissing" class="state-message">
      The profile's data format is outdated and can no longer be opened. Recreate it from the
      original recording — the analysis runs again and produces a fresh profile.
    </p>
    <p v-else class="state-message">
      The original recording no longer exists, so this profile cannot be recreated. Delete the
      profile and import the recording again if you still have it.
    </p>
    <div class="state-actions">
      <button
        v-if="!recordingMissing"
        class="btn btn-primary"
        :disabled="working"
        @click="recreateProfile"
      >
        <span v-if="working" class="spinner-border spinner-border-sm me-2"></span>
        <i v-else class="bi bi-arrow-repeat me-2"></i>
        Recreate profile
      </button>
      <button class="btn btn-outline-danger" :disabled="working" @click="deleteProfile">
        <i class="bi bi-trash me-2"></i>
        Delete profile
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ToastService from '@shared/services/ToastService';
import DirectProfileClient from '@/services/api/DirectProfileClient';
import { ApiError } from '@/services/HttpInterceptor';

const props = defineProps<{
  profileId: string;
}>();

const emit = defineEmits<{
  resolved: [];
}>();

const directProfileClient = new DirectProfileClient();
const working = ref(false);
const recordingMissing = ref(false);

const recreateProfile = async () => {
  working.value = true;
  try {
    await directProfileClient.recreate(props.profileId);
    ToastService.success(
      'Profile recreation started',
      'The recording is being analyzed again — the new profile appears in the list shortly'
    );
    emit('resolved');
  } catch (error) {
    if (error instanceof ApiError && error.errorResponse?.code === 'RECORDING_NOT_FOUND') {
      recordingMissing.value = true;
    }
    console.error('Failed to recreate profile:', error);
  } finally {
    working.value = false;
  }
};

const deleteProfile = async () => {
  working.value = true;
  try {
    await directProfileClient.deleteById(props.profileId);
    ToastService.success('Profile deleted', 'The outdated profile has been removed');
    emit('resolved');
  } catch (error) {
    console.error('Failed to delete profile:', error);
  } finally {
    working.value = false;
  }
};
</script>

<style scoped>
.schema-outdated-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 4rem 2rem;
}

.state-icon {
  display: grid;
  place-items: center;
  width: 4rem;
  height: 4rem;
  border-radius: var(--radius-circle);
  background: var(--color-warning-light);
  color: var(--color-warning);
  font-size: 1.8rem;
  margin-bottom: 1.25rem;
}

.state-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--color-dark);
  margin-bottom: 0.6rem;
}

.state-message {
  color: var(--color-text-muted);
  font-size: 0.9rem;
  max-width: 34rem;
  margin-bottom: 1.5rem;
}

.state-actions {
  display: flex;
  gap: 0.75rem;
}
</style>
