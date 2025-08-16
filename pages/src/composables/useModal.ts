/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import { ref, Ref } from 'vue';

/**
 * Composable for managing modal state and interactions
 * 
 * @param modalRef - Reference to the modal component
 * @returns Modal state and control functions
 */
export function useModal(modalRef: Ref<any>) {
  const isVisible = ref(false);
  const isLoading = ref(false);
  const validationErrors = ref<string[]>([]);

  /**
   * Show the modal
   */
  const showModal = () => {
    if (modalRef.value) {
      clearValidationErrors();
      modalRef.value.showModal();
      isVisible.value = true;
    }
  };

  /**
   * Hide the modal
   */
  const hideModal = () => {
    if (modalRef.value) {
      modalRef.value.hideModal();
      isVisible.value = false;
    }
  };

  /**
   * Set validation errors on the modal
   */
  const setValidationErrors = (errors: string[]) => {
    validationErrors.value = errors;
    if (modalRef.value && modalRef.value.setValidationErrors) {
      modalRef.value.setValidationErrors(errors);
    }
  };

  /**
   * Clear validation errors
   */
  const clearValidationErrors = () => {
    validationErrors.value = [];
    if (modalRef.value && modalRef.value.clearValidationErrors) {
      modalRef.value.clearValidationErrors();
    }
  };

  /**
   * Handle modal shown event
   */
  const handleModalShown = () => {
    isVisible.value = true;
  };

  /**
   * Handle modal hidden event
   */
  const handleModalHidden = () => {
    isVisible.value = false;
    clearValidationErrors();
  };

  /**
   * Set loading state
   */
  const setLoading = (loading: boolean) => {
    isLoading.value = loading;
  };

  /**
   * Handle async form submission with error handling
   */
  const handleAsyncSubmit = async (
    submitFn: () => Promise<void>,
    onSuccess?: () => void,
    onError?: (error: any) => void
  ) => {
    setLoading(true);
    clearValidationErrors();

    try {
      await submitFn();
      if (onSuccess) {
        onSuccess();
      } else {
        hideModal();
      }
    } catch (error: any) {
      const errorMessage = error?.response?.data || error?.message || 'An error occurred';
      const errors = Array.isArray(errorMessage) ? errorMessage : [errorMessage];
      setValidationErrors(errors);
      
      if (onError) {
        onError(error);
      }
    } finally {
      setLoading(false);
    }
  };

  return {
    // State
    isVisible,
    isLoading,
    validationErrors,

    // Actions
    showModal,
    hideModal,
    setValidationErrors,
    clearValidationErrors,
    setLoading,
    handleAsyncSubmit,

    // Event handlers
    handleModalShown,
    handleModalHidden
  };
}