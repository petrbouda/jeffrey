<template>
  <div class="table-card">
    <slot name="toolbar" />
    <div class="table-responsive">
      <table :class="['table', 'table-sm', 'table-hover', 'mb-0', tableClass]">
        <slot />
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  tableClass?: string;
}>(), {
  tableClass: undefined
});
</script>

<style scoped>
.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  overflow: hidden;
}
</style>
