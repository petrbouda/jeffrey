<template>
  <!-- Differential entry: plain anchor with a lock icon until a secondary profile is set -->
  <a
    v-if="item.differentialType"
    href="#"
    class="nav-item"
    :class="{ active: isDifferentialActive }"
    @click.prevent="emit('navigate-differential', item.differentialType)"
  >
    <i class="bi" :class="item.icon"></i>
    <span>{{ item.label }}</span>
    <i
      v-if="!hasSecondaryProfile"
      class="bi bi-lock ms-auto"
      title="Select a secondary profile to enable this page"
    ></i>
  </a>

  <!-- Regular entry: router-link with either the default active-class or manual matching -->
  <router-link
    v-else
    :to="targetPath"
    class="nav-item"
    :class="[
      item.cssClass ?? '',
      subitem ? 'nav-subitem' : '',
      { 'disabled-feature': isDisabled, active: isManuallyActive }
    ]"
    :active-class="usesDefaultActiveClass ? 'active' : undefined"
  >
    <i class="bi" :class="item.icon"></i>
    <span>{{ item.label }}</span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { DifferentialType, ProfileNavItem } from '@/views/profiles/navigation/profileNavConfig';

const props = defineProps<{
  item: ProfileNavItem;
  profileId: string;
  isFeatureDisabled: (key: string) => boolean;
  hasSecondaryProfile?: boolean;
  subitem?: boolean;
}>();

const emit = defineEmits<{
  (e: 'navigate-differential', type: DifferentialType): void;
}>();

const route = useRoute();

const targetPath = computed<string>(() => {
  if (!props.item.path) {
    return '';
  }
  return props.item.path(props.profileId);
});

const isDisabled = computed<boolean>(() => {
  if (!props.item.disabledKeys) {
    return false;
  }
  return props.item.disabledKeys.some(key => props.isFeatureDisabled(key));
});

// Query-mode items handle the active state manually; everything else relies on
// router-link's active-class (kept as the default `router-link-active` for manual items,
// mirroring the original markup which set no active-class there).
const usesDefaultActiveClass = computed<boolean>(() => {
  return !props.item.activeQueryMode;
});

const matchesQueryMode = computed<boolean>(() => {
  const mode = route.query.mode;
  if (props.item.activeQueryMode === 'server') {
    return mode === 'server' || !mode;
  }
  return mode === 'client';
});

const isManuallyActive = computed<boolean>(() => {
  if (props.item.activeQueryMode) {
    if (!props.item.activePathIncludes) {
      return false;
    }
    return route.path.includes(props.item.activePathIncludes) && matchesQueryMode.value;
  }
  if (props.item.activeExactPath) {
    return route.path === targetPath.value;
  }
  return false;
});

const isDifferentialActive = computed<boolean>(() => {
  if (!props.item.activePathIncludes) {
    return false;
  }
  return route.path.includes(props.item.activePathIncludes);
});
</script>
