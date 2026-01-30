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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.ArrayItemValue;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.Value;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeNode;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeRequest;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes object references in a heap dump, building tree structures
 * for exploring referrers and reachables.
 */
public class InstanceTreeAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceTreeAnalyzer.class);
    private static final int MAX_LIMIT = 500;

    /**
     * Get referrers of an instance (objects that reference this instance).
     *
     * @param heap     the loaded heap dump
     * @param objectId the object ID to find referrers for
     * @param limit    maximum number of referrers to return
     * @param offset   offset for pagination
     * @return tree response with referrers
     */
    @SuppressWarnings("unchecked")
    public InstanceTreeResponse getReferrers(Heap heap, long objectId, int limit, int offset) {
        return getReferrers(heap, objectId, limit, offset, false);
    }

    /**
     * Get referrers of an instance with compressed oops correction.
     */
    @SuppressWarnings("unchecked")
    public InstanceTreeResponse getReferrers(Heap heap, long objectId, int limit, int offset, boolean compressedOops) {
        Instance instance = (Instance) heap.getInstanceByID(objectId);
        if (instance == null) {
            LOG.debug("Instance not found: objectId={}", objectId);
            return InstanceTreeResponse.notFound();
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        // Get all references to this instance
        List<Value> references = (List<Value>) instance.getReferences();
        int totalCount = references.size();

        // Apply pagination
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        boolean hasMore = totalCount > offset + effectiveLimit;

        List<InstanceTreeNode> children = references.stream()
                .skip(offset)
                .limit(effectiveLimit)
                .map(ref -> toReferrerNode(ref, formatter, heap, compressedOops))
                .filter(node -> node != null)
                .toList();

        // Create root node
        InstanceTreeNode root = createRootNode(instance, formatter, InstanceTreeRequest.TreeMode.REFERRERS, totalCount, heap, compressedOops);

        return InstanceTreeResponse.of(root, children, hasMore, totalCount);
    }

    /**
     * Get reachables from an instance (objects that this instance references).
     *
     * @param heap     the loaded heap dump
     * @param objectId the object ID to find reachables for
     * @param limit    maximum number of reachables to return
     * @param offset   offset for pagination
     * @return tree response with reachables
     */
    @SuppressWarnings("unchecked")
    public InstanceTreeResponse getReachables(Heap heap, long objectId, int limit, int offset) {
        return getReachables(heap, objectId, limit, offset, false);
    }

    /**
     * Get reachables from an instance with compressed oops correction.
     */
    @SuppressWarnings("unchecked")
    public InstanceTreeResponse getReachables(Heap heap, long objectId, int limit, int offset, boolean compressedOops) {
        Instance instance = (Instance) heap.getInstanceByID(objectId);
        if (instance == null) {
            LOG.debug("Instance not found: objectId={}", objectId);
            return InstanceTreeResponse.notFound();
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        // Get all object field values (reachables)
        List<FieldValue> fieldValues = (List<FieldValue>) instance.getFieldValues();
        List<ObjectFieldWithName> objectFields = fieldValues.stream()
                .filter(fv -> fv instanceof ObjectFieldValue)
                .map(fv -> new ObjectFieldWithName((ObjectFieldValue) fv, fv.getField().getName()))
                .filter(ofw -> ofw.objectField.getInstance() != null)
                .toList();

        int totalCount = objectFields.size();

        // Apply pagination
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        boolean hasMore = totalCount > offset + effectiveLimit;

        List<InstanceTreeNode> children = objectFields.stream()
                .skip(offset)
                .limit(effectiveLimit)
                .map(ofw -> toReachableNode(ofw, formatter, heap, compressedOops))
                .toList();

        // Create root node
        InstanceTreeNode root = createRootNode(instance, formatter, InstanceTreeRequest.TreeMode.REACHABLES, totalCount, heap, compressedOops);

        return InstanceTreeResponse.of(root, children, hasMore, totalCount);
    }

    private InstanceTreeNode createRootNode(Instance instance, InstanceValueFormatter formatter,
                                            InstanceTreeRequest.TreeMode mode, int childCount, Heap heap,
                                            boolean compressedOops) {
        String value = formatter.formatAsString(instance);
        boolean hasChildren = childCount > 0;

        return InstanceTreeNode.root(
                instance.getInstanceId(),
                instance.getJavaClass().getName(),
                value,
                CompressedOopsCorrector.correctedShallowSize(instance, compressedOops),
                hasChildren,
                childCount
        );
    }

    @SuppressWarnings("unchecked")
    private InstanceTreeNode toReferrerNode(Value reference, InstanceValueFormatter formatter, Heap heap,
                                             boolean compressedOops) {
        Instance definingInstance = reference.getDefiningInstance();
        if (definingInstance == null) {
            return null;
        }

        // Determine field name based on the type of reference
        String fieldName = null;
        if (reference instanceof ObjectFieldValue ofv) {
            fieldName = ofv.getField().getName();
        } else if (reference instanceof ArrayItemValue aiv) {
            fieldName = "[" + aiv.getIndex() + "]";
        }

        String value = formatter.formatAsString(definingInstance);

        // Check if this referrer has its own referrers
        List<Value> refs = (List<Value>) definingInstance.getReferences();
        boolean hasChildren = !refs.isEmpty();
        int childCount = refs.size();

        return InstanceTreeNode.referrer(
                definingInstance.getInstanceId(),
                definingInstance.getJavaClass().getName(),
                value,
                CompressedOopsCorrector.correctedShallowSize(definingInstance, compressedOops),
                fieldName,
                hasChildren,
                childCount
        );
    }

    @SuppressWarnings("unchecked")
    private InstanceTreeNode toReachableNode(ObjectFieldWithName objectFieldWithName,
                                              InstanceValueFormatter formatter, Heap heap,
                                              boolean compressedOops) {
        Instance referencedInstance = objectFieldWithName.objectField.getInstance();
        String value = formatter.formatAsString(referencedInstance);

        // Check if this reachable has its own reachables
        List<FieldValue> fieldValues = (List<FieldValue>) referencedInstance.getFieldValues();
        int objectFieldCount = (int) fieldValues.stream()
                .filter(fv -> fv instanceof ObjectFieldValue)
                .map(fv -> (ObjectFieldValue) fv)
                .filter(ofv -> ofv.getInstance() != null)
                .count();

        boolean hasChildren = objectFieldCount > 0;

        return InstanceTreeNode.reachable(
                referencedInstance.getInstanceId(),
                referencedInstance.getJavaClass().getName(),
                value,
                CompressedOopsCorrector.correctedShallowSize(referencedInstance, compressedOops),
                objectFieldWithName.fieldName,
                hasChildren,
                objectFieldCount
        );
    }

    /**
     * Helper record to carry both the ObjectFieldValue and its field name.
     */
    private record ObjectFieldWithName(ObjectFieldValue objectField, String fieldName) {
    }
}
