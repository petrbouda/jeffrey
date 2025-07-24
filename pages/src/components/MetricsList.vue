<template>
  <div class="metrics-list-container">
    <!-- Controls -->
    <div v-if="showControls" class="metrics-controls">
      <div class="metrics-controls-left">
        <slot name="controls-left"></slot>
      </div>
      <div class="metrics-controls-right">
        <div v-if="sortable" class="sort-controls">
          <label class="sort-label">Sort by:</label>
          <div class="btn-group" role="group">
            <button
              v-for="option in sortOptions"
              :key="option.key"
              type="button"
              class="btn btn-outline-secondary btn-sm"
              :class="{ active: currentSort === option.key }"
              @click="handleSort(option.key)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <slot name="controls-right"></slot>
      </div>
    </div>

    <!-- Metrics List -->
    <div class="metrics-list" :class="listClasses">
      <div
        v-for="(item, index) in sortedItems"
        :key="getItemKey(item, index)"
        class="metrics-item"
        :class="getItemClass(item, index)"
        @click="onItemClick(item, index)"
      >
        <slot name="item" :item="item" :index="index">
          <!-- Default Item Layout -->
          <div class="item-header">
            <div class="item-title">
              <slot name="item-title" :item="item">
                {{ getItemTitle(item) }}
              </slot>
            </div>
            <div class="item-actions">
              <slot name="item-actions" :item="item"></slot>
            </div>
          </div>
          
          <div v-if="showMetrics" class="item-metrics">
            <Badge
              v-for="metric in metrics"
              :key="`metric-${metric.key}`"
              :key-label="metric.label"
              :value="formatMetricValue(getMetricValue(item, metric.key), metric)"
              :variant="getMetricVariant(metric, item)"
              size="s"
            />
          </div>
          
          <div v-if="showSubtitle" class="item-subtitle">
            <slot name="item-subtitle" :item="item">
              {{ getItemSubtitle(item) }}
            </slot>
          </div>
        </slot>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="metrics-loading">
      <div class="loading-content">
        <div class="spinner-border spinner-border-sm" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
        <span class="loading-text">{{ loadingText }}</span>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && items.length === 0" class="metrics-empty">
      <slot name="empty">
        <div class="empty-content">
          <i class="bi bi-bar-chart empty-icon"></i>
          <p class="empty-text">{{ emptyText }}</p>
        </div>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Badge from '@/components/Badge.vue'

export interface MetricDefinition {
  key: string
  label: string
  type?: 'number' | 'duration' | 'bytes' | 'percentage' | 'text'
  formatter?: (value: any) => string
  class?: string | ((value: any, item: any) => string)
  threshold?: {
    warning?: number
    danger?: number
  }
}

export interface SortOption {
  key: string
  label: string
  compare?: (a: any, b: any) => number
}

interface Props {
  items: any[]
  metrics?: MetricDefinition[]
  sortOptions?: SortOption[]
  defaultSort?: string
  titleKey?: string
  subtitleKey?: string
  itemKey?: string | ((item: any, index: number) => string)
  itemClass?: string | ((item: any, index: number) => string)
  loading?: boolean
  loadingText?: string
  emptyText?: string
  showControls?: boolean
  showMetrics?: boolean
  showSubtitle?: boolean
  sortable?: boolean
  selectable?: boolean
  multiSelect?: boolean
  compact?: boolean
  striped?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  metrics: () => [],
  sortOptions: () => [],
  titleKey: 'name',
  subtitleKey: 'description',
  itemKey: 'id',
  loading: false,
  loadingText: 'Loading...',
  emptyText: 'No items available',
  showControls: true,
  showMetrics: true,
  showSubtitle: false,
  sortable: true,
  selectable: false,
  multiSelect: false,
  compact: false,
  striped: false
})

const emit = defineEmits<{
  'item-click': [item: any, index: number]
  'item-select': [item: any, selected: boolean]
  'sort-change': [sortKey: string]
}>()

// Reactive state
const currentSort = ref(props.defaultSort || props.sortOptions[0]?.key || '')
const selectedItems = ref<Set<string>>(new Set())

// Computed properties
const listClasses = computed(() => ({
  'metrics-list-compact': props.compact,
  'metrics-list-striped': props.striped,
  'metrics-list-selectable': props.selectable
}))

const sortedItems = computed(() => {
  if (!currentSort.value || !props.sortable) {
    return props.items
  }

  const sortOption = props.sortOptions.find(opt => opt.key === currentSort.value)
  if (!sortOption) {
    return props.items
  }

  const sorted = [...props.items]
  
  if (sortOption.compare) {
    sorted.sort(sortOption.compare)
  } else {
    // Default sorting by the sort key
    sorted.sort((a, b) => {
      const aVal = getMetricValue(a, currentSort.value)
      const bVal = getMetricValue(b, currentSort.value)
      
      // Sort in descending order by default for metrics
      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return bVal - aVal
      }
      
      return String(bVal).localeCompare(String(aVal))
    })
  }
  
  return sorted
})

// Methods
const getItemKey = (item: any, index: number): string => {
  if (typeof props.itemKey === 'function') {
    return props.itemKey(item, index)
  }
  return item[props.itemKey] || index.toString()
}

const getItemClass = (item: any, index: number): string => {
  const classes = []
  
  if (props.selectable) {
    const itemId = getItemKey(item, index)
    if (selectedItems.value.has(itemId)) {
      classes.push('selected')
    }
  }
  
  if (typeof props.itemClass === 'function') {
    classes.push(props.itemClass(item, index))
  } else if (props.itemClass) {
    classes.push(props.itemClass)
  }
  
  return classes.join(' ')
}

