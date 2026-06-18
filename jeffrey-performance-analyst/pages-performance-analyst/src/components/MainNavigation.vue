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

<template>
  <div class="navigation-tabs">
    <div class="container-fluid">
      <div class="nav-container">
        <router-link to="/" class="nav-pill" active-class="active" exact-active-class="active">
          <i class="bi bi-speedometer2"></i>
          <span>Overview</span>
        </router-link>
        <router-link to="/workspaces" class="nav-pill" active-class="active">
          <i class="bi bi-folder"></i>
          <span>Workspaces</span>
        </router-link>
      </div>
    </div>
  </div>
  <div class="content-spacer"></div>
</template>

<script setup lang="ts"></script>

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
