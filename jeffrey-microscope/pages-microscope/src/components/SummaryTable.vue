<template>
  <table class="summary-table">
    <tbody>
      <tr v-for="(item, index) in items" :key="index">
        <td>{{ item.label }}</td>
        <td>{{ item.value }}</td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
export interface SummaryItem {
  label: string;
  value: string;
}

defineProps<{
  items: SummaryItem[];
}>();
</script>

<style scoped>
.summary-table {
  width: 100%;
  border-collapse: collapse;
}

.summary-table td {
  padding: 0.6rem 0.75rem;
  font-size: 0.85rem;
  border-bottom: 1px solid var(--color-border-light);
}

.summary-table tr:last-child td {
  border-bottom: none;
}

.summary-table td:first-child {
  color: var(--color-text-muted);
}

.summary-table td:last-child {
  text-align: right;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: var(--color-dark);
}
</style>
