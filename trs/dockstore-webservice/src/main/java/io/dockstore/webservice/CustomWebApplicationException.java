/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author xliu
 */
public class CustomWebApplicationException extends WebApplicationException {

    public final String errorMessage;
    public CustomWebApplicationException(String message, int status) {
        super(Response.status(status).entity(message).type(MediaType.TEXT_PLAIN).build());
        this.errorMessage = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
