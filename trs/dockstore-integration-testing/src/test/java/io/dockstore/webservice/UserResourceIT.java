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

package io.dockstore.webservice;

import java.util.List;
import java.util.Objects;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.resources.WorkflowResource;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.BioWorkflow;
import io.swagger.client.model.Collection;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.EntryUpdateTime;
import io.swagger.client.model.Organization;
import io.swagger.client.model.OrganizationUpdateTime;
import io.swagger.client.model.Repository;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests operations from the UserResource
 *
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class UserResourceIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test(expected = ApiException.class)
    public void testChangingNameFail() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("1direction"); // do not lengthen test, failure expected
    }

    @Test(expected = ApiException.class)
    public void testChangingNameFail2() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("foo@gmail.com"); // do not lengthen test, failure expected
    }

    @Test
    public void testChangingNameSuccess() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("foo");
        assertEquals("foo", userApi.getUser().getUsername());

        // Add hosted workflow, should use new username
        HostedApi userHostedApi = new HostedApi(client);
        Workflow hostedWorkflow = userHostedApi.createHostedWorkflow("hosted1", null, "cwl", null, null);
        assertEquals("Hosted workflow should used foo as workflow org, has " + hostedWorkflow.getOrganization(), "foo",
            hostedWorkflow.getOrganization());
    }

    @Test
    public void testUserTermination() throws ApiException {
        ApiClient adminWebClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userWebClient = getWebClient(USER_2_USERNAME, testingPostgres);

        UsersApi userUserWebClient = new UsersApi(userWebClient);
        final User user = userUserWebClient.getUser();
        assertFalse(user.getUsername().isEmpty());

        UsersApi adminAdminWebClient = new UsersApi(adminWebClient);
        final Boolean aBoolean = adminAdminWebClient.terminateUser(user.getId());

        assertTrue(aBoolean);

        try {
            userUserWebClient.getUser();
            fail("should be unreachable, user must not have been banned properly");
        } catch (ApiException e) {
            assertEquals(e.getCode(), HttpStatus.SC_UNAUTHORIZED);
        }
    }

    @Test
    public void longAvatarUrlTest() {
        String generatedString = RandomStringUtils.randomAlphanumeric(9001);
        testingPostgres.runUpdateStatement(String.format("update enduser set avatarurl='%s'", generatedString));
    }

    /**
     * Creates an organization using the given names
     * @param client
     * @param name
     * @param displayName
     * @return new organization
     */
    private Organization createOrganization(ApiClient client, String name, String displayName) {
        OrganizationsApi organizationsApi = new OrganizationsApi(client);

        Organization organization = new Organization();
        organization.setName(name);
        organization.setDisplayName(displayName);
        organization.setLocation("testlocation");
        organization.setLink("https://www.google.com");
        organization.setEmail("test@email.com");
        organization.setDescription("hello");
        organization.setTopic("This is a short topic");
        organization.setAvatarUrl("https://www.lifehardin.net/images/employees/default-logo.png");

        return organizationsApi.createOrganization(organization);
    }

    /**
     * Should not be able to update username after creating an organisation
     *
     * @throws ApiException
     */
    @Test
    public void testChangeUsernameAfterOrgCreation() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);

        // Can change username when not a member of any organisations
        assertTrue(userApi.getExtendedUserData().isCanChangeUsername());

        // Create organization
        createOrganization(client, "testname", "test name");

        // Cannot change username now that user is part of an organisation
        assertFalse(userApi.getExtendedUserData().isCanChangeUsername());
    }

    @Test
    public void testSelfDestruct() throws ApiException {
        ApiClient client = getAnonymousWebClient();
        UsersApi userApi = new UsersApi(client);
        // anon should not exist
        boolean shouldFail = false;
        try {
            userApi.getUser();
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        // use a real account
        client = getWebClient(USER_2_USERNAME, testingPostgres);
        userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        final ApiClient adminWebClient = getWebClient(ADMIN_USERNAME, testingPostgres);

        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(adminWebClient);

        User user = userApi.getUser();
        assertNotNull(user);
        // try to delete with published workflows
        userApi.refreshWorkflows(user.getId());
        final Workflow workflowByPath = workflowsApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        // refresh targeted
        workflowsApi.refresh(workflowByPath.getId());

        // Verify that admin can access unpublished workflow, because admin is going to verify later
        // that the workflow is gone
        adminWorkflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);

        // publish one
        workflowsApi.publish(workflowByPath.getId(), SwaggerUtility.createPublishRequest(true));

        assertFalse(userApi.getExtendedUserData().isCanChangeUsername());

        boolean expectedFailToDelete = false;
        try {
            userApi.selfDestruct();
        } catch (ApiException e) {
            expectedFailToDelete = true;
        }
        assertTrue(expectedFailToDelete);
        // then unpublish them
        workflowsApi.publish(workflowByPath.getId(), SwaggerUtility.createPublishRequest(false));
        assertTrue(userApi.getExtendedUserData().isCanChangeUsername());
        assertTrue(userApi.selfDestruct());
        //TODO need to test that profiles are cascaded to and cleared

        // Verify that self-destruct also deleted the workflow
        boolean expectedAdminAccessToFail = false;
        try {
            adminWorkflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);

        } catch (ApiException e) {
            expectedAdminAccessToFail = true;
        }
        assertTrue(expectedAdminAccessToFail);

        // I shouldn't be able to get info on myself after deletion
        boolean expectedFailToGetInfo = false;
        try {
            userApi.getUser();
        } catch (ApiException e) {
            expectedFailToGetInfo = true;
        }
        assertTrue(expectedFailToGetInfo);

        expectedFailToGetInfo = false;
        try {
            userApi.getExtendedUserData();
        } catch (ApiException e) {
            expectedFailToGetInfo = true;
        }
        assertTrue(expectedFailToGetInfo);
    }

    /**
     * Tests that the endpoints for the wizard registration work
     * @throws ApiException
     */
    @Test
    public void testWizardEndpoints() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        List<String> registries = userApi.getUserRegistries();
        assertTrue(registries.size() > 0);
        assertTrue(registries.contains(SourceControl.GITHUB.toString()));
        assertTrue(registries.contains(SourceControl.GITLAB.toString()));
        assertTrue(registries.contains(SourceControl.BITBUCKET.toString()));

        // Test GitHub
        List<String> orgs = userApi.getUserOrganizations(SourceControl.GITHUB.name());
        assertTrue(orgs.size() > 0);
        assertTrue(orgs.contains("dockstoretesting"));
        assertTrue(orgs.contains("DockstoreTestUser"));
        assertTrue(orgs.contains("DockstoreTestUser2"));

        List<Repository> repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-tool") && !repo.isPresent()));
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && !repo.isPresent()));

        // Register a workflow
        BioWorkflow ghWorkflow = workflowsApi.addWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        assertNotNull("GitHub workflow should be added", ghWorkflow);
        assertEquals(ghWorkflow.getFullWorkflowPath(), "github.com/dockstoretesting/basic-workflow");

        // dockstoretesting/basic-workflow should be present now
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-tool") && !repo.isPresent()));
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && repo.isPresent()));

        // Try deleting a workflow
        workflowsApi.deleteWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        Workflow deletedWorkflow = null;
        try {
            deletedWorkflow = workflowsApi.getWorkflow(ghWorkflow.getId(), null);
            assertFalse("Should not reach here as entry should not exist", false);
        } catch (ApiException ex) {
            assertNull("Workflow should be null", deletedWorkflow);
        }

        // Try making a repo undeletable
        ghWorkflow = workflowsApi.addWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        workflowsApi.refresh(ghWorkflow.getId());
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && repo.isPresent() && !repo.isCanDelete()));

        // Test Gitlab
        orgs = userApi.getUserOrganizations(SourceControl.GITLAB.name());
        assertTrue(orgs.size() > 0);
        assertTrue(orgs.contains("dockstore.test.user2"));

        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITLAB.name(), "dockstore.test.user2");
        assertTrue(repositories.size() > 0);
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-md5sum-unified") && !repo.isPresent()));
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-example") && !repo.isPresent()));

        // Register a workflow
        BioWorkflow glWorkflow = workflowsApi.addWorkflow(SourceControl.GITLAB.name(), "dockstore.test.user2", "dockstore-workflow-example");
        assertEquals(glWorkflow.getFullWorkflowPath(), "gitlab.com/dockstore.test.user2/dockstore-workflow-example");

        // dockstore.test.user2/dockstore-workflow-example should be present now
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITLAB.name(), "dockstore.test.user2");
        assertTrue(repositories.size() > 0);
        assertTrue(
            repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-example") && repo.isPresent()));

        // Try registering the workflow again (duplicate) should fail
        try {
            workflowsApi.addWorkflow(SourceControl.GITLAB.name(), "dockstore.test.user2", "dockstore-workflow-example");
            assertFalse("Should not reach this, should fail", false);
        } catch (ApiException ex) {
            assertTrue("Should have error message that workflow already exists.", ex.getMessage().contains("already exists"));
        }

        // Try registering a hosted workflow
        try {
            BioWorkflow dsWorkflow = workflowsApi.addWorkflow(SourceControl.DOCKSTORE.name(), "foo", "bar");
            assertFalse("Should not reach this, should fail", false);
        } catch (ApiException ex) {
            assertTrue("Should have error message that hosted workflows cannot be added this way.", ex.getMessage().contains(WorkflowResource.SC_REGISTRY_ACCESS_MESSAGE));
        }

    }

    /**
     * Tests the endpoints used for logged in homepage to retrieve recent entries and organizations
     */
    @Test
    public void testLoggedInHomepageEndpoints() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        User user = userApi.getUser();

        Workflow addedWorkflow = workflowsApi.manualRegister("gitlab", "dockstore.test.user2/dockstore-workflow-md5sum-unified", "/Dockstore.cwl", "", "cwl", "/test.json");

        List<DockstoreTool> tools = userApi.refresh(user.getId());
        List<EntryUpdateTime> entries = userApi.getUserEntries(10, null);
        assertFalse(entries.isEmpty());
        assertEquals("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified", entries.get(entries.size() - 1).getPath());
        assertEquals("dockstore-workflow-md5sum-unified", entries.get(entries.size() - 1).getPrettyPath());

        // Update an entry
        Workflow workflow = workflowsApi.getWorkflowByPath("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified", null, false);
        Workflow refreshedWorkflow = workflowsApi.refresh(workflow.getId());

        // Develop branch doesn't have a descriptor with the default Dockstore.cwl, it should pull from README instead
        Assert.assertTrue(refreshedWorkflow.getDescription().contains("To demonstrate the checker workflow proposal"));

        // Entry should now be at the top
        entries = userApi.getUserEntries(10, null);
        assertEquals("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified", entries.get(0).getPath());
        assertEquals("dockstore-workflow-md5sum-unified", entries.get(0).getPrettyPath());

        // Create organizations
        Organization foobarOrg = createOrganization(client, "Foobar", "Foo Bar");
        Organization foobarOrgTwo = createOrganization(client, "Foobar2", "Foo Bar the second");
        Organization tacoOrg = createOrganization(client, "taco", "taco place");

        // taco should be most recent
        List<OrganizationUpdateTime> organizations = userApi.getUserDockstoreOrganizations(10, null);
        assertFalse(organizations.isEmpty());
        assertEquals("taco", organizations.get(0).getName());

        // Add collection to foobar2
        OrganizationsApi organizationsApi = new OrganizationsApi(client);
        organizationsApi.createCollection(foobarOrgTwo.getId(), createCollection());

        // foobar2 should be the most recent
        organizations = userApi.getUserDockstoreOrganizations(10, null);
        assertFalse(organizations.isEmpty());
        assertEquals("Foobar2", organizations.get(0).getName());

        // Search for taco organization
        organizations = userApi.getUserDockstoreOrganizations(10, "tac");
        assertFalse(organizations.isEmpty());
        assertEquals("taco", organizations.get(0).getName());
    }

    /**
     * Creates a collection (does not save to database)
     * @return new collection
     */
    private Collection createCollection() {
        Collection collection = new Collection();
        collection.setDisplayName("cool name");
        collection.setName("coolname");
        collection.setTopic("this is the topic");
        collection.setDescription("this is the description");

        return collection;
    }

}
