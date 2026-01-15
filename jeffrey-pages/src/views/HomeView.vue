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
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import IntroductionCard from '@/components/IntroductionCard.vue';
import FeatureCard from '@/components/FeatureCard.vue';
import BlogCard from '@/components/BlogCard.vue';

interface IntroCard {
  title: string;
  content: string;
  file: string;
  icon: string;
  colorClass: string;
  route: string;
}

interface Feature {
  title: string;
  description: string;
  icon: string;
  screenshots: string[];
  primaryScreenshot: string;
}

interface BlogPost {
  title: string;
  date: string;
  summary: string;
  image: string;
  slug: string;
  colorClass: string;
}

const route = useRoute();

onMounted(() => {
  if (route.path === '/features') {
    setTimeout(() => {
      const featuresSection = document.getElementById('features');
      if (featuresSection) {
        featuresSection.scrollIntoView({ behavior: 'smooth' });
      }
    }, 100);
  }
});

const copyToClipboard = (text: string): void => {
  navigator.clipboard.writeText(text);
};

const introCards = ref<IntroCard[]>([
  {
    title: 'Launch It!',
    content: 'Get started with Jeffrey and learn the basics of Java profiling with JFR recordings.',
    file: 'startup.md',
    icon: 'bi-rocket-takeoff',
    colorClass: 'bg-success-gradient',
    route: '/launch-it'
  },
  {
    title: 'Documentation',
    content: 'Comprehensive guides covering concepts, features, and best practices for performance analysis.',
    file: 'documentation.md',
    icon: 'bi-book',
    colorClass: 'bg-docs-gradient',
    route: '/docs'
  },
  {
    title: 'Upcoming Features',
    content: 'Check out the roadmap showing completed and planned features for upcoming Jeffrey versions.',
    file: 'new_features.md',
    icon: 'bi-calendar-check',
    colorClass: 'bg-info-gradient',
    route: '/new-features'
  }
]);

