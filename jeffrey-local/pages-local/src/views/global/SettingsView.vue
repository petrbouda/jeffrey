<template>
  <div>
    <MainCard>
    <!-- Restart Banner -->
    <div v-if="showRestartBanner" class="restart-banner">
      <i class="bi bi-exclamation-triangle"></i>
      <span>AI settings have been updated. Restart the application to apply changes.</span>
      <button class="dismiss-btn" @click="showRestartBanner = false">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <!-- Encryption Warning -->
    <div v-if="encryptionMode === 'USER_BOUND'" class="encryption-warning">
      <i class="bi bi-info-circle"></i>
      <span>Machine-specific encryption unavailable. Secrets are encrypted with user-level binding only.</span>
    </div>

    <!-- Tabs -->
    <div class="settings-tabs">
      <button
        class="settings-tab"
        :class="{ active: activeTab === 'ai' }"
        @click="activeTab = 'ai'">
        <i class="bi bi-robot"></i>
        AI Configuration
      </button>
      <button
        class="settings-tab"
        :class="{ active: activeTab === 'general' }"
        @click="activeTab = 'general'">
        <i class="bi bi-gear"></i>
        General
      </button>
    </div>

    <!-- AI Configuration Tab -->
    <div v-if="activeTab === 'ai'" class="settings-content">
      <div class="content-header-with-toggle">
        <div>
          <h3>AI Configuration</h3>
        </div>
        <div class="toggle-area">
          <span class="toggle-label">Enable AI</span>
          <label class="toggle-switch">
            <input type="checkbox" v-model="aiToggle" @change="onAiToggleChange" />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>

      <div class="settings-form-grid" :class="{ 'settings-form-disabled': !aiEnabled }">
        <div class="settings-form-group">
          <label class="settings-label">Provider</label>
          <select v-model="ai.provider" class="form-control select-with-indicator" :disabled="!aiEnabled">
            <option value="claude">Claude (Anthropic)</option>
            <option value="chatgpt">ChatGPT (OpenAI)</option>
          </select>
        </div>
        <div class="settings-form-group">
          <label class="settings-label">Model</label>
          <select v-model="ai.model" class="form-control select-with-indicator" :disabled="!aiEnabled">
            <template v-if="ai.provider === 'claude'">
              <option value="claude-opus-4-6">claude-opus-4-6</option>
              <option value="claude-opus-4-5-20251101">claude-opus-4-5-20251101</option>
              <option value="claude-sonnet-4-5-20250929">claude-sonnet-4-5-20250929</option>
              <option value="claude-sonnet-4-20250514">claude-sonnet-4-20250514</option>
            </template>
            <template v-else-if="ai.provider === 'chatgpt'">
              <option value="gpt-4o">gpt-4o</option>
              <option value="gpt-4o-mini">gpt-4o-mini</option>
              <option value="o3-mini">o3-mini</option>
            </template>
          </select>
        </div>
        <div class="settings-form-group">
          <label class="settings-label">API Key</label>
          <div class="password-wrap">
            <input
              :type="showApiKey ? 'text' : 'password'"
              v-model="ai.apiKey"
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="Enter your API key" />
            <button class="toggle-eye" @click="showApiKey = !showApiKey">
              <i :class="showApiKey ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
            </button>
          </div>
          <div class="settings-hint">
            <i class="bi bi-lock"></i> Encrypted at rest with machine-bound key
          </div>
        </div>
        <div class="settings-form-group">
          <label class="settings-label">Max Tokens</label>
          <input
            type="number"
            v-model.number="ai.maxTokens"
            class="form-control"
            :disabled="!aiEnabled"
            placeholder="128000" />
          <div class="settings-hint">Maximum token limit per AI request</div>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" @click="saveAiSettings" :disabled="saving || !aiEnabled">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>

    <!-- General Tab -->
    <div v-if="activeTab === 'general'" class="settings-content">
      <div class="content-header">
        <h3>General</h3>
        <p>General application preferences.</p>
      </div>

      <div class="settings-form-grid settings-form-grid-single">
        <div class="settings-form-group">
          <label class="settings-label">Log Level</label>
          <select v-model="general.logLevel" class="form-control select-with-indicator" style="max-width: 300px;">
            <option value="INFO">INFO</option>
            <option value="DEBUG">DEBUG</option>
            <option value="TRACE">TRACE</option>
          </select>
          <div class="settings-hint">
            <i class="bi bi-lightning-charge"></i> Takes effect immediately, no restart needed
          </div>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" @click="saveGeneralSettings" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import '@/styles/form-utilities.css'
import { computed, onMounted, ref, watch } from 'vue'
import SettingsClient from '@/services/api/SettingsClient'
import MainCard from '@/components/MainCard.vue'
import type Setting from '@/services/api/model/Setting'

const client = new SettingsClient()

const activeTab = ref('ai')
const showRestartBanner = ref(false)
const showApiKey = ref(false)
const saving = ref(false)
const encryptionMode = ref('')

const ai = ref({
  provider: 'none',
  model: 'claude-opus-4-6',
  apiKey: '',
  maxTokens: 128000
})

const aiToggle = ref(false)
const aiEnabled = computed(() => aiToggle.value)

const general = ref({
  logLevel: 'INFO'
})

