<template>
  <span class="jdbc-operation-badge" :class="`operation-${cleanedOperation.toLowerCase()}`">
    {{ cleanedOperation }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import JdbcUtils from '@/services/profile/custom/jdbc/JdbcUtils.ts';

interface Props {
  operation: string;
}

const props = defineProps<Props>();

const cleanedOperation = computed(() => JdbcUtils.cleanOperationName(props.operation));
</script>

<style scoped>
.jdbc-operation-badge {
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  min-height: 2rem;
}

.jdbc-operation-badge.operation-query { 
  background-color: #e3f2fd; 
  color: #1565c0; 
  border: 1px solid #bbdefb; 
}

.jdbc-operation-badge.operation-insert { 
  background-color: #e8f5e8; 
  color: #2e7d32; 
  border: 1px solid #c8e6c9; 
}

.jdbc-operation-badge.operation-update { 
  background-color: #fff8e1; 
  color: #f57c00; 
  border: 1px solid #ffecb3; 
}

.jdbc-operation-badge.operation-delete { 
  background-color: #ffebee; 
  color: #d32f2f; 
  border: 1px solid #ffcdd2; 
}

.jdbc-operation-badge.operation-generic-execute { 
  background-color: #f3e5f5; 
  color: #7b1fa2; 
  border: 1px solid #e1bee7; 
}

.jdbc-operation-badge.operation-stream { 
  background-color: #e0f2f1; 
  color: #00695c; 
  border: 1px solid #b2dfdb; 
}

.jdbc-operation-badge.operation-select { 
  background-color: #e3f2fd; 
  color: #1565c0; 
  border: 1px solid #bbdefb; 
}

.jdbc-operation-badge.operation-execute { 
  background-color: #f3e5f5; 
  color: #7b1fa2; 
  border: 1px solid #e1bee7; 
}
</style>