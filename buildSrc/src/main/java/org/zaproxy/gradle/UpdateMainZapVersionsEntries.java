/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2019 The ZAP Development Team
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * A task that updates {@code ZapVersions.xml} files with a main release.
 *
 * <p><strong>Note:</strong>
 *
 * <ul>
 *   <li>This task does not have outputs as the target {@code ZapVersions.xml} files might be
 *       changed externally/manually, for example, updated with add-ons or weekly releases.
 *   <li>There are no guarantees that all {@code ZapVersions.xml} files are updated if an error
 *       occurs while updating them.
 * </ul>
 */
public class UpdateMainZapVersionsEntries extends DefaultTask {

    private static final String HTTPS_SCHEME = "HTTPS";

    private static final String VERSION_TOKEN = "@@VERSION@@";
    private static final String VERSION_UNDERSCORES_TOKEN = "@@VERSION_UNDERSCORES@@";

    private static final String CORE_VERSION_ELEMENT = "core.version";
    private static final String CORE_REL_NOTES_ELEMENT = "core.relnotes";
    private static final String CORE_REL_NOTES_URL_ELEMENT = "core.relnotes-url";

    private static final String WINDOWS_ELEMENT = "core.windows";
    private static final String LINUX_ELEMENT = "core.linux";
    private static final String MAC_ELEMENT = "core.mac";

    private static final String FILE_ELEMENT = ".file";
    private static final String HASH_ELEMENT = ".hash";
    private static final String SIZE_ELEMENT = ".size";
    private static final String URL_ELEMENT = ".url";

    private final ConfigurableFileCollection into;
    private final Property<String> version;
    private final Property<String> baseDownloadUrl;
    private final Property<String> windowsFileName;
    private final Property<String> linuxFileName;
    private final Property<String> macFileName;
    private final Property<String> releaseNotes;
    private final Property<String> releaseNotesUrl;
    private final Property<String> checksumAlgorithm;

    private String versionDots;
    private String versionUnderscores;

    public UpdateMainZapVersionsEntries() {
        this.into = getProject().getObjects().fileCollection();
        this.version = getProject().getObjects().property(String.class);
        this.baseDownloadUrl = getProject().getObjects().property(String.class);
        this.windowsFileName = getProject().getObjects().property(String.class);
        this.linuxFileName = getProject().getObjects().property(String.class);
        this.macFileName = getProject().getObjects().property(String.class);
        this.releaseNotes = getProject().getObjects().property(String.class);
        this.releaseNotesUrl = getProject().getObjects().property(String.class);
        this.checksumAlgorithm = getProject().getObjects().property(String.class);

        setGroup("ZAP");
        setDescription("Updates ZapVersions.xml files with a main release.");
    }

    @InputFiles
    public ConfigurableFileCollection getInto() {
        return into;
    }

    @Option(option = "release", description = "The main release version.")
    public void setReleaseVersion(String version) {
        this.version.set(version);
    }

    @Input
    public Property<String> getVersion() {
        return version;
    }

    @Input
    public Property<String> getBaseDownloadUrl() {
        return baseDownloadUrl;
    }

    @Input
    public Property<String> getWindowsFileName() {
        return windowsFileName;
    }

    @Input
    public Property<String> getLinuxFileName() {
        return linuxFileName;
    }

    @Input
    public Property<String> getMacFileName() {
        return macFileName;
    }

    @Input
    public Property<String> getReleaseNotes() {
        return releaseNotes;
    }

    @Input
    public Property<String> getReleaseNotesUrl() {
        return releaseNotesUrl;
    }

