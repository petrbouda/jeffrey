<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="modal"
       :class="{ 'd-block': show, 'd-none': !show }"
       :id="modalId"
       tabindex="-1"
       :aria-labelledby="modalId + 'Label'"
       @keyup.esc="closeModal">
    <div class="modal-dialog" :class="modalSize">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" :id="modalId + 'Label'">
            <i v-if="icon" :class="icon + ' me-2'"></i>
            {{ title }}
          </h5>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <slot></slot>
        </div>
        <div class="modal-footer" v-if="showFooter">
          <slot name="footer">
            <button type="button" class="btn btn-secondary" @click="closeModal">Close</button>
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  modalId: string;
  show: boolean;
  title: string;
  icon?: string;
  size?: 'sm' | 'lg' | 'xl';
  showFooter?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  size: 'lg',
  showFooter: true
});

const emit = defineEmits(['update:show']);

const closeModal = () => {
  emit('update:show', false);
};

const modalSize = computed(() => {
  switch (props.size) {
    case 'sm': return 'modal-sm';
    case 'lg': return 'modal-lg';
    case 'xl': return 'modal-xl';
    default: return '';
  }
});
</script>

<style scoped>
/* Modal backdrop and positioning */
.modal {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1055;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  outline: 0;
  background-color: rgba(0, 0, 0, 0.5);
}

.modal.d-none {
  display: none !important;
}

.modal.d-block {
  display: block !important;
}
</style>