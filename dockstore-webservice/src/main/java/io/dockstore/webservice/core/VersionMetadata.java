/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.core;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import io.swagger.annotations.ApiModelProperty;

/**
 * Data about versions of a workflow/tool in Dockstore rather than about the original workflow.
 *
 * Stays modifiable even when the parent (version) becomes immutable via postgres security policies, allowing
 * us to modify things like verification status, DOIs, and whether a workflow version is hidden.
 *
 * Note that this entity is not directly serialized, instead individual fields are exposed in the Version
 * model.
 */
@Entity
@Table(name = "version_metadata")
public class VersionMetadata {
    @Column(columnDefinition =  "boolean default false")
    protected boolean verified;

    @Column()
    protected String verifiedSource;

    @Column()
    protected String doiURL;

    @Column()
    protected boolean hidden;

    @Column(columnDefinition = "text default 'NOT_REQUESTED'", nullable = false)
    @Enumerated(EnumType.STRING)
    protected Version.DOIStatus doiStatus;

    @Column
    @ApiModelProperty(value = "This is the name of the author stated in the descriptor")
    protected String author;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    protected  String description;

    @Column(name = "description_source")
    @Enumerated(EnumType.STRING)
    protected DescriptionSource descriptionSource;

    @Column
    @ApiModelProperty(value = "This is the email of the author stated in the descriptor")
    protected String email;

    @MapsId
    @OneToOne
    @JoinColumn(name = "id")
    protected Version parent;

    @Id
    @Column(name = "id")
    private long id;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
