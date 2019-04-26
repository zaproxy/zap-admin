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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * A task that updates {@code ZapVersions.xml} files with a daily release.
 *
 * <p><strong>Note:</strong>
 *
 * <ul>
 *   <li>This task does not have outputs as the target {@code ZapVersions.xml} files might be
 *       changed externally/manually, for example, updated with add-ons or main releases.
 *   <li>There are no guarantees that all {@code ZapVersions.xml} files are updated if an error
 *       occurs while updating them.
 * </ul>
 */
public class UpdateDailyZapVersionsEntries extends DefaultTask {

    private static final String DAILY_VERSION_ELEMENT = "core.daily-version";
    private static final String DAILY_ELEMENT = "core.daily";
    private static final String DAILY_FILE_ELEMENT = DAILY_ELEMENT + ".file";
    private static final String DAILY_HASH_ELEMENT = DAILY_ELEMENT + ".hash";
    private static final String DAILY_SIZE_ELEMENT = DAILY_ELEMENT + ".size";
    private static final String DAILY_URL_ELEMENT = DAILY_ELEMENT + ".url";

    private final RegularFileProperty from;
    private final ConfigurableFileCollection into;
    private final Property<String> baseDownloadUrl;
    private final Property<String> checksumAlgorithm;

    public UpdateDailyZapVersionsEntries() {
        this.from = newInputFile();
        this.into = getProject().getLayout().configurableFiles();
        this.baseDownloadUrl = getProject().getObjects().property(String.class);
        this.checksumAlgorithm = getProject().getObjects().property(String.class);

        setGroup("ZAP");
        setDescription("Updates ZapVersions.xml files with a daily release.");
    }

    @Option(option = "file", description = "The file system path to the daily release.")
    public void setDailyFilePath(String path) {
        from.set(getProject().file(path));
    }

    @Input
    public RegularFileProperty getFrom() {
        return from;
    }

    @Input
    public ConfigurableFileCollection getInto() {
        return into;
    }

    @Input
    public Property<String> getBaseDownloadUrl() {
        return baseDownloadUrl;
    }

    @Input
    public Property<String> getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    @TaskAction
    public void update() throws Exception {
        File dailyRelease = from.get().getAsFile();
        if (!Files.isRegularFile(dailyRelease.toPath())) {
            throw new IllegalArgumentException(
                    "The provided daily release does not exist or it's not a file: "
                            + dailyRelease);
        }

        if (checksumAlgorithm.get().isEmpty()) {
            throw new IllegalArgumentException("The checksum algorithm must not be empty.");
        }

        if (baseDownloadUrl.get().isEmpty()) {
            throw new IllegalArgumentException("The base download URL must not be empty.");
        }

        String fileName = dailyRelease.getName();
        String dailyVersion = getDailyVersion(fileName);
        String hash = createChecksum(checksumAlgorithm.get(), dailyRelease);
        String size = String.valueOf(dailyRelease.length());
        String url = baseDownloadUrl.get() + dailyVersion.substring(2) + "/" + fileName;

        for (File zapVersionsFile : into.getFiles()) {
            if (!Files.isRegularFile(zapVersionsFile.toPath())) {
                throw new IllegalArgumentException(
                        "The provided path is not a file: " + zapVersionsFile);
            }

            XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
            zapVersionsXml.load(zapVersionsFile);
            zapVersionsXml.setProperty(DAILY_VERSION_ELEMENT, dailyVersion);
            zapVersionsXml.setProperty(DAILY_FILE_ELEMENT, fileName);
            zapVersionsXml.setProperty(DAILY_HASH_ELEMENT, hash);
            zapVersionsXml.setProperty(DAILY_SIZE_ELEMENT, size);
            zapVersionsXml.setProperty(DAILY_URL_ELEMENT, url);
            zapVersionsXml.save(zapVersionsFile);
        }
    }

    private String getDailyVersion(String fileName) {
        int beginIdx = fileName.indexOf("D-");
        if (beginIdx == -1) {
            throw new IllegalArgumentException(
                    "The file name does not have the expected daily prefix ('D-'): " + fileName);
        }
        int endIdx = fileName.indexOf(".zip");
        if (endIdx == -1) {
            throw new IllegalArgumentException(
                    "The file name does not have the expected extension ('.zip'): " + fileName);
        }
        return fileName.substring(beginIdx, endIdx);
    }

    private static String createChecksum(String algorithm, File addOnFile) throws IOException {
        return algorithm + ":" + new DigestUtils(algorithm).digestAsHex(addOnFile);
    }
}
