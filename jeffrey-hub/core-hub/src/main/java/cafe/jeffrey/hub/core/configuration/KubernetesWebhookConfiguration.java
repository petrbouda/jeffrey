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

package cafe.jeffrey.hub.core.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.kubernetes.AdmissionWebhook;
import cafe.jeffrey.hub.core.kubernetes.PodMutator;

/**
 * Optional mutating admission webhook, activated with
 * {@code jeffrey.hub.kubernetes.webhook.enabled=true}. Independent of the
 * informer-based discovery — either can run without the other.
 */
@Configuration
@ConditionalOnProperty(name = "jeffrey.hub.kubernetes.webhook.enabled", havingValue = "true")
public class KubernetesWebhookConfiguration {

    @Bean
    public PodMutator podMutator(
            @Value("${jeffrey.hub.kubernetes.webhook.init-image:debian:stable-slim}") String initImage,
            @Value("${jeffrey.hub.kubernetes.webhook.pvc-claim-name:jeffrey-pvc}") String pvcClaimName) {

        return new PodMutator(new PodMutator.WebhookInjectionSettings(initImage, pvcClaimName));
    }

    @Bean
    public AdmissionWebhook admissionWebhook(PodMutator podMutator) {
        return new AdmissionWebhook(podMutator);
    }
}
