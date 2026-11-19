<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="class-display">
    <code class="cd-class">{{ simpleName }}</code>
    <span v-if="packageName" class="cd-package" :class="packageColorClass">{{ packageName }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { isJdkPackage } from '@/services/JavaPackage';

const props = defineProps<{
  className: string;
}>();

const simpleName = computed(() => {
  const name = props.className ?? '';
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(lastDot + 1) : name;
});

const packageName = computed(() => {
  const name = props.className ?? '';
  const lastDot = name.lastIndexOf('.');
  return lastDot > 0 ? name.substring(0, lastDot) : '';
});

const packageColorClass = computed(() => {
  const pkg = packageName.value;
  if (!pkg) {
    return '';
  }
  return isJdkPackage(pkg) ? 'cd-pkg-jdk' : 'cd-pkg-other';
});
</script>

<style scoped>
.class-display {
  display: flex;
  flex-direction: column;
}

.cd-class {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text);
  background-color: transparent;
  white-space: nowrap;
}

.cd-package {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.78rem;
  font-weight: 500;
  margin-top: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cd-pkg-jdk {
  color: var(--color-primary);
}

.cd-pkg-other {
  color: var(--color-green-text);
}
</style>
