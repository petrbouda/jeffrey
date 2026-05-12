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
    <button
      v-if="showInstanceDetail"
      class="btn btn-action"
      title="Show Instance Details"
      @click="$emit('showInstanceDetail', objectId)"
    >
      <i class="bi bi-info-circle"></i>
    </button>
  </div>
</template>

<script setup lang="ts">
interface Props {
  objectId: number | null;
  showGcRootPath?: boolean;
  showInstanceDetail?: boolean;
}

withDefaults(defineProps<Props>(), {
  showGcRootPath: true,
  showInstanceDetail: false
});

defineEmits<{
  showReferrers: [objectId: number];
  showReachables: [objectId: number];
  showGCRootPath: [objectId: number];
  showInstanceDetail: [objectId: number];
}>();
</script>

<style scoped>
.instance-action-buttons {
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.15rem;
  max-width: 100%;
}

.btn-action {
  padding: 0.25rem 0.35rem;
  font-size: 0.7rem;
  line-height: 1;
  border: none;
  background-color: transparent;
  color: var(--color-text-muted);
  border-radius: 3px;
  transition: all 0.15s ease;
}

.btn-action:hover {
  background-color: rgba(111, 66, 193, 0.15);
  color: var(--color-purple);
}

.btn-action i {
  font-size: 1rem;
}
</style>
