/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { ref, computed } from 'vue';
import RecordingsClient from '@/services/api/RecordingsClient';
import type Recording from '@/services/api/model/Recording';

const recordingsClient = new RecordingsClient();

import router from '@/router';

/**
 * Status of the quick analysis process.
 */
export type RecordingsAssistantStatus = 'idle' | 'parsing' | 'completed' | 'failed';

/**
 * Type of file being analyzed.
 */
export type RecordingsAssistantFileType = 'jfr' | 'hprof';

/**
 * Global state for Recordings Assistant.
 * Allows ad-hoc JFR and Heap Dump file analysis without creating workspaces/projects.
 */

// Global state
const isOpen = ref(false);
const isExpanded = ref(true);
const selectedFile = ref<File | null>(null);
const selectedFileType = ref<RecordingsAssistantFileType>('jfr');
const status = ref<RecordingsAssistantStatus>('idle');
const statusMessage = ref('');
const recentRecordings = ref<Recording[]>([]);
const errorMessage = ref<string | null>(null);

/**
 * Check if currently processing (parsing).
 */
const isProcessing = computed(() => status.value === 'parsing');

/**
 * Check if there's an active analysis that shouldn't be interrupted.
 */
const hasActiveAnalysis = computed(() => isProcessing.value);

/**
 * Detect file type from filename.
 */
const detectFileType = (filename: string): RecordingsAssistantFileType => {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.hprof') || lower.endsWith('.hprof.gz')) {
    return 'hprof';
  }
  return 'jfr';
};

/**
 * Opens the Recordings panel.
 */
const open = () => {
  isOpen.value = true;
  isExpanded.value = true;
  loadRecentRecordings();
};

/**
 * Closes the Recordings panel.
 */
const close = () => {
  if (!isProcessing.value) {
    isOpen.value = false;
    reset();
  }
};

/**
 * Expands the panel from minimized state.
 */
const expand = () => {
  isExpanded.value = true;
};

/**
 * Minimizes the panel to a floating button.
 */
const minimize = () => {
  isExpanded.value = false;
};

/**
 * Resets the state.
 */
const reset = () => {
  if (!isProcessing.value) {
    selectedFile.value = null;
    selectedFileType.value = 'jfr';
    status.value = 'idle';
    statusMessage.value = '';
    errorMessage.value = null;
  }
};

/**
 * Sets the selected file and detects its type.
 */
const setSelectedFile = (file: File | null) => {
  selectedFile.value = file;
  errorMessage.value = null;

  if (file) {
    selectedFileType.value = detectFileType(file.name);
  } else {
    selectedFileType.value = 'jfr';
  }
};

/**
 * Loads the list of recent quick analysis recordings.
 */
const loadRecentRecordings = async () => {
  try {
    recentRecordings.value = await recordingsClient.listRecordings();
  } catch (error) {
    console.error('Failed to load recent recordings:', error);
  }
};

/**
 * Starts the analysis by uploading the selected file and then analyzing it.
 */
const startAnalysis = async () => {
  const file = selectedFile.value;
  if (!file) return;

  const isHeapDump = selectedFileType.value === 'hprof';

  status.value = 'parsing';
  statusMessage.value = isHeapDump ? 'Uploading heap dump...' : 'Uploading JFR file...';
  errorMessage.value = null;

  try {
    // Step 1: Upload the recording
    const recordingId = await recordingsClient.uploadRecording(file);

    statusMessage.value = isHeapDump ? 'Analyzing heap dump...' : 'Analyzing JFR file...';

    // Step 2: Analyze the recording
    const profileId = await recordingsClient.analyzeRecording(recordingId);

    status.value = 'completed';

    // Close panel and navigate to appropriate page
    isOpen.value = false;

    if (isHeapDump) {
      await router.push(`/profiles/${profileId}/heap-dump/settings`);
    } else {
      await router.push(`/profiles/${profileId}/overview`);
    }

    // Reset after navigation
    reset();
    await loadRecentRecordings();
  } catch (error) {
    status.value = 'failed';
    const fileTypeLabel = isHeapDump ? 'heap dump' : 'JFR file';
    errorMessage.value =
      error instanceof Error ? error.message : `Failed to process ${fileTypeLabel}`;
  }
};

/**
 * Opens a profile in the viewer.
 */
const openProfile = async (profileId: string) => {
  isOpen.value = false;
  await router.push(`/profiles/${profileId}/overview`);
};

/**
 * Deletes a quick analysis recording.
 */
const deleteRecording = async (recordingId: string) => {
  try {
    await recordingsClient.deleteRecording(recordingId);
    await loadRecentRecordings();
  } catch {
    // Toast is shown automatically by HttpInterceptor
  }
};

/**
 * Global Recordings Assistant store.
 */
export const recordingsAssistantStore = {
  // State (reactive refs)
  isOpen,
  isExpanded,
  selectedFile,
  selectedFileType,
  status,
  statusMessage,
  recentRecordings,
  errorMessage,

  // Computed
  isProcessing,
  hasActiveAnalysis,

  // Actions
  open,
  close,
  expand,
  minimize,
  reset,
  setSelectedFile,
  loadRecentRecordings,
  startAnalysis,
  openProfile,
  deleteRecording
};
