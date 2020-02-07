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
package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NFLParseTest {

    @Test
    public void testNFLCoreMetadataNoAuthorExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("nfl-chipseq/nextflow.config");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.NEXTFLOW_CONFIG);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new WorkflowVersion());
        assertNull("incorrect author", entry.getAuthor());
        assertTrue("incorrect description", entry.getDescription().startsWith("Analysis pipeline used for ChIP-seq (chromatin immunoprecipitation sequencing) data"));
    }

    @Test
    public void testNFLCoreMetadataWithAuthorExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("nfl-rnaseq/nextflow.config");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.NEXTFLOW_CONFIG);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new WorkflowVersion());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertTrue("incorrect description", entry.getDescription().startsWith("Nextflow RNA-Seq analysis pipeline, part of the nf-core community."));
    }

    @Test
    public void testNFLNotCoreExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("nfl-ampa/nextflow.config");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.NEXTFLOW_CONFIG);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new WorkflowVersion());
        assertEquals("incorrect description", "Fast automated prediction of protein antimicrobial regions", entry.getDescription());
    }
}
