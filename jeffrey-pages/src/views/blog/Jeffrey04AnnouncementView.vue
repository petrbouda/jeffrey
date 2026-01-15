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

interface SelectedImage {
  src: string;
  alt: string;
}

const selectedImage = ref<SelectedImage | null>(null);
const showModal = ref(false);

const openImageModal = (imageSrc: string, imageAlt: string): void => {
  selectedImage.value = { src: imageSrc, alt: imageAlt };
  showModal.value = true;
};

const closeImageModal = (): void => {
  showModal.value = false;
  selectedImage.value = null;
};
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-announcement-gradient">
              <i class="bi bi-megaphone"></i>
            </div>
            <div>
              <h1 class="page-title mb-0">0.4 Release Announcement</h1>
              <p class="text-muted mb-0">Published on June 13, 2025</p>
            </div>
          </div>
          <router-link to="/blog" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Blog
          </router-link>
        </div>

        <div class="page-content bg-white rounded-3 shadow-sm p-5">
          <div class="blog-content">
            <div class="lead mb-4">
              Let's Get Started with Jeffrey 0.4 release
            </div>

            <h2>Let's Get Started with Jeffrey App</h2>

            <p>
              Today, I'm happy to announce the <strong>0.4</strong> release of Jeffrey App -
              <a href="https://github.com/petrbouda/jeffrey/releases/download/0.4/jeffrey.jar" target="_blank">Jeffrey App</a>.
              There is a new cool feature, let's have a look at it!
            </p>

            <p>Start the jar file using the following command:</p>

            <div class="code-block">
              <pre><code>java -jar jeffrey.jar</code></pre>
            </div>

            <p>or you can spin up docker container with the following command, and check predefined examples.</p>

            <div class="code-block">
              <pre><code>docker run -it -p 8585:8585 petrbouda/jeffrey-examples:0.4</code></pre>
            </div>

            <p>
              Open the browser: <a href="http://localhost:8585" target="_blank">http://localhost:8585</a>
            </p>

            <h3>New Features</h3>

            <h4>Support for the latest Async-Profiler - Native Memory Leaks Profiling</h4>

            <p>
              The latest versions of Async-Profiler (Nightly builds) bring a support for the Native Memory Leaks profiling.
              In a nutshell, it records Malloc and Free (+ their addresses) events and then Jeffrey is able to find
              which Malloc events have no corresponding Free events.
            </p>

            <div class="reference-link mb-4">
              <a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md#native-memory-leaks" target="_blank">
                <i class="bi bi-link-45deg me-2"></i>
                Async-Profiler Native Memory Leaks Documentation
              </a>
            </div>

            <div class="instructions-box mb-4">
              <h5>Try the New Feature:</h5>
              <ul>
                <li>Use Jeffrey-examples docker image above to try the new feature</li>
                <li>Enter to <strong>jeffrey-persons-native-allocation-samples</strong> profile and navigate to predefined flamegraphs: <strong>Flamegraphs → Primary</strong></li>
              </ul>
            </div>

            <div class="images-section">
              <div class="row">
                <div class="col-md-6 mb-4">
                  <div class="image-container clickable" @click="openImageModal('images/blog/04-announcement/flamegraph-sections.png', 'Flamegraph Sections')">
                    <img src="/images/blog/04-announcement/flamegraph-sections.png" alt="Flamegraph Sections" class="img-fluid rounded">
                    <div class="image-overlay">
                      <i class="bi bi-zoom-in"></i>
                    </div>
                    <p class="image-caption">Flamegraph Sections</p>
                  </div>
                </div>
                <div class="col-md-6 mb-4">
                  <div class="image-container clickable" @click="openImageModal('images/blog/04-announcement/flamegraph-malloc.png', 'Flamegraph Malloc')">
                    <img src="/images/blog/04-announcement/flamegraph-malloc.png" alt="Flamegraph Malloc" class="img-fluid rounded">
                    <div class="image-overlay">
                      <i class="bi bi-zoom-in"></i>
                    </div>
                    <p class="image-caption">Flamegraph Malloc</p>
                  </div>
                </div>
              </div>
            </div>

            <div class="feature-highlight">
              <h5>New Option: Only Allocations with Unsafe</h5>
              <p>
                There is a new option: <strong>Only Allocations with Unsafe</strong>, it takes into account only samples
                that are allocated using <strong>Unsafe</strong>.
              </p>
            </div>

            <div class="download-section mt-5">
              <h3>Download Jeffrey 0.4</h3>
              <p>Ready to try the new features? Download Jeffrey 0.4 now:</p>
              <div class="download-buttons">
                <a href="https://github.com/petrbouda/jeffrey/releases/download/0.4/jeffrey.jar"
                   class="btn btn-primary btn-lg me-3" target="_blank">
                  <i class="bi bi-download me-2"></i>Download Jeffrey 0.4
                </a>
                <a href="https://github.com/petrbouda/jeffrey"
                   class="btn btn-outline-primary btn-lg" target="_blank">
                  <i class="bi bi-github me-2"></i>View on GitHub
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Image Modal -->
  <Teleport to="body">
    <div v-if="showModal" class="image-modal" @click="closeImageModal">
      <div class="modal-backdrop" @click="closeImageModal"></div>
      <div class="modal-content-image" @click.stop>
        <button class="modal-close" @click="closeImageModal">
          <i class="bi bi-x-lg"></i>
        </button>
        <img v-if="selectedImage" :src="selectedImage.src" :alt="selectedImage.alt" class="modal-image">
        <p v-if="selectedImage" class="modal-caption">{{ selectedImage.alt }}</p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.page-header-icon {
  font-size: 1.5rem;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff6b6b 0%, #ffa726 100%);
  border-radius: 12px;
  color: white;
  flex-shrink: 0;
}

