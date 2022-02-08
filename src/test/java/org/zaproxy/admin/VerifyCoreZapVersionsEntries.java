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
package org.zaproxy.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.configuration.tree.ConfigurationNodeVisitor;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/**
 * Verifies that the core entry of the {@code ZapVersions.xml} files have the expected structure and
 * that all have the same values.
 */
public class VerifyCoreZapVersionsEntries {

    private static final String[] EXPECTED_ELEMENTS = {
        "ZAP.core",
        "ZAP.core.version",
        "ZAP.core.daily-version",
        "ZAP.core.daily",
        "ZAP.core.daily.url",
        "ZAP.core.daily.file",
        "ZAP.core.daily.hash",
        "ZAP.core.daily.size",
        "ZAP.core.windows32",
        "ZAP.core.windows32.url",
        "ZAP.core.windows32.file",
        "ZAP.core.windows32.hash",
        "ZAP.core.windows32.size",
        "ZAP.core.windows",
        "ZAP.core.windows.url",
        "ZAP.core.windows.file",
        "ZAP.core.windows.hash",
        "ZAP.core.windows.size",
        "ZAP.core.linux",
        "ZAP.core.linux.url",
        "ZAP.core.linux.file",
        "ZAP.core.linux.hash",
        "ZAP.core.linux.size",
        "ZAP.core.mac",
        "ZAP.core.mac.url",
        "ZAP.core.mac.file",
        "ZAP.core.mac.hash",
        "ZAP.core.mac.size",
        "ZAP.core.relnotes",
        "ZAP.core.relnotes-url"
    };

    private static List<Path> zapVersionsfiles;

    private Optional<List<Element>> previousElements = Optional.empty();

    @BeforeAll
    public static void suppressLogging() throws Exception {
        Logger.getRootLogger().addAppender(new NullAppender());

        readZapVersionsFiles();
    }

    @ParameterizedTest
    @MethodSource("readZapVersionsFiles")
    public void shouldBeTheSameCoreDataInAllZapVersions(Path zapVersionsFile) throws Exception {
        // Given / When
        List<Element> elements = elements(zapVersionsFile);
        // Then
        assertThat(elements)
                .extracting(Element::getName)
                .as("Elements of %s", zapVersionsFile.getFileName())
                .containsExactly(EXPECTED_ELEMENTS);
        if (previousElements.isPresent()) {
            assertThat(previousElements.get())
                    .as("Values of %s", zapVersionsFile.getFileName())
                    .isEqualTo(elements);
        } else {
            previousElements = Optional.of(elements);
        }
    }

    private static List<Element> elements(Path zapVersionsFile) throws Exception {
        List<Element> elements = new ArrayList<>();
        new ZapXmlConfiguration(zapVersionsFile.toFile())
                .configurationAt("core")
                .getRootNode()
                .visit(
                        new ConfigurationNodeVisitor() {

                            @Override
                            public void visitBeforeChildren(ConfigurationNode node) {
                                elements.add(
                                        Element.of(
                                                getHierarchicalName(node),
                                                Objects.toString(node.getValue())));
                            }

                            @Override
                            public void visitAfterChildren(ConfigurationNode node) {
                                // Nothing to do.
                            }

                            @Override
                            public boolean terminate() {
                                return false;
                            }
                        });

        return elements;
    }

    private static String getHierarchicalName(ConfigurationNode node) {
        if (node.getParentNode() == null) {
            return node.getName();
        }
        return getHierarchicalName(node.getParentNode()) + "." + node.getName();
    }

    static Stream<Path> readZapVersionsFiles() throws Exception {
        Optional<String> path =
                Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                        .filter(e -> e.endsWith("/ZapVersionsTests"))
                        .findFirst();
        assertThat(path)
                .as("The ZapVersionsTests directory was not found on the classpath.")
                .isPresent();

        zapVersionsfiles = new ArrayList<>();
        Files.walkFileTree(
                Paths.get(path.get()),
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String fileName = file.getFileName().toString();
                        if (fileName.startsWith("ZapVersions") && fileName.endsWith(".xml")) {
                            zapVersionsfiles.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

        Collections.sort(zapVersionsfiles);
        return zapVersionsfiles.stream();
    }

    private static class Element {

        private final String name;
        private final String value;

        private Element(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Element other = (Element) obj;
            if (!Objects.equals(name, other.name)) {
                return false;
            }
            if (!Objects.equals(value, other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Name: " + name + "\tValue: " + value;
        }

        public static Element of(String name, String value) {
            return new Element(name, value);
        }
    }
}