    @Input
    public Property<String> getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    @TaskAction
    public void update() throws Exception {
        validateNotEmpty(version, "version");
        validateNotEmpty(baseDownloadUrl, "base download URL");
        validateNotEmpty(windowsFileName, "Windows file name");
        validateNotEmpty(linuxFileName, "Linux file name");
        validateNotEmpty(macFileName, "macOS file name");
        validateNotEmpty(releaseNotes, "release notes");
        validateNotEmpty(releaseNotesUrl, "release notes URL");
        validateNotEmpty(checksumAlgorithm, "checksum algorithm");

        versionDots = version.get();
        versionUnderscores = versionDots.replace('.', '_');

        String finalBaseDownloadUrl = replaceVersionTokens(baseDownloadUrl.get());
        try {
            URL url = new URL(finalBaseDownloadUrl);
            if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
                throw new IllegalArgumentException(
                        "The provided download URL does not use HTTPS scheme: "
                                + url.getProtocol());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to parse the download URL: " + e.getMessage(), e);
        }

        List<ReleaseFile> releaseFiles = new ArrayList<>();
        releaseFiles.add(
                createReleaseFile(
                        WINDOWS_ELEMENT,
                        createDownloadUrl(finalBaseDownloadUrl, windowsFileName.get())));
        releaseFiles.add(
                createReleaseFile(
                        LINUX_ELEMENT,
                        createDownloadUrl(finalBaseDownloadUrl, linuxFileName.get())));
        releaseFiles.add(
                createReleaseFile(
                        MAC_ELEMENT, createDownloadUrl(finalBaseDownloadUrl, macFileName.get())));

        for (File zapVersionsFile : into.getFiles()) {
            if (!Files.isRegularFile(zapVersionsFile.toPath())) {
                throw new IllegalArgumentException(
                        "The provided path is not a file: " + zapVersionsFile);
            }

            XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
            zapVersionsXml.load(zapVersionsFile);

            zapVersionsXml.setProperty(CORE_VERSION_ELEMENT, versionDots);
            zapVersionsXml.setProperty(CORE_REL_NOTES_ELEMENT, releaseNotes.get());
            zapVersionsXml.setProperty(
                    CORE_REL_NOTES_URL_ELEMENT, replaceVersionTokens(releaseNotesUrl.get()));

            releaseFiles.forEach(
                    c -> {
                        zapVersionsXml.setProperty(
                                c.getKeyPrefix() + FILE_ELEMENT, c.getFileName());
                        zapVersionsXml.setProperty(c.getKeyPrefix() + HASH_ELEMENT, c.getHash());
                        zapVersionsXml.setProperty(c.getKeyPrefix() + SIZE_ELEMENT, c.getSize());
                        zapVersionsXml.setProperty(c.getKeyPrefix() + URL_ELEMENT, c.getUrl());
                    });

            zapVersionsXml.save(zapVersionsFile);
        }
    }

    private static void validateNotEmpty(Property<String> property, String propertyName) {
        if (property.get().isEmpty()) {
            throw new IllegalArgumentException("The " + propertyName + " must not be empty.");
        }
    }

    private String replaceVersionTokens(String string) {
        return string.replace(VERSION_TOKEN, versionDots)
                .replace(VERSION_UNDERSCORES_TOKEN, versionUnderscores);
    }

    private ReleaseFile createReleaseFile(String keyPrefix, String url) throws IOException {
        Path file = downloadFile(url);
        return new ReleaseFile(
                keyPrefix,
                url,
                file.getFileName().toString(),
                createChecksum(checksumAlgorithm.get(), file),
                String.valueOf(Files.size(file)));
    }

    private String createDownloadUrl(String baseUrl, String name) {
        return baseUrl + replaceVersionTokens(name);
    }

    private static String createChecksum(String algorithm, Path file) throws IOException {
        return algorithm + ":" + new DigestUtils(algorithm).digestAsHex(file.toFile());
    }

    private Path downloadFile(String urlString) throws IOException {
        URL url = new URL(urlString);
        if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException(
                    "The provided URL does not use HTTPS scheme: " + url.getProtocol());
        }

        Path file = getTemporaryDir().toPath().resolve(extractFileName(urlString));
        if (Files.exists(file)) {
            getLogger().info("File already exists, skipping download.");
            return file;
        }

        try (InputStream in = url.openStream()) {
            Files.copy(in, file);
        } catch (IOException e) {
            throw new IOException("Failed to download the file: " + e.getMessage(), e);
        }
        getLogger().info("File downloaded to: " + file);
        return file;
    }

    private static String extractFileName(String url) {
        int idx = url.lastIndexOf("/");
        if (idx == -1) {
            throw new IllegalArgumentException(
                    "The provided URL does not have a file name: " + url);
        }
        return url.substring(idx + 1);
    }

    private static final class ReleaseFile {

        private final String keyPrefix;
        private final String url;
        private final String fileName;
        private final String hash;
        private final String size;

        ReleaseFile(String keyPrefix, String url, String fileName, String hash, String size) {
            this.keyPrefix = keyPrefix;
            this.url = url;
            this.fileName = fileName;
            this.hash = hash;
            this.size = size;
        }

        String getKeyPrefix() {
            return keyPrefix;
        }

        String getUrl() {
            return url;
        }

        String getFileName() {
            return fileName;
        }

        String getHash() {
            return hash;
        }

        String getSize() {
            return size;
        }
    }
}
