<template>
  <div v-for="(path, pathIndex) in paths" :key="pathIndex" class="path-card">
    <div class="path-header">
      <span class="path-number">Path {{ pathIndex + 1 }}</span>
      <span class="path-step-count">{{ path.steps.length }} steps</span>
    </div>

    <!-- GC Root block -->
    <div class="gc-root-block">
      <div class="gc-root-header">
        <span class="gc-root-label">GC Root</span>
        <span class="gc-root-type">{{ path.rootType }}</span>
      </div>
      <div v-if="path.threadName" class="gc-root-detail">
        <span class="detail-label">Thread:</span>
        <span class="detail-value">"{{ path.threadName }}"</span>
      </div>
      <div v-if="path.stackFrame" class="gc-root-detail">
        <span class="detail-label">Frame:</span>
        <span class="detail-value">{{ frameShort(path.stackFrame) }}</span>
      </div>
      <div v-if="path.stackFrame && framePackagePart(path.stackFrame)" class="gc-root-detail-sub">
        {{ framePackagePart(path.stackFrame) }}
      </div>
    </div>

    <!-- Steps -->
    <div class="steps-chain">
      <div v-for="(step, stepIndex) in path.steps" :key="stepIndex" class="step-wrapper">
        <!-- Connector between steps -->
        <div v-if="stepIndex > 0" class="step-connector">
          <div class="connector-left">
            <div class="connector-line"></div>
            <div class="connector-arrow">&#9660;</div>
          </div>
          <span v-if="path.steps[stepIndex - 1].fieldName" class="connector-field">
            {{ path.steps[stepIndex - 1].fieldName }}
          </span>
        </div>

        <!-- Step node -->
        <div class="step-node" :class="{ 'step-node-target': step.isTarget }" @click="emit('selectObjectId', step.objectId)">
          <div class="step-class-line">
            <span class="step-classname"><span class="step-package">{{ packageName(step.className) }}</span>{{ simpleClassName(step.className) }}</span>
            <span class="step-object-id">#{{ step.objectId }}</span>
            <span class="step-size">{{ FormattingService.formatBytes(step.shallowSize) }}</span>
            <span v-if="step.isTarget" class="target-badge">TARGET</span>
            <i class="bi bi-info-circle step-detail-icon" title="Show instance details"></i>
          </div>
          <div v-if="step.displayValue" class="step-display-value">
            {{ truncateValue(step.displayValue, 120) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { GCRootPath } from '@/services/api/model/GCRootPath'
import FormattingService from '@/services/FormattingService'

defineProps<{
  paths: GCRootPath[]
}>()

const emit = defineEmits<{
  selectObjectId: [objectId: number]
}>()

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.')
  return lastDot >= 0 ? name.substring(lastDot + 1) : name
}

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.')
  return lastDot >= 0 ? name.substring(0, lastDot + 1) : ''
}

const truncateValue = (value: string, maxLen: number): string => {
  return value.length > maxLen ? value.substring(0, maxLen) + '…' : value
}

// "s.f.p.a.DataCatalogUnited.getUnitedBid(File.kt:229)" → "DataCatalogUnited.getUnitedBid(File.kt:229)"
const frameShort = (frame: string): string => {
  const parenIdx = frame.indexOf('(')
  const fqn = parenIdx >= 0 ? frame.substring(0, parenIdx) : frame
  const lastDot = fqn.lastIndexOf('.')
  const secondLastDot = fqn.lastIndexOf('.', lastDot - 1)
  const classMethod = secondLastDot >= 0 ? fqn.substring(secondLastDot + 1) : fqn
  return parenIdx >= 0 ? classMethod + frame.substring(parenIdx) : classMethod
}

// → "secondfoundation.power.models.baseprice.api"
const framePackagePart = (frame: string): string => {
  const parenIdx = frame.indexOf('(')
  const fqn = parenIdx >= 0 ? frame.substring(0, parenIdx) : frame
  const lastDot = fqn.lastIndexOf('.')
  const secondLastDot = fqn.lastIndexOf('.', lastDot - 1)
  return secondLastDot >= 0 ? fqn.substring(0, secondLastDot) : ''
}


</script>

<style scoped>
.path-card {
  border: 1px solid #e0e4e8;
  border-radius: 10px;
  padding: 1.25rem;
  margin-bottom: 1.25rem;
  background: #ffffff;
}

.path-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.path-number {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.path-step-count {
  font-size: 0.8rem;
  color: #868e96;
}

/* GC Root block — same shape as step-node + step-node-target, green */
.gc-root-block {
  border: 1px solid #e9ecef;
  border-left: 4px solid #2b8a3e;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  background: #f1f8f3;
  margin-bottom: 0.5rem;
}

.gc-root-header {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.gc-root-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #2b8a3e;
}

.gc-root-type {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.gc-root-detail {
  font-size: 0.8rem;
  margin-top: 0.25rem;
}

.detail-label {
  color: #868e96;
  font-weight: 600;
  margin-right: 0.35rem;
}

.detail-value {
  font-family: monospace;
  color: #495057;
  overflow-wrap: break-word;
}

.gc-root-detail-sub {
  font-size: 0.8rem;
  color: #adb5bd;
  margin-top: 0.1rem;
  margin-left: 3.2rem;
}



/* Steps chain */
.steps-chain {
  padding-left: 0.25rem;
}

.step-wrapper {
  position: relative;
}

/* Connector */
.step-connector {
  display: flex;
  align-items: center;
  padding: 0.4rem 0 0.4rem 1rem;
  gap: 0.75rem;
}

.connector-left {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.connector-line {
  width: 2px;
  height: 10px;
  background: #ced4da;
}

.connector-field {
  font-size: 0.75rem;
  font-family: monospace;
  color: #6f42c1;
  background: #f3eeff;
  padding: 0.1rem 0.5rem;
  border-radius: 3px;
}

.connector-arrow {
  font-size: 0.55rem;
  color: #ced4da;
}

/* Step node */
.step-node {
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  background: #f8f9fa;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.step-node:hover {
  border-color: #c5d2de;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.step-node-target {
  border-left: 4px solid #1971c2;
  background: #f0f6ff;
}

.step-class-line {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.step-classname {
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
  word-break: break-all;
}

.step-package {
  font-weight: 400;
  color: #adb5bd;
}

.step-object-id {
  font-size: 0.7rem;
  font-family: monospace;
  color: #adb5bd;
}

.step-detail-icon {
  font-size: 0.75rem;
  color: #adb5bd;
  margin-left: 0.25rem;
  transition: color 0.15s;
}

.step-node:hover .step-detail-icon {
  color: #1971c2;
}

.step-size {
  font-size: 0.75rem;
  font-family: monospace;
  color: #495057;
  margin-left: auto;
}

.target-badge {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #1971c2;
  background: #d0ebff;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
}

.step-display-value {
  font-size: 0.78rem;
  color: #868e96;
  font-family: monospace;
  margin-top: 0.25rem;
  word-break: break-all;
}
</style>