// Reset model when provider changes
watch(() => ai.value.provider, (newProvider) => {
  if (newProvider === 'claude') {
    ai.value.model = 'claude-opus-4-6'
  } else if (newProvider === 'chatgpt') {
    ai.value.model = 'gpt-4o'
  } else {
    ai.value.model = ''
  }
})

onMounted(async () => {
  try {
    const [settings, encryption] = await Promise.all([
      client.fetchAll(),
      client.fetchEncryptionMode()
    ])

    encryptionMode.value = encryption.mode

    for (const setting of settings) {
      applySetting(setting)
    }
  } catch (e) {
    console.error('Failed to load settings', e)
  }
})

function applySetting(setting: Setting) {
  if (setting.category === 'ai') {
    switch (setting.key) {
      case 'provider':
        ai.value.provider = setting.value
        aiToggle.value = setting.value !== 'none'
        break
      case 'model': ai.value.model = setting.value; break
      case 'api-key': ai.value.apiKey = setting.value; break
      case 'max-tokens': ai.value.maxTokens = parseInt(setting.value) || 128000; break
    }
  } else if (setting.category === 'general') {
    switch (setting.key) {
      case 'log-level': general.value.logLevel = setting.value; break
    }
  }
}

async function onAiToggleChange() {
  if (!aiToggle.value) {
    // Disabling — save provider=none immediately
    saving.value = true
    try {
      await client.upsert('ai', 'provider', 'none', false)
      ai.value.provider = 'none'
      showRestartBanner.value = true
    } catch (e) {
      console.error('Failed to disable AI', e)
      aiToggle.value = true
    } finally {
      saving.value = false
    }
  } else {
    // Enabling — set defaults, user will configure and save
    if (ai.value.provider === 'none' || ai.value.provider === '') {
      ai.value.provider = 'claude'
      ai.value.model = 'claude-opus-4-6'
    }
  }
}

async function saveAiSettings() {
  saving.value = true
  try {
    await Promise.all([
      client.upsert('ai', 'provider', ai.value.provider, false),
      client.upsert('ai', 'model', ai.value.model, false),
      client.upsert('ai', 'max-tokens', String(ai.value.maxTokens), false),
      ...(ai.value.apiKey && !ai.value.apiKey.includes('****')
        ? [client.upsert('ai', 'api-key', ai.value.apiKey, true)]
        : [])
    ])
    showRestartBanner.value = true
  } catch (e) {
    console.error('Failed to save AI settings', e)
  } finally {
    saving.value = false
  }
}

async function saveGeneralSettings() {
  saving.value = true
  try {
    await client.upsert('general', 'log-level', general.value.logLevel, false)
  } catch (e) {
    console.error('Failed to save general settings', e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.restart-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #8d6e00;
  font-size: 13px;
  font-weight: 500;
}

.restart-banner .dismiss-btn {
  margin-left: auto;
  cursor: pointer;
  opacity: 0.6;
  background: none;
  border: none;
  color: inherit;
  padding: 4px;
}

.restart-banner .dismiss-btn:hover {
  opacity: 1;
}

.encryption-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #e3f2fd;
  border: 1px solid #90caf9;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #1565c0;
  font-size: 13px;
  font-weight: 500;
}

.settings-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--color-border, #e2e8f0);
  margin-bottom: 24px;
}

.settings-tab {
  padding: 12px 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted, #718096);
  cursor: pointer;
  border: none;
  background: none;
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
}

.settings-tab i {
  font-size: 16px;
}

.settings-tab::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background: transparent;
  transition: background 0.2s;
}

.settings-tab.active {
  color: var(--color-primary, #5e64ff);
}

.settings-tab.active::after {
  background: var(--color-primary, #5e64ff);
}

.settings-tab:hover {
  color: var(--color-text, #4a5568);
}

.settings-content {
  padding-top: 4px;
}

.content-header {
  margin-bottom: 24px;
}

.content-header h3 {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}

.content-header p {
  font-size: 12px;
  color: var(--color-text-muted, #718096);
  margin: 0;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.settings-form-grid-single {
  grid-template-columns: 1fr;
}

.settings-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-dark, #0b1727);
  margin-bottom: 6px;
  display: block;
}

.settings-hint {
  font-size: 11px;
  color: var(--color-text-muted, #748194);
  margin-top: 4px;
}

.password-wrap {
  position: relative;
}

.password-wrap .form-control {
  padding-right: 40px;
}

.password-wrap .toggle-eye {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-muted, #748194);
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
}

.content-header-with-toggle {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.content-header-with-toggle h3 {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}

.content-header-with-toggle p {
  font-size: 12px;
  color: var(--color-text-muted, #718096);
  margin: 0;
}

.toggle-area {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.toggle-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #5e6e82);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #cbd5e0;
  border-radius: 24px;
  transition: 0.3s;
}

.toggle-slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.3s;
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--color-primary, #5e64ff);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(20px);
}

.settings-form-disabled {
  opacity: 0.4;
  pointer-events: none;
}

.btn-primary {
  padding: 9px 24px;
  background: var(--color-primary, #5e64ff);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: var(--color-primary-hover, #4c52db);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-control:disabled {
  background: var(--color-bg-hover, #f9fafd);
  cursor: not-allowed;
  opacity: 0.7;
}

</style>