const features = ref<Feature[]>([
  {
    title: 'Flamegraphs',
    description: 'Interactive flamegraphs for CPU profiling with execution, wall-clock, and allocation samples',
    icon: 'bi-fire',
    screenshots: [
      'images/feature-screenshots/profile_flamegraph_2.png',
      'images/feature-screenshots/profile_flamegraph_3.png',
      'images/feature-screenshots/profile_flamegraph.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_flamegraph_2.png'
  },
  {
    title: 'Differential Flamegraphs',
    description: 'Compare performance between different profiles using differential flamegraphs',
    icon: 'bi-fire',
    screenshots: [
      'images/feature-screenshots/profile_diffgraph_2.png',
      'images/feature-screenshots/profile_diffgraph_3.png',
      'images/feature-screenshots/profile_diffgraph.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_diffgraph_2.png'
  },
  {
    title: 'Native Leaks Flamegraphs',
    description: 'Find leaking native memory in your Java applications with native leaks flamegraphs',
    icon: 'bi-fire',
    screenshots: [
      'images/feature-screenshots/profile_native_leak_flamegraph_2.png',
      'images/feature-screenshots/profile_native_leak_flamegraph_3.png',
      'images/feature-screenshots/profile_native_leak_flamegraph.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_native_leak_flamegraph_2.png'
  },
  {
    title: 'Sub-Second Analysis',
    description: 'Analyze application startup and performance at sub-second time intervals',
    icon: 'bi-grid-3x3-gap-fill',
    screenshots: [
      'images/feature-screenshots/profile_subsecond.png',
      'images/feature-screenshots/profile_subsecond_2.png',
      'images/feature-screenshots/profile_subsecond_3.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_subsecond_2.png'
  },
  {
    title: 'Events and Event Types Viewer',
    description: 'Browse and filter JFR events with comprehensive event type categorization',
    icon: 'bi-list-ul',
    screenshots: [
      'images/feature-screenshots/profile_events.png',
      'images/feature-screenshots/profile_events_2.png',
      'images/feature-screenshots/profile_event_types.png',
      'images/feature-screenshots/profile_event_types_2.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_events.png'
  },
  {
    title: 'Profile Configuration',
    description: 'Detailed profile metadata including JVM settings, command line arguments, and system information',
    icon: 'bi-info-square',
    screenshots: [
      'images/feature-screenshots/profile_configuration.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_configuration.png'
  },
  {
    title: 'Guardian Analysis',
    description: 'Analysis based on traversing stacktraces to find suspicious behavior and highlighting in flamegraphs',
    icon: 'bi-shield-check',
    screenshots: [
      'images/feature-screenshots/profile_guardian.png',
      'images/feature-screenshots/profile_guardian_2.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_guardian.png'
  },
  {
    title: 'Auto-Analysis',
    description: 'Automated performance analysis with color-coded insights and predefined rules',
    icon: 'bi-speedometer2',
    screenshots: [
      'images/feature-screenshots/profile_analysis.png',
      'images/feature-screenshots/profile_analysis_2.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_analysis.png'
  },
  {
    title: 'Thread Statistics',
    description: 'General information about threads and their lifecycle',
    icon: 'bi-threads',
    screenshots: [
      'images/feature-screenshots/profile_thread_statistics.png',
      'images/feature-screenshots/profile_thread_statistics_2.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_thread_statistics.png'
  },
  {
    title: 'Thread Timelines',
    description: 'Timelines for the threads with occurred events in a single row',
    icon: 'bi-graph-up',
    screenshots: [
      'images/feature-screenshots/profile_thread_timeline.png',
      'images/feature-screenshots/profile_thread_timeline_2.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_thread_timeline.png'
  },
  {
    title: 'JIT Compilations',
    description: 'Provides information about Compilations and activities of JIT Compilers',
    icon: 'bi-cpu',
    screenshots: [
      'images/feature-screenshots/profile_jit_compilation.png',
      'images/feature-screenshots/profile_jit_compilation_2.png'
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_jit_compilation.png'
  },
  {
    title: 'Heap Memory',
    description: 'Information about the Java Heap memory and timeseries data',
    icon: 'bi-memory',
    screenshots: [
      'images/feature-screenshots/profile_heap_memory.png',
      'images/feature-screenshots/profile_heap_memory_2.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_heap_memory.png'
  },
  {
    title: 'Garbage Collection',
    description: 'Provides information about Garbage Collection statistics, timeseries and longest pauses',
    icon: 'bi-recycle',
    screenshots: [
      'images/feature-screenshots/profile_gc.png',
      'images/feature-screenshots/profile_gc_2.png',
      'images/feature-screenshots/profile_gc_3.png',
      'images/feature-screenshots/profile_gc_4.png',
      'images/feature-screenshots/profile_gc_5.png',
      'images/feature-screenshots/profile_gc_6.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/profile_gc.png'
  }
]);

const customFeatures = ref<Feature[]>([
  {
    title: 'HTTP Communication',
    description: 'Information about HTTP communication, including request and response details and timings',
    icon: 'bi-globe',
    screenshots: [
      'images/feature-screenshots/custom_http.png',
      'images/feature-screenshots/custom_http_2.png',
      'images/feature-screenshots/custom_http_3.png',
      'images/feature-screenshots/custom_http_4.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/custom_http.png'
  },
  {
    title: 'JDBC Connection Pools',
    description: 'JDBC Pools analysis with detailed information about the usage of pooled connections',
    icon: 'bi-diagram-2',
    screenshots: [
      'images/feature-screenshots/custom_jdbc_pool.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/custom_jdbc_pool.png'
  },
  {
    title: 'JDBC Statements',
    description: 'JDBC Statements analysis with detailed information about execution times and counts',
    icon: 'bi-database',
    screenshots: [
      'images/feature-screenshots/custom_jdbc_statements.png',
      'images/feature-screenshots/custom_jdbc_statements_2.png',
      'images/feature-screenshots/custom_jdbc_statements_3.png',
      'images/feature-screenshots/custom_jdbc_statements_4.png',
    ],
    primaryScreenshot: 'images/feature-screenshots/custom_jdbc_statements.png'
  }
]);

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
    summary: "Let's Get Started with Jeffrey 0.4 release",
    image: '/images/capability_analysis.png',
    slug: 'jeffrey-04-announcement',
    colorClass: 'bg-blog-announcement-gradient'
  }
]);

const latestBlogPosts = computed(() => blogPosts.value.slice(0, 6));
</script>

<template>
  <!-- Introduction -->
  <section class="bg-gradient-header text-white py-10">
    <div class="container-wide">
      <div class="row align-items-center">
        <div class="col-md-2 text-center text-md-end pe-md-0">
          <div class="avatar-container-modern">
            <img src="/images/avatar.jpg" alt="Jeffrey Logo" class="avatar-image">
          </div>
        </div>
        <div class="col-md-9 ps-md-4">
          <div class="header-content">
            <h1 class="display-4 fw-bold">Java Application Analyzer</h1>
            <p class="lead mb-4">Advanced JFR analysis tool for identifying and resolving performance bottlenecks.</p>
            <div class="mt-4">
              <a href="https://github.com/petrbouda/jeffrey/releases/latest/download/jeffrey.jar" class="btn btn-light btn-lg me-2">
                <i class="bi bi-download me-2"></i>Download Jeffrey
              </a>
              <a href="https://github.com/petrbouda/jeffrey" class="btn btn-outline-light btn-lg" target="_blank">
                <i class="bi bi-github me-2"></i>GitHub Repository
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Main Content -->
  <main>
    <!-- Introduction Cards -->
    <section id="introduction" class="py-5 bg-light">
      <div class="container-wide">
        <div class="row">
          <div v-for="(card, index) in introCards" :key="index" class="col-md-4 mb-4">
            <IntroductionCard
              :title="card.title"
              :content="card.content"
              :file="card.file"
              :icon="card.icon"
              :colorClass="card.colorClass"
              :route="card.route"
            />
          </div>
        </div>

        <!-- Quick Start Container -->
        <div class="quick-start-container mt-3">
          <div class="quick-start-content">
            <div class="quick-start-header">
              <i class="bi bi-lightning-charge-fill me-2"></i>
              <h4 class="mb-0">Quick Start</h4>
            </div>
            <div class="command-container">
              <code class="command-text">docker run -it --network host petrbouda/jeffrey-examples</code>
              <button class="btn btn-outline-primary btn-sm copy-btn" @click="copyToClipboard('docker run -it --network host petrbouda/jeffrey-examples')">
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
            <div class="quick-start-footer">
              <i class="bi bi-info-circle me-1"></i>
              <span>Then open <a href="http://localhost:8585" target="_blank" class="localhost-link">http://localhost:8585</a> in your browser</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section id="features" class="py-5">
      <div class="container-wide">
        <div class="section-header-container" data-text="FEATURES">
          <h2 class="section-header">Features</h2>
        </div>

        <!-- Main Features Delimiter -->
        <div class="custom-features-delimiter my-4">
          <div class="delimiter-line"></div>
          <div class="delimiter-text">
            <span class="badge bg-primary px-3 py-2">Main Features</span>
          </div>
          <div class="delimiter-line"></div>
        </div>

        <div class="row">
          <div v-for="(feature, index) in features" :key="index" class="col-md-4 mb-4">
            <FeatureCard
              :title="feature.title"
              :description="feature.description"
              :icon="feature.icon"
              :screenshots="feature.screenshots"
              :primaryScreenshot="feature.primaryScreenshot"
            />
          </div>
        </div>

        <!-- Custom Features Delimiter -->
        <div class="custom-features-delimiter my-3">
          <div class="delimiter-line"></div>
          <div class="delimiter-text">
            <span class="badge bg-secondary px-3 py-2">Custom Features</span>
          </div>
          <div class="delimiter-line"></div>
        </div>

        <!-- Custom Features -->
        <div class="row" v-if="customFeatures.length > 0">
          <div v-for="(feature, index) in customFeatures" :key="'custom-' + index" class="col-md-4 mb-4">
            <FeatureCard
              :title="feature.title"
              :description="feature.description"
              :icon="feature.icon"
              :screenshots="feature.screenshots"
              :primaryScreenshot="feature.primaryScreenshot"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Blog Section -->
    <section id="blog" class="py-5 bg-light">
      <div class="container-wide">
        <div class="d-flex justify-content-between align-items-end mb-4">
          <div class="section-header-container" data-text="BLOG">
            <h2 class="section-header">Latest Blog Posts</h2>
          </div>
          <router-link to="/blog" class="btn btn-outline-primary">
            View All Posts <i class="bi bi-arrow-right ms-2"></i>
          </router-link>
        </div>
        <div class="row">
          <div v-for="(post, index) in latestBlogPosts" :key="index" class="col-md-4 mb-4">
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
    </section>
  </main>
</template>

<style scoped>
.bg-gradient-header {
  background: linear-gradient(135deg, #343a40 0%, #495057 100%);
  padding: 60px 0;
  position: relative;
  overflow: hidden;
}

.bg-gradient-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: inherit;
  z-index: -1;
  filter: blur(30px);
}

.header-content {
  text-align: left;
}

.section-header {
  z-index: 0;
}

.custom-features-delimiter {
  display: flex;
  align-items: center;
  width: 100%;
  position: relative;
}

.delimiter-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, transparent, #dee2e6, transparent);
}

.delimiter-text {
  margin: 0 20px;
  flex-shrink: 0;
}

.delimiter-text .badge {
  font-size: 0.9rem;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  font-weight: 500;
}

html {
  scroll-behavior: smooth;
}

section {
  scroll-margin-top: 80px;
}

section:target {
  animation: sectionHighlight 2s ease-in-out;
}

@keyframes sectionHighlight {
  0% {
    background-color: rgba(102, 126, 234, 0.1);
  }
  100% {
    background-color: transparent;
  }
}

/* Quick Start Container Styles */
.quick-start-container {
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-radius: 12px;
  padding: 2rem;
  border: 1px solid #dee2e6;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.quick-start-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #007bff, #6610f2);
}

.quick-start-content {
  position: relative;
  z-index: 1;
}

.quick-start-header {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
  color: #495057;
}

.quick-start-header i {
  color: #007bff;
  font-size: 1.2rem;
}

.command-container {
  display: flex;
  align-items: center;
  background: #f8f9fa;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
  border: 1px solid #dee2e6;
}

.command-text {
  color: #495057;
  font-family: 'Courier New', monospace;
  font-size: 1rem;
  flex-grow: 1;
  background: transparent;
  border: none;
  user-select: all;
}

.copy-btn {
  margin-left: 1rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  transition: all 0.2s;
}

.copy-btn:hover {
  background-color: #007bff;
  border-color: #007bff;
  color: white;
}

.quick-start-footer {
  display: flex;
  align-items: center;
  color: #6c757d;
  font-size: 0.95rem;
}

.quick-start-footer i {
  color: #17a2b8;
}

.localhost-link {
  color: #007bff;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.2s;
}

.localhost-link:hover {
  color: #0056b3;
  text-decoration: underline;
}
</style>
