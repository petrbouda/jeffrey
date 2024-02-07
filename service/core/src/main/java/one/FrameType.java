package one;

/*
 * Copyright 2020 Andrei Pangin
 * Modifications copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public abstract class FrameType {
    public static final byte FRAME_INTERPRETED = 0;
    public static final byte FRAME_JIT_COMPILED = 1;
    public static final byte FRAME_INLINED = 2;
    public static final byte FRAME_NATIVE = 3;
    public static final byte FRAME_CPP = 4;
    public static final byte FRAME_KERNEL = 5;
    public static final byte FRAME_C1_COMPILED = 6;
}
