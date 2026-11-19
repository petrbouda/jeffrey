<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<!--
  A titled section inside an AboutPanel: a muted icon + bold title, then arbitrary slotted content
  (FeatureGrid, prose paragraphs, lists, diagrams). Slotted prose gets consistent typography.
-->
<template>
  <section class="about-section">
    <h6 class="about-section-title">
      <i v-if="icon" class="bi" :class="icon"></i>
      <span>{{ title }}</span>
    </h6>
    <div class="about-section-body">
      <slot></slot>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  icon?: string;
  title: string;
}>();
</script>

<style scoped>
.about-section {
  margin-bottom: 1.5rem;
}

.about-section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-dark);
  margin: 1.5rem 0 1rem;
}

.about-section-title i {
  color: var(--color-text-muted);
}

.about-section-body :deep(p) {
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-text);
  margin: 0 0 0.85rem;
}

.about-section-body :deep(p:last-child) {
  margin-bottom: 0;
}

.about-section-body :deep(ul) {
  margin: 0 0 0.85rem;
  padding-left: 1.1rem;
}

.about-section-body :deep(li) {
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text);
}
</style>
