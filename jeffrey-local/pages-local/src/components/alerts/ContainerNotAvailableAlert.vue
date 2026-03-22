<template>
  <div class="container-not-available-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Container Information Unavailable</h5>
        <p class="mb-3">
          The Container Configuration dashboard is not available because your application is not running in a containerized environment.
          Container information is only accessible when the JVM is running inside a container such as Docker, Kubernetes, or other container runtimes.
        </p>
        
        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What Container Configuration provides:</h6>
          <ul class="mb-0">
            <li>CPU limits and resource allocation information</li>
            <li>Memory constraints and usage boundaries</li>
            <li>Container runtime configuration details</li>
            <li>Host system resource visibility from container perspective</li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">To access container information:</h6>
          <ol class="mb-0">
            <li>Deploy your application in a containerized environment (Docker, Kubernetes, etc.)</li>
            <li>Ensure your container runtime provides resource constraint information</li>
            <li>
              Generate a new JFR profile while running in the container
              <div class="automatic-note mt-1">
                <small class="text-muted">
                  <i class="bi bi-info-circle me-1"></i>
                  Container-based JFR events are automatically emitted by JDK Flight Recorder (no need to emit them yourself)
                </small>
              </div>
            </li>
            <li>Container configuration will automatically be available in the dashboard</li>
          </ol>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// No props needed for this static information component
</script>

<style scoped>
.container-not-available-state {
  margin: 2rem;
}

.alert {
  border: 1px solid #ffeaa7;
  border-left: 4px solid #ffc107;
  background: linear-gradient(135deg, #fffbf0, #ffffff);
}

.alert-heading {
  color: #b7791f;
  font-weight: 600;
}

.feature-benefits ul {
  padding-left: 1.5rem;
}

.feature-benefits li,
.next-steps li {
  margin-bottom: 0.5rem;
}

.next-steps ol {
  padding-left: 1.5rem;
}

.text-warning {
  color: #b7791f !important;
}

.automatic-note {
  padding-left: 0.5rem;
  margin-top: 0.25rem;
}

.automatic-note small {
  display: flex;
  align-items: center;
}
</style>