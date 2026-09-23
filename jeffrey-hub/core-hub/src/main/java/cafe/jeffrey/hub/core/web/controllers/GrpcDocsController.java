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
package cafe.jeffrey.hub.core.web.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the gRPC API reference the build generated from the protos, for the API Documentation page.
 */
@RestController
@RequestMapping("/api/internal/grpc-docs")
public class GrpcDocsController {

    private static final String GRPC_DOCS_RESOURCE = "grpc-api-docs.json";

    @GetMapping
    public ResponseEntity<Resource> getGrpcDocs() {
        Resource docs = new ClassPathResource(GRPC_DOCS_RESOURCE);
        if (!docs.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(docs);
    }
}
