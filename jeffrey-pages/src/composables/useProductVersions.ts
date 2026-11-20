/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ref, onMounted } from 'vue';

export interface ProductVersion {
  version: string;
  url: string;
  source: string;
}

export interface ProductVersions {
  microscope: ProductVersion | null;
  mcp: ProductVersion | null;
  provisioner: ProductVersion | null;
  jib: ProductVersion | null;
  plugin: ProductVersion | null;
  'volume-scheduler': ProductVersion | null;
}

type ProductId = keyof ProductVersions;

const GITHUB_RELEASES_URL = 'https://api.github.com/repos/petrbouda/jeffrey/releases';
const MAX_PAGES = 5;
const PER_PAGE = 100;

interface GitHubRelease {
  tag_name: string;
  html_url: string;
}

const TAG_PREFIX_MICROSCOPE = 'v';
const TAG_PREFIX_PROVISIONER = 'provisioner-v';
const TAG_PREFIX_VOLUME_SCHEDULER = 'volume-scheduler-v';
const TAG_PREFIX_JIB = 'jib-v';
const TAG_PREFIX_INTELLIJ_PLUGIN = 'intellij-plugin-v';

const ALL_PREFIXES = [
  TAG_PREFIX_MICROSCOPE,
  TAG_PREFIX_PROVISIONER,
  TAG_PREFIX_VOLUME_SCHEDULER,
  TAG_PREFIX_JIB,
  TAG_PREFIX_INTELLIJ_PLUGIN
];

const PREFIXED_PRODUCTS = [
  TAG_PREFIX_PROVISIONER,
  TAG_PREFIX_VOLUME_SCHEDULER,
  TAG_PREFIX_JIB,
  TAG_PREFIX_INTELLIJ_PLUGIN
];

function isMicroscopeTag(tagName: string): boolean {
  return /^v\d/.test(tagName) && PREFIXED_PRODUCTS.every(p => !tagName.startsWith(p));
}

function findRelease(releases: GitHubRelease[], prefix: string): GitHubRelease | undefined {
  if (prefix === TAG_PREFIX_MICROSCOPE) {
    return releases.find(r => isMicroscopeTag(r.tag_name));
  }
  return releases.find(r => r.tag_name.startsWith(prefix));
}

function toVersion(release: GitHubRelease, prefix: string): ProductVersion {
  const version = prefix === TAG_PREFIX_MICROSCOPE
    ? release.tag_name
    : release.tag_name.slice(prefix.length - 1);
  return {
    version,
    url: release.html_url,
    source: 'GitHub Releases'
  };
}

function allFound(found: Map<string, GitHubRelease>): boolean {
  return ALL_PREFIXES.every(p => found.has(p));
}

async function fetchAllReleases(): Promise<Map<string, GitHubRelease>> {
  const found = new Map<string, GitHubRelease>();

  for (let page = 1; page <= MAX_PAGES; page++) {
    const response = await fetch(`${GITHUB_RELEASES_URL}?per_page=${PER_PAGE}&page=${page}`);
    if (!response.ok) {
      break;
    }
    const releases: GitHubRelease[] = await response.json();
    if (releases.length === 0) {
      break;
    }

    for (const prefix of ALL_PREFIXES) {
      if (!found.has(prefix)) {
        const release = findRelease(releases, prefix);
        if (release) {
          found.set(prefix, release);
        }
      }
    }

    if (allFound(found) || releases.length < PER_PAGE) {
      break;
    }
  }

  return found;
}

const VOLUME_SCHEDULER_DOCKER_HUB_URL = 'https://hub.docker.com/r/petrbouda/volume-scheduler';

const JIB_GRADLE_URL = 'https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-gradle';
const JIB_MAVEN_URL = 'https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-maven';

export interface JibLinks {
  maven: string;
  gradle: string;
}

export function jibVersionLinks(version: string): JibLinks {
  const v = version.startsWith('v') ? version.slice(1) : version;
  return {
    maven: `${JIB_MAVEN_URL}/${v}`,
    gradle: `${JIB_GRADLE_URL}/${v}`
  };
}

export function useProductVersions() {
  const versions = ref<ProductVersions>({
    microscope: null,
    mcp: null,
    provisioner: null,
    jib: null,
    plugin: null,
    'volume-scheduler': null
  });

  const loading = ref(true);

  onMounted(async () => {
    try {
      const found = await fetchAllReleases();

      const microscope = found.get(TAG_PREFIX_MICROSCOPE);
      const provisioner = found.get(TAG_PREFIX_PROVISIONER);
      const volumeScheduler = found.get(TAG_PREFIX_VOLUME_SCHEDULER);
      const jib = found.get(TAG_PREFIX_JIB);
      const plugin = found.get(TAG_PREFIX_INTELLIJ_PLUGIN);

      versions.value = {
        microscope: microscope ? toVersion(microscope, TAG_PREFIX_MICROSCOPE) : null,
        mcp: microscope ? toVersion(microscope, TAG_PREFIX_MICROSCOPE) : null,
        provisioner: provisioner ? toVersion(provisioner, TAG_PREFIX_PROVISIONER) : null,
        jib: jib ? toVersion(jib, TAG_PREFIX_JIB) : null,
        plugin: plugin ? toVersion(plugin, TAG_PREFIX_INTELLIJ_PLUGIN) : null,
        'volume-scheduler': volumeScheduler ? toVersion(volumeScheduler, TAG_PREFIX_VOLUME_SCHEDULER) : null
      };
    } catch {
      // All banners gracefully hidden on failure
    }

    loading.value = false;
  });

  function versionFor(id: ProductId): ProductVersion | null {
    return versions.value[id];
  }

  return { versions, loading, versionFor, jibVersionLinks, volumeSchedulerDockerHubUrl: VOLUME_SCHEDULER_DOCKER_HUB_URL };
}
