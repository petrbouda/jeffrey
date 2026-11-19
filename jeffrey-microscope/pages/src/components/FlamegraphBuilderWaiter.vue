<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!--
  Animated "flamegraph being assembled" waiter, shown while a recording is imported and a
  profile is initialized. Frames stack up bottom-up like a flamegraph being built.
  Purely presentational: pass a `caption` describing the current phase.
-->
<template>
  <div class="fg-waiter">
    <div class="fg-stage">
      <div class="fg" aria-hidden="true">
        <div class="fg-row">
          <span class="fg-seg"></span>
        </div>
        <div class="fg-row">
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
        </div>
        <div class="fg-row">
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
        </div>
        <div class="fg-row">
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
          <span class="fg-seg"></span>
        </div>
      </div>
    </div>

    <h2 class="fg-title">{{ title }}</h2>
    <p class="fg-caption" role="status" aria-live="polite">{{ caption }}</p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string;
  caption?: string;
}

withDefaults(defineProps<Props>(), {
  title: 'Preparing your profile',
  caption: 'Working…'
});
</script>

<style scoped>
.fg-waiter {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  font-family: var(--font-family-base);
}

.fg-stage {
  height: 120px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  margin-bottom: var(--spacing-6);
}

.fg {
  display: flex;
  flex-direction: column-reverse;
  gap: 4px;
  width: 180px;
  filter: drop-shadow(var(--shadow-md));
}

.fg-row {
  display: flex;
  gap: 4px;
  height: 20px;
}

.fg-seg {
  flex: 1;
  border-radius: var(--radius-sm);
  transform: scaleX(0);
  transform-origin: left center;
  animation: fg-grow 2.4s ease-in-out infinite;
}

/* Bottom-up build: each higher row enters a beat later, in lighter brand shades. */
.fg-row:nth-child(1) .fg-seg {
  background: var(--color-primary);
  animation-delay: 0s;
}

.fg-row:nth-child(2) .fg-seg {
  background: var(--color-indigo-pastel);
  animation-delay: 0.2s;
}

.fg-row:nth-child(3) .fg-seg {
  background: var(--color-primary-border);
  animation-delay: 0.4s;
}

.fg-row:nth-child(4) .fg-seg {
  background: var(--color-indigo-accent);
  animation-delay: 0.6s;
}

.fg-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  margin: 0 0 var(--spacing-2);
}

.fg-caption {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
  margin: 0;
}

@keyframes fg-grow {
  0% {
    transform: scaleX(0);
    opacity: 0.35;
  }
  35%,
  70% {
    transform: scaleX(1);
    opacity: 1;
  }
  100% {
    transform: scaleX(1);
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fg-seg {
    animation-duration: 0.001s;
    transform: scaleX(1);
    opacity: 1;
  }
}
</style>
