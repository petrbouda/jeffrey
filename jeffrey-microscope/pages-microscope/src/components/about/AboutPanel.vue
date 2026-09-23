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
  Full-width container for a "How It Works" / explainer tab. Renders a gradient icon header with
  title + subtitle, then a default slot for AboutCallout / AboutSection / FeatureGrid content.
  Provides consistent inline-<code> styling for all nested slotted content.
-->
<template>
  <div class="about-panel">
    <div class="about-panel-header">
      <div class="about-panel-icon">
        <i class="bi" :class="icon"></i>
      </div>
      <div>
        <h5 class="about-panel-title">{{ title }}</h5>
        <p v-if="subtitle" class="about-panel-subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div class="about-panel-body">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  icon: string;
  title: string;
  subtitle?: string;
}>();
</script>

<style scoped>
.about-panel {
  width: 100%;
  padding: 1.5rem 0;
}

.about-panel-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
}

.about-panel-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-white);
  font-size: 1.5rem;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-violet-dark) 100%);
}

.about-panel-title {
  margin: 0;
  font-weight: 600;
  color: var(--color-dark);
}

.about-panel-subtitle {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.about-panel-body {
  display: flex;
  flex-direction: column;
}

/* Consistent inline code for everything rendered inside the panel (callouts, sections, cards). */
.about-panel :deep(code) {
  background-color: var(--color-code-bg);
  padding: 0.1rem 0.35rem;
  border-radius: var(--radius-sm);
  font-size: 0.85em;
  color: var(--color-code-text);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}
</style>
