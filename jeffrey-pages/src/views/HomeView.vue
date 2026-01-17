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
  <!-- Hero Section -->
  <section class="hero-section">
    <!-- Animated background grid -->
    <div class="hero-grid"></div>

    <!-- Floating code particles -->
    <div class="floating-particles">
      <span class="particle" style="--delay: 0s; --x: 10%; --y: 20%;">JFR</span>
      <span class="particle" style="--delay: 2s; --x: 85%; --y: 15%;">CPU</span>
      <span class="particle" style="--delay: 4s; --x: 75%; --y: 70%;">GC</span>
      <span class="particle" style="--delay: 1s; --x: 15%; --y: 75%;">Heap</span>
      <span class="particle" style="--delay: 3s; --x: 50%; --y: 10%;">Threads</span>
      <span class="particle" style="--delay: 5s; --x: 90%; --y: 50%;">Profiler</span>
    </div>

    <div class="container-wide hero-content">
      <div class="row align-items-center">
        <div class="col-lg-7">
          <div class="hero-text">
            <div class="hero-badge">
              <i class="bi bi-lightning-charge-fill"></i>
              <span>JFR Analysis Tool</span>
            </div>
            <h1 class="hero-title">
              <span class="title-line">Java Application</span>
              <span class="title-highlight">Analyzer</span>
            </h1>
            <p class="hero-description">
              Identify performance bottlenecks with interactive flamegraphs,
              memory analysis, and deep JFR event inspection.
            </p>
            <div class="hero-actions">
              <a href="https://github.com/petrbouda/jeffrey/releases/latest/download/jeffrey.jar" class="btn-hero-primary">
                <i class="bi bi-download"></i>
                <span>Download JAR</span>
              </a>
              <a href="https://github.com/petrbouda/jeffrey" class="btn-hero-secondary" target="_blank">
                <i class="bi bi-github"></i>
                <span>GitHub</span>
              </a>
            </div>
          </div>
        </div>
        <div class="col-lg-5">
          <div class="hero-visual">
            <div class="avatar-glow"></div>
            <div class="avatar-ring avatar-ring-1"></div>
            <div class="avatar-ring avatar-ring-2"></div>
            <div class="avatar-ring avatar-ring-3"></div>
            <img src="/images/avatar.jpg" alt="Jeffrey Logo" class="hero-avatar">
            <div class="floating-icon icon-1"><i class="bi bi-fire"></i></div>
            <div class="floating-icon icon-2"><i class="bi bi-cpu"></i></div>
            <div class="floating-icon icon-3"><i class="bi bi-memory"></i></div>
            <div class="floating-icon icon-4"><i class="bi bi-graph-up"></i></div>
            <div class="floating-icon icon-5"><i class="bi bi-speedometer2"></i></div>
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
              <span>Then open <a href="http://localhost:8080" target="_blank" class="localhost-link">http://localhost:8080</a> in your browser</span>
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
/* ============================
   Hero Section
   ============================ */
.hero-section {
  position: relative;
  background: linear-gradient(135deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%);
  padding: 50px 0;
  overflow: hidden;
  min-height: 400px;
  display: flex;
  align-items: center;
}

/* Animated grid background */
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(94, 100, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(94, 100, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: gridMove 20s linear infinite;
}

@keyframes gridMove {
  0% { transform: translate(0, 0); }
  100% { transform: translate(50px, 50px); }
}

/* Floating particles */
.floating-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  left: var(--x);
  top: var(--y);
  font-family: 'Courier New', monospace;
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(94, 100, 255, 0.4);
  animation: float 8s ease-in-out infinite;
  animation-delay: var(--delay);
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); opacity: 0.4; }
  50% { transform: translateY(-20px) rotate(5deg); opacity: 0.7; }
}

/* Hero content */
.hero-content {
  position: relative;
  z-index: 10;
}

.hero-text {
  color: #fff;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: rgba(94, 100, 255, 0.15);
  border: 1px solid rgba(94, 100, 255, 0.3);
  border-radius: 50px;
  font-size: 0.85rem;
  font-weight: 500;
  color: #a5a8ff;
  margin-bottom: 1.5rem;
}

.hero-badge i {
  color: #5e64ff;
}

.hero-title {
  font-size: 3.5rem;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 1.5rem;
}

.title-line {
  display: block;
  color: #fff;
}

.title-highlight {
  display: block;
  background: linear-gradient(135deg, #5e64ff 0%, #a855f7 50%, #ec4899 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-description {
  font-size: 1.15rem;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.7);
  max-width: 500px;
  margin-bottom: 2rem;
}

