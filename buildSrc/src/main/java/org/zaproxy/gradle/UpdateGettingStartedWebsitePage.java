/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2024 The ZAP Development Team
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
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class UpdateGettingStartedWebsitePage extends DefaultTask {

    @Input
    public abstract Property<Optional<File>> getAddOn();

    @Input
    public abstract Property<String> getFilenameRegex();

    @Input
    public abstract Property<File> getGettingStartedPage();

    @Input
    public abstract Property<File> getPdfDirectory();

    @TaskAction
    void executeTasks() throws Exception {
        Optional<File> optionalAddOn = getAddOn().get();
        if (optionalAddOn.isEmpty()) {
            return;
        }

        File addOn = optionalAddOn.get();
        Pattern filenamePattern = Pattern.compile(getFilenameRegex().get());
        String filename = null;

        try (ZipFile zip = new ZipFile(addOn)) {
            boolean found = false;
            for (Iterator<? extends ZipEntry> it = zip.entries().asIterator();
                    it.hasNext() && !found; ) {
                ZipEntry entry = it.next();
                if (!filenamePattern.matcher(entry.getName()).find()) {
                    continue;
                }

                found = true;
                filename = extractFilename(entry.getName());
                try (InputStream zis = zip.getInputStream(entry)) {
                    Path target = getPdfDirectory().get().toPath().resolve(filename);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        if (filename == null) {
            throw new IOException(
                    "No file matching the provided filename pattern was found in the add-on.");
        }

        updateFilename(filename, filenamePattern, getGettingStartedPage().get().toPath());
    }

    private static String extractFilename(String path) {
        return path.substring(path.indexOf('/') + 1);
    }

    private static void updateFilename(String filename, Pattern filenamePattern, Path file)
            throws IOException {
        String contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Matcher matcher = filenamePattern.matcher(contents);
        if (!matcher.find()) {
            throw new IOException("The filename pattern was not found in: " + file);
        }
        StringBuilder sb = new StringBuilder();
        matcher.appendReplacement(sb, filename);
        matcher.appendTail(sb);
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
