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

package io.dockstore.common;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author dyuen
 */
public class DescriptorLanguageTest {

    @Test
    public void testGetFileType() {
        Assert.assertEquals(DescriptorLanguage.getFileType("CWL").get(), DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Assert.assertEquals(DescriptorLanguage.getFileType("PLAIN_CWL").get(), DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Assert.assertEquals(DescriptorLanguage.getFileType("WDL").get(), DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Assert.assertEquals(DescriptorLanguage.getFileType("PLAIN_WDL").get(), DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Assert.assertEquals(DescriptorLanguage.getFileType("FOO"), Optional.empty());
    }

    @Test
    public void testGetTestParamFileType() {
        Assert.assertEquals(DescriptorLanguage.getTestParameterType("CWL").get(), DescriptorLanguage.FileType.CWL_TEST_JSON);
        Assert.assertEquals(DescriptorLanguage.getTestParameterType("WDL").get(), DescriptorLanguage.FileType.WDL_TEST_JSON);
        Assert.assertEquals(DescriptorLanguage.getTestParameterType("FOO"), Optional.empty());
    }
}
