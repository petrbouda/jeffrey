<template>
  <div
    class="workspace-selection-card"
    :class="{ 'active': selected }"
    @click="$emit('select')"
  >
    <div class="workspace-card-content">
      <div class="workspace-card-header">
        <div class="workspace-name-container">
          <i class="bi bi-folder workspace-icon"></i>
          <h6 class="workspace-name">{{ name }}</h6>
        </div>

        <!-- Status Badges -->
        <Badge
          v-if="showStatusBadges && status === 'UNAVAILABLE'"
          :value="'X'"
          variant="red"
          size="s"
        />
        <Badge
          v-else-if="showStatusBadges && status === 'OFFLINE'"
          :value="'OFFLINE'"
          variant="red"
          size="s"
        />
        <Badge
          v-else-if="showStatusBadges && status === 'UNKNOWN'"
          :value="'?'"
          variant="yellow"
          size="s"
        />
        <span v-else class="workspace-badge">{{ badgeValue }}</span>
      </div>
      <div class="workspace-card-description">
        {{ description }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@/components/Badge.vue';

interface Props {
  name: string;
  description: string;
  selected: boolean;
  badgeValue?: string | number;
  status?: 'AVAILABLE' | 'UNAVAILABLE' | 'OFFLINE' | 'UNKNOWN';
  showStatusBadges?: boolean;
}

withDefaults(defineProps<Props>(), {
  badgeValue: '0',
  status: 'AVAILABLE',
  showStatusBadges: false
});

defineEmits<{
  select: [];
}>();
</script>

<style scoped>
.workspace-selection-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 2px solid rgba(94, 100, 255, 0.15);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-selection-card:hover:not(.active) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.3);
}

.workspace-selection-card.active {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.workspace-selection-card.active .workspace-name {
  color: #374151;
}

.workspace-selection-card.active .workspace-card-description {
  color: #6b7280;
}

.workspace-selection-card.active .workspace-badge {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
}

.workspace-selection-card.active .workspace-icon {
  color: #5e64ff;
}

/* Card Content */
.workspace-card-content {
  width: 100%;
  position: relative;
}

.workspace-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.workspace-name-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
  letter-spacing: 0.01em;
}

.workspace-icon {
  font-size: 0.8rem;
  color: #5e64ff;
  transition: all 0.2s ease;
}

.workspace-card-description {
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
  margin: 0;
}

.workspace-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
}
</style>
