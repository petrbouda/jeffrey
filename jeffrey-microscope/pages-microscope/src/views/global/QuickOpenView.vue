<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!--
  Full-screen "quick open" landing page. Opened via a deep link such as
  /quick-open?path=/abs/path/to/recording.jfr (typically by the IntelliJ plugin).
  It imports the file as an ungrouped recording, initializes the profile, shows the
  Flamegraph Builder waiter while it works, then jumps straight into the profile.
-->
<template>
  <div class="quick-open">
    <div class="quick-open__inner">
      <template v-if="status === 'error'">
        <ErrorState :message="errorMessage" />
        <button class="btn btn-outline-primary btn-sm quick-open__back" @click="goToRecordings">
          <i class="bi bi-arrow-left me-1"></i>
          Back to recordings
        </button>
      </template>
      <FlamegraphBuilderWaiter v-else :caption="caption" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import RecordingsClient from '@hubs/services/api/RecordingsClient';
import { profileLandingRoute } from '@/services/ProfileLandingRoute';
import FlamegraphBuilderWaiter from '@/components/FlamegraphBuilderWaiter.vue';
import ErrorState from '@shared/components/ErrorState.vue';

type Status = 'importing' | 'analyzing' | 'error';

const route = useRoute();
const router = useRouter();
const recordingsClient = new RecordingsClient();

const status = ref<Status>('importing');
const errorMessage = ref('Failed to open the recording.');

const caption = computed(() => {
  if (status.value === 'analyzing') {
    return 'Analyzing recording and building views…';
  }
  return 'Importing recording…';
});

function goToRecordings(): void {
  router.push('/recordings');
}

function resolvePath(): string | null {
  const raw = route.query.path;
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (typeof value === 'string' && value.trim().length > 0) {
    return value;
  }
  return null;
}

function extractError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    if (data?.message) {
      return data.message;
    }
    if (data?.error) {
      return data.error;
    }
    return error.message;
  }
  return 'Failed to open the recording. Please try again.';
}

onMounted(async () => {
  const path = resolvePath();
  if (!path) {
    status.value = 'error';
    errorMessage.value = 'No recording path was provided in the URL (expected ?path=...).';
    return;
  }

  try {
    const imported = await recordingsClient.importFromPath(path);
    status.value = 'analyzing';
    const profileId = await recordingsClient.analyzeRecording(imported.recordingId, {
      suppressToast: true
    });
    // Land by kind: a heap dump opens on its overview, not on the JFR dashboard the bare profile
    // route would pick without looking.
    await router.replace(profileLandingRoute(profileId, imported.eventSource));
  } catch (error) {
    status.value = 'error';
    errorMessage.value = extractError(error);
  }
});
</script>

<style scoped>
.quick-open {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-body);
  padding: var(--spacing-6);
}

.quick-open__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.quick-open__back {
  margin-top: var(--spacing-4);
}
</style>
