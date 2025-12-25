<template>
  <Badge
    :value="cleanedOperation"
    :variant="badgeVariant"
    size="l"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@/components/Badge.vue';
import JdbcUtils from '@/services/api/model/JdbcUtils.ts';

interface Props {
  operation: string;
}

const props = defineProps<Props>();

const cleanedOperation = computed(() => JdbcUtils.cleanOperationName(props.operation));

const badgeVariant = computed(() => {
  const operation = cleanedOperation.value.toLowerCase();
  
  const variants: Record<string, string> = {
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
