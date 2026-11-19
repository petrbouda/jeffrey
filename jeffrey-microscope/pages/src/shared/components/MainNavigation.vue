<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="navigation-tabs">
    <div class="container-fluid">
      <div class="nav-container">
        <router-link
          v-for="item in items"
          :key="item.to"
          :to="item.to"
          class="nav-pill"
          active-class="active"
          :exact-active-class="item.exact ? 'active' : undefined"
        >
          <i :class="item.icon"></i>
          <span>{{ item.label }}</span>
        </router-link>
      </div>
    </div>
  </div>
  <div class="content-spacer"></div>
</template>

<script setup lang="ts">
import type { NavItem } from '@shared/types/ui';

defineProps<{
  items: NavItem[];
}>();
</script>

<style scoped>
.navigation-tabs {
  background-color: white;
  padding: 0;
  position: relative;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.content-spacer {
  height: 24px;
}

.nav-container {
  display: flex;
  align-items: center;
  padding: 0 1rem;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.nav-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0.75rem 1.5rem;
  border: none;
  background: transparent;
  position: relative;
  color: var(--color-slate-muted);
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 100px;
  border-radius: 0;
  transition: all 0.25s ease;
  text-decoration: none;

  i {
    font-size: 1.25rem;
    margin-bottom: 0.25rem;
    transition: all 0.25s ease;
  }

  span {
    opacity: 0.7;
    transition: all 0.25s ease;
  }

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 3px;
    background-color: transparent;
    transition: background-color 0.25s ease;
  }

  &:hover {
    color: var(--color-slate-text);

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }
  }

  &.active {
    color: var(--color-primary);

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }

    &::after {
      background-color: var(--color-primary);
    }
  }
}
</style>
