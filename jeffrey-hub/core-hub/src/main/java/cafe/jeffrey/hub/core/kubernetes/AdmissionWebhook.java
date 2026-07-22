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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Processes Kubernetes {@code AdmissionReview} requests for pod mutation.
 *
 * <p>Strictly fail-open: whatever goes wrong — unparseable payload, an object
 * that is not a pod, a mutation error — the response is {@code allowed=true}
 * without a patch, so the webhook can never block pod creation. Combined with
 * {@code failurePolicy: Ignore} on the {@code MutatingWebhookConfiguration},
 * Jeffrey's availability guarantee (profiling failures never stop the
 * application) extends to the admission path.</p>
 */
public class AdmissionWebhook {

    private static final Logger LOG = LoggerFactory.getLogger(AdmissionWebhook.class);

    private static final String ADMISSION_REVIEW_KIND = "AdmissionReview";
    private static final String JSON_PATCH_TYPE = "JSONPatch";
    private static final String REPLACE_SPEC_PATCH_PREFIX = "[{\"op\":\"replace\",\"path\":\"/spec\",\"value\":";
    private static final String REPLACE_SPEC_PATCH_SUFFIX = "}]";

    private final PodMutator podMutator;

    public AdmissionWebhook(PodMutator podMutator) {
        this.podMutator = podMutator;
    }

    /**
     * Takes a serialized {@code AdmissionReview} request and returns the
     * serialized {@code AdmissionReview} response.
     */
    public String process(String admissionReviewJson) {
        AdmissionReview review = null;
        AdmissionResponseBuilder responseBuilder = new AdmissionResponseBuilder().withAllowed(true);

        try {
            review = Serialization.unmarshal(admissionReviewJson, AdmissionReview.class);
            responseBuilder.withUid(review.getRequest().getUid());

            if (review.getRequest().getObject() instanceof Pod pod) {
                Optional<Pod> mutated = podMutator.mutate(pod);
                if (mutated.isPresent()) {
                    responseBuilder
                            .withPatchType(JSON_PATCH_TYPE)
                            .withPatch(encodeReplaceSpecPatch(mutated.get()));
                }
            }
        } catch (Exception e) {
            // Fail-open: never block pod creation because of Jeffrey
            LOG.error("Admission review processing failed, allowing pod without mutation", e);
        }

        AdmissionResponse response = responseBuilder.build();
        AdmissionReview result = new AdmissionReviewBuilder()
                .withApiVersion(review != null ? review.getApiVersion() : "admission.k8s.io/v1")
                .withKind(ADMISSION_REVIEW_KIND)
                .withResponse(response)
                .build();
        return Serialization.asJson(result);
    }

    private static String encodeReplaceSpecPatch(Pod mutatedPod) {
        String patch = REPLACE_SPEC_PATCH_PREFIX
                + Serialization.asJson(mutatedPod.getSpec())
                + REPLACE_SPEC_PATCH_SUFFIX;
        return Base64.getEncoder().encodeToString(patch.getBytes(StandardCharsets.UTF_8));
    }
}
