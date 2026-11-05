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

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRoute } from 'vue-router';

// How a header link decides it is the current page: Home only on "/", Docs on every docs page,
// Agent Plugin on its own page, and an in-page anchor (Products) never.
type NavMatch = 'exact' | 'prefix' | 'never';

interface NavLink {
  label: string;
  path: string;
  hash?: string;
  match: NavMatch;
}

const NAV_MATCHERS: Record<NavMatch, (current: string, target: string) => boolean> = {
  exact: (current, target) => current === target,
  prefix: (current, target) => current === target || current.startsWith(`${target}/`),
  never: () => false
};

const CONTACT_URL = 'https://www.linkedin.com/in/petr-bouda/';
const GITHUB_URL = 'https://github.com/petrbouda/jeffrey';
const BLUESKY_URL = 'https://bsky.app/profile/pbouda.bsky.social';
const SCROLLED_THRESHOLD_PX = 8;

const NAV_LINKS: NavLink[] = [
  { label: 'Home', path: '/', match: 'exact' },
  { label: 'Products', path: '/', hash: '#products', match: 'never' },
  { label: 'Docs', path: '/docs', match: 'prefix' },
  { label: 'Agent Plugin', path: '/agent-plugin', match: 'prefix' },
  { label: 'Release Notes', path: '/release-notes', match: 'prefix' }
];

const route = useRoute();
const isMenuOpen = ref(false);
const isScrolled = ref(false);
const currentYear = new Date().getFullYear();

function isActive(link: NavLink): boolean {
  return NAV_MATCHERS[link.match](route.path, link.path);
}

function toggleMenu(): void {
  isMenuOpen.value = !isMenuOpen.value;
}

function closeMenu(): void {
  isMenuOpen.value = false;
}

function onScroll(): void {
  isScrolled.value = window.scrollY > SCROLLED_THRESHOLD_PX;
}

watch(() => route.fullPath, closeMenu);

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll);
});
</script>

<template>
  <div class="site">
    <header class="site-header" :class="{ 'site-header--scrolled': isScrolled }">
      <div class="site-header-row">
        <router-link to="/" class="site-brand" @click="closeMenu">
          <img src="/images/jeffrey-icon.svg" alt="" class="site-brand-logo" />
          <span>Jeffrey</span>
        </router-link>

        <nav class="site-nav" :class="{ 'site-nav--open': isMenuOpen }" aria-label="Main">
          <router-link
            v-for="link in NAV_LINKS"
            :key="link.label"
            :to="{ path: link.path, hash: link.hash }"
            class="site-nav-link"
            :class="{ 'site-nav-link--active': isActive(link) }"
            @click="closeMenu"
          >{{ link.label }}</router-link>
          <a :href="CONTACT_URL" class="site-nav-link" target="_blank" rel="noopener">Contact</a>
          <router-link to="/launch-it" class="site-launch site-launch--menu" @click="closeMenu">
            Launch it <i class="bi bi-arrow-right"></i>
          </router-link>
        </nav>

        <router-link to="/launch-it" class="site-launch">
          Launch it <i class="bi bi-arrow-right"></i>
        </router-link>

        <button
          type="button"
          class="site-menu-toggle"
          :aria-expanded="isMenuOpen"
          aria-label="Toggle menu"
          @click="toggleMenu"
        >
          <i class="bi" :class="isMenuOpen ? 'bi-x-lg' : 'bi-list'"></i>
        </button>
      </div>
    </header>

    <router-view />

    <footer class="site-footer">
      <div class="site-footer-row">
        <router-link to="/" class="site-brand site-brand--small">
          <img src="/images/jeffrey-icon.svg" alt="" class="site-brand-logo" />
          <span>Jeffrey</span>
        </router-link>
        <span class="site-footer-copy">&copy; {{ currentYear }} Jeffrey · Performance Analyst · Apache-2.0</span>
        <div class="site-footer-links">
          <a :href="GITHUB_URL" target="_blank" rel="noopener" aria-label="GitHub">
            <i class="bi bi-github"></i>
          </a>
          <a :href="BLUESKY_URL" target="_blank" rel="noopener" aria-label="Bluesky">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M3.468 1.948C5.303 3.325 7.276 6.118 8 7.616c.725-1.498 2.698-4.29 4.532-5.668C13.855.955 16 .186 16 2.632c0 .489-.28 4.105-.444 4.692-.572 2.04-2.653 2.561-4.504 2.246 3.236.551 4.06 2.375 2.281 4.2-3.376 3.464-4.852-.87-5.23-1.98-.07-.204-.103-.3-.103-.218 0-.081-.033.014-.102.218-.379 1.11-1.855 5.444-5.231 1.98-1.778-1.825-.955-3.65 2.28-4.2-1.85.315-3.932-.205-4.503-2.246C.28 6.737 0 3.12 0 2.632 0 .186 2.145.955 3.468 1.948"/>
            </svg>
          </a>
          <a :href="CONTACT_URL" target="_blank" rel="noopener" aria-label="LinkedIn">
            <i class="bi bi-linkedin"></i>
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>

