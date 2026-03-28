<template>
  <div class="main-card" :class="{ 'mb-4': bottomMargin }">
    <slot name="header" />
    <div v-if="noPadding">
      <slot />
    </div>
    <div v-else class="main-card-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import '@/styles/shared-components.css'

withDefaults(defineProps<{
  bottomMargin?: boolean
  noPadding?: boolean
}>(), {
  bottomMargin: true,
  noPadding: false
})
</script>
