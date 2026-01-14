<template>
  <div
    class="config-card"
    :class="[
      cardType === 'required' ? 'required-card' : 'optional-card',
      {
        'card-enabled': isEnabled,
        'card-collapsed': collapsible ? !isExpanded : (!isEnabled && cardType !== 'required')
      },
      `theme-${colorTheme}`
    ]"
  >
    <div
      class="config-card-header"
      :class="{ 'clickable-header': collapsible || cardType === 'optional' }"
      @click="handleHeaderClick"
    >
      <div class="card-title-group">
        <i :class="`bi ${icon} card-icon`"></i>
        <div class="card-title-stack">
          <span class="card-title">{{ title }}</span>
          <span class="card-subtitle">{{ subtitle }}</span>
        </div>
      </div>
      <div class="card-header-controls">
        <span v-if="cardType === 'required'" class="required-label">Required</span>
        <label v-if="cardType === 'optional'" class="toggle-switch" @click.stop>
          <input
            type="checkbox"
            class="toggle-input"
            :checked="isEnabled"
            @change="$emit('toggle', $event.target.checked)"
          >
          <span class="toggle-slider"></span>
        </label>
        <i v-if="collapsible" :class="`bi ${isExpanded ? 'bi-chevron-up' : 'bi-chevron-down'} collapse-icon`"></i>
      </div>
    </div>
    <div v-if="collapsible ? isExpanded : (isEnabled || cardType === 'required')" class="config-card-body">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string;
  subtitle: string;
  icon: string;
  cardType?: 'required' | 'optional';
  isEnabled?: boolean;
  colorTheme?: 'default' | 'blue' | 'yellow';
  collapsible?: boolean;
  isExpanded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  cardType: 'optional',
  isEnabled: false,
  colorTheme: 'default',
  collapsible: false,
  isExpanded: true
});

const emit = defineEmits<{
  toggle: [enabled: boolean];
  'toggle-collapse': [];
}>();

const handleHeaderClick = () => {
  // Handle collapse for collapsible cards
  if (props.collapsible) {
    emit('toggle-collapse');
  }
  // Handle enable/disable toggle for optional cards
  else if (props.cardType === 'optional') {
    emit('toggle', !props.isEnabled);
  }
};
</script>

<style scoped>
/* Configuration Card Styling */
.config-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: cardSlideIn 0.3s ease-out;
}

