<!--
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
 -->

<template>
  <div class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi" :class="icon"></i>
      <span>{{ message }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  message?: string;
  icon?: string;
}

withDefaults(defineProps<Props>(), {
  message: 'Failed to load data',
  icon: 'bi-exclamation-triangle-fill me-2'
});
</script>

<style scoped>
.error-state {
  text-align: center;
  padding: 3rem 1rem;
}

.error-state .alert {
  display: inline-flex;
  margin: 0;
}

.error-state i {
  font-size: 1rem;
}
</style>
