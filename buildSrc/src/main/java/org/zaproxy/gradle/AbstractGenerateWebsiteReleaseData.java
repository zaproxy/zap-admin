/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.gradle;

import java.util.List;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.ReleaseData.ReleaseFile;

/** An abstract task for generation of release data for the website. */
public abstract class AbstractGenerateWebsiteReleaseData extends DefaultTask {

    private static final double MEGABYTE = 1024 * 1024;

    public AbstractGenerateWebsiteReleaseData(String releaseName) {
        setGroup("ZAP");
        setDescription("Generates the " + releaseName + " release data for the website.");
    }

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @Input
    public abstract Property<String> getGeneratedDataComment();

    @OutputFile
    public abstract RegularFileProperty getInto();

    @TaskAction
    public void generate() throws Exception {
        XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
        zapVersionsXml.load(getZapVersions().get().getAsFile());

        ReleaseData releaseData = new ReleaseData(createReleaseFiles(zapVersionsXml));
        releaseData.save(getInto().get().getAsFile().toPath(), getGeneratedDataComment().get());
    }

    protected abstract List<ReleaseFile> createReleaseFiles(XMLConfiguration zapVersionsXml)
            throws Exception;

    protected static String toMegaBytes(String value) {
        return toMegaBytes(Long.parseLong(value));
    }

    protected static String toMegaBytes(long value) {
        return Math.round(value / MEGABYTE) + " MB";
    }
}
