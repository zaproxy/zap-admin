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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;

interface UpdateZapVersionsEntries {

    @InputFiles
    ConfigurableFileCollection getInto();

    @Input
    Property<String> getChecksumAlgorithm();

    default void updateZapVersionsFiles(Consumer<XMLConfiguration> consumer) throws Exception {
        if (getChecksumAlgorithm().get().isEmpty()) {
            throw new IllegalArgumentException("The checksum algorithm must not be empty.");
        }

        for (File zapVersionsFile : getInto()) {
            if (!Files.isRegularFile(zapVersionsFile.toPath())) {
                throw new IllegalArgumentException(
                        "The provided path is not a file: " + zapVersionsFile);
            }

            XMLConfiguration zapVersionsXml = new CustomXmlConfiguration();
            zapVersionsXml.load(zapVersionsFile);
            consumer.accept(zapVersionsXml);
            zapVersionsXml.save(zapVersionsFile);
        }
    }

    default String createChecksumString(Path file) throws IOException {
        return createChecksumString(file, null);
    }

    default String createChecksumString(Path file, String expectedChecksum) throws IOException {
        return getChecksumAlgorithm().get() + ":" + calculateChecksum(file, expectedChecksum);
    }

    default String calculateChecksum(Path file, String expectedChecksum) throws IOException {
        String checksum = new DigestUtils(getChecksumAlgorithm().get()).digestAsHex(file.toFile());
        if (expectedChecksum == null
                || expectedChecksum.isEmpty()
                || checksum.equals(expectedChecksum)) {
            return checksum;
        }

        throw new IllegalArgumentException(
                String.format(
                        "Checksums do not match for: %s\nExpected:\n%s\nActual:\n%s",
                        file, expectedChecksum, checksum));
    }
}
