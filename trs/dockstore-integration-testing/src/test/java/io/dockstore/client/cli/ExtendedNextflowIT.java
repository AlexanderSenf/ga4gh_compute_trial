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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.swagger.client.ApiClient;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertNotSame;

@Category({ ConfidentialTest.class, WorkflowTest.class })
public class ExtendedNextflowIT extends BaseIT {

    // workflow with a bin directory
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser/ampa-nf";
    // bitbucket workflow
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW =
        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/ampa-nf";
    // workflow with binaries in bin directory
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW =
        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/kallisto-nf";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testNextflowSecondaryFiles() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        // Tests that nf-core nextflow.config files can be parsed
        List<SourceFile> sourceFileList = new ArrayList<>(
            refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("nfcore")).findFirst().get()
                .getSourceFiles());
        Assert.assertEquals(4, sourceFileList.size());
        Assert.assertTrue("files are not what we expected",
            sourceFileList.stream().anyMatch(file -> file.getPath().equals("bin/AMPA-BIGTABLE.pl")) && sourceFileList.stream()
                .anyMatch(file -> file.getPath().equals("bin/multi-AMPA-BIGTABLE.pl")));

        // check that metadata made it through properly
        Assert.assertEquals("test.user@test.com", refreshGithub.getAuthor());
        Assert.assertEquals("Fast automated prediction of protein antimicrobial regions", refreshGithub.getDescription());
    }

    @Test
    public void testBitbucketNextflowWorkflow() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        // get workflow stubs
        usersApi.refreshWorkflows(user.getId());

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathBitbucket.setWorkflowPath("/nextflow.config");
        workflowByPathBitbucket.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathBitbucket.getId(), workflowByPathBitbucket);
        workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, null, false);
        final Workflow bitbucketWorkflow = workflowApi.refresh(workflowByPathBitbucket.getId());
        Workflow byPathWorkflow = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, null, false);
        // There are 3 versions: master, v1.0, and v2.0
        // master and v2.0 has a nextflow.config file that has description and author, v1.0 does not
        // v1.0 will pull description from README instead but the others will use nextflow.config
        testWorkflowVersionMetadata(bitbucketWorkflow);
        testWorkflowVersionMetadata(byPathWorkflow);
        // Purposely mess up the metadata to test if it can be updated through refresh
        testingPostgres.runUpdateStatement("update version_metadata set email='bad_potato'");
        testingPostgres.runUpdateStatement("update version_metadata set author='bad_potato'");
        testingPostgres.runUpdateStatement("update version_metadata set description='bad_potato'");
        final Workflow refreshedBitbucketWorkflow = workflowApi.refresh(workflowByPathBitbucket.getId());
        byPathWorkflow = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, null, false);
        // This tests if it can fix outdated metadata
        testWorkflowVersionMetadata(refreshedBitbucketWorkflow);
        testWorkflowVersionMetadata(byPathWorkflow);
        List<SourceFile> sourceFileList = new ArrayList<>(
            bitbucketWorkflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("v2.0")).findFirst().get()
                .getSourceFiles());
        Assert.assertEquals(4, sourceFileList.size());
    }

    /**
     * This tests the DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW metadata is correct after a refresh
     * @param workflow  The DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW workflow
     */
    private void testWorkflowVersionMetadata(Workflow workflow) {
        final String partialReadmeDescription = "AMPA-NF is a pipeline for assessing the antimicrobial domains of proteins,";
        final String descriptorDescription = "Fast automated prediction of protein antimicrobial regions";
        final String versionWithReadmeDescription = "v1.0";
        Assert.assertEquals(descriptorDescription, workflow.getDescription());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.getName().equals(versionWithReadmeDescription)));
        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            if (workflowVersion.getName().equals(versionWithReadmeDescription)) {
                Assert.assertTrue(workflowVersion.getDescription().contains(partialReadmeDescription));
                Assert.assertNull(workflowVersion.getAuthor());
                Assert.assertNull(workflowVersion.getEmail());
            } else {
                Assert.assertNotNull(descriptorDescription, workflowVersion.getDescription());
                Assert.assertEquals("test.user@test.com", workflowVersion.getAuthor());
                Assert.assertNull(workflowVersion.getEmail());
            }
        });
    }

    @Test
    public void testGitlabNextflowWorkflow() {
        // TODO: need to look into the SlowTest situation but we also need to reactivate the tests against API V4 for 1.5.0
    }

    @Test
    public void testBitbucketBinaryWorkflow() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        // get workflow stubs
        usersApi.refreshWorkflows(user.getId());

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW, null, false);
        final Workflow bitbucketWorkflow = workflowApi.refresh(workflowByPathGithub.getId());
        Assert.assertTrue("Should have gotten the description from README", bitbucketWorkflow.getDescription().contains("A Nextflow implementation of Kallisto & Sleuth RNA-Seq Tools"));
        List<SourceFile> sourceFileList = new ArrayList<>(
            bitbucketWorkflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("v1.0")).findFirst().get()
                .getSourceFiles());
        Assert.assertEquals(6, sourceFileList.size());
        // two of the files should essentially be blanked
        Assert.assertEquals("two files have our one-line warning", 2, sourceFileList.stream()
            .filter(file -> file.getContent().split("\n").length == 1 && file.getContent().contains("Dockstore does not")).count());
    }

}
