<template>
  <Badge :value="cleanedOperation" :variant="badgeVariant" :size="size" :borderless="borderless" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@/components/Badge.vue';
import type { Size, Variant } from '@/types/ui';
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
