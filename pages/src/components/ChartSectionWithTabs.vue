<template>
  <section class="dashboard-section">
    <div class="charts-grid">
      <div class="chart-card" :class="{ 'full-width': fullWidth }">
        <div v-if="title" class="chart-header">
          <h4>
            <i v-if="icon" :class="`bi bi-${icon} me-2`"></i>
            {{ title }}
          </h4>
        </div>
        <div class="chart-container-with-tabs">
          <div class="dashboard-tabs">
            <ul class="nav nav-tabs" role="tablist">
              <li v-for="(tab, index) in tabs" :key="tab.id" class="nav-item" role="presentation">
                <button 
                  class="nav-link" 
                  :class="{ active: activeTabIndex === index }" 
                  :id="`${props.idPrefix}${tab.id}-tab`" 
                  data-bs-toggle="tab" 
                  :data-bs-target="`#${props.idPrefix}${tab.id}-tab-pane`" 
                  type="button" 
                  role="tab" 
                  :aria-controls="`${props.idPrefix}${tab.id}-tab-pane`" 
                  :aria-selected="activeTabIndex === index"
                  @click="setActiveTab(index)"
                >
                  <i v-if="tab.icon" :class="`bi bi-${tab.icon} me-2`"></i>{{ tab.label }}
                </button>
              </li>
            </ul>

            <div class="tab-content">
              <div 
                v-for="(tab, index) in tabs" 
                :key="tab.id" 
                class="tab-pane fade" 
                :class="{ 'show active': activeTabIndex === index }" 
                :id="`${props.idPrefix}${tab.id}-tab-pane`" 
                role="tabpanel" 
                :aria-labelledby="`${props.idPrefix}${tab.id}-tab`" 
                tabindex="0"
              >
                <slot :name="tab.id" :tab="tab" :index="index"></slot>
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

interface Tab {
  id: string;
  label: string;
  icon?: string;
}

interface Props {
  title?: string;
  icon?: string;
  fullWidth?: boolean;
  tabs: Tab[];
  idPrefix?: string;
}

const props = withDefaults(defineProps<Props>(), {
  fullWidth: false,
  idPrefix: ''
});

const emit = defineEmits<{
  tabChange: [tabIndex: number, tab: Tab]
}>();

const activeTabIndex = ref(0);

const setActiveTab = (index: number) => {
  activeTabIndex.value = index;
  emit('tabChange', index, props.tabs[index]);
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1.5rem;
}

.chart-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
}

.chart-container-with-tabs {
  min-height: 200px;
}

.dashboard-tabs {
  background-color: #fff;
  border-radius: 0 0 12px 12px;
  flex-grow: 1;
}

.nav-tabs {
  border-bottom: 1px solid #e9ecef;
  padding: 0 1rem;
}

.nav-tabs .nav-link {
  margin-bottom: -1px;
  border-radius: 0;
  padding: 0.75rem 1rem;
  font-size: 0.9rem;
  color: #6c757d;
  border: none;
  border-bottom: 2px solid transparent;
}

.nav-tabs .nav-link.active {
  background-color: transparent;
  color: #0d6efd;
  border-bottom: 2px solid #0d6efd;
}

.nav-tabs .nav-link:hover:not(.active) {
  border-color: transparent;
  color: #212529;
}

.tab-content {
  padding: 1.5rem;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>