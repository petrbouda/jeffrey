<template>
  <div v-if="objectId !== null" class="instance-action-buttons">
    <button
        class="btn btn-action"
        title="Show Referrers (who references this)"
        @click="$emit('showReferrers', objectId)"
    >
      <i class="bi bi-box-arrow-in-left"></i>
    </button>
    <button
        class="btn btn-action"
        title="Show Reachables (what this references)"
        @click="$emit('showReachables', objectId)"
    >
      <i class="bi bi-box-arrow-right"></i>
    </button>
    <button
        v-if="showGcRootPath"
        class="btn btn-action"
        title="Path to GC Root"
        @click="$emit('showGCRootPath', objectId)"
    >
      <i class="bi bi-signpost-2"></i>
    </button>
  </div>
</template>

<script setup lang="ts">
interface Props {
  objectId: number | null;
  showGcRootPath?: boolean;
}

withDefaults(defineProps<Props>(), {
  showGcRootPath: true,
});

defineEmits<{
  showReferrers: [objectId: number];
  showReachables: [objectId: number];
  showGCRootPath: [objectId: number];
}>();
</script>

<style scoped>
.instance-action-buttons {
  display: inline-flex;
  gap: 0.125rem;
  margin-left: 0.25rem;
}

.btn-action {
  padding: 0.2rem 0.4rem;
  font-size: 0.7rem;
  line-height: 1;
  border: none;
  background-color: transparent;
  color: #6c757d;
  border-radius: 3px;
  transition: all 0.15s ease;
}

.btn-action:hover {
  background-color: rgba(111, 66, 193, 0.15);
  color: #6f42c1;
}

.btn-action i {
  font-size: 0.9rem;
}
</style>
