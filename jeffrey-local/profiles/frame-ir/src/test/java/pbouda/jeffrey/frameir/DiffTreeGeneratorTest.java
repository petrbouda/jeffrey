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

package pbouda.jeffrey.frameir;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.common.model.FrameType;

import static org.junit.jupiter.api.Assertions.*;

class DiffTreeGeneratorTest {

    @Nested
    class IdenticalTrees {

        @Test
        void identicalSingleFrameTreesProduceSharedNode() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame secondary = createFrameWithSamples("main", 50, 500);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals("all", result.methodName);
            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertEquals(100, result.primarySamples);
            assertEquals(1000, result.primaryWeight);
            assertEquals(50, result.secondarySamples);
            assertEquals(500, result.secondaryWeight);
        }

        @Test
        void identicalNestedTreesProduceAllSharedNodes() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryChild = createFrameWithSamples("doWork", 80, 800);
            primary.put("doWork", primaryChild);

            Frame secondary = createFrameWithSamples("main", 50, 500);
            Frame secondaryChild = createFrameWithSamples("doWork", 40, 400);
            secondary.put("doWork", secondaryChild);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertEquals(1, result.size());

            DiffFrame childDiff = result.get("doWork");
            assertNotNull(childDiff);
            assertEquals(DiffFrame.Type.SHARED, childDiff.type);
            assertEquals(80, childDiff.primarySamples);
            assertEquals(40, childDiff.secondarySamples);
        }
    }

    @Nested
    class AddedFrames {

        @Test
        void frameOnlyInPrimaryIsMarkedAsAdded() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryChild = createFrameWithSamples("newMethod", 30, 300);
            primary.put("newMethod", primaryChild);

            Frame secondary = createFrameWithSamples("main", 50, 500);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertEquals(1, result.size());

            DiffFrame addedChild = result.get("newMethod");
            assertNotNull(addedChild);
            assertEquals(DiffFrame.Type.ADDED, addedChild.type);
            assertEquals("newMethod", addedChild.methodName);
            assertNotNull(addedChild.frame);
            assertEquals(30, addedChild.frame.totalSamples());
        }

        @Test
        void nestedFrameOnlyInPrimaryIsMarkedAsAdded() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryChild = createFrameWithSamples("doWork", 80, 800);
            Frame primaryGrandchild = createFrameWithSamples("helperMethod", 20, 200);
            primaryChild.put("helperMethod", primaryGrandchild);
            primary.put("doWork", primaryChild);

            Frame secondary = createFrameWithSamples("main", 50, 500);
            Frame secondaryChild = createFrameWithSamples("doWork", 40, 400);
            secondary.put("doWork", secondaryChild);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            DiffFrame workDiff = result.get("doWork");
            assertEquals(DiffFrame.Type.SHARED, workDiff.type);

            DiffFrame helperDiff = workDiff.get("helperMethod");
            assertNotNull(helperDiff);
            assertEquals(DiffFrame.Type.ADDED, helperDiff.type);
        }
    }

    @Nested
    class RemovedFrames {

        @Test
        void frameOnlyInSecondaryIsMarkedAsRemoved() {
            Frame primary = createFrameWithSamples("main", 100, 1000);

            Frame secondary = createFrameWithSamples("main", 50, 500);
            Frame secondaryChild = createFrameWithSamples("oldMethod", 25, 250);
            secondary.put("oldMethod", secondaryChild);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertEquals(1, result.size());

            DiffFrame removedChild = result.get("oldMethod");
            assertNotNull(removedChild);
            assertEquals(DiffFrame.Type.REMOVED, removedChild.type);
            assertEquals("oldMethod", removedChild.methodName);
            assertNotNull(removedChild.frame);
            assertEquals(25, removedChild.frame.totalSamples());
        }

        @Test
        void nestedFrameOnlyInSecondaryIsMarkedAsRemoved() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryChild = createFrameWithSamples("doWork", 80, 800);
            primary.put("doWork", primaryChild);

            Frame secondary = createFrameWithSamples("main", 50, 500);
            Frame secondaryChild = createFrameWithSamples("doWork", 40, 400);
            Frame secondaryGrandchild = createFrameWithSamples("deprecatedMethod", 10, 100);
            secondaryChild.put("deprecatedMethod", secondaryGrandchild);
            secondary.put("doWork", secondaryChild);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            DiffFrame workDiff = result.get("doWork");
            assertEquals(DiffFrame.Type.SHARED, workDiff.type);

            DiffFrame deprecatedDiff = workDiff.get("deprecatedMethod");
            assertNotNull(deprecatedDiff);
            assertEquals(DiffFrame.Type.REMOVED, deprecatedDiff.type);
        }
    }

    @Nested
    class MixedChanges {

        @Test
        void mixOfAddedRemovedAndSharedFrames() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryShared = createFrameWithSamples("sharedMethod", 50, 500);
            Frame primaryAdded = createFrameWithSamples("addedMethod", 20, 200);
            primary.put("sharedMethod", primaryShared);
            primary.put("addedMethod", primaryAdded);

            Frame secondary = createFrameWithSamples("main", 80, 800);
            Frame secondaryShared = createFrameWithSamples("sharedMethod", 40, 400);
            Frame secondaryRemoved = createFrameWithSamples("removedMethod", 15, 150);
            secondary.put("sharedMethod", secondaryShared);
            secondary.put("removedMethod", secondaryRemoved);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertEquals(3, result.size());

            DiffFrame sharedDiff = result.get("sharedMethod");
            assertEquals(DiffFrame.Type.SHARED, sharedDiff.type);
            assertEquals(50, sharedDiff.primarySamples);
            assertEquals(40, sharedDiff.secondarySamples);

            DiffFrame addedDiff = result.get("addedMethod");
            assertEquals(DiffFrame.Type.ADDED, addedDiff.type);

            DiffFrame removedDiff = result.get("removedMethod");
            assertEquals(DiffFrame.Type.REMOVED, removedDiff.type);
        }

        @Test
        void deepTreeWithMultipleLevelsOfChanges() {
            // Build primary: main -> level1 -> level2Added
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryLevel1 = createFrameWithSamples("level1", 80, 800);
            Frame primaryLevel2 = createFrameWithSamples("level2Added", 30, 300);
            primaryLevel1.put("level2Added", primaryLevel2);
            primary.put("level1", primaryLevel1);

            // Build secondary: main -> level1 -> level2Removed
            Frame secondary = createFrameWithSamples("main", 90, 900);
            Frame secondaryLevel1 = createFrameWithSamples("level1", 70, 700);
            Frame secondaryLevel2 = createFrameWithSamples("level2Removed", 25, 250);
            secondaryLevel1.put("level2Removed", secondaryLevel2);
            secondary.put("level1", secondaryLevel1);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            DiffFrame level1Diff = result.get("level1");
            assertEquals(DiffFrame.Type.SHARED, level1Diff.type);
            assertEquals(2, level1Diff.size());

            DiffFrame level2AddedDiff = level1Diff.get("level2Added");
            assertEquals(DiffFrame.Type.ADDED, level2AddedDiff.type);

            DiffFrame level2RemovedDiff = level1Diff.get("level2Removed");
            assertEquals(DiffFrame.Type.REMOVED, level2RemovedDiff.type);
        }
    }

    @Nested
    class SamplesAndWeightAggregation {

        @Test
        void sharedFrameContainsBothPrimaryAndSecondarySamples() {
            Frame primary = createFrameWithSamples("main", 150, 1500);
            Frame secondary = createFrameWithSamples("main", 100, 1000);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(150, result.primarySamples);
            assertEquals(1500, result.primaryWeight);
            assertEquals(100, result.secondarySamples);
            assertEquals(1000, result.secondaryWeight);
            assertEquals(250, result.samples());
            assertEquals(2500, result.weight());
        }

        @Test
        void addedFrameReturnsOnlyPrimarySamplesFromFrame() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame primaryChild = createFrameWithSamples("added", 60, 600);
            primary.put("added", primaryChild);

            Frame secondary = createFrameWithSamples("main", 50, 500);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            DiffFrame addedDiff = result.get("added");
            assertEquals(60, addedDiff.samples());
            assertEquals(600, addedDiff.weight());
        }

        @Test
        void removedFrameReturnsOnlySecondarySamplesFromFrame() {
            Frame primary = createFrameWithSamples("main", 100, 1000);

            Frame secondary = createFrameWithSamples("main", 50, 500);
            Frame secondaryChild = createFrameWithSamples("removed", 35, 350);
            secondary.put("removed", secondaryChild);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            DiffFrame removedDiff = result.get("removed");
            assertEquals(35, removedDiff.samples());
            assertEquals(350, removedDiff.weight());
        }
    }

    @Nested
    class FrameTypePreservation {

        @Test
        void sharedFramePreservesFrameTypeFromPrimary() {
            Frame primary = createFrameWithType("main", FrameType.JIT_COMPILED);
            Frame secondary = createFrameWithType("main", FrameType.INTERPRETED);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(FrameType.JIT_COMPILED, result.frameType);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyChildrenInBothTreesProducesLeafNode() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            Frame secondary = createFrameWithSamples("main", 50, 500);

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(DiffFrame.Type.SHARED, result.type);
            assertTrue(result.isEmpty());
        }

        @Test
        void multipleChildrenAtSameLevelAreMergedCorrectly() {
            Frame primary = createFrameWithSamples("main", 100, 1000);
            primary.put("methodA", createFrameWithSamples("methodA", 30, 300));
            primary.put("methodB", createFrameWithSamples("methodB", 40, 400));
            primary.put("methodC", createFrameWithSamples("methodC", 20, 200));

            Frame secondary = createFrameWithSamples("main", 80, 800);
            secondary.put("methodA", createFrameWithSamples("methodA", 25, 250));
            secondary.put("methodB", createFrameWithSamples("methodB", 35, 350));
            secondary.put("methodD", createFrameWithSamples("methodD", 15, 150));

            DiffTreeGenerator generator = new DiffTreeGenerator(primary, secondary);
            DiffFrame result = generator.generate();

            assertEquals(4, result.size());
            assertEquals(DiffFrame.Type.SHARED, result.get("methodA").type);
            assertEquals(DiffFrame.Type.SHARED, result.get("methodB").type);
            assertEquals(DiffFrame.Type.ADDED, result.get("methodC").type);
            assertEquals(DiffFrame.Type.REMOVED, result.get("methodD").type);
        }
    }

    private static Frame createFrameWithSamples(String methodName, long samples, long weight) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, weight, samples, false);
        return frame;
    }

    private static Frame createFrameWithType(String methodName, FrameType type) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(type, 100, 10, false);
        return frame;
    }
}