const getItemTitle = (item: any): string => {
  return item[props.titleKey] || ''
}

const getItemSubtitle = (item: any): string => {
  return item[props.subtitleKey] || ''
}

const getMetricValue = (item: any, key: string): any => {
  return key.split('.').reduce((obj, k) => obj?.[k], item)
}

const formatMetricValue = (value: any, metric: MetricDefinition): string => {
  if (metric.formatter) {
    return metric.formatter(value)
  }
  
  if (value == null) return '-'
  
  switch (metric.type) {
    case 'number':
      return typeof value === 'number' ? value.toLocaleString() : String(value)
    case 'duration':
      return formatDuration(value)
    case 'bytes':
      return formatBytes(value)
    case 'percentage':
      return `${Number(value).toFixed(1)}%`
    default:
      return String(value)
  }
}


const getMetricVariant = (metric: MetricDefinition, item: any): string => {
  const value = getMetricValue(item, metric.key)
  
  // Special handling for HTTP status codes
  if (metric.key === 'statusCode' && typeof value === 'number') {
    if (value >= 200 && value < 300) return 'success'  // 2xx - Success
    if (value >= 300 && value < 400) return 'info'     // 3xx - Redirect
    if (value >= 400 && value < 500) return 'warning'  // 4xx - Client Error
    if (value >= 500) return 'danger'                  // 5xx - Server Error
    return 'info' // Other status codes
  }
  
  // Check if there's a custom class function that might indicate variant
  if (metric.class) {
    if (typeof metric.class === 'function') {
      const customClass = metric.class(value, item)
      if (customClass.includes('primary')) return 'primary'
      if (customClass.includes('info')) return 'info'
      if (customClass.includes('secondary')) return 'secondary'
      if (customClass.includes('success')) return 'success'
      if (customClass.includes('warning')) return 'warning'
      if (customClass.includes('danger')) return 'danger'
    } else if (typeof metric.class === 'string') {
      if (metric.class.includes('primary')) return 'primary'
      if (metric.class.includes('info')) return 'info'
      if (metric.class.includes('secondary')) return 'secondary'
      if (metric.class.includes('success')) return 'success'
      if (metric.class.includes('warning')) return 'warning'
      if (metric.class.includes('danger')) return 'danger'
    }
  }
  
  // Apply threshold-based variants
  if (metric.threshold && typeof value === 'number') {
    if (metric.threshold.danger && value >= metric.threshold.danger) {
      return 'danger'
    } else if (metric.threshold.warning && value >= metric.threshold.warning) {
      return 'warning'
    } else {
      return 'success'
    }
  }
  
  // Default to info variant
  return 'info'
}

const handleSort = (sortKey: string) => {
  currentSort.value = sortKey
  emit('sort-change', sortKey)
}

const onItemClick = (item: any, index: number) => {
  if (props.selectable) {
    const itemId = getItemKey(item, index)
    const isSelected = selectedItems.value.has(itemId)
    
    if (props.multiSelect) {
      if (isSelected) {
        selectedItems.value.delete(itemId)
      } else {
        selectedItems.value.add(itemId)
      }
    } else {
      selectedItems.value.clear()
      if (!isSelected) {
        selectedItems.value.add(itemId)
      }
    }
    
    emit('item-select', item, !isSelected)
  }
  
  emit('item-click', item, index)
}

// Utility formatters
const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}m`
}

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}
</script>

<style scoped>
.metrics-list-container {
  position: relative;
}

.metrics-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-4) 0;
  gap: var(--spacing-4);
}

.metrics-controls-left,
.metrics-controls-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.sort-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin: 0;
  font-weight: var(--font-weight-medium);
}

.metrics-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.metrics-list-compact {
  gap: var(--spacing-2);
}

.metrics-list-striped .metrics-item:nth-child(even) {
  background-color: rgba(0, 0, 0, 0.02);
}

.metrics-item {
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-4) var(--spacing-5);
  transition: all var(--transition-fast);
  cursor: default;
}

.metrics-list-selectable .metrics-item {
  cursor: pointer;
}

.metrics-item:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-sm);
}

.metrics-item.selected {
  background-color: var(--color-primary-light);
  border-color: var(--color-primary);
}

.metrics-list-compact .metrics-item {
  padding: var(--spacing-3) var(--spacing-4);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-3);
}

.item-title {
  flex: 1;
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  line-height: var(--line-height-tight);
}

.item-actions {
  display: flex;
  gap: var(--spacing-2);
}

.item-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}


.item-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-style: italic;
  line-height: var(--line-height-relaxed);
}

.metrics-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-12) var(--spacing-8);
}

.loading-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-4) var(--spacing-6);
  background-color: var(--color-white);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.loading-text {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.metrics-empty {
  padding: var(--spacing-12) var(--spacing-8);
  text-align: center;
}

.empty-content {
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: var(--font-size-xxxl);
  margin-bottom: var(--spacing-4);
  opacity: 0.5;
}

.empty-text {
  margin: 0;
  font-size: var(--font-size-base);
}


/* Responsive adjustments */
@media (max-width: 767.98px) {
  .metrics-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .metrics-controls-left,
  .metrics-controls-right {
    justify-content: center;
  }
  
  .sort-controls {
    flex-direction: column;
    align-items: stretch;
    gap: var(--spacing-2);
  }
  
  .btn-group {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-1);
  }
  
  .item-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .item-metrics {
    justify-content: center;
  }
  
}
</style>
