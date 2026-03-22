<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">{{ title }} Unavailable</h5>
        <p class="mb-3">
          {{ title }} is not available because of missing {{ eventType }} events in this profile.
          To enable this dashboard, you need to include the Jeffrey Events library in your application.
        </p>
        
        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What you need to do:</h6>
          <ul class="mb-0">
            <li>Add the Jeffrey Events dependency to your project</li>
            <li>
              Instrument your code to emit {{ eventType }} events at relevant points
              <div class="example-link mt-1">
                <small class="text-muted">
                  <i class="bi bi-github me-1"></i>
                  See implementation examples: 
                  <a href="https://github.com/petrbouda/jeffrey-testapp" target="_blank" class="text-decoration-none">
                    jeffrey-testapp
                  </a>
                </small>
              </div>
            </li>
            <li>Rebuild and restart your application with profiling enabled</li>
            <li>The dashboard will automatically become available once events are detected</li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">Maven Dependency:</h6>
          <p class="mb-2 small text-muted">
            Events are available on Maven Central: 
            <a href="https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events" target="_blank" class="text-decoration-none">
              https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events
            </a>
          </p>
          <pre class="mt-2 mb-0 bg-light p-2 rounded"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-events&lt;/artifactId&gt;
    &lt;version&gt;0.2.1&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
        </div>
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

.example-link {
  padding-left: 0.5rem;
  margin-top: 0.25rem;
}

.example-link small {
  display: flex;
  align-items: center;
}

.example-link a {
  font-weight: 500;
  display: inline-flex;
  align-items: center;
}

.example-link a:hover {
  text-decoration: underline !important;
}
</style>
