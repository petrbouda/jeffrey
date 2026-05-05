<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
defineProps<{
  to: string
  title: string
  description: string
  icon: string
  role: string
  components: string[]
  ctaText: string
  variant?: 'primary' | 'secondary'
}>()
</script>

<template>
  <router-link
    :to="to"
    class="product-card"
    :class="{ 'variant-secondary': variant === 'secondary' }"
  >
    <div class="card-rail"></div>

    <div class="card-head">
      <div class="card-icon">
        <i :class="icon"></i>
      </div>
      <div class="card-titling">
        <div class="card-role">{{ role }}</div>
        <h3 class="card-title">{{ title }}</h3>
      </div>
    </div>

    <p class="card-desc">{{ description }}</p>

    <div class="card-chips">
      <span v-for="c in components" :key="c" class="card-chip">{{ c }}</span>
    </div>

    <div class="card-cta">
      <span>{{ ctaText }}</span>
      <i class="bi bi-arrow-right card-arrow"></i>
    </div>
  </router-link>
</template>

<style scoped>
.product-card {
  --accent: #5e64ff;
  --accent-tint: #eef0fb;
  --accent-soft: #fafbff;
  --accent-shadow: rgba(94, 100, 255, 0.35);

  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 18px;
  text-decoration: none !important;
  color: inherit;
  overflow: hidden;
  position: relative;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.product-card.variant-secondary {
  --accent: #10b981;
  --accent-tint: #e6f8f0;
  --accent-soft: #f5fbf8;
  --accent-shadow: rgba(16, 185, 129, 0.35);
}

.product-card:hover {
  transform: translateY(-3px);
  border-color: var(--accent);
  box-shadow: 0 28px 50px -28px var(--accent-shadow);
}

.card-rail {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 6px;
  background: var(--accent);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 30px 28px 8px;
}

.card-icon {
  width: 64px;
  height: 64px;
  background: var(--accent-tint);
  color: var(--accent);
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 32px;
  flex-shrink: 0;
}

.card-role {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #8492a6;
}

.card-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 4px 0 0;
  color: #1a1f36;
  line-height: 1.05;
}

.card-desc {
  margin: 18px 28px 22px;
  color: #5e6e82;
  font-size: 15px;
  line-height: 1.55;
}

.card-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 28px 24px;
}

.card-chip {
  font-size: 12px;
  color: #2a2f37;
  background: #f3f5f8;
  border: 1px solid #e8ecf1;
  border-radius: 999px;
  padding: 4px 10px;
  font-weight: 500;
}

.card-cta {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  border-top: 1px solid #e8ecf1;
  color: var(--accent);
  font-weight: 600;
  font-size: 14.5px;
  background: var(--accent-soft);
}

.card-arrow {
  transition: transform 0.15s ease;
}

.product-card:hover .card-arrow {
  transform: translateX(4px);
}
</style>
