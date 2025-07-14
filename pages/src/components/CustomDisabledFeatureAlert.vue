<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-center">
      <i class="bi bi-exclamation-triangle me-2"></i>
      <div>
        <p class="mb-2">{{ title }} is not available because of missing {{ eventType }} events in this profile.</p>
        <p class="mb-0">
          <small class="text-muted">
            Events are available on Maven Central: 
            <a href="https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events" target="_blank" class="text-decoration-none">
              https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events
            </a>
            <br>
            Use maven dependency to submit the events and make the dashboard available:
          </small>
        </p>
        <pre class="mt-2 mb-0 bg-light p-2 rounded"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-events&lt;/artifactId&gt;
    &lt;version&gt;0.2.1&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { withDefaults, defineProps } from 'vue';

// Define props
interface Props {
  title: string;
  eventType: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: 'Dashboard',
  eventType: 'required'
});
</script>

<style scoped>
.feature-disabled-state {
  margin-bottom: 1rem;
}

.alert {
  border-left: 4px solid #ffc107;
}

pre {
  font-size: 0.8rem;
  border: 1px solid #dee2e6;
}

code {
  color: #495057;
}

a {
  color: #0d6efd;
}

a:hover {
  color: #0a58ca;
}
</style>
