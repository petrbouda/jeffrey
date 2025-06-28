<template>
  <section class="dashboard-section">
    <div class="groups-controls">
      <div class="sort-controls">
        <div class="sort-button-group">
          <button 
            @click="sortBy = 'maxExecutionTime'"
            :class="['sort-btn', { 'active': sortBy === 'maxExecutionTime' }]"
          >
            MAX
          </button>
          <button 
            @click="sortBy = 'p99ExecutionTime'"
            :class="['sort-btn', { 'active': sortBy === 'p99ExecutionTime' }]"
          >
            P99
          </button>
          <button 
            @click="sortBy = 'p95ExecutionTime'"
            :class="['sort-btn', { 'active': sortBy === 'p95ExecutionTime' }]"
          >
            P95
          </button>
          <button 
            @click="sortBy = 'errorCount'"
            :class="['sort-btn', { 'active': sortBy === 'errorCount' }]"
          >
            Errors
          </button>
          <button 
            @click="sortBy = 'count'"
            :class="['sort-btn', { 'active': sortBy === 'count' }]"
          >
            Executions
          </button>
        </div>
      </div>
      
      <button 
        v-if="getAllGroups().length > maxDisplayedGroups"
        @click="showAllGroups = !showAllGroups"
        class="show-all-link"
      >
        {{ showAllGroups ? 'Show Less' : `Show All (${getAllGroups().length})` }}
      </button>
    </div>
    
    <div class="group-list">
      <div 
        v-for="group in getDisplayedGroups()" 
        :key="group.group"
        class="group-row"
        @click="handleGroupClick(group.group)"
        :class="{ 
          'selected': selectedGroup === group.group,
          'has-errors': group.errorCount > 0
        }"
      >
        <div class="group-primary">
          <div class="group-info">
            <div class="group-name-display">
              <span class="group-name">{{ group.group }}</span>
            </div>
            <div class="group-badges">
              <div class="request-count-badge">
                <span class="count-number">{{ group.count.toLocaleString() }}</span>
                <span class="count-label">executions</span>
              </div>
              <div class="metric-badge info-badge">
                <span class="metric-number">{{ FormattingService.formatDuration2Units(group.maxExecutionTime || 0) }}</span>
                <span class="metric-label">Max</span>
              </div>
              <div class="metric-badge info-badge">
                <span class="metric-number">{{ FormattingService.formatDuration2Units(group.p99ExecutionTime || 0) }}</span>
                <span class="metric-label">P99</span>
              </div>
              <div class="metric-badge info-badge">
                <span class="metric-number">{{ FormattingService.formatDuration2Units(group.p95ExecutionTime || 0) }}</span>
                <span class="metric-label">P95</span>
              </div>
              <div class="metric-badge secondary-badge">
                <span class="metric-number">{{ FormattingService.formatNumber(group.totalRowsProcessed) }}</span>
                <span class="metric-label">Rows</span>
              </div>
              <div class="metric-badge" :class="group.errorCount > 0 ? 'danger-badge' : 'success-badge'">
                <span class="metric-number">{{ group.errorCount }}</span>
                <span class="metric-label">Errors</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import JdbcGroup from '@/services/profile/custom/jdbc/JdbcGroup.ts';
import FormattingService from "@/services/FormattingService.ts";

interface Props {
  groups: JdbcGroup[];
  selectedGroup?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedGroup: null
});

const emit = defineEmits<{
  groupClick: [group: string]
}>();

// Reactive state
const showAllGroups = ref(false);
const sortBy = ref('maxExecutionTime');
const maxDisplayedGroups = 10;

const getAllGroups = () => {
  if (!props.groups) return [];
  
  const groups = props.groups;
  
  // Sort based on selected criteria
  switch (sortBy.value) {
    case 'maxExecutionTime':
      return groups.sort((a, b) => b.maxExecutionTime - a.maxExecutionTime);
    case 'p99ExecutionTime':
      return groups.sort((a, b) => b.p99ExecutionTime - a.p99ExecutionTime);
    case 'p95ExecutionTime':
      return groups.sort((a, b) => b.p95ExecutionTime - a.p95ExecutionTime);
    case 'errorCount':
      return groups.sort((a, b) => b.errorCount - a.errorCount);
    default: // 'count'
      return groups.sort((a, b) => b.count - a.count);
  }
};

const getDisplayedGroups = () => {
  const allGroups = getAllGroups();
  return showAllGroups.value ? allGroups : allGroups.slice(0, maxDisplayedGroups);
};

const handleGroupClick = (group: string) => {
  emit('groupClick', group);
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.groups-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: #f8f9fa;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.sort-controls {
  display: flex;
  align-items: center;
}

.sort-button-group {
  display: flex;
  gap: 0.5rem;
}

.sort-btn {
  background: #f8faff;
  border: 1px solid #d1d9f0;
  color: #667eea;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  padding: 0.375rem 0.75rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.sort-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.sort-btn.active {
  background: #667eea;
  color: white;
  border-color: #667eea;
  font-weight: 600;
}

.sort-btn.active:hover {
  background: #5a67d8;
  border-color: #5a67d8;
}

.show-all-link {
  background: #f8faff;
  border: 1px solid #d1d9f0;
  color: #667eea;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  padding: 0.375rem 0.75rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.show-all-link:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.group-list {
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.group-row {
  border-bottom: 1px solid #f1f3f4;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.group-row:last-child {
  border-bottom: none;
}

.group-row:hover {
  background: #f8f9fa;
}

.group-row.selected {
  background: #f8faff;
  border-left: 4px solid #667eea;
}

.group-row.has-errors {
  background: #fef2f2 !important;
}

.group-row.selected.has-errors {
  background: #fceded;
  border-left: 4px solid #667eea;
}

.group-primary {
  padding: 0.75rem 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.group-info {
  flex: 1;
  min-width: 0;
}

.group-name-display {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  font-style: italic;
  background: #f7fafc;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.125rem;
  max-width: 100%;
}

.group-name {
  color: #2d3748;
  font-weight: 500;
  font-style: italic;
}

.group-badges {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
}

.request-count-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #5e64ff;
  color: white;
  padding: 0.375rem 0.75rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.2);
  min-width: 70px;
}

.count-number {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 0.125rem;
}

.count-label {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.9;
  font-weight: 500;
}

.metric-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.375rem 0.75rem;
  border-radius: 8px;
  min-width: 70px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.metric-badge .metric-number {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 0.125rem;
}

.metric-badge .metric-label {
  font-size: 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.9;
  font-weight: 500;
}

/* Badge variants */
.info-badge {
  background: #39afd1;
  color: white;
}

.secondary-badge {
  background: #7780bf;
  color: white;
}

.success-badge {
  background: #00d27a;
  color: white;
}

.warning-badge {
  background: #f5803e;
  color: white;
}

.danger-badge {
  background: #e63757;
  color: white;
}

@media (max-width: 768px) {
  .group-badges {
    gap: 0.25rem;
  }
  
  .metric-badge {
    min-width: 60px;
    padding: 0.3rem 0.6rem;
  }
  
  .metric-badge .metric-number {
    font-size: 0.8rem;
  }
  
  .metric-badge .metric-label {
    font-size: 0.55rem;
  }
}
</style>
