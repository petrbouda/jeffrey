/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
