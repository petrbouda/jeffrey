/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package io.jafar.parser.internal_api.metadata;

public interface MetadataVisitor {
    default void visitRoot(io.jafar.parser.internal_api.metadata.MetadataRoot root) {};
    default void visitEnd(MetadataRoot root) {};
    default void visitMetadata(MetadataElement metadata) {};
    default void visitEnd(MetadataElement metadata) {};
    default void visitClass(MetadataClass clz) {};
    default void visitEnd(MetadataClass clz) {};
    default void visitSetting(MetadataSetting setting) {};
    default void visitEnd(MetadataSetting setting) {};
    default void visitAnnotation(MetadataAnnotation annotation) {};
    default void visitEnd(MetadataAnnotation annotation) {};
    default void visitField(io.jafar.parser.internal_api.metadata.MetadataField field) {}
    default void visitEnd(MetadataField field) {}
    default void visitRegion(io.jafar.parser.internal_api.metadata.MetadataRegion region) {}
    default void visitEnd(MetadataRegion region) {}
}
