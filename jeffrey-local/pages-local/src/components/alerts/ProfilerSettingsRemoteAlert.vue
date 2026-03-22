<template>
  <div class="profiler-settings-remote-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-hourglass-split fs-4 me-3 mt-1"></i>
      <div>
        <h5 class="alert-heading mb-3">Feature Under Development</h5>
        <p class="mb-3">
          Profiler Settings for Remote workspaces will be available in a future release
          when exposed via the Public API.
        </p>

        <div class="feature-info mb-3">
          <h6 class="mb-2">What this feature will provide:</h6>
          <ul class="mb-0">
            <li>Configure profiler settings for projects in remote workspaces</li>
            <li>Sync settings with remote Jeffrey instances via Public API</li>
            <li>Consistent profiling configuration across environments</li>
          </ul>
        </div>

        <div class="current-options">
          <h6 class="mb-2">Current alternatives:</h6>
          <ol class="mb-0">
            <li>Configure profiler settings directly on the remote Jeffrey instance</li>
            <li>Use global or workspace-level settings on the remote instance</li>
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
.profiler-settings-remote-state {
  margin: 2rem;
}

.alert {
  border: 1px solid #ffeeba;
  border-left: 4px solid #f59e0b;
  background: linear-gradient(135deg, #fffbeb, #ffffff);
}

.alert-heading {
  color: #92400e;
  font-weight: 600;
}

.alert i {
  color: #f59e0b;
}

.feature-info ul {
  padding-left: 1.5rem;
}

.feature-info li,
.current-options li {
  margin-bottom: 0.5rem;
}

.current-options ol {
  padding-left: 1.5rem;
}
</style>
