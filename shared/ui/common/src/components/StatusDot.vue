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
  A small round indicator of a state — online, offline, a verdict, available — on its own or beside a
  label. Takes a tone, never a colour, so every dot in the app draws from the same tokens. With a
  `label` the dot is announced and shown as its tooltip; without one it is decoration, and the text
  beside it carries the meaning.
-->
<template>
  <span
    class="status-dot"
    :class="[`tone-${tone}`, `size-${size}`, { halo }]"
    :title="label"
    :role="label ? 'img' : undefined"
    :aria-label="label"
    :aria-hidden="label ? undefined : 'true'"
  ></span>
</template>

<script setup lang="ts">
import type { StatusDotSize, StatusDotTone } from '@shared/types/ui.ts';

interface Props {
  tone: StatusDotTone;
  size?: StatusDotSize;
  /** A soft ring in a light shade of the tone, for a dot that is a page's headline verdict. */
  halo?: boolean;
  label?: string;
}

withDefaults(defineProps<Props>(), {
  size: 'm',
  halo: false,
  label: undefined
});
</script>

<style scoped>
.status-dot {
  display: inline-block;
  flex-shrink: 0;
  border-radius: var(--radius-circle);
}

.size-xs {
  width: 6px;
  height: 6px;
}

.size-s {
  width: 7px;
  height: 7px;
}

.size-m {
  width: 8px;
  height: 8px;
}

.size-l {
  width: 10px;
  height: 10px;
}

.tone-success {
  background: var(--color-success);
}

.tone-warning {
  background: var(--color-warning);
}

.tone-danger {
  background: var(--color-danger);
}

.tone-muted {
  background: var(--color-text-light);
}

.tone-faint {
  background: var(--color-border-input);
}

/* An outline follows the dot's radius and takes no layout space, so the ring needs no shadow. */
.halo.tone-success {
  outline: 4px solid var(--color-success-light);
}

.halo.tone-warning {
  outline: 4px solid var(--color-warning-light);
}

.halo.tone-danger {
  outline: 4px solid var(--color-danger-light);
}
</style>
