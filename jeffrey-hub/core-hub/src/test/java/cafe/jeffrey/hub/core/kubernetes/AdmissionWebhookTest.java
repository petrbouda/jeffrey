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

package cafe.jeffrey.hub.core.kubernetes;

import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.shared.common.Json;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionWebhookTest {

    private static final String UID = "705ab4f5-6393-11e8-b7cc-42010a800002";

    private final AdmissionWebhook webhook = new AdmissionWebhook(
            new PodMutator(new PodMutator.WebhookInjectionSettings("debian:stable-slim", "jeffrey-pvc")));

    private static String admissionReviewFor(String podJson) {
        return """
                {
                  "apiVersion": "admission.k8s.io/v1",
                  "kind": "AdmissionReview",
                  "request": {
                    "uid": "%s",
                    "kind": {"group": "", "version": "v1", "kind": "Pod"},
                    "resource": {"group": "", "version": "v1", "resource": "pods"},
                    "operation": "CREATE",
                    "object": %s
                  }
                }
                """.formatted(UID, podJson);
    }

    private static final String ANNOTATED_POD = """
            {
              "apiVersion": "v1",
              "kind": "Pod",
              "metadata": {
                "generateName": "my-service-7d9f6c-",
                "labels": {"app": "my-service"},
                "annotations": {"jeffrey.cafe/enabled": "true", "jeffrey.cafe/workspace": "production"}
              },
              "spec": {
                "containers": [
                  {"name": "app", "image": "my-service:1.0.0"}
                ]
              }
            }
            """;

    private static final String PLAIN_POD = """
            {
              "apiVersion": "v1",
              "kind": "Pod",
              "metadata": {"name": "plain-pod"},
              "spec": {"containers": [{"name": "app", "image": "app:1"}]}
            }
            """;

    @Nested
    class MutatingRequests {

        @Test
        void annotatedPod_respondsWithReplaceSpecPatch() {
            String response = webhook.process(admissionReviewFor(ANNOTATED_POD));

            AdmissionReview review = Serialization.unmarshal(response, AdmissionReview.class);
            assertEquals(UID, review.getResponse().getUid());
            assertTrue(review.getResponse().getAllowed());
            assertEquals("JSONPatch", review.getResponse().getPatchType());

            String patchJson = new String(
                    Base64.getDecoder().decode(review.getResponse().getPatch()), StandardCharsets.UTF_8);
            JsonNode patch = Json.readTree(patchJson);
            assertEquals(1, patch.size());
            assertEquals("replace", patch.get(0).get("op").asString());
            assertEquals("/spec", patch.get(0).get("path").asString());

            PodSpec mutatedSpec = Serialization.unmarshal(patch.get(0).get("value").toString(), PodSpec.class);
            assertEquals(PodMutator.INIT_CONTAINER_NAME, mutatedSpec.getInitContainers().getFirst().getName());
            assertEquals(2, mutatedSpec.getVolumes().size());
        }

        @Test
        void nonAnnotatedPod_allowedWithoutPatch() {
            String response = webhook.process(admissionReviewFor(PLAIN_POD));

            AdmissionReview review = Serialization.unmarshal(response, AdmissionReview.class);
            assertEquals(UID, review.getResponse().getUid());
            assertTrue(review.getResponse().getAllowed());
            assertNull(review.getResponse().getPatch());
        }
    }

    @Nested
    class FailOpen {

        @Test
        void unparseablePayload_allowedWithoutPatch() {
            String response = webhook.process("NOT JSON {{{");

            AdmissionReview review = Serialization.unmarshal(response, AdmissionReview.class);
            assertTrue(review.getResponse().getAllowed());
            assertNull(review.getResponse().getPatch());
        }
    }
}
