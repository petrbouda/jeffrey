<template>
  <div>
    <!-- Integrated Header -->
    <div class="page-header">
      <div class="header-title-section">
        <h4 class="header-title">
          <i v-if="icon" class="bi" :class="icon"></i>
          {{ title }}
        </h4>
        <p v-if="description" class="header-description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="header-actions">
        <slot name="actions"></slot>
      </div>
    </div>

    <!-- Content Area -->
    <div class="page-header-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  icon?: string;
}>();
</script>

<style scoped>
/* Integrated Header */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  margin: -1rem -1rem 0 -1rem; /* Negative margin to extend to card edges */
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
}

.page-header-content {
  padding: 24px 0 0 0;
}

.header-title-section {
  flex: 1;
}

.header-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 4px 0;
  letter-spacing: -0.01em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title i {
  color: #5e64ff;
  font-size: 1rem;
}

.header-description {
  font-size: 0.8rem;
  color: #6b7280;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* Responsive Design */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px 20px;
  }

  .header-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>
