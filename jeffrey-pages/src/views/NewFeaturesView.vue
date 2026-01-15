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
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-2">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-info-gradient">
              <i class="bi bi-calendar-check"></i>
            </div>
            <h1 class="page-title mb-0">Upcoming Features</h1>
          </div>
          <router-link to="/" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Home
          </router-link>
        </div>

        <div class="page-content bg-white rounded-3 p-0">
          <div class="content-section">
            <div class="features-grid">
              <div class="feature-card">
                <div class="feature-header">
                  <div class="feature-icon cloud">
                    <i class="bi bi-cloud"></i>
                  </div>
                  <div class="feature-title">
                    <h4>0.6 - Simple Cloud Deployment</h4>
                  </div>
                </div>
                <div class="feature-items">
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Deployment to Kubernetes environment with shared filesystem</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Automatically load projects from the shared filesystem and display them in the UI</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Examples of the deployment using local kubernetes cluster (e.g. Minikube, ...)</p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-header">
                  <div class="feature-icon performance">
                    <i class="bi bi-speedometer2"></i>
                  </div>
                  <div class="feature-title">
                    <h4>0.x - Performance</h4>
                  </div>
                </div>
                <div class="feature-items">
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Performance improvements for bigger flamegraphs</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Faster loading of the flamegraphs</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>PoC for other Databases to make loading of profiles faster (e.g. PostgreSQL, CockroachDB, ...)</p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-header">
                  <div class="feature-icon quality">
                    <i class="bi bi-tools"></i>
                  </div>
                  <div class="feature-title">
                    <h4>0.x - Code Quality (Removing TechDebt)</h4>
                  </div>
                </div>
                <div class="feature-description">
                  <p>We omitted tests to be focused on bringing features to the first major version of Jeffrey (to check whether community has any interest to this app)</p>
                  <p><strong>Reasons for omitting tests:</strong></p>
                  <ul>
                    <li>Limited time is invested to features</li>
                    <li>A lot of unknowns (choosing proper plotting libraries requiring data in different formats)</li>
                    <li>Plenty of internal refactorings would cause rewriting tests for the price of delaying features</li>
                  </ul>
                </div>
                <div class="feature-items">
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Test Coverage</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Documentation to interfaces and other building blocks</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Code Refactorings</p>
                    </div>
                  </div>
                  <div class="feature-item">
                    <div class="feature-checkbox">
                      <i class="bi bi-square"></i>
                    </div>
                    <div class="feature-content">
                      <p>Error handling - meaningful error messages from backend to frontend</p>
                    </div>
                  </div>
                </div>
              </div>
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
  background-color: rgba(54, 162, 235, 0.8);
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
  overflow: hidden;
}

.content-section {
  padding: 1rem;
}

.section-header h3 {
  font-size: 1.8rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 0;
}

.features-grid {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  margin-top: 2rem;
}

.feature-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.feature-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 30px rgba(0,0,0,0.1);
  border-color: #667eea;
}

.feature-header {
  display: flex;
  align-items: center;
  margin-bottom: 1.5rem;
}

.feature-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 1rem;
  font-size: 1.5rem;
  color: white;
}

.feature-icon.cloud {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.feature-icon.performance {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.feature-icon.quality {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}

.feature-title h4 {
  font-size: 1.3rem;
  font-weight: 600;
  color: #2c3e50;
  margin: 0;
}

.feature-description {
  margin-bottom: 1.5rem;
  color: #6c757d;
  line-height: 1.6;
}

.feature-description p {
  margin-bottom: 0.5rem;
}

.feature-description ul {
  margin-left: 1.5rem;
  margin-top: 0.5rem;
}

.feature-description li {
  margin-bottom: 0.25rem;
}

.feature-items {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.feature-item {
  display: flex;
  align-items: flex-start;
}

.feature-checkbox {
  margin-right: 0.75rem;
  margin-top: 0.1rem;
  color: #6c757d;
  font-size: 0.9rem;
}

.feature-content {
  flex: 1;
}

.feature-content p {
  margin: 0;
  line-height: 1.5;
  color: #495057;
}

@media (max-width: 768px) {
  .features-grid {
    gap: 1.5rem;
  }

  .feature-card {
    padding: 1rem;
  }
}
</style>
