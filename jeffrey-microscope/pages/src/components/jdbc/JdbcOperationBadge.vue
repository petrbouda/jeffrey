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
  <Badge :value="cleanedOperation" :variant="badgeVariant" :size="size" :borderless="borderless" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import type { Size, Variant } from '@shared/types/ui';
import JdbcUtils from '@/services/api/model/JdbcUtils.ts';

interface Props {
  operation: string;
  size?: Size;
  borderless?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  size: 'l',
  borderless: false
});

const cleanedOperation = computed(() => JdbcUtils.cleanOperationName(props.operation));

const badgeVariant = computed(() => {
  const operation = cleanedOperation.value.toLowerCase();

  const variants: Record<string, Variant> = {
    query: 'blue',
    select: 'blue',
    insert: 'green',
    update: 'orange',
    delete: 'red',
    'generic-execute': 'purple',
    execute: 'purple',
    stream: 'info'
  };

  return variants[operation] || 'purple';
});
</script>
