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

package io.dockstore.webservice.helpers;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This defines the set of operations that is needed to interact with a particular
 * source code repository.
 *
 * @author dyuen
 */
public abstract class SourceCodeRepoInterface {
    public static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoInterface.class);
    public static final int BYTES_IN_KB = 1024;
    String gitUsername;

    /**
     * Tries to get the README contents
     * First gets all the file names, then see if any of them matches the README regex
     * @param repositoryId
     * @param branch
     * @return
     */
    public String getREADMEContent(String repositoryId, String branch) {
        List<String> strings = this.listFiles(repositoryId, "/", branch);
        if (strings == null) {
            return null;
        }
        Optional<String> first = strings.stream().filter(SourceCodeRepoInterface::matchesREADME).findFirst();
        return first.map(s -> this.readFile(repositoryId, s, branch)).orElse(null);
    }

    public static boolean matchesREADME(String filename) {
        return filename.matches("(?i:/?readme([.]md)?)");
    }

    /**
     * If this interface is pointed at a specific repository, grab a
     * file from a specific branch/tag
     *
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param fileName  the name of the file (full path) to retrieve
     * @param reference the tag/branch to get the file from
     * @return content of the file
     */
    public abstract String readFile(String repositoryId, String fileName, @NotNull String reference);

    /**
     * Read a file from the importer and add it into files
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param tag the version of source control we want to read from
     * @param files the files collection we want to add to
     * @param fileType the type of file
     */
    void readFile(String repositoryId, Version tag, Collection<SourceFile> files, DescriptorLanguage.FileType fileType, String path) {
        Optional<SourceFile> sourceFile = this.readFile(repositoryId, tag, fileType, path);
        sourceFile.ifPresent(files::add);
    }

    /**
     * Read a file from the importer and add it into files
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param tag the version of source control we want to read from
     * @param fileType the type of file
     */
    public Optional<SourceFile> readFile(String repositoryId, Version tag, DescriptorLanguage.FileType fileType, String path) {
        String fileResponse = this.readGitRepositoryFile(repositoryId, fileType, tag, path);
        if (fileResponse != null) {
            SourceFile dockstoreFile = new SourceFile();
            dockstoreFile.setType(fileType);
            // a file of 1MB size is probably up to no good
            if (fileResponse.getBytes(StandardCharsets.UTF_8).length >= BYTES_IN_KB * BYTES_IN_KB) {
                fileResponse = "Dockstore does not store files over 1MB in size";
            }
            // some binary files that I tried has this character which cannot be stored
            // in postgres anyway https://www.postgresql.org/message-id/1171970019.3101.328.camel%40coppola.muc.ecircle.de
            if (Bytes.indexOf(fileResponse.getBytes(StandardCharsets.UTF_8), Byte.decode("0x00")) != -1) {
                fileResponse = "Dockstore does not store binary files";
            }
            dockstoreFile.setContent(fileResponse);
            dockstoreFile.setPath(path);
            dockstoreFile.setAbsolutePath(path);
            return Optional.of(dockstoreFile);
        }
        return Optional.empty();
    }

    /**
     * For Nextflow workflows, they seem to auto-import the contents of the lib and bin directories
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param pathToDirectory  full path to the directory to list
     * @param reference the tag/branch to get the file from
     * @return a list of files in the directory
     */
    public abstract List<String> listFiles(String repositoryId, String pathToDirectory, String reference);



    /**
     * Get a map of git url to an id that can uniquely identify a repository
     *
     * @return giturl -> repositoryid
     */
    public abstract Map<String, String> getWorkflowGitUrl2RepositoryId();

    /**
     * Checks to see if a particular source code repository is properly setup for issues like token scope
     */
    public abstract boolean checkSourceCodeValidity();


    /**
     * Set up workflow with basic attributes from git repository
     *
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @return workflow with some attributes set
     */
    public abstract Workflow initializeWorkflow(String repositoryId, Workflow workflow);

    /**
     * Set up service with basic attributes from git repository
     *
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @return service with some attributes set
     */
    public Service initializeService(String repositoryId) {
        Service service = (Service)initializeWorkflow(repositoryId, new Service());
        service.setDescriptorType(DescriptorLanguage.SERVICE);
        service.setMode(WorkflowMode.SERVICE);
        service.setDefaultWorkflowPath(".dockstore.yml");
        return service;
    }

    /**
     * Finds all of the workflow versions for a given workflow and store them and their corresponding source files
     *
     * @param repositoryId
     * @param workflow
     * @param existingWorkflow
     * @param existingDefaults
     * @return workflow with associated workflow versions
     */
    public abstract Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults);

    /**
     * Creates a basic workflow object with default values
     * @param repository repository organization and name (ex. dockstore/dockstore-ui2)
     * @return basic workflow object
     */
    public Workflow createStubBioworkflow(String repository) {
        Workflow workflow = initializeWorkflow(repository, new BioWorkflow());
        workflow.setDescriptorType(DescriptorLanguage.CWL);
        return workflow;
    }

    /**
     * Creates or updates a workflow based on the situation. Will grab workflow versions and more metadata if workflow is FULL
     *
     * @param repositoryId
     * @param existingWorkflow
     * @return workflow
     */
    public Workflow getWorkflow(String repositoryId, Optional<Workflow> existingWorkflow) {
        // Initialize workflow
        Workflow workflow = initializeWorkflow(repositoryId, new BioWorkflow());

        // Nextflow and (future) dockstore.yml workflow can be detected and handled without stubs

        // Determine if workflow should be returned as a STUB or FULL
        if (existingWorkflow.isEmpty()) {
            // when there is no existing workflow at all, just return a stub workflow. Also set descriptor type to default cwl.
            workflow.setDescriptorType(DescriptorLanguage.CWL);
            return workflow;
        }
        if (existingWorkflow.get().getMode() == WorkflowMode.STUB) {
            // when there is an existing stub workflow, just return the new stub as well
            return workflow;
        }

        // If this point has been reached, then the workflow will be a FULL workflow (and not a STUB)
        if (Objects.equals(existingWorkflow.get().getDescriptorType(), DescriptorLanguage.SERVICE)) {
            workflow.setMode(WorkflowMode.SERVICE);
        } else {
            workflow.setMode(WorkflowMode.FULL);
        }

        // if it exists, extract paths from the previous workflow entry
        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
        // Copy over existing workflow versions
        existingWorkflow.get().getWorkflowVersions()
                .forEach(existingVersion -> existingDefaults.put(existingVersion.getReference(), existingVersion));

        // Copy workflow information from source (existingWorkflow) to target (workflow)
        existingWorkflow.get().copyWorkflow(workflow);

        // Create branches and associated source files
        //TODO: calls validation eventually, may simplify if we take into account metadata parsing below
        workflow = setupWorkflowVersions(repositoryId, workflow, existingWorkflow, existingDefaults);
        // setting last modified date can be done uniformly
        Optional<Date> max = workflow.getWorkflowVersions().stream().map(WorkflowVersion::getLastModified).max(Comparator.naturalOrder());
        // TODO: this conversion is lossy
        if (max.isPresent()) {
            long time = max.get().getTime();
            workflow.setLastModified(new Date(Math.max(time, 0L)));
        }

        // update each workflow with reference types
        Set<WorkflowVersion> versions = workflow.getWorkflowVersions();
        versions.forEach(version -> updateReferenceType(repositoryId, version));

        // Get metadata for workflow and update workflow with it
        //TODO to parse metadata in WDL, there is a hidden dependency on validation now (validation does checks for things like recursive imports)
        // this means that two paths need to pass data in the same way to avoid oddities like validation passing and metadata parsing crashing on an invalid parse tree
        updateEntryMetadata(workflow, workflow.getDescriptorType());
        return workflow;
    }

    /**
     * Update all versions with metadata from the contents of the descriptor file from a source code repo
     * If no description from the descriptor file, fall back to README
     *
     * @param entry entry to update
     * @param type the type of language to look for
     * @return the entry again
     */
    Entry updateEntryMetadata(final Entry entry, final DescriptorLanguage type) {
        // Determine which branch to use
        String repositoryId = getRepositoryId(entry);

        if (repositoryId == null) {
            LOG.info("Could not find repository information.");
            return entry;
        }

        // If no tags or workflow versions, have no metadata
        if (entry.getWorkflowVersions().isEmpty()) {
            return entry;
        }

        if (entry instanceof Tool) {
            Tool tool = (Tool)entry;
            tool.getWorkflowVersions().forEach(tag -> {
                String filePath;
                if (type == DescriptorLanguage.CWL) {
                    filePath = tag.getCwlPath();
                } else if (type == DescriptorLanguage.WDL) {
                    filePath = tag.getWdlPath();
                } else {
                    throw new UnsupportedOperationException("tool is not a CWL or WDL file");
                }
                updateVersionMetadata(filePath, tag, type, repositoryId);
            });
        }
        if (entry instanceof Workflow) {
            Workflow workflow = (Workflow)entry;
            workflow.getWorkflowVersions().forEach(workflowVersion -> {
                String filePath = workflowVersion.getWorkflowPath();
                updateVersionMetadata(filePath, workflowVersion, type, repositoryId);
            });
        }
        return entry;
    }

    /**
     * Sets the default version if there isn't already one present.
     * This is required because entry-level metadata depends on the default version
     *
     * @param entry
     * @param repositoryId
     */
    public void setDefaultBranchIfNotSet(Entry entry, String repositoryId) {
        if (entry.getDefaultVersion() == null) {
            String branch = getMainBranch(entry, repositoryId);
            if (branch == null) {
                String message = String.format("%s : Error getting the main branch.", repositoryId);
                LOG.info(message);
            } else {
                Set<Version> workflowVersions = entry.getWorkflowVersions();
                Optional<Version> firstWorkflowVersion = workflowVersions.stream()
                        .filter(workflowVersion -> {
                            String reference = workflowVersion.getReference();
                            return branch.equals(reference);
                        }).findFirst();
                firstWorkflowVersion.ifPresent(version -> entry.checkAndSetDefaultVersion(version.getName()));
            }
        }
    }

    private void updateVersionMetadata(String filePath, Version version, DescriptorLanguage type, String repositoryId) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        String branch = version.getName();
        if (Strings.isNullOrEmpty(filePath)) {
            String message = String.format("%s : No descriptor found for %s.", repositoryId, branch);
            LOG.info(message);
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            String message = String.format("%s : Error getting descriptor for %s with path %s", repositoryId, branch, filePath);
            LOG.info(message);
            if (version.getReference() != null) {
                String readmeContent = getREADMEContent(repositoryId, version.getReference());
                if (StringUtils.isNotBlank(readmeContent)) {
                    version.setDescriptionAndDescriptionSource(readmeContent, DescriptionSource.README);
                }
            }
            return;
        }
        String fileContent;
        Optional<SourceFile> first = sourceFiles.stream().filter(file -> file.getPath().equals(filePath)).findFirst();
        if (first.isPresent()) {
            fileContent = first.get().getContent();
            LanguageHandlerInterface anInterface = LanguageHandlerFactory.getInterface(type);
            anInterface.parseWorkflowContent(filePath, fileContent, sourceFiles, version);
            if ((version.getDescription() == null || version.getDescription().isEmpty()) && version.getReference() != null) {
                String readmeContent = getREADMEContent(repositoryId, version.getReference());
                if (StringUtils.isNotBlank(readmeContent)) {
                    version.setDescriptionAndDescriptionSource(readmeContent, DescriptionSource.README);
                }
            }
        }
    }

    /**
     * Get the repository Id of an entry to be used for API calls
     *
     * @param entry
     * @return repository id of an entry, now standardised to be organization/repo_name
     */
    public abstract String getRepositoryId(Entry entry);

    /**
     * Returns the branch of interest used to determine tool and workflow metadata
     *
     * @param entry
     * @param repositoryId
     * @return Branch of interest
     */
    public abstract String getMainBranch(Entry entry, String repositoryId);

    /**

    /**
     * Returns the branch name for the default version
     * @param entry
     * @return
     */
    String getBranchNameFromDefaultVersion(Entry entry) {
        String defaultVersion = entry.getDefaultVersion();
        if (entry instanceof Tool) {
            for (Tag tag : ((Tool)entry).getWorkflowVersions()) {
                if (Objects.equals(tag.getName(), defaultVersion)) {
                    return tag.getReference();
                }
            }
        } else if (entry instanceof Workflow) {
            for (WorkflowVersion workflowVersion : ((Workflow)entry).getWorkflowVersions()) {
                if (Objects.equals(workflowVersion.getName(), defaultVersion)) {
                    return workflowVersion.getReference();
                }
            }
        }
        return null;
    }

    /*
     * Initializes workflow version for given branch
     *
     * @param branch
     * @param existingWorkflow
     * @param existingDefaults
     * @return workflow version
     */
    WorkflowVersion initializeWorkflowVersion(String branch, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults) {
        WorkflowVersion version = new WorkflowVersion();
        version.setName(branch);
        version.setReference(branch);
        version.setValid(false);

        // Determine workflow version from previous
        String calculatedPath;

        // Set to false if new version
        if (existingDefaults.get(branch) == null) {
            version.setDirtyBit(false);
            calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
        } else {
            // existing version
            if (existingDefaults.get(branch).isDirtyBit()) {
                calculatedPath = existingDefaults.get(branch).getWorkflowPath();
            } else {
                calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
            }
            version.setDirtyBit(existingDefaults.get(branch).isDirtyBit());
        }

        version.setWorkflowPath(calculatedPath);

        return version;
    }

    /**
     * Resolves imports for a sourcefile, associates with version
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param sourceFile
     * @param workflow
     * @param identifiedType
     * @param version
     * @return workflow version
     */
    WorkflowVersion combineVersionAndSourcefile(String repositoryId, SourceFile sourceFile, Workflow workflow,
        DescriptorLanguage.FileType identifiedType, WorkflowVersion version, Map<String, WorkflowVersion> existingDefaults) {
        Set<SourceFile> sourceFileSet = new HashSet<>();

        if (sourceFile != null && sourceFile.getContent() != null) {
            final Map<String, SourceFile> stringSourceFileMap = this
                .resolveImports(repositoryId, sourceFile.getContent(), identifiedType, version, sourceFile.getPath());
            sourceFileSet.addAll(stringSourceFileMap.values());
        }

        // Look for test parameter files if existing workflow
        if (existingDefaults.get(version.getName()) != null) {
            WorkflowVersion existingVersion = existingDefaults.get(version.getName());
            DescriptorLanguage.FileType workflowDescriptorType = workflow.getTestParameterType();

            List<SourceFile> testParameterFiles = existingVersion.getSourceFiles().stream()
                .filter((SourceFile u) -> u.getType() == workflowDescriptorType).collect(Collectors.toList());
            testParameterFiles
                .forEach(file -> this.readFile(repositoryId, existingVersion, sourceFileSet, workflowDescriptorType, file.getPath()));
        }

        // If source file is found and valid then add it
        if (sourceFile != null && sourceFile.getContent() != null) {
            version.getSourceFiles().add(sourceFile);
        }

        // look for a mutated version and delete it first (can happen due to leading slash)
        if (sourceFile != null) {
            Set<SourceFile> collect = sourceFileSet.stream().filter(file -> file.getPath().equals(sourceFile.getPath()) || file.getPath()
                .equals(StringUtils.stripStart(sourceFile.getPath(), "/"))).collect(Collectors.toSet());
            sourceFileSet.removeAll(collect);
        }
        // add extra source files here (dependencies from "main" descriptor)
        if (sourceFileSet.size() > 0) {
            version.getSourceFiles().addAll(sourceFileSet);
        }

        return version;
    }

    /**
     * Look in a source code repo for a particular file
     * @param repositoryId identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param fileType
     * @param version
     * @param specificPath if specified, look for a specific file, otherwise return the "default" for a fileType
     * @return  a FileResponse instance
     */
    public String readGitRepositoryFile(String repositoryId, DescriptorLanguage.FileType fileType, Version version, String specificPath) {

        final String reference = version.getReference();

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";
        if (specificPath != null) {
            String workingDirectory = version.getWorkingDirectory();
            if (specificPath.startsWith("/")) {
                // if we're looking at an absolute path, ignore the working directory
                fileName = specificPath;
            } else if (!workingDirectory.isEmpty() && !"/".equals(workingDirectory)) {
                // if the working directory is different from the root, take it into account
                fileName = workingDirectory + "/" +  specificPath;
            } else {
                fileName = specificPath;
            }
        } else if (version instanceof Tag) {
            Tag tag = (Tag)version;
            // Add for new descriptor types
            if (fileType == DescriptorLanguage.FileType.DOCKERFILE) {
                fileName = tag.getDockerfilePath();
            } else if (fileType == DescriptorLanguage.FileType.DOCKSTORE_CWL) {
                if (Strings.isNullOrEmpty(tag.getCwlPath())) {
                    return null;
                }
                fileName = tag.getCwlPath();
            } else if (fileType == DescriptorLanguage.FileType.DOCKSTORE_WDL) {
                if (Strings.isNullOrEmpty(tag.getWdlPath())) {
                    return null;
                }
                fileName = tag.getWdlPath();
            }
        } else if (version instanceof WorkflowVersion) {
            WorkflowVersion workflowVersion = (WorkflowVersion)version;
            fileName = workflowVersion.getWorkflowPath();
        }

        if (!fileName.isEmpty()) {
            return this.readFile(repositoryId, fileName, reference);
        } else {
            return null;
        }
    }

    public Map<String, SourceFile> resolveImports(String repositoryId, String content, DescriptorLanguage.FileType fileType, Version version, String filepath) {
        LanguageHandlerInterface languageInterface = LanguageHandlerFactory.getInterface(fileType);
        return languageInterface.processImports(repositoryId, content, version, this, filepath);
    }

    /**
     * The following methods were duplicated code, but are not well designed for this interface
     */

    public abstract SourceFile getSourceFile(String path, String id, String branch, DescriptorLanguage.FileType type);

    void createTestParameterFiles(Workflow workflow, String id, String branchName, WorkflowVersion version,
        DescriptorLanguage.FileType identifiedType) {
        if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
            // Set Filetype
            DescriptorLanguage.FileType testJsonType = null;
            if (identifiedType.equals(DescriptorLanguage.FileType.DOCKSTORE_CWL)) {
                testJsonType = DescriptorLanguage.FileType.CWL_TEST_JSON;
            } else if (identifiedType.equals(DescriptorLanguage.FileType.DOCKSTORE_WDL)) {
                testJsonType = DescriptorLanguage.FileType.WDL_TEST_JSON;
            }

            // Check if test parameter file has already been added
            final DescriptorLanguage.FileType finalFileType = testJsonType;
            long duplicateCount = version.getSourceFiles().stream().filter((SourceFile v) -> v.getPath().equals(workflow.getDefaultTestParameterFilePath()) && v.getType() == finalFileType).count();
            if (duplicateCount == 0) {
                SourceFile testJsonSourceFile = getSourceFile(workflow.getDefaultTestParameterFilePath(), id, branchName, testJsonType);
                if (testJsonSourceFile != null) {
                    version.getSourceFiles().add(testJsonSourceFile);
                }
            }
        }
    }

    /**
     * Given a version of a tool or workflow, ensure that its reference type is up-to-date
     * @param repositoryId
     * @param version
     */
    public abstract void updateReferenceType(String repositoryId, Version version);

    /**
     * Given a version of a tool or workflow, return the corresponding current commit id
     * @param repositoryId
     * @param version
     */
    protected abstract String getCommitID(String repositoryId, Version version);

    /**
     * Returns a workflow version with validation information updated
     * @param version Version to validate
     * @param entry Entry containing version to validate
     * @param mainDescriptorPath Descriptor path to validate
     * @return Workflow version with validation information
     */
    public WorkflowVersion versionValidation(WorkflowVersion version, Workflow entry, String mainDescriptorPath) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        DescriptorLanguage.FileType identifiedType = entry.getFileType();
        Optional<SourceFile> mainDescriptor = sourceFiles.stream().filter((sourceFile -> Objects
                .equals(sourceFile.getPath(), mainDescriptorPath))).findFirst();

        // Validate descriptor set
        if (mainDescriptor.isPresent()) {
            VersionTypeValidation validDescriptorSet = LanguageHandlerFactory.getInterface(identifiedType).validateWorkflowSet(sourceFiles, mainDescriptorPath);
            Validation descriptorValidation = new Validation(identifiedType, validDescriptorSet);
            version.addOrUpdateValidation(descriptorValidation);
        } else {
            Map<String, String> validationMessage = new HashMap<>();
            validationMessage.put(mainDescriptorPath, "Missing the primary descriptor.");
            VersionTypeValidation noPrimaryDescriptor = new VersionTypeValidation(false, validationMessage);
            Validation noPrimaryDescriptorValidation = new Validation(identifiedType, noPrimaryDescriptor);
            version.addOrUpdateValidation(noPrimaryDescriptorValidation);
        }

        // Validate test parameter set
        VersionTypeValidation validTestParameterSet = LanguageHandlerFactory.getInterface(identifiedType)
            .validateTestParameterSet(sourceFiles);
        Validation testParameterValidation = new Validation(entry.getTestParameterType(), validTestParameterSet);
        version.addOrUpdateValidation(testParameterValidation);

        version.setValid(isValidVersion(version));

        return version;
    }

    /**
     * Checks if the given workflow version is valid based on existing validations
     * @param version Version to check validation
     * @return True if valid workflow version, false otherwise
     */
    private boolean isValidVersion(WorkflowVersion version) {
        return version.getValidations().stream().allMatch(Validation::isValid);
    }
}
