/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2025 The ZAP Development Team
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.ReleaseState.VersionChange;

public abstract class UpdateZapMgmtScriptsData extends DefaultTask {

    private static final String TAGS_START = "tags = [\n";
    private static final String TAGS_END = "]";

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @Input
    public abstract Property<File> getBaseDirectory();

    @TaskAction
    void executeTasks() throws Exception {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        if (!isNewMainRelease(releaseState)) {
            return;
        }

        XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
        zapVersionsXml.load(getZapVersions().get().getAsFile());

        String version = zapVersionsXml.getString("core.version");

        Path repo = getBaseDirectory().get().toPath();

        updateGitHubStats(repo, version);
    }

    private static boolean isNewMainRelease(ReleaseState releaseState) {
        VersionChange mainRelease = releaseState.getMainRelease();
        return mainRelease != null && mainRelease.isNewVersion();
    }

    private static void updateGitHubStats(Path dir, String version) throws IOException {
        Path file = dir.resolve("stats/github.py");
        String contents = Files.readString(file);

        int start = contents.indexOf(TAGS_START);
        if (start == -1) {
            throw new IOException("The tags array start was not found in: " + file);
        }

        int end = contents.indexOf(TAGS_END, start);
        if (end == -1) {
            throw new IOException("The tags array end was not found in: " + file);
        }

        List<String> tags = new ArrayList<>();
        tags.add("\"v" + version + "\"");

        Arrays.stream(
                        contents.substring(start + TAGS_START.length(), end + TAGS_END.length())
                                .split(",", -1))
                .map(String::trim)
                .forEach(tags::add);

        while (tags.size() > 3) {
            tags.remove(tags.size() - 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(contents, 0, start + TAGS_START.length());
        for (String tag : tags) {
            sb.append("    ").append(tag).append(",\n");
        }
        sb.append(contents, end, contents.length());

        Files.writeString(file, sb.toString());
    }
}
