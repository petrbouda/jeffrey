/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
 * Frontend counterpart to the Java `cafe.jeffrey.profile.common.model.FrameType` enum.
 * String values match the Java enum names exactly — they travel over protobuf as the
 * `FRAME_TYPE_*` enum suffix (see `ProtobufConverter.FRAME_TYPE_MAP`).
 */
export default class FrameType {
  static C1_COMPILED = 'C1_COMPILED';
  static NATIVE = 'NATIVE';
  static CPP = 'CPP';
  static INTERPRETED = 'INTERPRETED';
  static JIT_COMPILED = 'JIT_COMPILED';
  static INLINED = 'INLINED';
  static KERNEL = 'KERNEL';
  static PYTHON = 'PYTHON';
  static JAVASCRIPT = 'JAVASCRIPT';
  static GO = 'GO';
  static OTHER_RUNTIME = 'OTHER_RUNTIME';
  static THREAD_NAME_SYNTHETIC = 'THREAD_NAME_SYNTHETIC';
  static ALLOCATED_OBJECT_SYNTHETIC = 'ALLOCATED_OBJECT_SYNTHETIC';
  static ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC = 'ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC';
  static ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC = 'ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC';
  static BLOCKING_OBJECT_SYNTHETIC = 'BLOCKING_OBJECT_SYNTHETIC';
  static LAMBDA_SYNTHETIC = 'LAMBDA_SYNTHETIC';
  static COLLAPSED_SYNTHETIC = 'COLLAPSED_SYNTHETIC';
  static TRUNCATED_SYNTHETIC = 'TRUNCATED_SYNTHETIC';
  static HIGHLIGHTED_WARNING = 'HIGHLIGHTED_WARNING';
  static UNKNOWN = 'UNKNOWN';

  private static readonly SYNTHETIC_TYPES = new Set<string>([
    FrameType.THREAD_NAME_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC,
    FrameType.BLOCKING_OBJECT_SYNTHETIC,
    FrameType.LAMBDA_SYNTHETIC,
    FrameType.COLLAPSED_SYNTHETIC,
    FrameType.TRUNCATED_SYNTHETIC
  ]);

  static isSynthetic(type: string): boolean {
    return FrameType.SYNTHETIC_TYPES.has(type);
  }
}