<style>
/* Bootstrap Icons */
@import url("https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css");

/* Site-wide tokens: the warm-paper palette of the Constellation homepage */
.site {
  --site-paper: #faf7f2;
  --site-surface: #ffffff;
  --site-ink: #17130e;
  --site-muted: #5f584e;
  --site-faint: #91887b;
  --site-line: rgba(48, 32, 12, 0.09);
  --site-line-strong: rgba(48, 32, 12, 0.16);
  --site-flame: #ff6d00;
  --site-ember: #e4570b;
  --site-flame-ink: #1b0c00;
  --site-font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --site-radius-sm: 8px;
  --site-header-height: 70px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.site > footer {
  margin-top: auto;
}

/* ============ Header ============ */
.site-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}

.site-header--scrolled {
  border-bottom-color: var(--site-line);
}

.site-header-row {
  position: relative;
  max-width: 1360px;
  height: var(--site-header-height);
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.site-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--site-ink);
  text-decoration: none;
}

.site-brand:hover {
  color: var(--site-ink);
}

.site-brand-logo {
  width: 28px;
  height: 28px;
  filter: drop-shadow(0 0 10px rgba(255, 109, 0, 0.35));
}

.site-nav {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 34px;
}

.site-nav-link {
  position: relative;
  font-size: 14.5px;
  color: var(--site-muted);
  text-decoration: none;
  transition: color 0.15s;
}

.site-nav-link:hover {
  color: var(--site-ink);
}

.site-nav-link--active {
  color: var(--site-ink);
}

.site-nav-link--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -8px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, #ffab00, var(--site-flame));
}

/* Same treatment as the hero's "Get started": a light orange tint that fills in on hover */
.site-launch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-radius: var(--site-radius-sm);
  border: 1px solid rgba(255, 109, 0, 0.65);
  background: rgba(255, 109, 0, 0.22);
  color: var(--site-ember);
  font-size: 14.5px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.site-launch i {
  transition: transform 0.15s;
}

.site-launch:hover {
  background: var(--site-flame);
  border-color: var(--site-flame);
  color: var(--site-flame-ink);
  box-shadow: 0 8px 22px -12px var(--site-flame);
}

.site-launch:hover i {
  transform: translateX(3px);
}

.site-launch--menu {
  display: none;
}

.site-menu-toggle {
  display: none;
  margin-left: auto;
  width: 42px;
  height: 42px;
  border-radius: var(--site-radius-sm);
  border: 1px solid var(--site-line-strong);
  background: var(--site-surface);
  color: var(--site-ink);
  font-size: 20px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

@media (max-width: 959px) {
  .site-header-row {
    padding: 0 16px;
  }

  .site-menu-toggle {
    display: inline-flex;
  }

  .site-header-row > .site-launch {
    display: none;
  }

  .site-nav {
    display: none;
    position: absolute;
    top: calc(100% + 4px);
    left: 16px;
    right: 16px;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 8px;
    background: var(--site-surface);
    border: 1px solid var(--site-line-strong);
    border-radius: 14px;
    box-shadow: 0 16px 36px -18px rgba(48, 32, 12, 0.28);
  }

  .site-nav--open {
    display: flex;
  }

  .site-nav-link {
    padding: 12px 14px;
    border-radius: var(--site-radius-sm);
    font-size: 15px;
  }

  .site-nav-link::after {
    display: none;
  }

  .site-launch--menu {
    display: inline-flex;
    justify-content: center;
    margin-top: 6px;
    padding: 12px 14px;
  }
}

/* ============ Footer ============ */
.site-footer {
  background: var(--site-paper);
  border-top: 1px solid var(--site-line);
  color: var(--site-faint);
}

.site-footer-row {
  max-width: 1360px;
  margin: 0 auto;
  padding: 28px 40px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-size: 14px;
}

.site-brand--small {
  font-size: 17px;
}

.site-brand--small .site-brand-logo {
  width: 22px;
  height: 22px;
}

.site-footer-links {
  display: flex;
  align-items: center;
  gap: 18px;
}

.site-footer-links a {
  display: inline-flex;
  font-size: 20px;
  color: var(--site-muted);
  transition: color 0.15s;
}

.site-footer-links a:hover {
  color: var(--site-ink);
}

@media (max-width: 959px) {
  .site-footer-row {
    padding: 24px 16px;
  }
}
</style>
