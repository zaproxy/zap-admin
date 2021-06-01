/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2018 The ZAP Development Team
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** A task that updates {@code ZapVersions.xml} files with a daily release. */
public abstract class UpdateDailyZapVersionsEntries extends AbstractUpdateZapVersionsEntries {

    private static final String HTTPS_SCHEME = "HTTPS";
    private static final String DAILY_RELEASE_EXTENSION = ".zip";

    private static final String DAILY_VERSION_ELEMENT = "core.daily-version";
    private static final String DAILY_ELEMENT = "core.daily";
    private static final String DAILY_FILE_ELEMENT = DAILY_ELEMENT + ".file";
    private static final String DAILY_HASH_ELEMENT = DAILY_ELEMENT + ".hash";
    private static final String DAILY_SIZE_ELEMENT = DAILY_ELEMENT + ".size";
    private static final String DAILY_URL_ELEMENT = DAILY_ELEMENT + ".url";

    private final Property<String> checksum;

    public UpdateDailyZapVersionsEntries() {
        this.checksum = getProject().getObjects().property(String.class);
        setDescription("Updates ZapVersions.xml files with a daily release.");
    }

    @Option(option = "file", description = "The file system path to the daily release.")
    public void setDailyFilePath(String path) {
        getFrom().set(getProject().file(path));
    }

    @Input
    @Optional
    public abstract RegularFileProperty getFrom();

    @Option(option = "url", description = "The URL to the daily release.")
    public void setUrl(String url) {
        getFromUrl().set(url);
    }

    @Input
    @Optional
    public abstract Property<String> getFromUrl();

    @Option(option = "checksum", description = "The checksum of the daily release.")
    public void setChecksum(String checksum) {
        getChecksum().set(checksum);
    }

    @Input
    @Optional
    public Property<String> getChecksum() {
        return checksum;
    }

    @Input
    public abstract Property<String> getBaseDownloadUrl();

    @TaskAction
    public void update() throws Exception {
        Path dailyRelease = getReleaseFile();
        String fileName = dailyRelease.getFileName().toString();
        String dailyVersion = getDailyVersion(fileName);
        String url;
        if (getFrom().isPresent()) {
            if (getBaseDownloadUrl().get().isEmpty()) {
                throw new IllegalArgumentException("The base download URL must not be empty.");
            }
            url = getBaseDownloadUrl().get() + dailyVersion.substring(2) + "/" + fileName;
        } else {
            url = getFromUrl().get();
        }

        String hash = createChecksumString(dailyRelease, getChecksum().getOrNull());
        String size = String.valueOf(Files.size(dailyRelease));

        updateZapVersionsFiles(
                zapVersionsXml -> {
                    zapVersionsXml.setProperty(DAILY_VERSION_ELEMENT, dailyVersion);
                    zapVersionsXml.setProperty(DAILY_FILE_ELEMENT, fileName);
                    zapVersionsXml.setProperty(DAILY_HASH_ELEMENT, hash);
                    zapVersionsXml.setProperty(DAILY_SIZE_ELEMENT, size);
                    zapVersionsXml.setProperty(DAILY_URL_ELEMENT, url);
                });
    }

    private String getDailyVersion(String fileName) {
        int beginIdx = fileName.indexOf("D-");
        if (beginIdx == -1) {
            throw new IllegalArgumentException(
                    "The file name does not have the expected daily prefix ('D-'): " + fileName);
        }
        int endIdx = fileName.indexOf(DAILY_RELEASE_EXTENSION);
        if (endIdx == -1) {
            throw new IllegalArgumentException(
                    "The file name does not have the expected extension ('.zip'): " + fileName);
        }
        return fileName.substring(beginIdx, endIdx);
    }

    private Path getReleaseFile() throws IOException {
        if (getFrom().isPresent()) {
            Path release = getFrom().getAsFile().get().toPath();
            if (!Files.isRegularFile(release)) {
                throw new IllegalArgumentException(
                        "The provided path does not exist or it's not a file: " + release);
            }
            return release;
        }

        if (!getChecksum().isPresent()) {
            throw new IllegalArgumentException(
                    "The checksum must be provided when downloading the file.");
        }

        String urlString = getFromUrl().get();
        URL url = new URL(urlString);
        if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException(
                    "The provided URL does not use HTTPS scheme: " + url.getProtocol());
        }

        Path release = getTemporaryDir().toPath().resolve(extractFileName(urlString));
        if (Files.exists(release)) {
            getLogger().info("File already exists, skipping download.");
            return release;
        }

        try (InputStream in = url.openStream()) {
            Files.copy(in, release);
        } catch (IOException e) {
            throw new IOException("Failed to download the file: " + e.getMessage(), e);
        }
        getLogger().info("File downloaded to: " + release);
        return release;
    }

    private static String extractFileName(String url) {
        int idx = url.lastIndexOf("/");
        if (idx == -1) {
            throw new IllegalArgumentException(
                    "The provided URL does not have a file name: " + url);
        }
        String fileName = url.substring(idx + 1);
        if (!fileName.endsWith(DAILY_RELEASE_EXTENSION)) {
            throw new IllegalArgumentException(
                    "The provided URL does not have a file with zap extension: " + fileName);
        }
        return fileName;
    }
}
