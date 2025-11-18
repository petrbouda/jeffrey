<template>
  <div class="dashboard-card" :class="[props.variant]">
    <div v-if="props.icon" class="dashboard-icon" :style="{ background: props.iconBgColor, color: props.iconColor }">
      <i :class="['bi', `bi-${props.icon}`]"></i>
    </div>
    <div class="dashboard-content">
      <div class="dashboard-title">
        {{ props.title }}
        <slot name="title-action"></slot>
      </div>

      <!-- Main Value Display -->
      <div class="dashboard-value">{{ props.value }}</div>

      <!-- Dual Values Display -->
      <div v-if="props.valueA !== undefined && props.valueA !== null && props.valueB !== undefined && props.valueB !== null" class="dashboard-dual-values">
        <div class="dual-value-item">
          <span class="value-label">{{ props.labelA || 'A' }}:</span>
          <span class="value-number">{{ props.valueA }}</span>
        </div>
        <div class="value-divider">
          <div class="divider-line-vertical"></div>
        </div>
        <div class="dual-value-item">
          <span class="value-label">{{ props.labelB || 'B' }}:</span>
          <span class="value-number">{{ props.valueB }}</span>
        </div>
      </div>

      <!-- Single Value A Display (with dual-value styling) -->
      <div v-else-if="props.valueA !== undefined && props.valueA !== null" class="dashboard-dual-values single-value">
        <div class="dual-value-item">
          <span class="value-label">{{ props.labelA || 'Value' }}:</span>
          <span class="value-number">{{ props.valueA }}</span>
        </div>
      </div>

      <!-- Optional subtitle text (falls back if no valueA or valueB) -->
      <div v-else-if="props.subtitle" class="dashboard-subtitle">{{ props.subtitle }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">

const props = defineProps<{
  title: string;
  value?: string | number;
  icon?: string;
  variant?: 'highlight' | 'danger' | 'warning' | 'info' | 'success' | '';
  subtitle?: string;
  iconBgColor?: string;
  iconColor?: string;
  // New dual value props
  valueA?: string | number;
  valueB?: string | number;
  labelA?: string;
  labelB?: string;
}>();
</script>

<style scoped>
.dashboard-card {
  display: flex;
  align-items: center;
  padding: 0.875rem 1rem;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;
  border-left: 3px solid transparent;
  text-align: center;
}

.dashboard-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.1);
}

.dashboard-card.highlight {
  border-left-color: #4285F4;
}

.dashboard-card.danger {
  border-left-color: #EA4335;
}

.dashboard-card.warning {
  border-left-color: #FBBC05;
}

.dashboard-card.info {
  border-left-color: #34A853;
}

.dashboard-card.success {
  border-left-color: #28a745;
}

.dashboard-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f5f8ff;
  color: #4285F4;
  margin-right: 0.75rem;
  font-size: 1.125rem;
}

.dashboard-card.danger .dashboard-icon {
  background: #fff8f0;
  color: #EA4335;
}

.dashboard-content {
  flex: 1;
  text-align: center;
}

.dashboard-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #444;
  margin-bottom: 0.15rem;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.dashboard-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #111;
  margin-bottom: 0.15rem;
  line-height: 1.2;
}

.dashboard-subtitle {
  font-size: 0.75rem;
  color: #777;
}

/* New styles for dual values */
.dashboard-dual-values {
  display: flex;
  align-items: center;
  justify-content: space-around;
  margin-top: 0.35rem;
  padding: 0.35rem 0 0.15rem 0;
  border-top: 1px dashed #e5e5e5;
}

/* Single value styling to match dual values */
.dashboard-dual-values.single-value {
  justify-content: center;
}

.dashboard-dual-values.single-value .dual-value-item {
  flex: 0 1 auto;
  min-width: 80%;
}

.dual-value-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.value-label {
  font-size: 0.65rem;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  margin-bottom: 0.1rem;
  letter-spacing: 0.3px;
}

.value-number {
  font-size: 0.9rem;
  font-weight: 700;
  color: #333;
  line-height: 1.2;
}

.value-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0 8px;
}

.divider-line-vertical {
  width: 1px;
  height: 24px;
  background-color: #e5e5e5;
}

/* Card variants affect value colors */
.dashboard-card.highlight .value-number {
  color: #4285F4;
}

.dashboard-card.danger .value-number {
  color: #EA4335;
}

.dashboard-card.warning .value-number {
  color: #FBBC05;
}

.dashboard-card.info .value-number {
  color: #34A853;
}

.dashboard-card.success .value-number {
  color: #28a745;
}
</style>
