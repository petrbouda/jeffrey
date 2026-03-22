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
        <span class="detail-value thread-name">{{ path.threadName }}</span>
      </div>
      <div v-if="path.stackFrame" class="gc-root-detail">
        <span class="detail-label">Frame:</span>
        <span class="detail-value">
          <span class="frame-class-method">{{ frameClassName(path.stackFrame) }}<span class="frame-method">{{ frameMethodName(path.stackFrame) }}</span></span>
          <span v-if="framePackagePart(path.stackFrame)" class="frame-package-inline">{{ framePackagePart(path.stackFrame) }}</span>
        </span>
      </div>
      <div v-if="path.stackFrame && frameSourceLocation(path.stackFrame)" class="gc-root-detail-sub">
        {{ frameSourceLocation(path.stackFrame) }}
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
          <div class="step-sizes">
            <span class="step-size-item">
              <span class="step-size-label">shallow</span>
              <span class="step-size-value">{{ FormattingService.formatBytes(step.shallowSize) }}</span>
            </span>
            <span v-if="step.retainedSize > 0" class="step-size-item step-size-retained">
              <span class="step-size-label">retained</span>
              <span class="step-size-value">{{ FormattingService.formatBytes(step.retainedSize) }}</span>
            </span>
          </div>
          <div class="step-class-line">
            <span class="step-classname">{{ simpleClassName(step.className) }}</span>
            <span v-if="packageName(step.className)" class="step-package">{{ packageName(step.className) }}</span>
          </div>
          <div v-if="step.objectId || Object.keys(step.objectParams).length > 0" class="step-identity-line">
            <span class="step-object-id">{{ FormattingService.formatObjectId(step.objectId) }}</span>
            <span v-if="Object.keys(step.objectParams).length > 0" class="step-identity-sep">&middot;</span>
            <span v-if="Object.keys(step.objectParams).length > 0" class="step-display-value">{{ FormattingService.formatObjectParams(step.objectParams) }}</span>
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
  return lastDot >= 0 ? name.substring(0, lastDot) : ''
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

// "DataCatalogUnited.getUnitedBid(File.kt:229)" → "DataCatalogUnited."
const frameClassName = (frame: string): string => {
  const short = frameShort(frame)
  const parenIdx = short.indexOf('(')
  const classMethod = parenIdx >= 0 ? short.substring(0, parenIdx) : short
  const dotIdx = classMethod.lastIndexOf('.')
  return dotIdx >= 0 ? classMethod.substring(0, dotIdx + 1) : classMethod
}

// "DataCatalogUnited.getUnitedBid(File.kt:229)" → "getUnitedBid"
const frameMethodName = (frame: string): string => {
  const short = frameShort(frame)
  const parenIdx = short.indexOf('(')
  const classMethod = parenIdx >= 0 ? short.substring(0, parenIdx) : short
  const dotIdx = classMethod.lastIndexOf('.')
  return dotIdx >= 0 ? classMethod.substring(dotIdx + 1) : ''
}

const frameSourceLocation = (frame: string): string => {
  const short = frameShort(frame)
  const parenIdx = short.indexOf('(')
  return parenIdx >= 0 ? short.substring(parenIdx) : ''
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

.thread-name {
  font-weight: 600;
}

.gc-root-detail-sub {
  font-size: 0.8rem;
  color: #868e96;
  margin-top: 0.1rem;
  margin-left: 3.2rem;
}

.frame-package-inline {
  color: #868e96;
  margin-left: 0.35rem;
}

.frame-source {
  color: #868e96;
  margin-left: 0.25rem;
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
  position: relative;
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
  font-size: 0.8rem;
  color: #868e96;
}

.step-identity-line {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: 0.15rem;
}

.step-identity-sep {
  color: #adb5bd;
  font-size: 0.85rem;
}

.step-object-id {
  font-size: 0.75rem;
  font-family: monospace;
  color: #868e96;
}

.step-sizes {
  position: absolute;
  top: 0.4rem;
  right: 0.6rem;
  display: flex;
  gap: 0.75rem;
}

.step-size-item {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
}

.step-size-label {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: #adb5bd;
}

.step-size-value {
  font-size: 0.75rem;
  font-family: monospace;
  font-weight: 600;
  color: #495057;
}

.step-size-retained .step-size-value {
  color: #b8860b;
}

.step-display-value {
  font-size: 0.78rem;
  color: #868e96;
  font-family: monospace;
  word-break: break-all;
}

.frame-package {
  font-weight: 400;
  color: #868e96;
}

.frame-class-method {
  font-weight: 600;
}

.frame-method {
  font-style: italic;
}
</style>
