/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.exception.ErrorResponse;
import pbouda.jeffrey.exception.JeffreyException;

public class JeffreyExceptionMapper implements ExceptionMapper<JeffreyException> {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionMapper.class);

    @Override
    public Response toResponse(JeffreyException exception) {
        LOG.error("Handling an exception: ", exception);

        return switch (exception.getType()) {
            case INTERNAL -> {
                yield Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse(exception.getType(), exception.getCode(), exception.getMessage()))
                        .build();
            }
            case CLIENT -> {
                Status status = exception.getCode().isNotFound() ? Status.NOT_FOUND : Status.BAD_REQUEST;
                yield Response.status(status)
                        .entity(new ErrorResponse(exception.getType(), exception.getCode(), exception.getMessage()))
                        .build();
            }
        };
    }
}
