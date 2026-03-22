<template>
  <div class="metric-card">
    <div class="metric-icon" :class="iconClass">
      <i :class="icon"></i>
    </div>
    <div class="metric-content">
      <h3 class="metric-value">{{ value }}</h3>
      <div class="metric-details" v-if="details">
        {{ details }}
      </div>
      <p class="metric-label">{{ label }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  icon: string;
  iconClass: string;
  value: string;
  details?: string;
  label: string;
}

defineProps<Props>();
</script>

<style scoped>
.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 0;
  display: flex;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.metric-icon {
  width: 80px;
  min-width: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.8rem;
  color: white;
}

.bg-primary-soft { 
  background: linear-gradient(135deg, #0d6efd 0%, #0056b3 100%);
}
.bg-success-soft { 
  background: linear-gradient(135deg, #198754 0%, #0f5132 100%);
}
.bg-warning-soft { 
  background: linear-gradient(135deg, #ffc107 0%, #d39e00 100%);
}
.bg-info-soft { 
  background: linear-gradient(135deg, #0dcaf0 0%, #0aa2c0 100%);
}
.bg-danger-soft { 
  background: linear-gradient(135deg, #dc3545 0%, #b02a37 100%);
}

.metric-content {
  flex-grow: 1;
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.metric-value {
  font-size: 1.2rem;
  font-weight: 600;
  margin: 0;
}

.metric-details {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0.2rem;
}

.metric-label {
  font-size: 0.75rem;
  color: #6c757d;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>