.page-title {
  font-weight: 700;
  font-size: 2rem;
  color: #343a40;
  line-height: 1.2;
}

.page-content {
  min-height: 60vh;
}

.blog-content {
  line-height: 1.7;
  font-size: 1.1rem;
}

.blog-content h2 {
  color: #2c3e50;
  font-weight: 700;
  margin-top: 3rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 3px solid #ff6b6b;
}

.blog-content h3 {
  color: #34495e;
  font-weight: 600;
  margin-top: 2.5rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.3rem;
  border-bottom: 2px solid #ffa726;
}

.blog-content h4 {
  color: #34495e;
  font-weight: 600;
  margin-top: 2rem;
  margin-bottom: 1rem;
}

.blog-content h5 {
  color: #2c3e50;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 1rem;
}

.code-block {
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 1rem;
  margin: 1rem 0;
  border-left: 4px solid #ff6b6b;
}

.code-block pre {
  margin: 0;
  background: none;
  border: none;
  padding: 0;
}

.code-block code {
  background: none;
  color: #2c3e50;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9rem;
}

.reference-link {
  background-color: #e8f4fd;
  padding: 1rem;
  border-radius: 6px;
  border-left: 4px solid #3498db;
}

.reference-link a {
  color: #2980b9;
  text-decoration: none;
  font-weight: 600;
}

.reference-link a:hover {
  color: #1f618d;
  text-decoration: underline;
}

.instructions-box {
  background-color: #fff3cd;
  border: 1px solid #ffeaa7;
  border-radius: 6px;
  padding: 1.5rem;
  border-left: 4px solid #ffa726;
}

.instructions-box h5 {
  color: #d68910;
  margin-top: 0;
  margin-bottom: 1rem;
}

.instructions-box ul {
  margin-bottom: 0;
}

.instructions-box li {
  margin-bottom: 0.5rem;
}

.images-section {
  margin: 2rem 0;
}

.image-container {
  text-align: center;
  position: relative;
}

.image-container.clickable {
  cursor: pointer;
  transition: transform 0.3s ease;
}

.image-container.clickable:hover {
  transform: scale(1.02);
}

.image-container img {
  max-width: 100%;
  height: auto;
  box-shadow: 0 8px 25px rgba(0,0,0,0.15);
  border: 1px solid #e9ecef;
  transition: opacity 0.3s ease;
}

.image-container.clickable:hover img {
  opacity: 0.9;
}

.image-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.image-container.clickable:hover .image-overlay {
  opacity: 1;
}

.image-caption {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  color: #6c757d;
  font-style: italic;
}

.feature-highlight {
  background-color: #d1ecf1;
  border: 1px solid #bee5eb;
  border-radius: 6px;
  padding: 1.5rem;
  border-left: 4px solid #17a2b8;
  margin: 2rem 0;
}

.feature-highlight h5 {
  color: #0c5460;
  margin-top: 0;
  margin-bottom: 1rem;
}

.download-section {
  background-color: #f0f8f4;
  padding: 2rem;
  border-radius: 8px;
  border-left: 4px solid #28a745;
  text-align: center;
}

.download-section h3 {
  color: #155724;
  margin-top: 0;
  border-bottom: none;
}

.download-buttons {
  margin-top: 1.5rem;
}

.lead {
  font-size: 1.2rem;
  color: #495057;
  font-weight: 400;
  font-style: italic;
}

.bg-announcement-gradient {
  background: linear-gradient(135deg, #ff6b6b 0%, #ffa726 100%);
}

/* Image Modal Styles */
.image-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  animation: fadeIn 0.3s ease;
  background-color: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(5px);
}

.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}

.modal-content-image {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  animation: scaleIn 0.3s ease;
  z-index: 1;
}

.modal-close {
  position: absolute;
  top: 15px;
  right: 15px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  font-size: 1.2rem;
  transition: background-color 0.3s ease;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.9);
}

.modal-image {
  width: 100%;
  height: auto;
  display: block;
  max-height: 80vh;
  object-fit: contain;
}

.modal-caption {
  padding: 1rem;
  margin: 0;
  text-align: center;
  background-color: #f8f9fa;
  color: #495057;
  font-weight: 600;
  border-top: 1px solid #e9ecef;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes scaleIn {
  from {
    transform: scale(0.9);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

/* Responsive modal */
@media (max-width: 768px) {
  .image-modal {
    padding: 1rem;
  }

  .modal-content-image {
    max-width: 95vw;
    max-height: 95vh;
  }

  .modal-close {
    top: 10px;
    right: 10px;
    width: 35px;
    height: 35px;
    font-size: 1rem;
  }
}
</style>
