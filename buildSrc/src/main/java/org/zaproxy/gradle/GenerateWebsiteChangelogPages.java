/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2026 The ZAP Development Team
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
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.ReleaseState.AddOnChange;
import org.zaproxy.gradle.website.WebsiteChangelogPageGenerator;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public abstract class GenerateWebsiteChangelogPages extends DefaultTask {

    private static final String ADDON_ELEMENT_PREFIX = "addon_";
    private static final String URL_ELEMENT = ".url";
    private static final String NAME_ELEMENT = ".name";

    private static final String ZAP_EXTENSIONS_PREFIX =
            "https://github.com/zaproxy/zap-extensions/";
    private static final String GITHUB_PREFIX = "https://github.com/";
    private static final String RAW_GITHUB_BASE = "https://raw.githubusercontent.com/";
    private static final Set<String> WEBDRIVER_ADDON_IDS =
            Set.of("webdriverlinux", "webdrivermacos", "webdriverwindows");

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void generate() throws Exception {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        List<AddOnChange> addOns = releaseState.getAddOns();
        if (addOns == null || addOns.isEmpty()) {
            return;
        }

        Path outputDir = getOutputDir().getAsFile().get().toPath();
        var zapVersions = new ZapXmlConfiguration(getZapVersions().getAsFile().get());

        for (AddOnChange addOn : addOns) {
            if (!addOn.isNewVersion()) {
                continue;
            }
            String addOnId = addOn.getId();
            Path changelogPath = getTemporaryDir().toPath().resolve(addOnId + "-CHANGELOG.md");
            try {
                String url = getString(zapVersions, addOnId, URL_ELEMENT);
                String tag = extractTagFromUrl(url);
                String changelogUrl = buildChangelogUrl(addOnId, url, tag);

                Files.deleteIfExists(changelogPath);
                TaskUtils.downloadFile(this, changelogUrl, changelogPath);
            } catch (Exception e) {
                getLogger().warn(e.getMessage(), e);
                continue;
            }

            String addOnName = getString(zapVersions, addOnId, NAME_ELEMENT);
            Path addOnDir = outputDir.resolve(Path.of("docs", "addons", addOnId));
            Files.createDirectories(addOnDir);

            WebsiteChangelogPageGenerator.generate(
                    changelogPath,
                    addOnName + " Add-on Changelog",
                    addOnId,
                    addOn.getCurrentVersion(),
                    addOnDir.resolve("changelog.md"));
        }
    }

    private static String buildChangelogUrl(String addOnId, String url, String tag) {
        if (url.startsWith(ZAP_EXTENSIONS_PREFIX)) {
            String addOnPath = addOnId;
            if (WEBDRIVER_ADDON_IDS.contains(addOnId)) {
                addOnPath = "webdrivers/" + addOnId;
            }
            return RAW_GITHUB_BASE
                    + "zaproxy/zap-extensions/"
                    + tag
                    + "/addOns/"
                    + addOnPath
                    + "/CHANGELOG.md";
        }

        String ownerRepo = extractOwnerRepoFromUrl(url);
        return RAW_GITHUB_BASE + ownerRepo + "/" + tag + "/CHANGELOG.md";
    }

    static String extractOwnerRepoFromUrl(String url) {
        if (!url.startsWith(GITHUB_PREFIX)) {
            throw new IllegalArgumentException("The provided URL is not a GitHub URL: " + url);
        }
        // URL format: https://github.com/{owner}/{repo}/releases/download/{tag}/{file}
        String path = url.substring(GITHUB_PREFIX.length());
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Cannot extract owner/repo from URL: " + url);
        }
        return parts[0] + "/" + parts[1];
    }

    static String extractTagFromUrl(String url) {
        String marker = "/releases/download/";
        int start = url.indexOf(marker);
        if (start == -1) {
            throw new IllegalArgumentException(
                    "Cannot extract tag from URL, missing '/releases/download/': " + url);
        }
        start += marker.length();
        int end = url.indexOf('/', start);
        if (end == -1) {
            throw new IllegalArgumentException(
                    "Cannot extract tag from URL, missing trailing '/': " + url);
        }
        return url.substring(start, end);
    }

    private static String getString(
            ZapXmlConfiguration zapVersions, String addOnId, String element) {
        return zapVersions.getString(ADDON_ELEMENT_PREFIX + addOnId + element);
    }
}
