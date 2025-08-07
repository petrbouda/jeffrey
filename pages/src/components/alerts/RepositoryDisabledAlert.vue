<template>
  <div class="repository-disabled-state">
    <div class="alert alert-info d-flex align-items-start">
      <i class="bi bi-info-circle fs-4 me-3 mt-1 text-primary"></i>
      <div>
        <h5 class="alert-heading mb-3">Repository Feature Unavailable</h5>
        <p class="mb-3">
          The Remote Repository feature is only available for projects in external workspaces. 
          This feature allows you to link a directory that serves as a repository for automatically generated raw recordings.
        </p>
        
        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What Remote Repository provides:</h6>
          <ul class="mb-0">
            <li>Automatic collection of raw recordings from external sources</li>
            <li>Centralized storage for profiling data from multiple environments</li>
            <li>Scheduled job processing of raw recordings into profiles</li>
            <li>Team collaboration on profiling data</li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">To use this feature:</h6>
          <ol class="mb-0">
            <li>Create a new external workspace using the "Add Workspace" button</li>
            <li>Projects in external workspaces automatically support repository functionality</li>
            <li>Configure the repository path to point to your data collection directory</li>
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
.repository-disabled-state {
  margin: 2rem;
}

.alert {
  border: 1px solid #b6d7ff;
  border-left: 4px solid #0d6efd;
  background: linear-gradient(135deg, #f8fbff, #ffffff);
}

.alert-heading {
  color: #0d6efd;
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

.text-primary {
  color: #0d6efd !important;
}
</style>