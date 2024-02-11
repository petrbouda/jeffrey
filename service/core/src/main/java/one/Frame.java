package one;

import java.util.TreeMap;

import static one.FlameGraph.stripSuffix;
import static one.FrameType.*;

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
public class Frame extends TreeMap<String, Frame> {
        public final byte type;
        public long samples;
        public long self;
        public long inlined, c1, interpreted;

        Frame(byte type) {
            this.type = type;
        }

        byte getType() {
            if (inlined * 3 >= samples) {
                return FRAME_INLINED;
            } else if (c1 * 2 >= samples) {
                return FRAME_C1_COMPILED;
            } else if (interpreted * 2 >= samples) {
                return FRAME_INTERPRETED;
            } else {
                return type;
            }
        }

        private Frame getChild(String title, byte type) {
            Frame child = super.get(title);
            if (child == null) {
                super.put(title, child = new Frame(type));
            }
            return child;
        }

        Frame addChild(String title, long ticks) {
            samples += ticks;

            Frame child;
            if (title.endsWith("_[j]")) {
                child = getChild(stripSuffix(title), FRAME_JIT_COMPILED);
            } else if (title.endsWith("_[i]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).inlined += ticks;
            } else if (title.endsWith("_[k]")) {
                child = getChild(title, FRAME_KERNEL);
            } else if (title.endsWith("_[1]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).c1 += ticks;
            } else if (title.endsWith("_[0]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).interpreted += ticks;
            } else if (title.contains("::") || title.startsWith("-[") || title.startsWith("+[")) {
                child = getChild(title, FRAME_CPP);
            } else if (title.indexOf('/') > 0 && title.charAt(0) != '['
                    || title.indexOf('.') > 0 && Character.isUpperCase(title.charAt(0))) {
                child = getChild(title, FRAME_JIT_COMPILED);
            } else {
                child = getChild(title, FRAME_NATIVE);
            }
            return child;
        }

        void addLeaf(long ticks) {
            samples += ticks;
            self += ticks;
        }

        int depth(long cutoff) {
            int depth = 0;
            if (size() > 0) {
                for (Frame child : values()) {
                    if (child.samples >= cutoff) {
                        depth = Math.max(depth, child.depth(cutoff));
                    }
                }
            }
            return depth + 1;
        }
    }
