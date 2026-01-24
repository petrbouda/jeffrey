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
import QuickAnalysisClient from '@/services/api/QuickAnalysisClient';
import QuickAnalysisProfile from '@/services/api/model/QuickAnalysisProfile';
import { ToastService } from '@/services/ToastService';
import router from '@/router';

/**
 * Status of the quick analysis process.
 */
export type QuickAnalysisStatus = 'idle' | 'parsing' | 'completed' | 'failed';

/**
 * Global state for Quick Analysis Assistant.
 * Allows ad-hoc JFR file analysis without creating workspaces/projects.
 */

// Global state
const isOpen = ref(false);
const isExpanded = ref(true);
const selectedFile = ref<File | null>(null);
const status = ref<QuickAnalysisStatus>('idle');
const statusMessage = ref('');
const recentProfiles = ref<QuickAnalysisProfile[]>([]);
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
 * Opens the Quick Analysis panel.
 */
const open = () => {
    isOpen.value = true;
    isExpanded.value = true;
    loadRecentProfiles();
};

/**
 * Closes the Quick Analysis panel.
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
        status.value = 'idle';
        statusMessage.value = '';
        errorMessage.value = null;
    }
};

/**
 * Sets the selected file.
 */
const setSelectedFile = (file: File | null) => {
    selectedFile.value = file;
    errorMessage.value = null;
};

/**
 * Loads the list of recent quick analysis profiles.
 */
const loadRecentProfiles = async () => {
    try {
        recentProfiles.value = await QuickAnalysisClient.listProfiles();
    } catch (error) {
        console.error('Failed to load recent profiles:', error);
    }
};

/**
 * Starts the analysis by uploading and parsing the selected file.
 */
const startAnalysis = async () => {
    const file = selectedFile.value;
    if (!file) return;

    status.value = 'parsing';
    statusMessage.value = 'Analyzing JFR file...';
    errorMessage.value = null;

    try {
        const profileId = await QuickAnalysisClient.uploadAndAnalyze(file);

        status.value = 'completed';

        // Close panel and navigate to profile
        isOpen.value = false;
        await router.push(`/profiles/${profileId}/overview`);

        // Reset after navigation
        reset();
        await loadRecentProfiles();

    } catch (error) {
        status.value = 'failed';
        errorMessage.value = error instanceof Error ? error.message : 'Failed to analyze JFR file';
        ToastService.error('Analysis Failed', errorMessage.value);
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
 * Deletes a quick analysis profile.
 */
const deleteProfile = async (profileId: string) => {
    try {
        await QuickAnalysisClient.deleteProfile(profileId);
        await loadRecentProfiles();
    } catch (error) {
        ToastService.error('Delete Failed', 'Failed to delete the profile.');
    }
};

/**
 * Global Quick Analysis Assistant store.
 */
export const quickAnalysisAssistantStore = {
    // State (reactive refs)
    isOpen,
    isExpanded,
    selectedFile,
    status,
    statusMessage,
    recentProfiles,
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
    loadRecentProfiles,
    startAnalysis,
    openProfile,
    deleteProfile
};
