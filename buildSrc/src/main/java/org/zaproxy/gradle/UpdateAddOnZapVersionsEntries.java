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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** A task that updates {@code ZapVersions.xml} files with an add-on. */
public abstract class UpdateAddOnZapVersionsEntries extends AbstractUpdateZapVersionsEntries
        implements AddOnZapVersionsUpdater {

    private final Property<String> fromUrl;
    private final Property<String> downloadUrl;
    private final Property<LocalDate> releaseDate;

    public UpdateAddOnZapVersionsEntries() {
        ObjectFactory objects = getProject().getObjects();
        this.fromUrl = objects.property(String.class);
        this.downloadUrl = objects.property(String.class);
        this.downloadUrl.set(fromUrl);
        this.releaseDate = objects.property(LocalDate.class);
        this.releaseDate.set(LocalDate.now());

        setDescription("Updates ZapVersions.xml files with an add-on.");
    }

    @Option(option = "file", description = "The file system path to the add-on.")
    public void setFile(String path) {
        getFromFile().set(getProject().file(path));
    }

    @Input
    @Optional
    public abstract RegularFileProperty getFromFile();

    @Option(option = "url", description = "The URL to the add-on.")
    public void setUrl(String url) {
        fromUrl.set(url);
    }

    @Input
    @Optional
    public Property<String> getFromUrl() {
        return fromUrl;
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
        if (getFromFile().isPresent()) {
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
            if (!TaskUtils.hasSecureScheme(url)) {
                throw new IllegalArgumentException(
                        "The provided download URL does not use HTTPS scheme: "
                                + url.getProtocol());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to parse the download URL: " + e.getMessage(), e);
        }

        updateAddOn(getAddOn(), getDownloadUrl().get(), getReleaseDate().get());
    }

    private Path getAddOn() throws IOException {
        if (getFromFile().isPresent()) {
            Path addOn = getFromFile().getAsFile().get().toPath();
            if (!Files.isRegularFile(addOn)) {
                throw new IllegalArgumentException(
                        "The provided path does not exist or it's not a file: " + addOn);
            }
            return addOn;
        }

        return TaskUtils.downloadAddOn(this, fromUrl.get());
    }
}