.config-card:hover {
  border-color: rgba(94, 100, 255, 0.15);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.required-card {
  border-color: rgba(239, 68, 68, 0.12);
  background: linear-gradient(135deg, #fefefe, #fef8f8);
}

.required-card:hover {
  border-color: rgba(239, 68, 68, 0.2);
}

.optional-card {
  border-color: rgba(16, 185, 129, 0.12);
  background: linear-gradient(135deg, #fefefe, #f0fdf4);
}

.optional-card:hover {
  border-color: rgba(16, 185, 129, 0.2);
}

@keyframes cardSlideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Card Header */
.config-card-header {
  background: rgba(94, 100, 255, 0.03);
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.clickable-header {
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.clickable-header:hover {
  background: rgba(94, 100, 255, 0.06);
}

.required-card .config-card-header {
  background: rgba(239, 68, 68, 0.03);
  border-bottom-color: rgba(239, 68, 68, 0.08);
}

.required-card .clickable-header:hover {
  background: rgba(239, 68, 68, 0.06);
}

.optional-card .config-card-header {
  background: rgba(16, 185, 129, 0.03);
  border-bottom-color: rgba(16, 185, 129, 0.08);
}

.optional-card .clickable-header:hover {
  background: rgba(16, 185, 129, 0.06);
}

.card-enabled .config-card-header {
  background: rgba(16, 185, 129, 0.08);
  border-bottom-color: rgba(16, 185, 129, 0.18);
}

.card-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-icon {
  color: #5e64ff;
  font-size: 1.1rem;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.required-card .card-icon {
  color: #ef4444;
}

.optional-card .card-icon {
  color: #10b981;
}

.card-enabled .card-icon {
  color: #047857;
}

.card-title {
  font-weight: 600;
  color: #374151;
  font-size: 0.95rem;
  margin: 0;
  line-height: 1;
}

.card-title-stack {
  display: flex;
  align-items: center;
  align-self: center;
  gap: 8px;
}

.card-subtitle {
  font-size: 0.8rem;
  color: #6b7280;
  font-weight: 400;
  margin: 0;
  line-height: 1;
}

.card-header-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.required-label {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: white;
  font-size: 0.65rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 12px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.collapse-icon {
  color: #6b7280;
  font-size: 0.9rem;
  transition: transform 0.2s ease, color 0.2s ease;
  flex-shrink: 0;
}

.clickable-header:hover .collapse-icon {
  color: #374151;
}

.card-collapsed .collapse-icon {
  color: #94a3b8;
}

/* Card Body */
.config-card-body {
  padding: 20px;
}

.card-enabled {
  border-color: rgba(16, 185, 129, 0.3);
  background: linear-gradient(135deg, #fefefe, #ecfdf5);
}

.card-collapsed {
  border-style: dashed;
  border-color: rgba(94, 100, 255, 0.12);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.6), rgba(255, 255, 255, 0.9));
}

.card-collapsed .config-card-header {
  background: rgba(248, 250, 252, 0.9);
  border-bottom-style: dashed;
  border-bottom-color: rgba(94, 100, 255, 0.1);
}

/* Required cards always have solid borders, even when collapsed */
.required-card.card-collapsed {
  border-style: solid;
}

.required-card.card-collapsed .config-card-header {
  border-bottom-style: solid;
}

.card-collapsed .card-title {
  color: #6b7280;
}

.card-collapsed .card-subtitle {
  color: #94a3b8;
}

.card-collapsed .card-icon {
  color: #94a3b8;
}

.card-collapsed .toggle-switch {
  background: rgba(148, 163, 184, 0.25);
}

/* Toggle switch */
.toggle-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  width: 48px;
  height: 26px;
  border-radius: 9999px;
  background: rgba(148, 163, 184, 0.35);
  transition: background 0.25s ease;
  cursor: pointer;
  flex-shrink: 0;
}

.toggle-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  width: 24px;
  height: 24px;
  top: 1px;
  left: 1px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
  transition: transform 0.25s ease, background 0.25s ease;
}

.toggle-input:checked + .toggle-slider {
  transform: translateX(22px);
  background: #5e64ff;
}

.card-enabled .toggle-switch {
  background: rgba(94, 100, 255, 0.35);
}

/* Blue Theme Styling */
.theme-blue.required-card {
  border-color: rgba(2, 132, 199, 0.15);
  background: linear-gradient(135deg, #fefefe, #f0f9ff);
}

.theme-blue.required-card:hover {
  border-color: rgba(2, 132, 199, 0.25);
}

.theme-blue.required-card .config-card-header {
  background: rgba(2, 132, 199, 0.05);
  border-bottom-color: rgba(2, 132, 199, 0.12);
}

.theme-blue.required-card .clickable-header:hover {
  background: rgba(2, 132, 199, 0.08);
}

.theme-blue.required-card .card-icon {
  color: #0284c7;
}

.theme-blue.required-card .required-label {
  background: linear-gradient(135deg, #0284c7, #0369a1);
}

/* Yellow Theme Styling */
.theme-yellow.required-card {
  border-color: rgba(245, 158, 11, 0.15);
  background: linear-gradient(135deg, #fefefe, #fffbeb);
}

.theme-yellow.required-card:hover {
  border-color: rgba(245, 158, 11, 0.25);
}

.theme-yellow.required-card .config-card-header {
  background: rgba(245, 158, 11, 0.05);
  border-bottom-color: rgba(245, 158, 11, 0.12);
}

.theme-yellow.required-card .clickable-header:hover {
  background: rgba(245, 158, 11, 0.08);
}

.theme-yellow.required-card .card-icon {
  color: #d97706;
}

.theme-yellow.required-card .required-label {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}
</style>
