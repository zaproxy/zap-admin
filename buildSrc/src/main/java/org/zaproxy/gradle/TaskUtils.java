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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;

final class TaskUtils {

    private static final String COMMENT_LINE = "#";

    private TaskUtils() {}

    private static final String HTTPS_SCHEME = "HTTPS";
    private static final String ADD_ON_EXTENSION = ".zap";

    static Path downloadAddOn(Task task, String urlString) throws Exception {
        return downloadAddOn(task, urlString, task.getTemporaryDir().toPath());
    }

    static Path downloadAddOn(Task task, String urlString, Path outputDir) throws Exception {
        return downloadFile(task, urlString, outputDir.resolve(extractFileName(urlString)));
    }

    static Path downloadFile(Task task, String urlString, Path outputFile) throws Exception {
        URL url = new URI(urlString).toURL();
        if (!HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException(
                    "The provided URL does not use HTTPS scheme: " + url.getProtocol());
        }

        if (Files.exists(outputFile)) {
            task.getLogger().info("File already exists at specified path, skipping download.");
            return outputFile;
        }

        try (InputStream in = url.openStream()) {
            Files.copy(in, outputFile);
        } catch (IOException e) {
            throw new IOException("Failed to download the file: " + e.getMessage(), e);
        }
        task.getLogger().info("File downloaded to: " + outputFile);
        return outputFile;
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

    static String calculateChecksum(Path file, String checksumAlgorithm, String expectedChecksum)
            throws IOException {
        String checksum = new DigestUtils(checksumAlgorithm).digestAsHex(file.toFile());
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

    static Set<String> readAllowedAddOns(RegularFileProperty fileProperty) throws IOException {
        Path file = fileProperty.getAsFile().get().toPath();
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(s -> !s.startsWith(COMMENT_LINE) || !s.isEmpty())
                .collect(Collectors.toSet());
    }

    static boolean hasSecureScheme(URL url) {
        if (url == null) {
            return false;
        }
        return HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol());
    }
}
