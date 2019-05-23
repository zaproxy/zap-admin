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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/**
 * A task that updates {@code ZapVersions.xml} files with an add-on.
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
public class UpdateAddOnZapVersionsEntries extends DefaultTask {

    private static final String HTTPS_SCHEME = "HTTPS";

    private static final String ADD_ON_ELEMENT = "addon";
    private static final String ADD_ON_NODE_PREFIX = "addon_";

    private static final String ADD_ON_MANIFEST_FILE_NAME = "ZapAddOn.xml";
    private static final String ADD_ON_EXTENSION = ".zap";

    private final RegularFileProperty fromFile;
    private final Property<String> fromUrl;
    private final ConfigurableFileCollection into;
    private final Property<String> downloadUrl;
    private final Property<String> checksumAlgorithm;
    private final Property<LocalDate> releaseDate;

    public UpdateAddOnZapVersionsEntries() {
        ObjectFactory objects = getProject().getObjects();
        this.fromFile = objects.fileProperty();
        this.fromUrl = objects.property(String.class);
        this.into = objects.fileCollection();
        this.downloadUrl = objects.property(String.class);
        this.downloadUrl.set(fromUrl);
        this.checksumAlgorithm = objects.property(String.class);
        this.releaseDate = objects.property(LocalDate.class);
        this.releaseDate.set(LocalDate.now());

        setGroup("ZAP");
        setDescription("Updates ZapVersions.xml files with an add-on.");
    }

    @Option(option = "file", description = "The file system path to the add-on.")
    public void setFile(String path) {
        fromFile.set(getProject().file(path));
    }

    @Input
    @Optional
    public RegularFileProperty getFromFile() {
        return fromFile;
    }

    @Option(option = "url", description = "The URL to the add-on.")
    public void setUrl(String url) {
        fromUrl.set(url);
    }

    @Input
    @Optional
    public Property<String> getFromUrl() {
        return fromUrl;
    }

    @Option(option = "into", description = "The ZapVersions.xml files to update.")
    public void setFiles(List<String> files) {
        into.setFrom(files);
    }

    @InputFiles
    public ConfigurableFileCollection getInto() {
        return into;
    }

    @Option(option = "downloadUrl", description = "The URL from where the add-on is downloaded.")
    public void setDownloadUrl(String url) {
        downloadUrl.set(url);
    }

    @Input
    @Optional
    public Property<String> getDownloadUrl() {
        return downloadUrl;
    }

    @Input
    public Property<String> getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    @Option(option = "releaseDate", description = "The release date.")
    public void setReleaseDate(String date) {
        releaseDate.set(LocalDate.parse(date));
    }

    @Input
    public Property<LocalDate> getReleaseDate() {
        return releaseDate;
    }

    @TaskAction
    public void update() throws Exception {
        if (checksumAlgorithm.get().isEmpty()) {
            throw new IllegalArgumentException("The checksum algorithm must not be empty.");
        }

        if (fromFile.isPresent()) {
            if (fromUrl.isPresent()) {
                throw new IllegalArgumentException(
                        "Only one of the properties, URL or file, can be set at the same time.");
            }

            if (!downloadUrl.isPresent()) {
                throw new IllegalArgumentException(
                        "The download URL must be provided when specifying the file.");
            }
        } else if (!fromUrl.isPresent()) {
            throw new IllegalArgumentException(
                    "Either one of the properties, URL or file, must be set.");
        }

        if (downloadUrl.get().isEmpty()) {
            throw new IllegalArgumentException("The download URL must not be empty.");
        }

        try {
            URL url = new URL(downloadUrl.get());
            if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
                throw new IllegalArgumentException(
                        "The provided download URL does not use HTTPS scheme: "
                                + url.getProtocol());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to parse the download URL: " + e.getMessage(), e);
        }

        Path addOn = getAddOn();
        String addOnId = extractAddOnId(addOn.getFileName().toString());
        AddOnEntry addOnEntry =
                new AddOnEntry(
                        addOnId,
                        new AddOnConfBuilder(
                                        addOn,
                                        downloadUrl.get(),
                                        checksumAlgorithm.get(),
                                        releaseDate.get())
                                .build());

        for (File zapVersionsFile : into.getFiles()) {
            if (!Files.isRegularFile(zapVersionsFile.toPath())) {
                throw new IllegalArgumentException(
                        "The provided path is not a file: " + zapVersionsFile);
            }

            SortedSet<AddOnEntry> addOns = new TreeSet<>();
            XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
            zapVersionsXml.load(zapVersionsFile);
            Arrays.stream(zapVersionsXml.getStringArray(ADD_ON_ELEMENT))
                    .filter(id -> !addOnId.equals(id))
                    .forEach(
                            id -> {
                                String key = ADD_ON_NODE_PREFIX + id;
                                addOns.add(new AddOnEntry(id, zapVersionsXml.configurationAt(key)));
                                zapVersionsXml.clearTree(key);
                            });

            zapVersionsXml.clearTree(ADD_ON_ELEMENT);
            zapVersionsXml.clearTree(ADD_ON_NODE_PREFIX + addOnId);

            addOns.add(addOnEntry);

            addOns.forEach(
                    e -> {
                        zapVersionsXml.addProperty(ADD_ON_ELEMENT, e.getAddOnId());
                        zapVersionsXml.addNodes(
                                ADD_ON_NODE_PREFIX + e.getAddOnId(),
                                e.getData().getRootNode().getChildren());
                    });
            zapVersionsXml.save(zapVersionsFile);
        }
    }

    private Path getAddOn() throws IOException {
        if (fromFile.isPresent()) {
            Path addOn = fromFile.getAsFile().get().toPath();
            if (!Files.isRegularFile(addOn)) {
                throw new IllegalArgumentException(
                        "The provided path does not exist or it's not a file: " + addOn);
            }
            return addOn;
        }

        String urlString = fromUrl.get();
        URL url = new URL(urlString);
        if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException(
                    "The provided URL does not use HTTPS scheme: " + url.getProtocol());
        }

        Path addOn = getTemporaryDir().toPath().resolve(extractFileName(urlString));
        if (Files.exists(addOn)) {
            getLogger().info("Add-on already exists, skipping download.");
            return addOn;
        }

        try (InputStream in = url.openStream()) {
            Files.copy(in, addOn);
        } catch (IOException e) {
            throw new IOException("Failed to download the add-on: " + e.getMessage(), e);
        }
        getLogger().info("Add-on downloaded to: " + addOn);
        return addOn;
    }

    private static String extractFileName(String url) {
        int idx = url.lastIndexOf("/");
        if (idx == -1) {
            throw new IllegalArgumentException(
                    "The provided URL does not have a file name: " + url);
        }
        String fileName = url.substring(idx + 1);
        if (!fileName.endsWith(ADD_ON_EXTENSION)) {
            throw new IllegalArgumentException(
                    "The provided URL does not have a file with zap extension: " + fileName);
        }
        return fileName;
    }

    private static String extractAddOnId(String fileName) {
        return fileName.substring(0, fileName.indexOf('.')).split("-")[0];
    }

    private static class AddOnEntry implements Comparable<AddOnEntry> {

        private final String addOnId;
        private final HierarchicalConfiguration data;

        public AddOnEntry(String addOnId, HierarchicalConfiguration data) {
            this.addOnId = addOnId;
            this.data = data;
        }

        public String getAddOnId() {
            return addOnId;
        }

        public HierarchicalConfiguration getData() {
            return data;
        }

        @Override
        public int compareTo(AddOnEntry other) {
            return addOnId.compareTo(other.getAddOnId());
        }
    }

    private static class AddOnConfBuilder {

        private static final String NAME_ELEMENT = "name";
        private static final String VERSION_ELEMENT = "version";
        private static final String SEM_VER_ELEMENT = "semver";
        private static final String DESCRIPTION_ELEMENT = "description";
        private static final String AUTHOR_ELEMENT = "author";
        private static final String URL_ELEMENT = "url";
        private static final String CHANGES_ELEMENT = "changes";
        private static final String NOT_BEFORE_VERSION_ELEMENT = "not-before-version";
        private static final String NOT_FROM_VERSION_ELEMENT = "not-from-version";

        private static final String DEPENDENCIES_ELEMENT = "dependencies";
        private static final String DEPENDENCIES_JAVA_VERSION_ELEMENT = "javaversion";
        private static final String DEPENDENCIES_ADDONS_ALL_ELEMENTS = "addons/addon";
        private static final String DEPENDENCIES_ADDONS_ALL_ELEMENTS_NO_XPATH = "addons.addon";
        private static final String ZAPADDON_ID_ELEMENT = "id";
        private static final String ZAPADDON_NOT_BEFORE_VERSION_ELEMENT = "not-before-version";
        private static final String ZAPADDON_NOT_FROM_VERSION_ELEMENT = "not-from-version";
        private static final String ZAPADDON_SEMVER_ELEMENT = "semver";

        private static final String FILE_ELEMENT = "file";
        private static final String STATUS_ELEMENT = "status";
        private static final String HASH_ELEMENT = "hash";
        private static final String INFO_ELEMENT = "info";
        private static final String SIZE_ELEMENT = "size";
        private static final String DATE_ELEMENT = "date";

        private final Path addOn;
        private final String downloadUrl;
        private final String checksumAlgorithm;
        private final LocalDate releaseDate;

        public AddOnConfBuilder(
                Path addOn, String downloadUrl, String checksumAlgorithm, LocalDate releaseDate) {
            this.addOn = addOn;
            this.downloadUrl = downloadUrl;
            this.checksumAlgorithm = checksumAlgorithm;
            this.releaseDate = releaseDate;
        }

        public HierarchicalConfiguration build() throws IOException {
            XMLConfiguration manifest = new XMLConfiguration();
            manifest.setEncoding("UTF-8");
            manifest.setDelimiterParsingDisabled(true);
            manifest.setExpressionEngine(new XPathExpressionEngine());

            try (ZipFile addOnZip = new ZipFile(addOn.toFile())) {
                ZipEntry manifestEntry = addOnZip.getEntry(ADD_ON_MANIFEST_FILE_NAME);
                if (manifestEntry == null) {
                    throw new IllegalArgumentException(
                            "The specified add-on does not have the manifest: " + addOn);
                }
                try (InputStream is = addOnZip.getInputStream(manifestEntry)) {
                    manifest.load(is);
                } catch (ConfigurationException e) {
                    throw new IOException(
                            "Failed to parse the manifest from the add-on: " + e.getMessage(), e);
                }
            }

            HierarchicalConfiguration configuration = new HierarchicalConfiguration();
            configuration.setDelimiterParsingDisabled(true);
            append(NAME_ELEMENT, manifest, configuration);
            append(DESCRIPTION_ELEMENT, manifest, configuration);
            append(AUTHOR_ELEMENT, manifest, configuration);
            append(VERSION_ELEMENT, manifest, configuration);
            append(SEM_VER_ELEMENT, manifest, configuration);
            appendIfNotEmpty(addOn.getFileName().toString(), configuration, FILE_ELEMENT);
            appendIfNotEmpty(manifest.getString("status", "alpha"), configuration, STATUS_ELEMENT);
            append(CHANGES_ELEMENT, manifest, configuration);
            appendIfNotEmpty(downloadUrl, configuration, URL_ELEMENT);
            appendIfNotEmpty(createChecksum(checksumAlgorithm, addOn), configuration, HASH_ELEMENT);
            append(URL_ELEMENT, manifest, INFO_ELEMENT, configuration);
            appendIfNotEmpty(releaseDate.toString(), configuration, DATE_ELEMENT);
            appendIfNotEmpty(String.valueOf(Files.size(addOn)), configuration, SIZE_ELEMENT);
            append(NOT_BEFORE_VERSION_ELEMENT, manifest, configuration);
            append(NOT_FROM_VERSION_ELEMENT, manifest, configuration);
            appendDependencies(manifest, configuration);
            return configuration;
        }

        private static String createChecksum(String algorithm, Path addOn) throws IOException {
            return algorithm + ":" + new DigestUtils(algorithm).digestAsHex(addOn.toFile());
        }

        private void append(
                String nameElement, HierarchicalConfiguration from, HierarchicalConfiguration to) {
            append(nameElement, from, nameElement, to);
        }

        private void append(
                String nameElement,
                HierarchicalConfiguration from,
                String toNameElement,
                HierarchicalConfiguration to) {
            String value = from.getString(nameElement, "");
            appendIfNotEmpty(value, to, toNameElement);
        }

        private void appendIfNotEmpty(String value, HierarchicalConfiguration to, String key) {
            if (!value.isEmpty()) {
                to.setProperty(key, value);
            }
        }

        private void appendDependencies(
                HierarchicalConfiguration from, HierarchicalConfiguration to) {
            List<HierarchicalConfiguration> dependencies =
                    from.configurationsAt(DEPENDENCIES_ELEMENT);
            if (dependencies.isEmpty()) {
                return;
            }

            HierarchicalConfiguration node = dependencies.get(0);

            appendIfNotEmpty(
                    node.getString(DEPENDENCIES_JAVA_VERSION_ELEMENT, ""),
                    to,
                    DEPENDENCIES_ELEMENT + "." + DEPENDENCIES_JAVA_VERSION_ELEMENT);

            List<HierarchicalConfiguration> fields =
                    node.configurationsAt(DEPENDENCIES_ADDONS_ALL_ELEMENTS);
            if (fields.isEmpty()) {
                return;
            }

            for (int i = 0, size = fields.size(); i < size; ++i) {
                String elementBaseKey =
                        DEPENDENCIES_ELEMENT
                                + "."
                                + DEPENDENCIES_ADDONS_ALL_ELEMENTS_NO_XPATH
                                + "("
                                + i
                                + ").";

                HierarchicalConfiguration sub = fields.get(i);

                appendIfNotEmpty(
                        sub.getString(ZAPADDON_ID_ELEMENT, ""),
                        to,
                        elementBaseKey + ZAPADDON_ID_ELEMENT);
                appendIfNotEmpty(
                        sub.getString(ZAPADDON_SEMVER_ELEMENT, ""),
                        to,
                        elementBaseKey + ZAPADDON_SEMVER_ELEMENT);
                appendIfNotEmpty(
                        sub.getString(ZAPADDON_NOT_BEFORE_VERSION_ELEMENT, ""),
                        to,
                        elementBaseKey + ZAPADDON_NOT_BEFORE_VERSION_ELEMENT);
                appendIfNotEmpty(
                        sub.getString(ZAPADDON_NOT_FROM_VERSION_ELEMENT, ""),
                        to,
                        elementBaseKey + ZAPADDON_NOT_FROM_VERSION_ELEMENT);
            }
        }
    }
}
