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

package cafe.jeffrey.microscope.core.guardian;

import cafe.jeffrey.microscope.core.guardian.GuardSpecJson.ParsedSpec;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.guardian.definition.GuardPreconditions;
import cafe.jeffrey.profile.guardian.definition.MatchExpr;
import cafe.jeffrey.profile.guardian.definition.MatchOp;
import cafe.jeffrey.profile.guardian.definition.TraversalStrategy;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GuardSpecJsonTest {

    @Test
    void parsesPrefixPredicateWithDefaultTraversal() {
        ParsedSpec spec = GuardSpecJson.parseSpec(
                "{\"anchor\":{\"type\":\"Predicate\",\"op\":\"PREFIX\",\"value\":\"ch.qos.logback\"}}");

        MatchExpr.Predicate predicate = assertInstanceOf(MatchExpr.Predicate.class, spec.anchor());
        assertEquals(MatchOp.PREFIX, predicate.op());
        assertEquals("ch.qos.logback", predicate.value());
        assertSame(TraversalStrategy.CURRENT_FRAME, spec.traversal());
    }

    @Test
    void parsesAnyOfComposite() {
        ParsedSpec spec = GuardSpecJson.parseSpec("""
                {"anchor":{"type":"AnyOf","of":[
                    {"type":"Predicate","op":"PREFIX","value":"java.util.regex.Matcher"},
                    {"type":"Predicate","op":"PREFIX","value":"java.util.regex.Pattern"}]}}""");

        MatchExpr.AnyOf anyOf = assertInstanceOf(MatchExpr.AnyOf.class, spec.anchor());
        assertEquals(2, anyOf.of().size());
    }

    @Test
    void parsesAllOfWithNot() {
        ParsedSpec spec = GuardSpecJson.parseSpec("""
                {"anchor":{"type":"AllOf","of":[
                    {"type":"Predicate","op":"PREFIX","value":"com.acme."},
                    {"type":"Not","expr":{"type":"Predicate","op":"CONTAINS","value":"$Proxy"}}]}}""");

        MatchExpr.AllOf allOf = assertInstanceOf(MatchExpr.AllOf.class, spec.anchor());
        assertEquals(2, allOf.of().size());
        assertInstanceOf(MatchExpr.Not.class, allOf.of().get(1));
    }

    @Test
    void parsesDescendTraversalWithBothStepKinds() {
        ParsedSpec spec = GuardSpecJson.parseSpec("""
                {"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},
                 "traversal":{"type":"Descend","steps":[
                    {"type":"ByName","frameName":"WorkerThread::run"},
                    {"type":"ByMatcher",
                     "base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},
                     "target":{"type":"Predicate","op":"PREFIX","value":"VM_G1"}}]}}""");

        TraversalStrategy.Descend descend = assertInstanceOf(TraversalStrategy.Descend.class, spec.traversal());
        assertEquals(2, descend.steps().size());
        assertInstanceOf(TraversalStrategy.Step.ByName.class, descend.steps().get(0));
        assertInstanceOf(TraversalStrategy.Step.ByMatcher.class, descend.steps().get(1));
    }

    @Test
    void parsesNullPreconditionsAsNone() {
        assertSame(GuardPreconditions.NONE, GuardSpecJson.parsePreconditions(null));
        assertSame(GuardPreconditions.NONE, GuardSpecJson.parsePreconditions("  "));
    }

    @Test
    void parsesEventSourceAndGcPreconditions() {
        GuardPreconditions preconditions = GuardSpecJson.parsePreconditions(
                "{\"eventSource\":\"ASYNC_PROFILER\",\"garbageCollectorType\":\"G1\"}");

        assertEquals(RecordingEventSource.ASYNC_PROFILER, preconditions.eventSource());
        assertEquals(GarbageCollectorType.G1, preconditions.garbageCollectorType());
        assertNull(preconditions.debugSymbolsAvailable());
    }
}
