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
import BlogCard from '@/components/BlogCard.vue';

interface BlogPost {
  title: string;
  date: string;
  summary: string;
  image: string;
  slug: string;
  colorClass: string;
}

const blogPosts = ref<BlogPost[]>([
  {
    title: 'Mandatory Literature',
    date: 'June 15, 2025',
    summary: 'All you need to efficiently profile and analyze your profiles',
    image: '/images/capability_information.png',
    slug: 'java-profiling-literature',
    colorClass: 'bg-blog-literature-gradient'
  },
  {
    title: 'JFR in Depth (Not only Native Image)',
    date: 'June 14, 2025',
    summary: 'Robert Toyonaga - An In-Depth Look at JFR in GraalVM Native Image (FOSFDAM Conference)',
    image: '/images/capability_flame.png',
    slug: 'jfr-in-depth',
    colorClass: 'bg-blog-jfr-gradient'
  },
  {
    title: '0.4 Release Announcement',
    date: 'June 13, 2025',
    summary: 'Let\'s Get Started with Jeffrey 0.4 release',
    image: '/images/capability_analysis.png',
    slug: 'jeffrey-04-announcement',
    colorClass: 'bg-blog-announcement-gradient'
  }
]);
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-blog-gradient">
              <i class="bi bi-journal-text"></i>
            </div>
            <h1 class="page-title mb-0">Blog</h1>
          </div>
          <router-link to="/" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Home
          </router-link>
        </div>

        <div class="page-content">
          <div class="row">
            <div v-for="(post, index) in blogPosts" :key="index" class="col-md-4 mb-4">
              <BlogCard
                :title="post.title"
                :date="post.date"
                :summary="post.summary"
                :slug="post.slug"
                :image="post.image"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header-icon {
  font-size: 1.5rem;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
}

.page-title {
  font-weight: 700;
  font-size: 2rem;
  color: #343a40;
}

.page-content {
  min-height: 60vh;
}

.bg-blog-gradient {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.bg-blog-literature-gradient {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.bg-blog-jfr-gradient {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.bg-blog-announcement-gradient {
  background: linear-gradient(135deg, #ff6b6b 0%, #ffa726 100%);
}
</style>
