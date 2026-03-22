<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">AI Analysis Unavailable</h5>
        <p class="mb-3">
          The AI-powered analysis feature is not configured. To enable AI analysis for your profiles,
          you need to configure an AI provider with a valid API key.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">How to enable AI Analysis:</h6>
          <ol class="mb-0">
            <li>
              Set <code>jeffrey.ai.provider=claude</code> (or <code>chatgpt</code>) in <code>application.properties</code>
            </li>
            <li>
              Copy <code>secrets.properties.templates</code> to <code>secrets.properties</code>
              and configure the import (<code>spring.config.import</code> in <code>application.properties</code>):
              <ul class="mt-1">
                <li>
                  <strong>Classpath (default)</strong> &mdash; place the file on the classpath;
                  already configured as <code>optional:classpath:secrets.properties</code>
                </li>
                <li>
                  <strong>Filesystem</strong> &mdash; point to an absolute path:
                  <code>optional:file:/path/to/secrets.properties</code>
                </li>
              </ul>
            </li>
            <li>
              Add your API key for one of the supported providers:
              <ul class="mt-1">
                <li>
                  <strong>Anthropic Claude</strong> &mdash;
                  <a href="https://console.anthropic.com" target="_blank" class="text-decoration-none">console.anthropic.com</a>
                </li>
                <li>
                  <strong>OpenAI</strong> &mdash;
                  <a href="https://platform.openai.com" target="_blank" class="text-decoration-none">platform.openai.com</a>
                </li>
              </ul>
            </li>
            <li>Restart the Jeffrey application</li>
          </ol>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">Configuration Example:</h6>
          <pre class="mt-2 mb-0 bg-light p-2 rounded"><code># application.properties
jeffrey.ai.provider=claude

# Default: auto-imports secrets.properties from classpath
spring.config.import=optional:classpath:secrets.properties

# Alternative: import from filesystem path
# spring.config.import=optional:file:/path/to/secrets.properties

# secrets.properties
jeffrey.ai.api-key=sk-ant-...
</code></pre>
        </div>
      </div>
    </div>
  </div>
</template>

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

.feature-benefits ul,
.feature-benefits ol {
  padding-left: 1.5rem;
}

.feature-benefits li {
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
</style>