/* Hero buttons */
.hero-actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.btn-hero-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.875rem 1.75rem;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  color: #fff;
  font-weight: 600;
  font-size: 1rem;
  border-radius: 10px;
  text-decoration: none;
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(94, 100, 255, 0.4);
}

.btn-hero-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 30px rgba(94, 100, 255, 0.5);
  color: #fff;
}

.btn-hero-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.875rem 1.75rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  font-weight: 600;
  font-size: 1rem;
  border-radius: 10px;
  text-decoration: none;
  transition: all 0.3s ease;
}

.btn-hero-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
}

/* Hero visual (avatar with effects) */
.hero-visual {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 460px;
  width: 460px;
  margin: 0 auto;
}

.avatar-glow {
  position: absolute;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(94, 100, 255, 0.5) 0%, transparent 70%);
  border-radius: 50%;
  filter: blur(50px);
  animation: pulse 4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.2); opacity: 0.9; }
}

.avatar-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(94, 100, 255, 0.25);
}

.avatar-ring-1 {
  width: 310px;
  height: 310px;
  animation: rotateRing 30s linear infinite;
}

.avatar-ring-2 {
  width: 380px;
  height: 380px;
  border-style: dashed;
  animation: rotateRing 25s linear infinite reverse;
}

.avatar-ring-3 {
  width: 440px;
  height: 440px;
  animation: rotateRing 35s linear infinite;
}

@keyframes rotateRing {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.hero-avatar {
  width: 240px;
  height: 240px;
  border-radius: 50%;
  object-fit: cover;
  border: 5px solid rgba(94, 100, 255, 0.5);
  box-shadow: 0 0 70px rgba(94, 100, 255, 0.4);
  position: relative;
  z-index: 5;
}

/* Floating icons around avatar */
.floating-icon {
  position: absolute;
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 1.1rem;
  box-shadow: 0 4px 20px rgba(94, 100, 255, 0.5);
  z-index: 10;
}

.icon-1 {
  top: 5%;
  left: 50%;
  transform: translateX(-50%);
  animation: floatUp 3s ease-in-out infinite;
}

.icon-2 {
  top: 25%;
  right: 0;
  animation: floatRight 3.5s ease-in-out infinite;
}

.icon-3 {
  bottom: 15%;
  right: 10%;
  animation: floatDiagonal 4s ease-in-out infinite;
}

.icon-4 {
  bottom: 15%;
  left: 10%;
  animation: floatDiagonal 3.8s ease-in-out infinite reverse;
}

.icon-5 {
  top: 25%;
  left: 0;
  animation: floatLeft 3.2s ease-in-out infinite;
}

@keyframes floatUp {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-12px); }
}

@keyframes floatRight {
  0%, 100% { transform: translateX(0) translateY(0); }
  50% { transform: translateX(8px) translateY(-8px); }
}

@keyframes floatLeft {
  0%, 100% { transform: translateX(0) translateY(0); }
  50% { transform: translateX(-8px) translateY(-8px); }
}

@keyframes floatDiagonal {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-10px) scale(1.05); }
}

/* Responsive */
@media (max-width: 991px) {
  .hero-section {
    padding: 40px 0;
    min-height: auto;
  }

  .hero-title {
    font-size: 2.5rem;
  }

  .hero-visual {
    margin-top: 2rem;
    height: 300px;
    width: 300px;
  }

  .hero-avatar {
    width: 160px;
    height: 160px;
  }

  .avatar-glow {
    width: 190px;
    height: 190px;
  }

  .avatar-ring-1 { width: 200px; height: 200px; }
  .avatar-ring-2 { width: 245px; height: 245px; }
  .avatar-ring-3 { width: 285px; height: 285px; }

  .floating-icon {
    width: 34px;
    height: 34px;
    font-size: 0.9rem;
    border-radius: 10px;
  }
}

@media (max-width: 576px) {
  .hero-title {
    font-size: 2rem;
  }

  .hero-actions {
    flex-direction: column;
  }

  .btn-hero-primary,
  .btn-hero-secondary {
    justify-content: center;
  }

  .hero-visual {
    height: 240px;
    width: 240px;
  }

  .hero-avatar {
    width: 110px;
    height: 110px;
  }

  .avatar-glow {
    width: 130px;
    height: 130px;
  }

  .avatar-ring-1 { width: 145px; height: 145px; }
  .avatar-ring-2 { width: 185px; height: 185px; }
  .avatar-ring-3 { width: 225px; height: 225px; }

  .floating-icon {
    width: 28px;
    height: 28px;
    font-size: 0.8rem;
    border-radius: 8px;
  }
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
