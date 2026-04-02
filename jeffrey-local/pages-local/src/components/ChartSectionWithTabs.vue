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
  tabChange: [tabIndex: number, tab: Tab];
}>();

const activeTabIndex = ref(0);

const setActiveTab = (index: number) => {
  activeTabIndex.value = index;
  emit('tabChange', index, props.tabs[index]);
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 1.5rem;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 1rem;
}

.chart-card {
  background: var(--card-bg);
  border: 1px solid var(--card-border-color);
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--card-border-color);
}

.chart-header h4 {
  margin: 0;
  color: var(--color-dark);
  font-size: 1rem;
  font-weight: 600;
}

.chart-container-with-tabs {
  min-height: 100px;
}

.dashboard-tabs {
  background-color: var(--card-bg);
  border-radius: 0 0 var(--card-border-radius) var(--card-border-radius);
}

.nav-tabs {
  border-bottom: 2px solid var(--color-border, #e2e8f0);
  padding: 0;
  gap: 0;
  margin: 0;
}

.nav-tabs .nav-link {
  margin-bottom: -2px;
  border-radius: 0;
  padding: 0.5rem 1rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text-muted, #718096);
  border: none;
  background: none;
  position: relative;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.nav-tabs .nav-link i {
  font-size: 0.85rem;
}

.nav-tabs .nav-link::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: transparent;
  transition: background 0.2s;
}

.nav-tabs .nav-link.active {
  background-color: transparent;
  color: var(--color-primary);
}

.nav-tabs .nav-link.active::after {
  background: var(--color-primary);
}

.nav-tabs .nav-link:hover:not(.active) {
  border-color: transparent;
  color: var(--color-text, #4a5568);
}

.tab-content {
  padding: 1rem;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
