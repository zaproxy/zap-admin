/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/** A task that downloads the add-ons released in the last commit, if any. */
public abstract class DownloadReleasedAddOns extends DefaultTask {

    private static final String ADDON_ELEMENT_PREFIX = "addon_";
    private static final String URL_ELEMENT = ".url";
    private static final String HASH_ELEMENT = ".hash";

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @InputFile
    public abstract RegularFileProperty getAllowedAddOns();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    void downloadAddOns() throws Exception {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        List<ReleaseState.AddOnChange> addOns = releaseState.getAddOns();
        if (addOns == null || addOns.isEmpty()) {
            return;
        }

        Set<String> allowedAddOns = TaskUtils.readAllowedAddOns(getAllowedAddOns());
        Path outputDir = getOutputDir().getAsFile().get().toPath();
        ZapXmlConfiguration zapVersions =
                new ZapXmlConfiguration(getZapVersions().getAsFile().get());
        for (ReleaseState.AddOnChange addOn : addOns) {
            String addOnId = addOn.getId();
            if (!addOn.isNewVersion() || !allowedAddOns.contains(addOnId)) {
                continue;
            }

            try {
                String url = getString(zapVersions, addOnId, URL_ELEMENT);
                String[] checksumData = getString(zapVersions, addOnId, HASH_ELEMENT).split(":", 2);
                String checksumAlgorithm = checksumData[0];
                String checksum = checksumData[1];
                Path downloadedAddOn = TaskUtils.downloadAddOn(this, url, outputDir);
                TaskUtils.calculateChecksum(downloadedAddOn, checksumAlgorithm, checksum);
            } catch (Exception e) {
                getLogger()
                        .error(
                                "Failed to download the add-on {}. Cause: {}",
                                addOnId,
                                e.getMessage(),
                                e);
            }
        }
    }

    private static String getString(
            ZapXmlConfiguration zapVersions, String addOnId, String element) {
        return zapVersions.getString(ADDON_ELEMENT_PREFIX + addOnId + element);
    }
}
