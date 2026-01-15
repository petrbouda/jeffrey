<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import { ref } from 'vue';

const isMenuOpen = ref(false);

const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value;
  document.body.style.overflow = isMenuOpen.value ? 'hidden' : '';
};

const closeMenu = () => {
  if (isMenuOpen.value) {
    isMenuOpen.value = false;
    document.body.style.overflow = '';
  }
};
</script>

<template>
  <div class="container-fluid p-0">
    <!-- Modern Navbar -->
    <nav class="navbar">
      <div class="navbar-container">
        <router-link to="/" class="navbar-brand">Jeffrey</router-link>

        <ul class="nav-menu" :class="{ 'active': isMenuOpen }">
          <li class="nav-item">
            <router-link to="/launch-it" class="nav-link" @click="closeMenu">Launch It!</router-link>
          </li>
          <li class="nav-item">
            <router-link to="/docs" class="nav-link" @click="closeMenu">Documentation</router-link>
          </li>
          <li class="nav-item">
            <router-link to="/new-features" class="nav-link" @click="closeMenu">New Features</router-link>
          </li>
          <li class="nav-item">
            <router-link to="/blog" class="nav-link" @click="closeMenu">Blog</router-link>
          </li>
          <li class="nav-item">
            <a href="https://www.linkedin.com/in/petr-bouda/" class="nav-link" target="_blank">Contact</a>
          </li>
        </ul>

        <button class="hamburger" :class="{ 'active': isMenuOpen }" @click="toggleMenu">
          <span class="hamburger-line"></span>
          <span class="hamburger-line"></span>
          <span class="hamburger-line"></span>
        </button>
      </div>
    </nav>

    <!-- Router View -->
    <router-view />

    <!-- Footer -->
    <footer class="bg-dark text-white py-4">
      <div class="container-wide">
        <div class="row">
          <div class="col-md-6">
            <p>&copy; 2025 Jeffrey - Java Profiling Tool</p>
          </div>
          <div class="col-md-6 text-end">
            <a href="https://github.com/petrbouda/jeffrey" class="text-white me-3" target="_blank">
              <i class="bi bi-github fs-4"></i>
            </a>
            <a href="https://bsky.app/profile/pbouda.bsky.social" class="text-white me-3" target="_blank">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="mb-2" viewBox="0 0 16 16">
                <path d="M3.468 1.948C5.303 3.325 7.276 6.118 8 7.616c.725-1.498 2.698-4.29 4.532-5.668C13.855.955 16 .186 16 2.632c0 .489-.28 4.105-.444 4.692-.572 2.04-2.653 2.561-4.504 2.246 3.236.551 4.06 2.375 2.281 4.2-3.376 3.464-4.852-.87-5.23-1.98-.07-.204-.103-.3-.103-.218 0-.081-.033.014-.102.218-.379 1.11-1.855 5.444-5.231 1.98-1.778-1.825-.955-3.65 2.28-4.2-1.85.315-3.932-.205-4.503-2.246C.28 6.737 0 3.12 0 2.632 0 .186 2.145.955 3.468 1.948"/>
              </svg>
            </a>
            <a href="https://www.linkedin.com/in/petr-bouda/" class="text-white" target="_blank">
              <i class="bi bi-linkedin fs-4"></i>
            </a>
          </div>
        </div>
      </div>
    </footer>
  </div>
</template>

<style>
/* Bootstrap Icons */
@import url("https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css");

/* Navbar styles */
.navbar {
  padding: 1rem 0;
}

.navbar-brand {
  font-weight: 700;
  font-size: 1.5rem;
}

.nav-link {
  font-weight: 500;
  transition: color 0.3s;
}

.nav-link:hover {
  color: #3498db;
}

/* Navbar styles - Modern */
.navbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 2rem;
}

.nav-menu {
  list-style: none;
  display: flex;
  gap: 1.5rem;
}

.nav-item {
  position: relative;
}

.nav-link {
  text-decoration: none;
  color: #333;
  font-weight: 500;
  transition: color 0.3s;
}

.nav-link:hover {
  color: #007bff;
}

.hamburger {
  display: none;
  flex-direction: column;
  cursor: pointer;
}

.hamburger-line {
  width: 2rem;
  height: 0.25rem;
  background: #333;
  margin: 0.2rem 0;
  transition: all 0.3s;
}

/* Responsive styles */
@media (max-width: 991px) {
  .nav-menu {
    position: absolute;
    top: 100%;
    left: 0;
    width: 100%;
    background: #fff;
    padding: 1rem 0;
    display: none;
    flex-direction: column;
    gap: 0.5rem;
    z-index: 10;
  }

  .nav-menu.active {
    display: flex;
  }

  .hamburger {
    display: flex;
  }
}
</style>
