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

package cafe.jeffrey.jfr.events.http;

import jdk.jfr.*;

@Category({"Application", "HTTP"})
@StackTrace(false)
public abstract class AbstractHttpExchangeEvent extends Event {

    @Label("Remote Address")
    public String remoteHost;

    @Label("Remote Port")
    public int remotePort;

    @Label("HTTP Uri")
    public String uri;

    @Label("HTTP Method")
    public String method;

    @Label("Media Type")
    public String mediaType;

    @Label("Response Status")
    public int status;

    @Label("Query Parameters")
    public String queryParams;

    @Label("Path Parameters")
    public String pathParams;

    @Label("Request Body Length")
    @DataAmount
    public long requestLength;

    @Label("Response Body Length")
    @DataAmount
    public long responseLength;
}
