/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

/**
 * Service for generating URL-friendly slugs/IDs from human-readable labels.
 * Used for generating project names, workspace IDs, etc.
 */
export default class SlugService {
  /**
   * Generates a slug from a label.
   * Converts to lowercase, replaces spaces with dashes, removes special characters.
   *
   * @param label - The human-readable label (e.g., "My New Project")
   * @returns The generated slug (e.g., "my-new-project")
   */
  static generateSlug(label: string): string {
    if (!label) {
      return '';
    }
    return label
      .toLowerCase()
      .replace(/[^a-z0-9\s-]/g, '') // Remove non-alphanumeric characters except spaces and dashes
      .replace(/\s+/g, '-') // Replace spaces with dashes
      .replace(/-+/g, '-') // Replace multiple consecutive dashes with single dash
      .replace(/^-|-$/g, ''); // Remove leading/trailing dashes
  }

  /**
   * Validates and cleans a manually entered slug.
   * Only allows lowercase alphanumeric characters and dashes.
   *
   * @param slug - The slug to validate/clean
   * @returns The cleaned slug
   */
  static validateSlug(slug: string): string {
    return slug
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '') // Remove anything that's not alphanumeric or dash
      .replace(/-+/g, '-') // Replace multiple consecutive dashes with single dash
      .replace(/^-|-$/g, ''); // Remove leading/trailing dashes
  }
}
