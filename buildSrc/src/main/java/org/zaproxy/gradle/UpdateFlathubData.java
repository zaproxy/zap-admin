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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.ReleaseState.VersionChange;

public abstract class UpdateFlathubData extends DefaultTask {

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
        String url = zapVersionsXml.getString("core.linux.url");
        String hash = zapVersionsXml.getString("core.linux.hash").split(":", 2)[1];

        Path repo = getBaseDirectory().get().toPath();

        updateAppData(repo, version);
        updateJson(repo, url, hash);
    }

    private static boolean isNewMainRelease(ReleaseState releaseState) {
        VersionChange mainRelease = releaseState.getMainRelease();
        return mainRelease != null && mainRelease.isNewVersion();
    }

    private static void updateAppData(Path dir, String version) throws IOException {
        Path file = dir.resolve("org.zaproxy.ZAP.appdata.xml");
        String contents = Files.readString(file);

        Matcher matcher = Pattern.compile("<release version=\"([^\\\"]+)\" date=\"([^\\\"]+)\" />").matcher(contents);
        if (!matcher.find()) {
            throw new IOException("The XML entry release was not found in: " + file);
        }
        StringBuilder sb = new StringBuilder();
        matcher.appendReplacement(sb, "<release version=\"" + version + "\" date=\"" + LocalDate.now() + "\" />");
        matcher.appendTail(sb);

        Files.writeString(file, sb.toString());
    }

    private static void updateJson(Path dir, String url, String hash) throws IOException {
        Path file = dir.resolve("org.zaproxy.ZAP.json");
        String contents = Files.readString(file);

        Matcher matcher = Pattern.compile("url\": \"([^\\\"]+)\"").matcher(contents);
        if (!matcher.find()) {
            throw new IOException("The JSON url property was not found in: " + file);
        }
        StringBuilder sb = new StringBuilder();
        matcher.appendReplacement(sb, "url\": \"" + url + "\"");
        matcher.appendTail(sb);

        matcher = Pattern.compile("sha256\": \"([^\\\"]+)\"").matcher(sb.toString());
        if (!matcher.find()) {
            throw new IOException("The JSON sha256 property was not found in: " + file);
        }
        sb.setLength(0);
        matcher.appendReplacement(sb, "sha256\": \"" + hash + "\"");
        matcher.appendTail(sb);

        Files.writeString(file, sb.toString());
    }

}
