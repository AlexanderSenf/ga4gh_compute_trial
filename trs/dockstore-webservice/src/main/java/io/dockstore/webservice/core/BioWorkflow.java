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
import javax.persistence.FetchType;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * These represent actual workflows in terms of CWL, WDL, and other bioinformatics workflows
 */
@ApiModel(value = "BioWorkflow", description = "This describes one workflow in the dockstore", parent = Workflow.class)
@Entity
@Table(name = "workflow")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.WorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName) from BioWorkflow c where c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.findAllPublishedPathsOrderByDbupdatedate", query = "SELECT new io.dockstore.webservice.core.database.RSSWorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName, c.lastUpdated, c.description) from BioWorkflow c where c.isPublished = true and c.dbUpdateDate is not null ORDER BY c.dbUpdateDate desc")
})
@SuppressWarnings("checkstyle:magicnumber")
public class BioWorkflow extends Workflow {

    @OneToOne(mappedBy = "checkerWorkflow", targetEntity = Entry.class, fetch = FetchType.EAGER)
    @JsonIgnore
    @ApiModelProperty(value = "The parent ID of a checker workflow. Null if not a checker workflow. Required for checker workflows.", position = 22)
    private Entry parentEntry;

    @Column(columnDefinition = "boolean default false")
    @JsonProperty("is_checker")
    @ApiModelProperty(position = 23)
    private boolean isChecker = false;

    public EntryType getEntryType() {
        return EntryType.WORKFLOW;
    }

    @Override
    public Entry getParentEntry() {
        return parentEntry;
    }

    @Override
    public void setParentEntry(Entry parentEntry) {
        this.parentEntry = parentEntry;
    }

    @Override
    public boolean isIsChecker() {
        return this.isChecker;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        this.isChecker = isChecker;
    }

    @JsonProperty("parent_id")
    public Long getParentId() {
        if (parentEntry != null) {
            return parentEntry.getId();
        } else {
            return null;
        }
    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withBioWorkflow(this);
    }
}
