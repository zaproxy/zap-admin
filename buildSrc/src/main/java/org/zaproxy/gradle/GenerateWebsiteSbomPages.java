/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2023 The ZAP Development Team
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.website.WebsiteSbomPageGenerator;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public abstract class GenerateWebsiteSbomPages extends DefaultTask {

    private static final String ADDON_ELEMENT_PREFIX = "addon_";
    private static final String URL_ELEMENT = ".url";
    private static final String NAME_ELEMENT = ".name";

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void generate() throws Exception {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        List<ReleaseState.AddOnChange> addOns = releaseState.getAddOns();
        if (addOns == null || addOns.isEmpty()) {
            return;
        }

        Path outputDir = getOutputDir().getAsFile().get().toPath();
        ZapXmlConfiguration zapVersions =
                new ZapXmlConfiguration(getZapVersions().getAsFile().get());

        for (ReleaseState.AddOnChange addOn : addOns) {
            if (!addOn.isNewVersion()) {
                continue;
            }
            String addOnId = addOn.getId();
            String url = getString(zapVersions, addOnId, URL_ELEMENT);
            if (!url.startsWith("https://github.com/zaproxy/zap-extensions")) {
                continue;
            }

            String bomUrl = url.substring(0, url.lastIndexOf("/")) + "/bom.json";
            Path bomPath = getTemporaryDir().toPath().resolve(addOnId + ".cdx.json");
            TaskUtils.downloadFile(this, bomUrl, bomPath);

            String pageTitle = getString(zapVersions, addOnId, NAME_ELEMENT) + " Add-on SBOM";
            Path bomPageOutputPath = outputDir.resolve(Path.of("docs", "sbom", addOnId + ".md"));
            Files.createDirectories(bomPageOutputPath.getParent());
            WebsiteSbomPageGenerator.generate(
                    bomPath,
                    bomUrl,
                    pageTitle,
                    addOnId,
                    addOn.getCurrentVersion(),
                    outputDir.resolve(bomPageOutputPath));
        }
    }

    private static String getString(
            ZapXmlConfiguration zapVersions, String addOnId, String element) {
        return zapVersions.getString(ADDON_ELEMENT_PREFIX + addOnId + element);
    }
}
