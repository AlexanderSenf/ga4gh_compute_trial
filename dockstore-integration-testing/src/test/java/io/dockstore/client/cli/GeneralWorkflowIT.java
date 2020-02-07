/*
 *    Copyright 2018 OICR
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

package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.webservice.core.Version.CANNOT_FREEZE_VERSIONS_WITH_NO_FILES;
import static io.dockstore.webservice.helpers.EntryVersionHelper.CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY;
import static io.dockstore.webservice.resources.WorkflowResource.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.resources.WorkflowResource.NO_ZENDO_USER_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test suite tests various workflow related processes.
 * Created by aduncan on 05/04/16.
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class GeneralWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Manually register and publish a workflow with the given path and name
     *
     * @param workflowsApi
     * @param workflowPath
     * @param workflowName
     * @param descriptorType
     * @param sourceControl
     * @param descriptorPath
     * @param toPublish
     * @return Published workflow
     */
    private Workflow manualRegisterAndPublish(WorkflowsApi workflowsApi, String workflowPath, String workflowName, String descriptorType,
        SourceControl sourceControl, String descriptorPath, boolean toPublish) {
        // Manually register
        Workflow workflow = workflowsApi
            .manualRegister(sourceControl.getFriendlyName().toLowerCase(), workflowPath, descriptorPath, workflowName, descriptorType,
                "/test.json");
        assertEquals(Workflow.ModeEnum.STUB, workflow.getMode());

        // Refresh
        workflow = workflowsApi.refresh(workflow.getId());
        assertEquals(Workflow.ModeEnum.FULL, workflow.getMode());

        // Publish
        if (toPublish) {
            workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
            assertTrue(workflow.isIsPublished());
        }
        return workflow;
    }

    /**
     * This test checks that refresh all workflows (with a mix of stub and full) and refresh individual.  It then tries to publish them
     */
    @Test
    public void testRefreshAndPublish() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all
        usersApi.refreshWorkflows((long)1);

        // refresh individual that is valid
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/DockstoreTestUser2/hello-dockstore-workflow", "", false);
        workflow = workflowsApi.refresh(workflow.getId());

        // check that valid is valid and full
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count, 0, count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("there should be 4 versions, there are " + count4, 4, count4);

        // attempt to publish it
        workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));

        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 1 published entry, there are " + count5, 1, count5);

        // unpublish
        workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(false));

        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count6, 0, count6);
    }

    /**
     * This test manually publishing a workflow
     */
    @Test
    public void testManualPublish() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl", SourceControl.GITHUB,
            "/Dockstore.wdl", true);
    }

    /**
     * This tests attempting to manually publish a workflow with no valid versions
     */
    @Test
    public void testManualPublishInvalid() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        // try and publish
        try {
            manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/dockstore_empty_repo", "testname", "wdl", SourceControl.GITHUB,
                "/Dockstore.wdl", true);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    public void testLabelEditing() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Set up workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        // add labels
        workflow = workflowsApi.updateLabels(workflow.getId(), "test1,test2", "");
        assertEquals(2, workflow.getLabels().size());

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count, 2, count);

        // remove labels
        workflow = workflowsApi.updateLabels(workflow.getId(), "test2,test3", "");
        assertEquals(2, workflow.getLabels().size());

        final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count2, 2, count2);
    }

    /**
     * This tests manually publishing a duplicate workflow (should fail)
     */
    @Test
    public void testManualPublishDuplicate() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "wdl", SourceControl.GITHUB,
            "/Dockstore.wdl", true);

        // Manually register the same workflow
        try {
            manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "wdl", SourceControl.GITHUB,
                "/Dockstore.wdl", true);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("A workflow with the same path and name already exists."));
        }

    }

    /**
     * This tests that a user can update a workflow version
     */
    @Test
    public void testUpdateWorkflowVersion() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        // Update workflow
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setHidden(true);
        updateWorkflowVersion.setWorkflowPath("/Dockstore2.wdl");
        workflowVersions.add(updateWorkflowVersion);
        workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where wv.name = 'master' and vm.hidden = 't' and wv.workflowpath = '/Dockstore2.wdl' and wv.id = vm.id",
            long.class);
        assertEquals("there should be 1 matching workflow version, there is " + count, 1, count);
    }

    /**
     * This tests that a restub will work on an unpublished, full workflow
     */
    @Test
    public void testRestub() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Restub workflow
        workflowsApi.restub(workflow.getId());

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("there should be 0 workflow versions, there are " + count, 0, count);
    }

    /**
     * This tests that a restub will not work on an published, full workflow
     */
    @Test
    public void testRestubError() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Publish workflow
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));

        // Restub
        try {
            workflow = workflowsApi.restub(workflow.getId());
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("A workflow must be unpublished to restub"));
        }
    }

    /**
     * Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
     */
    @Test
    public void testDescriptorTypes() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", long.class);
        assertEquals("there should be 1 wdl workflow, there are " + count, 1, count);

        workflow = workflowsApi.refresh(workflow.getId());
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.CWL);
        try {
            workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("You cannot change the descriptor type of a FULL workflow"));
        }
    }

    /**
     * Tests updating a workflow tag with invalid workflow descriptor path
     */
    @Test
    public void testWorkflowVersionIncorrectPath() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Update workflow version to new path
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/newdescriptor.cwl");
        workflowVersions.add(updateWorkflowVersion);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflow = workflowsApi.refresh(workflow.getId());

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'",
                long.class);
        assertEquals("the workflow version should now have a new descriptor path", 1, count);

        // Update workflow version to incorrect path (wrong extension)
        workflowVersion = workflowVersions.stream().filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/Dockstore.wdl");
        workflowVersions.clear();
        workflowVersions.add(updateWorkflowVersion);
        try {
            workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Please ensure that the workflow path uses the file extension cwl"));
        }

    }

    /**
     * Tests that refreshing with valid imports will work (for WDL)
     */
    @Test
    public void testRefreshWithImportsWDL() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all
        usersApi.refreshWorkflows((long)1);

        // refresh individual that is valid
        Workflow workflow = workflowsApi
            .getWorkflowByPath(SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "", false);

        // Update workflow path
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setWorkflowPath("/Dockstore.wdl");

        // Update workflow descriptor type
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);

        // Refresh workflow
        workflow = workflowsApi.refresh(workflow.getId());

        // Publish workflow
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
    }

    @Test
    public void testUpdateWorkflowPath() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/Dockstore.cwl", "test-update-workflow", "cwl",
                "/test.json");

        // Publish github workflow
        Workflow workflow = workflowsApi.refresh(githubWorkflow.getId());

        //update the default workflow path to be hello.cwl , the workflow path in workflow versions should also be changes
        workflow.setWorkflowPath("/hello.cwl");
        workflowsApi.updateWorkflowPath(githubWorkflow.getId(), workflow);
        workflowsApi.refresh(githubWorkflow.getId());

        //check if the workflow versions have the same workflow path or not in the database
        final String masterpath = testingPostgres
            .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        final String testpath = testingPostgres
            .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        assertEquals("master workflow path should be the same as default workflow path, it is " + masterpath, "/Dockstore.cwl", masterpath);
        assertEquals("test workflow path should be the same as default workflow path, it is " + testpath, "/Dockstore.cwl", testpath);
    }

    @Test
    public void testWorkflowFreezingWithNoFiles() {
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/wrongpath.wdl", "test-update-workflow", "wdl",
                "/wrong-test.json");

        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();
        master.setFrozen(true);
        try {
            List<WorkflowVersion> workflowVersions = workflowsApi
                .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        } catch (ApiException e) {
            // should exception
            assertTrue("missing error message", e.getMessage().contains(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES));
            return;
        }
        fail("should be unreachable");
    }

    @Test
    public void testWorkflowFreezing() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        // Publish github workflow
        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // try various operations that should be disallowed

        // cannot modify version properties, like unfreezing for now
        workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId());
        master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        master.setFrozen(false);
        List<WorkflowVersion> workflowVersions = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // but should be able to change doi stuff
        master.setFrozen(true);
        master.setDoiStatus(WorkflowVersion.DoiStatusEnum.REQUESTED);
        master.setDoiURL("foo");
        workflowVersions = workflowsApi.updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertEquals("foo", master.getDoiURL());
        assertEquals(WorkflowVersion.DoiStatusEnum.REQUESTED, master.getDoiStatus());

        // refresh should skip over the frozen version
        final Workflow refresh = workflowsApi.refresh(githubWorkflow.getId());
        master = refresh.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();

        // cannot modify sourcefiles for a frozen version
        assertFalse(master.getSourceFiles().isEmpty());
        master.getSourceFiles().forEach(s -> {
            assertTrue(s.isFrozen());
            testingPostgres.runUpdateStatement("update sourcefile set content = 'foo' where id = " + s.getId());
            final String content = testingPostgres
                .runSelectStatement("select content from sourcefile where id = " + s.getId(), String.class);
            assertNotEquals("foo", content);
        });

        // try deleting a row join table
        master.getSourceFiles().forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("delete from version_sourcefile vs where vs.sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        // try updating a row in the join table
        master.getSourceFiles().forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("update version_sourcefile set sourcefileid=123456 where sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        final Long versionId = master.getId();
        // try creating a row in the join table
        master.getSourceFiles().forEach(s -> {
            try {
                testingPostgres.runUpdateStatement(
                    "insert into version_sourcefile (versionid, sourcefileid) values (" + versionId + ", " + 1234567890 + ")");
                fail("Insert should have failed to do row-level security");
            } catch (Exception ex) {
                Assert.assertTrue(ex.getMessage().contains("new row violates row-level"));
            }
        });

        // cannot add or delete test files for frozen versions
        try {
            workflowsApi.deleteTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "master");
            fail("could delete test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
        try {
            workflowsApi.addTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "", "master");
            fail("could add test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
    }

    /**
     * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    public void testUpdateWorkflowDefaultVersion() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", true);

        // Update workflow with version with no metadata
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "testWDL");

        // Assert default version is updated and no author or email is found
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL'", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count, 1, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL' and author is null and email is null",
                long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count2);

        // Update workflow with version with metadata
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "testBoth");
        workflow = workflowsApi.refresh(workflow.getId());

        // Assert default version is updated and author and email are set
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where defaultversion = 'testBoth'", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count3, 1, count3);

        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where defaultversion = 'testBoth' and author = 'testAuthor' and email = 'testEmail'",
            long.class);
        assertEquals("The given workflow should have contact info", 1, count4);

        // Unpublish
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(false));

        // Alter workflow so that it has no valid tags
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET valid='f'");

        // Now you shouldn't be able to publish the workflow
        try {
            workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    public void testRefreshRelatedConcepts() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // check that workflow is valid and full
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);

        // Change path for each version so that it is invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.cwl', dirtybit=true");
        workflow = workflowsApi.refresh(workflow.getId());

        // Workflow has no valid versions so you cannot publish

        // check that invalid
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertEquals("there should be 4 invalid versions, there are " + count4, 4, count4);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Update workflow to WDL
        workflow.setWorkflowPath("/Dockstore.wdl");
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.refresh(workflow.getId());

        // Can now publish workflow
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));

        // unpublish
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(false));

        // Set paths to invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.wdl', dirtybit=true");
        workflow = workflowsApi.refresh(workflow.getId());

        // Check that versions are invalid
        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertEquals("there should be 4 invalid versions, there are " + count5, 4, count5);

        // should now not be able to publish
        try {
            workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * This tests the dirty bit attribute for workflow versions with github
     */
    @Test
    public void testGithubDirtyBit() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Update workflow version to new path
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/Dockstoredirty.cwl");
        workflowVersions.add(updateWorkflowVersion);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflow = workflowsApi.refresh(workflow.getId());

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        workflow.setWorkflowPath("/Dockstoreclean.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId());

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals("there should be 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 3, count2);

    }

    /**
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    public void testBitbucketDirtyBit() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore_testuser2/dockstore-workflow", "testname", "cwl",
            SourceControl.BITBUCKET, "/Dockstore.cwl", false);

        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Update workflow version to new path
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/Dockstoredirty.cwl");
        workflowVersions.add(updateWorkflowVersion);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflow = workflowsApi.refresh(workflow.getId());

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        workflow.setWorkflowPath("/Dockstoreclean.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId());

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 4, count2);

    }

    /**
     * This is a high level test to ensure that gitlab basics are working for gitlab as a workflow repo
     */
    @Test
    public void testGitlab() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-example", "testname", "cwl",
            SourceControl.GITLAB, "/Dockstore.cwl", false);

        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);

        // Check a few things
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("there should be 1 workflow, there are " + count, 1, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid version, there are " + count2, 2, count2);

        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("there should be 1 workflow, there are " + count3, 1, count3);

        // publish
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals("there should be 1 published workflow, there are " + count4, 1, count4);

        // unpublish
        workflow = workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(false));
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals("there should be 0 published workflows, there are " + count5, 0, count5);

        // change default branch
        final long count6 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and author is null and email is null and description is null",
            long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count6);

        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "test");
        workflow = workflowsApi.refresh(workflow.getId());

        final long count7 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where defaultversion = 'test' and author is null and email is null and description is null",
            long.class);
        assertEquals("The given workflow should now have contact info and description", 0, count7);

        // restub
        workflow = workflowsApi.restub(workflow.getId());
        final long count8 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='STUB' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("The workflow should now be a stub", 1, count8);

        // Convert to WDL workflow
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);

        // Should now be a WDL workflow
        final long count9 = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype='wdl'", long.class);
        assertEquals("there should be no 1 wdl workflow" + count9, 1, count9);

    }

    /**
     * This tests manually publishing a Bitbucket workflow
     */
    @Test
    public void testManualPublishBitbucket() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore_testuser2/dockstore-workflow", "testname", "wdl",
            SourceControl.BITBUCKET, "/Dockstore.wdl", true);

        // Check for two valid versions (wdl_import and surprisingly, cwl_import)
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where valid='t' and (name='wdl_import' OR name='cwl_import')",
                long.class);
        assertEquals("There should be a valid 'wdl_import' version and a valid 'cwl_import' version", 2, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Optional<WorkflowVersion> version = workflow.getWorkflowVersions().stream()
            .filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "wdl_import")).findFirst();
        if (version.isEmpty()) {
            fail("wdl_import version should exist");
        }
        assertTrue(
            version.get().getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore.wdl"))
                .findFirst().isPresent());
    }

    /**
     * This tests manually publishing a gitlab workflow
     */
    @Test
    public void testManualPublishGitlab() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-example", "testname", "wdl",
            SourceControl.GITLAB, "/Dockstore.wdl", true);

        // Check for one valid version
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 1 valid version, there are " + count, 1, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Optional<WorkflowVersion> version = workflow.getWorkflowVersions().stream()
            .filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")).findFirst();
        if (version.isEmpty()) {
            fail("master version should exist");
        }
        assertTrue(
            version.get().getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore.wdl"))
                .findFirst().isPresent());
    }

    /**
     * This tests getting branches and tags from gitlab repositories
     */
    @Test
    @Category(SlowTest.class)
    public void testGitLabTagAndBranchTracking() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-md5sum-unified", "testname",
            "wdl", SourceControl.GITLAB, "/checker.wdl", true);

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue("there should be at least 5 versions, there are " + count, count >= 5);
        final long branchCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'BRANCH'", long.class);
        assertTrue("there should be at least 2 branches, there are " + count, branchCount >= 2);
        final long tagCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'TAG'", long.class);
        assertTrue("there should be at least 3 tags, there are " + count, tagCount >= 3);
    }

    /**
     * This tests that WDL files are properly parsed for secondary WDL files
     */
    @Test
    public void testWDLWithImports() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/test_workflow_wdl", "testname", "wdl",
            SourceControl.GITHUB, "/hello.wdl", false);

        // Check for WDL files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where path='helper.wdl'", long.class);
        assertEquals("there should be 1 secondary file named helper.wdl, there are " + count, 1, count);

    }

    /**
     * This tests basic concepts with workflow test parameter files
     */
    @Test
    public void testTestParameterFile() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        UsersApi usersApi = new UsersApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/parameter_test_workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // There should be no sourcefiles
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count, 0, count);

        // Update version master with test parameters
        List<String> toAdd = new ArrayList<>();
        toAdd.add("test.cwl.json");
        toAdd.add("test2.cwl.json");
        toAdd.add("fake.cwl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "master");
        List<String> toDelete = new ArrayList<>();
        toDelete.add("notreal.cwl.json");
        workflowsApi.deleteTestParameterFiles(workflow.getId(), toDelete, "master");
        workflow = workflowsApi.refresh(workflow.getId());

        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are test parameter files, there are " + count2, 2, count2);

        // Update version with test parameters
        toAdd.clear();
        toAdd.add("test.cwl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "master");
        toDelete.clear();
        toDelete.add("test2.cwl.json");
        workflowsApi.deleteTestParameterFiles(workflow.getId(), toDelete, "master");
        workflow = workflowsApi.refresh(workflow.getId());
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a test parameter file, there are " + count3, 1, count3);

        // Update other version with test parameters
        toAdd.clear();
        toAdd.add("test.wdl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "wdltest");
        workflow = workflowsApi.refresh(workflow.getId());
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are cwl test parameter files, there are " + count4, 2, count4);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Change to WDL
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setWorkflowPath("Dockstore.wdl");
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId());

        // Should be no sourcefiles
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count5, 0, count5);

        // Update version wdltest with test parameters
        toAdd.clear();
        toAdd.add("test.wdl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "wdltest");
        workflow = workflowsApi.refresh(workflow.getId());
        final long count6 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a wdl test parameter file, there are " + count6, 1, count6);
    }

    /**
     * This tests that you can refresh user data by refreshing a workflow
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    public void testRefreshingUserMetadata() {
        // Refresh all workflows
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        usersApi.refreshWorkflows((long)1);

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        // final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals("One user should have this info now, there are  " + count, 1, count);
    }

    @Test
    public void testGenerateDOIFrozenVersion() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        //register workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();

        //try issuing DOI for workflow version that is not frozen.
        try {
            workflowsApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute if version is mutable. DOI should only be generated for frozen versions of workflows.");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(FROZEN_VERSION_REQUIRED));
        }

        //freeze version 'master'
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        //TODO: For now just checking for next failure (no Zenodo token), but should replace with when DOI registration tests are written
        try {
            workflowsApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute without valid Zenodo token");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(NO_ZENDO_USER_TOKEN));

        }

    }
}
