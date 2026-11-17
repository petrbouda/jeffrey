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

package cafe.jeffrey.volumescheduler.web;

/**
 * Every error the API answers with, by what went wrong.
 */
public enum ApiErrorCode {
    /** {@code /api/v1/jobs/{job}} names no job (404). */
    JOB_NOT_FOUND,
    /** No route at that path (404). */
    NOT_FOUND,
    /** The route exists, the method does not (405). */
    METHOD_NOT_ALLOWED,
    /** Any other request the API cannot take (4xx). */
    BAD_REQUEST,
    /** The request was fine and the service failed it (5xx). */
    INTERNAL_ERROR
}
