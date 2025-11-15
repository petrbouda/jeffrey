<template>
  <div
    class="workspace-selection-card"
    :class="[
      { 'active': selected },
      workspaceTypeClass
    ]"
    @click="$emit('select')"
  >
    <div class="workspace-card-content">
      <div class="workspace-card-header">
        <div class="workspace-name-container">
          <i :class="workspaceIcon"></i>
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
          :value="'UNKNOWN'"
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
import { computed } from 'vue';
import Badge from '@/components/Badge.vue';

interface Props {
  name: string;
  description: string;
  selected: boolean;
  workspaceType?: 'LOCAL' | 'SANDBOX' | 'REMOTE';
  badgeValue?: string | number;
  status?: 'AVAILABLE' | 'UNAVAILABLE' | 'OFFLINE' | 'UNKNOWN';
  showStatusBadges?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  workspaceType: 'LOCAL',
  badgeValue: '0',
  status: 'AVAILABLE',
  showStatusBadges: false
});

defineEmits<{
  select: [];
}>();

const workspaceTypeClass = computed(() => {
  if (props.workspaceType === 'SANDBOX') return 'sandbox';
  if (props.workspaceType === 'REMOTE') return 'remote';
  return 'local';
});

const workspaceIcon = computed(() => {
  const baseClasses = 'bi';
  if (props.workspaceType === 'REMOTE') return `${baseClasses} bi-display workspace-remote-icon`;
  if (props.workspaceType === 'SANDBOX') return `${baseClasses} bi-house workspace-sandbox-icon`;
  return `${baseClasses} bi-folder workspace-local-icon`;
});
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

.workspace-selection-card.active .workspace-local-icon {
  color: #5e64ff;
}

/* Local Workspace (Blue) */
.workspace-selection-card.local {
  border-color: rgba(94, 100, 255, 0.15);
}

.workspace-selection-card.local .workspace-name {
  color: #374151;
}

.workspace-selection-card.local .workspace-card-description {
  color: #6b7280;
}

.workspace-selection-card.local .workspace-badge {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
}

.workspace-selection-card.local .workspace-local-icon {
  color: #5e64ff;
}

.workspace-selection-card.local:hover:not(.active) {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.workspace-selection-card.local.active {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.workspace-selection-card.local.active .workspace-badge {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
}

.workspace-selection-card.local.active .workspace-local-icon {
  color: #5e64ff;
}

/* Sandbox Workspace (Yellow) */
.workspace-selection-card.sandbox {
  border-color: rgba(245, 158, 11, 0.15);
}

.workspace-selection-card.sandbox .workspace-name {
  color: #374151;
}

.workspace-selection-card.sandbox .workspace-card-description {
  color: #6b7280;
}

.workspace-selection-card.sandbox .workspace-badge {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.workspace-selection-card.sandbox .workspace-sandbox-icon {
  color: #f59e0b;
}

.workspace-selection-card.sandbox:hover:not(.active) {
  border-color: rgba(245, 158, 11, 0.3);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.1);
}

.workspace-selection-card.sandbox.active {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border-color: #f59e0b;
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.2);
}

.workspace-selection-card.sandbox.active .workspace-badge {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
}

.workspace-selection-card.sandbox.active .workspace-sandbox-icon {
  color: #f59e0b;
}

/* Remote Workspace (Teal/Green) */
.workspace-selection-card.remote {
  border-color: rgba(16, 185, 129, 0.15);
}

.workspace-selection-card.remote .workspace-name {
  color: #374151;
}

.workspace-selection-card.remote .workspace-card-description {
  color: #6b7280;
}

.workspace-selection-card.remote .workspace-badge {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.workspace-selection-card.remote .workspace-remote-icon {
  color: #10b981;
}

.workspace-selection-card.remote:hover:not(.active) {
  border-color: rgba(16, 185, 129, 0.3);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.1);
}

.workspace-selection-card.remote.active {
  background: linear-gradient(135deg, #d1fae5, #a7f3d0);
  border-color: #10b981;
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.2);
}

.workspace-selection-card.remote.active .workspace-badge {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.workspace-selection-card.remote.active .workspace-remote-icon {
  color: #10b981;
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

.workspace-remote-icon,
.workspace-sandbox-icon,
.workspace-local-icon {
  font-size: 0.8rem;
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
