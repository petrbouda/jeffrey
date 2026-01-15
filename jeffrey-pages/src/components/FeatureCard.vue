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

interface Props {
  title: string;
  description: string;
  icon: string;
  colorClass?: string;
  screenshots: string[];
  primaryScreenshot: string;
}

interface SelectedImage {
  src: string;
  alt: string;
}

const props = defineProps<Props>();

const selectedImage = ref<SelectedImage | null>(null);
const showModal = ref(false);
const currentImageIndex = ref(0);

const openImageModal = (imageSrc: string, imageAlt: string) => {
  selectedImage.value = { src: imageSrc, alt: imageAlt };
  showModal.value = true;
  currentImageIndex.value = 0;
};

const nextImage = () => {
  if (props.screenshots && props.screenshots.length > 1) {
    currentImageIndex.value = (currentImageIndex.value + 1) % props.screenshots.length;
    selectedImage.value = {
      src: props.screenshots[currentImageIndex.value],
      alt: `${props.title} - Screenshot ${currentImageIndex.value + 1}`
    };
  }
};

const prevImage = () => {
  if (props.screenshots && props.screenshots.length > 1) {
    currentImageIndex.value = currentImageIndex.value === 0 ? props.screenshots.length - 1 : currentImageIndex.value - 1;
    selectedImage.value = {
      src: props.screenshots[currentImageIndex.value],
      alt: `${props.title} - Screenshot ${currentImageIndex.value + 1}`
    };
  }
};

const closeImageModal = () => {
  showModal.value = false;
  selectedImage.value = null;
};
</script>

<template>
  <div class="card h-100 feature-card" :class="colorClass" @click="openImageModal(primaryScreenshot, title + ' Preview')">
    <div class="card-body">
      <div class="feature-content">
        <div class="feature-text">
          <h3 class="card-title">{{ title }}</h3>
          <p class="card-text">{{ description }}</p>
        </div>
        <div class="feature-icon-container">
          <i :class="['bi', icon, 'feature-icon']"></i>
        </div>
      </div>
    </div>

    <!-- Modal overlay icon -->
    <div class="card-overlay">
      <i class="bi bi-zoom-in"></i>
    </div>

    <!-- Tooltip -->
    <div class="tooltip-container" v-if="primaryScreenshot">
      <img :src="primaryScreenshot" alt="Feature Preview" class="tooltip-image">
      <p class="tooltip-caption">{{ title }} Preview</p>
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

        <!-- Navigation buttons for multiple screenshots -->
        <button v-if="screenshots && screenshots.length > 1" class="modal-nav modal-nav-prev" @click="prevImage">
          <i class="bi bi-chevron-left"></i>
        </button>
        <button v-if="screenshots && screenshots.length > 1" class="modal-nav modal-nav-next" @click="nextImage">
          <i class="bi bi-chevron-right"></i>
        </button>

        <img v-if="selectedImage" :src="selectedImage.src" :alt="selectedImage.alt" class="modal-image">
        <div class="modal-footer">
          <p v-if="selectedImage" class="modal-caption">{{ selectedImage.alt }}</p>
          <div v-if="screenshots && screenshots.length > 1" class="modal-counter">
            {{ currentImageIndex + 1 }} / {{ screenshots.length }}
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.feature-card {
  border: none;
  border-radius: 16px;
  box-shadow: 0 8px 25px rgba(0,0,0,0.08);
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  position: relative;
  overflow: visible;
  cursor: pointer;
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 20px 40px rgba(0,0,0,0.15);
}

.card-overlay {
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

.feature-card:hover .card-overlay {
  opacity: 1;
}

.feature-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.feature-text {
  flex: 1;
}

.feature-icon-container {
  height: 50px;
  width: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.feature-icon-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0) 100%);
  pointer-events: none;
}

.feature-icon {
  font-size: 1.3rem;
  color: white;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.2));
}

.card-body {
  padding: 1.5rem;
}

.card-title {
  font-weight: 700;
  font-size: 1.2rem;
  margin-bottom: 0.5rem;
  color: #2c3e50;
  line-height: 1.3;
  text-align: left;
}

.card-text {
  color: #6c757d;
  font-size: 0.9rem;
  line-height: 1.6;
  margin: 0;
  text-align: left;
}

/* Tooltip Styles */
.tooltip-container {
  position: fixed;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: white;
  border-radius: 12px;
  padding: 1rem;
  box-shadow: 0 15px 35px rgba(0,0,0,0.2);
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
  z-index: 10000;
  min-width: 320px;
  max-width: 360px;
  margin-bottom: 15px;
  border: 1px solid #e9ecef;
}

.tooltip-container::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 8px solid transparent;
  border-top-color: white;
}

.feature-card:hover .tooltip-container {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(-10px);
}

.tooltip-image {
  width: 100%;
  height: auto;
  border-radius: 8px;
  margin-bottom: 0.75rem;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.tooltip-caption {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #495057;
  text-align: center;
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

.modal-footer {
  background-color: #f8f9fa;
  border-top: 1px solid #e9ecef;
  padding: 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-caption {
  margin: 0;
  color: #495057;
  font-weight: 600;
  flex: 1;
  text-align: center;
}

.modal-counter {
  color: #6c757d;
  font-size: 0.9rem;
  font-weight: 500;
  background: #dee2e6;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  margin-left: 1rem;
}

.modal-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0, 0, 0, 0.7);
  color: white;
  border: none;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  font-size: 1.5rem;
  transition: all 0.3s ease;
}

.modal-nav:hover {
  background: rgba(0, 0, 0, 0.9);
  transform: translateY(-50%) scale(1.1);
}

.modal-nav-prev {
  left: 20px;
}

.modal-nav-next {
  right: 20px;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes scaleIn {
  from { transform: scale(0.9); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

@media (max-width: 768px) {
  .tooltip-container {
    min-width: 280px;
    max-width: 320px;
  }

  .feature-icon {
    font-size: 1.3rem;
  }

  .feature-icon-container {
    height: 45px;
    width: 45px;
  }

  .feature-content {
    gap: 0.75rem;
  }

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

  .modal-nav {
    width: 40px;
    height: 40px;
    font-size: 1.2rem;
  }

  .modal-nav-prev { left: 10px; }
  .modal-nav-next { right: 10px; }

  .modal-footer {
    padding: 0.75rem;
    flex-direction: column;
    gap: 0.5rem;
  }

  .modal-counter { margin-left: 0; }
}
</style>
