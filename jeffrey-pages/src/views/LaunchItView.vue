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
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-primary-gradient">
              <i class="bi bi-rocket-takeoff"></i>
            </div>
            <h1 class="page-title mb-0">Launch It!</h1>
          </div>
          <router-link to="/" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Home
          </router-link>
        </div>

        <div class="page-content bg-white rounded-3 p-0">
          <div class="content-section">
            <div class="methods-grid">
              <div class="method-card recommended">
                <div class="method-header">
                  <div class="method-icon docker">
                    <i class="bi bi-box-seam"></i>
                  </div>
                  <div class="method-title">
                    <h4>Docker with Examples</h4>
                    <span class="badge">Recommended</span>
                  </div>
                </div>
                <p class="method-description">Start quickly with pre-generated recordings in a Docker container.</p>
                <div class="method-steps">
                  <div class="step">
                    <div class="step-content">
                      <p>Find the Docker Image at <a href="https://hub.docker.com/r/petrbouda/jeffrey-examples/tags"
                                                     target="_blank">Docker Hub</a></p>
                    </div>
                  </div>
                  <div class="step">
                    <div class="step-content">
                      <pre><code>docker run -it --network host petrbouda/jeffrey-examples</code></pre>
                    </div>
                  </div>
                  <div class="step">
                    <div class="step-content">
                      <p>Open <a href="http://localhost:8585" target="_blank">http://localhost:8585</a> in your browser
                      </p>
                    </div>
                  </div>
                </div>
                <div class="method-actions">
                  <router-link to="/tour-with-examples" class="btn btn-outline-primary">
                    <i class="bi bi-collection me-2"></i>Tour with Examples
                  </router-link>
                </div>
              </div>

              <div class="method-card">
                <div class="method-header">
                  <div class="method-icon docker">
                    <i class="bi bi-box"></i>
                  </div>
                  <div class="method-title">
                    <h4>Empty Docker</h4>
                  </div>
                </div>
                <p class="method-description">Start with a clean Docker container.</p>
                <div class="method-steps">
                  <div class="step">
                    <div class="step-content">
                      <pre><code>docker run -it --network host petrbouda/jeffrey</code></pre>
                    </div>
                  </div>
                  <div class="step">
                    <div class="step-content">
                      <p>Open <a href="http://localhost:8585" target="_blank">http://localhost:8585</a> in your browser
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="method-card">
                <div class="method-header">
                  <div class="method-icon java">
                    <i class="bi bi-cup-hot"></i>
                  </div>
                  <div class="method-title">
                    <h4>Plain Java</h4>
                  </div>
                </div>
                <p class="method-description">Run as a standalone Java application.</p>
                <div class="method-steps">
                  <div class="step">
                    <div class="step-content">
                      <pre><code>java -jar jeffrey.jar</code></pre>
                    </div>
                  </div>
                  <div class="step">
                    <div class="step-content">
                      <p>Open <a href="http://localhost:8585" target="_blank">http://localhost:8585</a> in your browser
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="method-card">
                <div class="method-header">
                  <div class="method-icon ide">
                    <i class="bi bi-code-slash"></i>
                  </div>
                  <div class="method-title">
                    <h4>IDE Development</h4>
                  </div>
                </div>
                <p class="method-description">Run in development mode with separate backend and frontend.</p>
                <div class="method-steps">
                  <div class="step">
                    <div class="step-content">
                      <p><strong>Backend:</strong> Start <code>Application.java</code> in your IDE</p>
                    </div>
                  </div>
                  <div class="step">
                    <div class="step-content">
                      <p><strong>Frontend:</strong> Run in <b>pages</b> directory:</p>
                      <pre><code>cd pages && npm run dev</code></pre></div>
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
  background-color: rgba(78, 115, 223, 0.8);
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

.methods-grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-top: 2rem;
}

.method-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.method-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  border-color: #667eea;
}

.method-card.recommended {
  border-color: #28a745;
  background: linear-gradient(135deg, #f8fff9 0%, #e8f5e8 100%);
}

.method-card.recommended::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 0;
  height: 0;
  border-left: 60px solid transparent;
  border-top: 60px solid #28a745;
}

.method-header {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
}

.method-icon {
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

.method-icon.docker {
  background: linear-gradient(135deg, #0db7ed, #0aa2d4);
}

.method-icon.java {
  background: linear-gradient(135deg, #ed8936, #d69e2e);
}

.method-icon.ide {
  background: linear-gradient(135deg, #805ad5, #6b46c1);
}

.method-title {
  flex: 1;
  position: relative;
}

.method-title h4 {
  font-size: 1.3rem;
  font-weight: 600;
  color: #2c3e50;
  margin: 0;
  margin-bottom: 0.25rem;
}

.badge {
  background: #28a745;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 20px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  position: absolute;
  top: -10px;
  right: -10px;
  z-index: 2;
}

.method-description {
  color: #6c757d;
  margin-bottom: 1.5rem;
  line-height: 1.6;
}

.method-steps {
  margin-top: 1rem;
  flex: 1;
}

.method-actions {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e9ecef;
}

.method-actions .btn {
  font-size: 0.9rem;
  padding: 0.5rem 1rem;
}

.step {
  margin-bottom: 1rem;
}


.step-content {
  flex: 1;
}

.step-content p {
  margin: 0;
  line-height: 1.5;
  color: #495057;
}

pre {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 0.75rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  margin: 0.5rem 0;
  overflow-x: auto;
}

pre code {
  background: none;
  padding: 0;
  font-size: 0.9rem;
  color: #495057;
  border: none;
}

.step-content a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.step-content a:hover {
  text-decoration: underline;
}

.step-content strong {
  color: #2c3e50;
}
</style>
