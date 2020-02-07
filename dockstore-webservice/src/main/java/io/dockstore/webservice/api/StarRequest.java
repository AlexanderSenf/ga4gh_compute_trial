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

package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

/**
 * This is an object to encapsulate a publish request in an entity. Does not need to be stored in the database. Used for the body of
 * /containers/{containerId}/publish
 *
 * @author xliu
 */
@ApiModel("StarRequest")
public class StarRequest {
    private boolean star;

    public StarRequest() {
    }

    public StarRequest(boolean star) {
        this.star = star;
    }

    @JsonProperty
    public boolean getStar() {
        return star;
    }

    public void setStar(boolean star) {
        this.star = star;
    }
}
