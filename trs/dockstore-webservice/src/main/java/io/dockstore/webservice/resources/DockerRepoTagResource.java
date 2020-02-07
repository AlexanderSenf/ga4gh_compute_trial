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

package io.dockstore.webservice.resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.Beta;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Path("/containers")
@Api("containertags")
@Produces(MediaType.APPLICATION_JSON)
@io.swagger.v3.oas.annotations.tags.Tag(name = "containertags", description = ResourceConstants.CONTAINERTAGS)
public class DockerRepoTagResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoTagResource.class);
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;
    private final EventDAO eventDAO;

    public DockerRepoTagResource(ToolDAO toolDAO, TagDAO tagDAO, EventDAO eventDAO) {
        this.tagDAO = tagDAO;
        this.toolDAO = toolDAO;
        this.eventDAO = eventDAO;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/{containerId}/tags")
    @ApiOperation(value = "Get tags for a tool by id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "Set")
    public Set<Tag> getTagsByPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = findToolByIdAndCheckToolAndUser(containerId, user);
        return tool.getWorkflowVersions();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Update the tags linked to a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List")
    public Set<Tag> updateTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of modified tags", required = true) List<Tag> tags) {
        Tool tool = findToolByIdAndCheckToolAndUser(containerId, user);

        // create a map for quick lookup
        Map<Long, Tag> mapOfExistingTags = new HashMap<>();
        for (Tag tag : tool.getWorkflowVersions()) {
            mapOfExistingTags.put(tag.getId(), tag);
        }

        for (Tag tag : tags) {
            if (mapOfExistingTags.containsKey(tag.getId())) {
                // remove existing copy and add the new one
                Tag existingTag = mapOfExistingTags.get(tag.getId());

                // If any paths have changed then set dirty bit to true
                boolean dirtyBitCheck = new EqualsBuilder().append(existingTag.getCwlPath(), tag.getCwlPath())
                        .append(existingTag.getWdlPath(), tag.getWdlPath()).append(existingTag.getDockerfilePath(), tag.getDockerfilePath())
                        .isEquals();

                if (!dirtyBitCheck) {
                    existingTag.setDirtyBit(true);
                }

                existingTag.updateByUser(tag);
            }
        }
        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result.getWorkflowVersions();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Add new tags linked to a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List")
    public Set<Tag> addTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of new tags", required = true) List<Tag> tags) {

        Tool tool = findToolByIdAndCheckToolAndUser(containerId, user);

        if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
            String msg = "Only manually added images can add version tags.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        for (Tag tag : tags) {
            final long tagId = tagDAO.create(tag);
            Tag byId = tagDAO.findById(tagId);
            this.eventDAO.createAddTagToEntryEvent(user, tool, byId);
            // Set dirty bit since this is a manual add
            byId.setDirtyBit(true);

            boolean ableToAdd = tool.addWorkflowVersion(byId);
            if (!ableToAdd) {
                tagDAO.delete(byId);
                throw new CustomWebApplicationException("Rollback of tag creation due to duplicate name", HttpStatus.SC_BAD_REQUEST);
            }
        }

        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result.getWorkflowVersions();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags/{tagId}")
    @ApiOperation(value = "Delete tag linked to a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public Response deleteTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tag to delete", required = true) @PathParam("tagId") Long tagId) {
        Tool tool = findToolByIdAndCheckToolAndUser(containerId, user);

        if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
            String msg = "Only manually added images can delete version tags.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Tag tag = tagDAO.findById(tagId);
        if (tag == null) {
            LOG.error(user.getUsername() + ": could not find tag: " + tool.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }

        Set<Tag> listOfTags = tool.getWorkflowVersions();

        if (listOfTags.contains(tag)) {
            tag.getSourceFiles().clear();

            if (tool.getWorkflowVersions().remove(tag)) {
                PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.UPDATE);
                return Response.noContent().build();
            } else {
                return Response.serverError().build();
            }
        } else {
            LOG.error(user.getUsername() + ": could not find tag: " + tagId + " in " + tool.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Beta
    @Path("/{containerId}/requestDOI/{tagId}")
    @ApiOperation(value = "Request a DOI for this version of a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List")
    public Set<Tag> requestDOIForToolTag(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tag to request DOI.", required = true) @PathParam("tagId") Long tagId) {
        Tool tool = findToolByIdAndCheckToolAndUser(containerId, user);

        Tag tag = tagDAO.findById(tagId);
        if (tag == null) {
            LOG.error(user.getUsername() + ": could not find tag: " + tool.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }

        // DOI submission (SQS or not) has not been implemented yet for tools
        throw new CustomWebApplicationException("DOI creation for tools has not been implemented yet.", HttpStatus.SC_BAD_REQUEST);

        //        if (tag.getDoiStatus() != Version.DOIStatus.CREATED) {
        //            DOIGeneratorInterface generator = DOIGeneratorFactory.createDOIGenerator();
        //            generator.createDOIForTool(containerId, tagId);
        //            tag.setDoiStatus(Version.DOIStatus.REQUESTED);
        //        }
        //
        //        Tool result = toolDAO.findById(containerId);
        //        checkEntry(result);
        //        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        //        return result.getWorkflowVersions();
    }


    /**
     * Finds the tool by Id, and then checks that it exists and that the user has access to it
     * @param toolId Id of tool of interest
     * @param user User to authenticate
     * @return Tool
     */
    private Tool findToolByIdAndCheckToolAndUser(Long toolId, User user) {
        Tool tool = toolDAO.findById(toolId);
        checkEntry(tool);
        checkUser(user, tool);
        return tool;
    }
}
