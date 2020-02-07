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
package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.LanguageHandlerHelper;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.common.WdlBridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);
    public static final String ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT = "Error parsing workflow. You may have a recursive import.";
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+\"(\\S+)\"");

    public static void checkForRecursiveLocalImports(String content, Set<SourceFile> sourceFiles, Set<String> absolutePaths, String parent)
            throws ParseException {
        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');
        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                    String localRelativePath = match.replaceFirst("file://", "");
                    String absolutePath = LanguageHandlerHelper.convertRelativePathToAbsolutePath(parent, localRelativePath);
                    if (absolutePaths.contains(absolutePath)) {
                        throw new ParseException("Recursive local import detected: " + absolutePath, 0);
                    }
                    // Creating a new set to avoid false positive caused by multiple "branches" that have the same import
                    Set<String> newAbsolutePaths = new HashSet<>();
                    newAbsolutePaths.addAll(absolutePaths);
                    newAbsolutePaths.add(absolutePath);
                    Optional<SourceFile> sourcefile = sourceFiles.stream()
                            .filter(sourceFile -> sourceFile.getAbsolutePath().equals(absolutePath)).findFirst();
                    if (sourcefile.isPresent()) {
                        File file = new File(absolutePath);
                        String newParent = file.getParent();
                        checkForRecursiveLocalImports(sourcefile.get().getContent(), sourceFiles, newAbsolutePaths, newParent);
                    }
                }
            }
        }

    }

    @Override
    public Version parseWorkflowContent(String filepath, String content, Set<SourceFile> sourceFiles, Version version) {
        try {
            String parent = filepath.startsWith("/") ? new File(filepath).getParent() : "/";
            checkForRecursiveLocalImports(content, sourceFiles, new HashSet<>(), parent);
        } catch (ParseException e) {
            LOG.error("Recursive local imports found: " + version.getName(), e);
            Map<String, String> validationMessageObject = new HashMap<>();
            validationMessageObject.put(filepath, e.getMessage());
            version.addOrUpdateValidation(new Validation(DescriptorLanguage.FileType.DOCKSTORE_WDL, false, validationMessageObject));
            return version;
        }
        WdlBridge wdlBridge = new WdlBridge();
        final Map<String, String> secondaryFiles = sourceFiles.stream()
                .collect(Collectors.toMap(SourceFile::getAbsolutePath, SourceFile::getContent));
        wdlBridge.setSecondaryFiles((HashMap<String, String>)secondaryFiles);
        File tempMainDescriptor = null;
        try {
            tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(content);
            try {
                List<Map<String, String>> metadata = wdlBridge.getMetadata(tempMainDescriptor.getAbsolutePath(), filepath);
                Set<String> authors = new HashSet<>();
                Set<String> emails = new HashSet<>();
                final String[] mainDescription = { null };

                metadata.forEach(metaBlock -> {
                    String author = metaBlock.get("author");
                    String[] callAuthors = author != null ? author.split(",") : null;
                    if (callAuthors != null) {
                        for (String callAuthor : callAuthors) {
                            authors.add(callAuthor.trim());
                        }
                    }

                    String email = metaBlock.get("email");
                    String[] callEmails = email != null ? email.split(",") : null;
                    if (callEmails != null) {
                        for (String callEmail : callEmails) {
                            emails.add(callEmail.trim());
                        }
                    }

                    String description = metaBlock.get("description");
                    if (description != null && !description.isBlank()) {
                        mainDescription[0] = description;
                    }
                });

                if (!authors.isEmpty()) {
                    version.setAuthor(String.join(", ", authors));
                }
                if (!emails.isEmpty()) {
                    version.setEmail(String.join(", ", emails));
                }
                if (!Strings.isNullOrEmpty(mainDescription[0])) {
                    version.setDescriptionAndDescriptionSource(mainDescription[0], DescriptionSource.DESCRIPTOR);
                }
            } catch (wdl.draft3.parser.WdlParser.SyntaxError ex) {
                LOG.error("Unable to parse WDL file " + filepath, ex);
                Map<String, String> validationMessageObject = new HashMap<>();
                validationMessageObject.put(filepath, "WDL file is malformed or missing, cannot extract metadata");
                version.addOrUpdateValidation(new Validation(DescriptorLanguage.FileType.DOCKSTORE_WDL, false, validationMessageObject));
                version.setAuthor(null);
                version.setDescriptionAndDescriptionSource(null, null);
                version.setEmail(null);
                return version;
            }
        } catch (IOException e) {
            throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            FileUtils.deleteQuietly(tempMainDescriptor);
        }
        return version;
    }

    /**
     * A common helper method for validating tool and workflow sets
     * @param sourcefiles Set of sourcefiles to validate
     * @param primaryDescriptorFilePath Path of primary descriptor
     * @param type workflow or tool
     * @return
     */
    public VersionTypeValidation validateEntrySet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath, String type) {
        File tempMainDescriptor = null;
        String mainDescriptor = null;

        List<DescriptorLanguage.FileType> fileTypes = new ArrayList<>(Collections.singletonList(DescriptorLanguage.FileType.DOCKSTORE_WDL));
        Set<SourceFile> filteredSourceFiles = filterSourcefiles(sourcefiles, fileTypes);

        Map<String, String> validationMessageObject = new HashMap<>();

        if (filteredSourceFiles.size() > 0) {
            try {
                Optional<SourceFile> primaryDescriptor = filteredSourceFiles.stream()
                        .filter(sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath)).findFirst();

                if (primaryDescriptor.isPresent()) {
                    if (primaryDescriptor.get().getContent() == null || primaryDescriptor.get().getContent().trim().replaceAll("\n", "").isEmpty()) {
                        validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' has no content. Please make it a valid WDL document if you want to save.");
                        return new VersionTypeValidation(false, validationMessageObject);
                    }
                    mainDescriptor = primaryDescriptor.get().getContent();
                } else {
                    validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' could not be found.");
                    return new VersionTypeValidation(false, validationMessageObject);
                }

                Map<String, String> secondaryDescContent = new HashMap<>();
                for (SourceFile sourceFile : filteredSourceFiles) {
                    if (!Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath) && sourceFile.getContent() != null) {
                        if (sourceFile.getContent().trim().replaceAll("\n", "").isEmpty()) {
                            if (Objects.equals(sourceFile.getType(), DescriptorLanguage.FileType.DOCKSTORE_WDL)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL document.");
                            } else if (Objects.equals(sourceFile.getType(), DescriptorLanguage.FileType.WDL_TEST_JSON)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL JSON/YAML file.");
                            } else {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it valid.");
                            }
                            return new VersionTypeValidation(false, validationMessageObject);
                        }
                        secondaryDescContent.put(sourceFile.getAbsolutePath(), sourceFile.getContent());
                    }
                }
                tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
                Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);
                String content = FileUtils.readFileToString(tempMainDescriptor, StandardCharsets.UTF_8);
                checkForRecursiveHTTPImports(content, new HashSet<>());

                WdlBridge wdlBridge = new WdlBridge();
                wdlBridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);

                if (Objects.equals(type, "tool")) {
                    wdlBridge.validateTool(tempMainDescriptor.getAbsolutePath(), primaryDescriptorFilePath);
                } else {
                    wdlBridge.validateWorkflow(tempMainDescriptor.getAbsolutePath(), primaryDescriptor.get().getAbsolutePath());
                }
            } catch (wdl.draft3.parser.WdlParser.SyntaxError | IllegalArgumentException e) {
                validationMessageObject.put(primaryDescriptorFilePath, e.getMessage());
                return new VersionTypeValidation(false, validationMessageObject);
            } catch (CustomWebApplicationException e) {
                throw e;
            } catch (Exception e) {
                LOG.error("Unhandled exception", e);
                throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                FileUtils.deleteQuietly(tempMainDescriptor);
            }
        } else {
            validationMessageObject.put(primaryDescriptorFilePath, "Primary WDL descriptor is not present.");
            return new VersionTypeValidation(false, validationMessageObject);
        }
        return new VersionTypeValidation(true, null);
    }

    /**
     *
     * @param content
     * @param currentFileImports
     * @throws IOException
     */
    public void checkForRecursiveHTTPImports(String content, Set<String> currentFileImports) throws IOException {
        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');

        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (match.startsWith("http://") || match.startsWith("https://")) { // Don't resolve URLs
                    if (currentFileImports.contains(match)) {
                        throw new CustomWebApplicationException(ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT,
                                HttpStatus.SC_BAD_REQUEST);
                    } else {
                        URL url = new URL(match);
                        try (InputStream is = url.openStream();
                            BoundedInputStream boundedInputStream = new BoundedInputStream(is, FileUtils.ONE_MB)) {
                            String fileContents = IOUtils.toString(boundedInputStream, StandardCharsets.UTF_8);
                            // need a depth-first search to avoid triggering warning on workflows
                            // where two files legitimately import the same file
                            Set<String> importsForThisPath = new HashSet<>(currentFileImports);
                            importsForThisPath.add(match);
                            checkForRecursiveHTTPImports(fileContents, importsForThisPath);
                        }
                    }
                }
            }
        }
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "workflow");
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "tool");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return checkValidJsonAndYamlFiles(sourceFiles, DescriptorLanguage.FileType.WDL_TEST_JSON);
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
            SourceCodeRepoInterface sourceCodeRepoInterface, String filepath) {
        return processImports(repositoryId, content, version, sourceCodeRepoInterface, new HashMap<>(), filepath);
    }

    private Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
            SourceCodeRepoInterface sourceCodeRepoInterface, Map<String, SourceFile> imports, String currentFilePath) {
        DescriptorLanguage.FileType fileType = DescriptorLanguage.FileType.DOCKSTORE_WDL;

        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');
        Set<String> currentFileImports = new HashSet<>();

        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                    currentFileImports.add(match.replaceFirst("file://", "")); // remove file:// from path
                }
            }
        }

        for (String importPath : currentFileImports) {
            String absoluteImportPath = convertRelativePathToAbsolutePath(currentFilePath, importPath);
            if (!imports.containsKey(absoluteImportPath)) {
                SourceFile importFile = new SourceFile();

                final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, absoluteImportPath);
                if (fileResponse == null) {
                    SourceCodeRepoInterface.LOG.error("Could not read: " + absoluteImportPath);
                    continue;
                }
                importFile.setContent(fileResponse);
                importFile.setPath(importPath);
                importFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
                importFile.setAbsolutePath(absoluteImportPath);
                imports.put(absoluteImportPath, importFile);
                imports.putAll(processImports(repositoryId, importFile.getContent(), version, sourceCodeRepoInterface, imports, absoluteImportPath));
            }

        }
        return imports;
    }

    /**
     * This method will get the content for tool tab with descriptor type = WDL
     * It will then call another method to transform the content into JSON string and return
     *
     * @param mainDescName         the name of the main descriptor
     * @param mainDescriptor       the content of the main descriptor
     * @param secondarySourceFiles the content of the secondary descriptors in a map, looks like file paths -> content
     * @param type                 tools or DAG
     * @param dao                  used to retrieve information on tools
     * @return either a list of tools or a json map
     */
    @Override
    public String getContent(String mainDescName, String mainDescriptor, Set<SourceFile> secondarySourceFiles,
            LanguageHandlerInterface.Type type, ToolDAO dao) {
        // Initialize general variables
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";
        // Initialize data structures for DAG
        Map<String, ToolInfo> toolInfoMap;
        Map<String, String> namespaceToPath;
        File tempMainDescriptor = null;
        // Write main descriptor to file
        // The use of temporary files is not needed here and might cause new problems
        try {
            tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);

            WdlBridge wdlBridge = new WdlBridge();
            final Map<String, String> pathToContentMap = secondarySourceFiles.stream()
                    .collect(Collectors.toMap(SourceFile::getAbsolutePath, SourceFile::getContent));
            wdlBridge.setSecondaryFiles(new HashMap<>(pathToContentMap));

            // Iterate over each call, grab docker containers
            Map<String, String> callsToDockerMap = wdlBridge.getCallsToDockerMap(tempMainDescriptor.getAbsolutePath(), mainDescName);

            // Iterate over each call, determine dependencies
            Map<String, List<String>> callsToDependencies = wdlBridge.getCallsToDependencies(tempMainDescriptor.getAbsolutePath(), mainDescName);
            toolInfoMap = mapConverterToToolInfo(callsToDockerMap, callsToDependencies);
            // Get import files
            namespaceToPath = wdlBridge.getImportMap(tempMainDescriptor.getAbsolutePath(), mainDescName);
        } catch (IOException | NoSuchElementException | wdl.draft3.parser.WdlParser.SyntaxError e) {
            throw new CustomWebApplicationException("could not process wdl into DAG: " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            FileUtils.deleteQuietly(tempMainDescriptor);
        }
        return convertMapsToContent(mainDescName, type, dao, callType, toolType, toolInfoMap, namespaceToPath);
    }

    /**
     * For existing code, converts from maps of untyped data to ToolInfo
     * @param callsToDockerMap map from names of tools to Docker containers
     * @param callsToDependencies map from names of tools to names of their parent tools (dependencies)
     * @return
     */
    static Map<String, ToolInfo> mapConverterToToolInfo(Map<String, String> callsToDockerMap, Map<String, List<String>> callsToDependencies) {
        Map<String, ToolInfo> toolInfoMap;
        toolInfoMap = new HashMap<>();
        callsToDockerMap.forEach((toolName, containerName) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(containerName, new ArrayList<>());
            } else {
                value.dockerContainer = containerName;
                return value;
            }
        }));
        callsToDependencies.forEach((toolName, dependencies) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(null, new ArrayList<>());
            } else {
                value.toolDependencyList.addAll(dependencies);
                return value;
            }
        }));
        return toolInfoMap;
    }
}
