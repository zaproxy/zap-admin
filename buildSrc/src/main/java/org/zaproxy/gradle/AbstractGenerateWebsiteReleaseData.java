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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.ReleaseData.ReleaseFile;

/** An abstract task for generation of release data for the website. */
public abstract class AbstractGenerateWebsiteReleaseData extends DefaultTask {

    private static final double MEGABYTE = 1024 * 1024;

    private final RegularFileProperty zapVersions;
    private final Property<String> generatedDataComment;
    private final RegularFileProperty into;

    public AbstractGenerateWebsiteReleaseData(String releaseName) {
        ObjectFactory objects = getProject().getObjects();
        this.zapVersions = objects.fileProperty();
        this.generatedDataComment = objects.property(String.class);
        this.into = objects.fileProperty();

        setGroup("ZAP");
        setDescription("Generates the " + releaseName + " release data for the website.");
    }

    @InputFile
    public RegularFileProperty getZapVersions() {
        return zapVersions;
    }

    @Input
    public Property<String> getGeneratedDataComment() {
        return generatedDataComment;
    }

    @OutputFile
    public RegularFileProperty getInto() {
        return into;
    }

    @TaskAction
    public void generate() throws Exception {
        XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
        zapVersionsXml.load(zapVersions.get().getAsFile());

        ReleaseData releaseData = new ReleaseData(createReleaseFiles(zapVersionsXml));
        releaseData.save(into.get().getAsFile().toPath(), generatedDataComment.get());
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
