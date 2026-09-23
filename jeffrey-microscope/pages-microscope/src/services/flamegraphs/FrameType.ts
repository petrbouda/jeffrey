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
  static THREAD_NAME_SYNTHETIC = 'THREAD_NAME_SYNTHETIC';
  static ALLOCATED_OBJECT_SYNTHETIC = 'ALLOCATED_OBJECT_SYNTHETIC';
  static ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC = 'ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC';
  static ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC = 'ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC';
  static BLOCKING_OBJECT_SYNTHETIC = 'BLOCKING_OBJECT_SYNTHETIC';
  static TRACED_METHOD_SYNTHETIC = 'TRACED_METHOD_SYNTHETIC';
  static COLLAPSED_SYNTHETIC = 'COLLAPSED_SYNTHETIC';
  static TRUNCATED_SYNTHETIC = 'TRUNCATED_SYNTHETIC';
  static HIGHLIGHTED_WARNING = 'HIGHLIGHTED_WARNING';
  static UNKNOWN = 'UNKNOWN';

  // TRACED_METHOD_SYNTHETIC is deliberately absent. The set marks frames whose identity is
  // categorical -- a TLAB marker, a collapsed subtree -- and which therefore keep their palette
  // colour in a differential instead of ramping. A traced method is synthesized only because JEP 520
  // leaves it off its own stack; it is an ordinary Java method, and "did it get slower between these
  // two profiles" is exactly what a reader wants the diff ramp to answer for it.
  private static readonly SYNTHETIC_TYPES = new Set<string>([
    FrameType.THREAD_NAME_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC,
    FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC,
    FrameType.BLOCKING_OBJECT_SYNTHETIC,
    FrameType.COLLAPSED_SYNTHETIC,
    FrameType.TRUNCATED_SYNTHETIC
  ]);

  static isSynthetic(type: string): boolean {
    return FrameType.SYNTHETIC_TYPES.has(type);
  }
}
