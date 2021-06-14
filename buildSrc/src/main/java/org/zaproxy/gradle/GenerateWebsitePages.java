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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.website.WebsitePageGenerator;
import org.zaproxy.zap.control.AddOn;

/** A task to generate website pages from the help of the add-on(s). */
public abstract class GenerateWebsitePages extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getAllowedAddOns();

    @InputFiles
    public abstract ConfigurableFileCollection getAddOns();

    @Input
    public abstract Property<String> getHelpAddOnRegex();

    @Input
    public abstract Property<String> getSiteUrl();

    @Input
    public abstract Property<String> getBaseUrlPath();

    @Input
    public abstract Property<String> getAddOnsDirName();

    @Input
    public abstract Property<String> getPageType();

    @Input
    public abstract Property<String> getRedirectPageType();

    @Input
    public abstract Property<String> getRedirectPageLayout();

    @Input
    public abstract Property<String> getSectionPageName();

    @Input
    public abstract Property<String> getImagesDirName();

    @Input
    public abstract Property<String> getNoticeGeneratedPage();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void generate() throws IOException {
        Set<String> allowedAddOns = TaskUtils.readAllowedAddOns(getAllowedAddOns());

        Path outputDir = getOutputDir().get().getAsFile().toPath();

        WebsitePageGenerator websitePageGenerator =
                new WebsitePageGenerator(
                        getSiteUrl().get(),
                        getBaseUrlPath().get(),
                        getHelpAddOnRegex().get(),
                        getAddOnsDirName().get(),
                        getPageType().get(),
                        getRedirectPageType().get(),
                        getRedirectPageLayout().get(),
                        getSectionPageName().get(),
                        getImagesDirName().get(),
                        getNoticeGeneratedPage().get());

        for (File addOnFile : getAddOns()) {
            AddOn addOn = createAddOn(addOnFile.toPath());
            if (addOn == null || !allowedAddOns.contains(addOn.getId())) {
                continue;
            }

            try {
                Path tempDir = getTemporaryDir().toPath().resolve(addOn.getId());
                getProject().delete(tempDir);
                Files.createDirectories(tempDir);

                if (websitePageGenerator.generate(addOn, tempDir)) {
                    copy(tempDir, outputDir);
                } else {
                    getLogger().lifecycle("No help found for add-on {}.", addOn.getId());
                }

            } catch (IOException e) {
                getLogger()
                        .error(
                                "Failed to copy generated pages for add-on {} Cause: {}",
                                addOn.getId(),
                                e.getMessage(),
                                e);
            } catch (Exception e) {
                getLogger()
                        .error(
                                "An error occurred while generating the pages for add-on {} Cause: {}",
                                addOn.getId(),
                                e.getMessage(),
                                e);
            }
        }
    }

    private AddOn createAddOn(Path addOnFile) {
        try {
            return new AddOn(addOnFile);
        } catch (IOException e) {
            getLogger()
                    .error(
                            "An error occurred while creating the add-on from {} Cause: {}",
                            addOnFile,
                            e.getMessage(),
                            e);
        }
        return null;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.walkFileTree(
                from,
                EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Path targetdir = to.resolve(from.relativize(dir));
                        try {
                            Files.copy(dir, targetdir);
                        } catch (FileAlreadyExistsException e) {
                            if (!Files.isDirectory(targetdir)) {
                                throw e;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Path destFile = to.resolve(from.relativize(file));
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
