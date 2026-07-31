<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">AI Analysis Unavailable</h5>
        <p class="mb-3">
          The AI-powered analysis feature is not configured. To enable AI analysis for your
          profiles, you need to configure an AI provider with a valid API key.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">How to enable AI Analysis:</h6>
          <ol class="mb-0">
            <li>
              Open <router-link to="/settings#ai">Settings &rarr; AI Configuration</router-link>
            </li>
            <li>
              Turn on <strong>Enable AI</strong> and choose a provider:
              <ul class="mt-1">
                <li>
                  <strong>Claude Code</strong> &mdash; uses your existing Claude subscription
                  through the Claude Code CLI; no API key required
                </li>
                <li>
                  <strong>Claude (Anthropic)</strong> &mdash; API key from
                  <a
                    href="https://console.anthropic.com"
                    target="_blank"
                    class="text-decoration-none"
                    >console.anthropic.com</a
                  >
                </li>
                <li>
                  <strong>ChatGPT (OpenAI)</strong> &mdash; API key from
                  <a href="https://platform.openai.com" target="_blank" class="text-decoration-none"
                    >platform.openai.com</a
                  >
                </li>
                <li>
                  <strong>Ollama</strong> &mdash; a self-hosted model; set the base URL of your
                  server
                </li>
              </ul>
            </li>
            <li>Save &mdash; the change applies immediately, no restart required</li>
          </ol>
        </div>

        <p class="mb-0 text-muted small">
          API keys are encrypted at rest with a machine-bound key.
        </p>
      </div>
    </div>
  </div>
</template>

<style>
@import '@shared/styles/shared-components.css';
</style>
