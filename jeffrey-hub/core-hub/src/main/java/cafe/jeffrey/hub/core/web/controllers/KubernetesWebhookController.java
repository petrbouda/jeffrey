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

package cafe.jeffrey.hub.core.web.controllers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.core.kubernetes.AdmissionWebhook;

/**
 * Mutating admission webhook endpoint. The request/response payloads are the
 * Kubernetes {@code AdmissionReview} envelope, handled as raw JSON and
 * (de)serialized by the fabric8 model inside {@link AdmissionWebhook} — the
 * controller is a thin HTTP boundary.
 *
 * <p>The Kubernetes API server requires webhooks to be served over HTTPS;
 * terminate TLS on the hub (or a fronting proxy) and reference this path from
 * the {@code MutatingWebhookConfiguration}.</p>
 */
@RestController
@ConditionalOnProperty(name = "jeffrey.hub.kubernetes.webhook.enabled", havingValue = "true")
@RequestMapping("/api/kubernetes/webhook")
public class KubernetesWebhookController {

    private final AdmissionWebhook admissionWebhook;

    public KubernetesWebhookController(AdmissionWebhook admissionWebhook) {
        this.admissionWebhook = admissionWebhook;
    }

    @PostMapping(value = "/mutate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String mutate(@RequestBody String admissionReviewJson) {
        return admissionWebhook.process(admissionReviewJson);
    }
}
