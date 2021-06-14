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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

interface AddOnZapVersionsUpdater extends UpdateZapVersionsEntries {

    String ADD_ON_ELEMENT = "addon";
    String ADD_ON_NODE_PREFIX = "addon_";

    String ADD_ON_MANIFEST_FILE_NAME = "ZapAddOn.xml";

    default void updateAddOn(Path addOn, String downloadUrl, LocalDate releaseDate)
            throws Exception {
        updateAddOn(addOn, downloadUrl, releaseDate, e -> {});
    }

    default void updateAddOn(
            Path addOn,
            String downloadUrl,
            LocalDate releaseDate,
            Consumer<AddOnEntry> addOnEntryConsumer)
            throws Exception {
        String addOnId = extractAddOnId(addOn.getFileName().toString());
        AddOnEntry addOnEntry =
                new AddOnEntry(
                        addOnId,
                        new AddOnConfBuilder(
                                        addOn,
                                        downloadUrl,
                                        releaseDate,
                                        createChecksumString(addOn))
                                .build());

        addOnEntryConsumer.accept(addOnEntry);

        updateZapVersionsFiles(
                zapVersionsXml -> {
                    SortedSet<AddOnEntry> addOns = new TreeSet<>();
                    Arrays.stream(zapVersionsXml.getStringArray(ADD_ON_ELEMENT))
                            .filter(id -> !addOnId.equals(id))
                            .forEach(
                                    id -> {
                                        String key = ADD_ON_NODE_PREFIX + id;
                                        addOns.add(
                                                new AddOnEntry(
                                                        id, zapVersionsXml.configurationAt(key)));
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
                });
    }

    static String extractAddOnId(String fileName) {
        return fileName.substring(0, fileName.indexOf('.')).split("-")[0];
    }

    class AddOnEntry implements Comparable<AddOnEntry> {

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

        public String getName() {
            return data.getString(AddOnConfBuilder.NAME_ELEMENT);
        }

        public String getVersion() {
            return data.getString(AddOnConfBuilder.VERSION_ELEMENT);
        }

        @Override
        public int compareTo(AddOnEntry other) {
            return addOnId.compareTo(other.getAddOnId());
        }
    }

    class AddOnConfBuilder {

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
        private static final String ZAPADDON_VERSION_ELEMENT = "version";
        private static final String ZAPADDON_NOT_BEFORE_VERSION_ELEMENT = "not-before-version";
        private static final String ZAPADDON_NOT_FROM_VERSION_ELEMENT = "not-from-version";
        private static final String ZAPADDON_SEMVER_ELEMENT = "semver";

        private static final String FILE_ELEMENT = "file";
        private static final String STATUS_ELEMENT = "status";
        private static final String HASH_ELEMENT = "hash";
        private static final String INFO_ELEMENT = "info";
        private static final String REPO_ELEMENT = "repo";
        private static final String SIZE_ELEMENT = "size";
        private static final String DATE_ELEMENT = "date";

        private final Path addOn;
        private final String downloadUrl;
        private final LocalDate releaseDate;
        private final String checksum;

        public AddOnConfBuilder(
                Path addOn, String downloadUrl, LocalDate releaseDate, String checksum) {
            this.addOn = addOn;
            this.downloadUrl = downloadUrl;
            this.releaseDate = releaseDate;
            this.checksum = checksum;
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
            appendIfNotEmpty(checksum, configuration, HASH_ELEMENT);
            append(URL_ELEMENT, manifest, INFO_ELEMENT, configuration);
            append(REPO_ELEMENT, manifest, configuration);
            appendIfNotEmpty(releaseDate.toString(), configuration, DATE_ELEMENT);
            appendIfNotEmpty(String.valueOf(Files.size(addOn)), configuration, SIZE_ELEMENT);
            append(NOT_BEFORE_VERSION_ELEMENT, manifest, configuration);
            append(NOT_FROM_VERSION_ELEMENT, manifest, configuration);
            appendDependencies(manifest, configuration);
            return configuration;
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
                        sub.getString(ZAPADDON_VERSION_ELEMENT, ""),
                        to,
                        elementBaseKey + ZAPADDON_VERSION_ELEMENT);
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
