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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/** Task that creates news file and entries for main release. */
public abstract class CreateNewsMainRelease extends DefaultTask {

    private static final String VERSION_TOKEN = "@@VERSION@@";
    private static final String VERSION_HYPHENS_TOKEN = "@@VERSION_HYPHENS@@";
    private static final String DATE_TOKEN = "@@DATE@@";

    private String currentVersion;
    private String currentVersionHyphens;
    private String date;

    public CreateNewsMainRelease() {
        setGroup("ZAP");
        setDescription("Creates news file and news entries for a main release.");
    }

    @Input
    public abstract Property<String> getPreviousVersion();

    @Option(option = "release", description = "The main release version.")
    public void setReleaseVersion(String version) {
        getVersion().set(version);
    }

    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<File> getNewsDir();

    @Input
    public abstract Property<String> getItem();

    @Input
    public abstract Property<String> getLink();

    @Input
    public abstract Property<String> getItemCurrent();

    @Input
    public abstract Property<String> getLinkCurrent();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void create() throws Exception {
        currentVersion = getVersion().get();
        currentVersionHyphens = currentVersion.replace('.', '-');
        date = LocalDate.now(ZoneId.of("Z")).toString();

        Path newsDir = getNewsDir().get().toPath();

        createNewsFileCurrentVersion(newsDir, currentVersion, getPreviousVersion().get());

        String item = replaceVars(getItem().get());
        String link = replaceVars(getLink().get());

        String itemLatest = replaceVars(getItemCurrent().get());
        String linkLatest = replaceVars(getLinkCurrent().get());
        WorkQueue workQueue = getWorkerExecutor().noIsolation();

        Path currentNewsFile = newsDir.resolve(convertToNewsFileName(currentVersion));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(newsDir)) {
            stream.forEach(
                    file -> {
                        if (currentNewsFile.equals(file)) {
                            workQueue.submit(
                                    CreateNewsEntry.class,
                                    parameters -> {
                                        parameters.getFile().set(file.toFile());
                                        parameters.getItem().set(itemLatest);
                                        parameters.getLink().set(linkLatest);
                                    });
                        } else {

                            workQueue.submit(
                                    CreateNewsEntry.class,
                                    parameters -> {
                                        parameters.getFile().set(file.toFile());
                                        parameters.getItem().set(item);
                                        parameters.getLink().set(link);
                                    });
                        }
                    });
        }
    }

    public interface CreateNewsEntryParameters extends WorkParameters {

        RegularFileProperty getFile();

        Property<String> getItem();

        Property<String> getLink();
    }

    public abstract static class CreateNewsEntry implements WorkAction<CreateNewsEntryParameters> {

        private static final String NEWS_KEY = "news.";
        private static final String NEWS_ID_KEY = NEWS_KEY + "id";
        private static final String NEWS_DEFAULT_KEY = NEWS_KEY + "default.";
        private static final String NEWS_DEFAULT_ITEM_KEY = NEWS_DEFAULT_KEY + "item";
        private static final String NEWS_DEFAULT_LINK_KEY = NEWS_DEFAULT_KEY + "link";

        @Override
        public void execute() {
            try {
                File file = getParameters().getFile().getAsFile().get();
                CustomXmlConfiguration news = new CustomXmlConfiguration(file);
                String id = newNewsId(news.getString(NEWS_ID_KEY, null));
                news.clear();
                news.setProperty(NEWS_ID_KEY, id);
                news.setProperty(NEWS_DEFAULT_ITEM_KEY, getParameters().getItem().get());
                news.setProperty(NEWS_DEFAULT_LINK_KEY, getParameters().getLink().get());
                news.save(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String newNewsId(String currentId) {
            if (currentId == null || currentId.isEmpty()) {
                return "1";
            }
            return String.valueOf(Integer.parseInt(currentId) + 1);
        }
    }

    private static void createNewsFileCurrentVersion(
            Path newsDir, String currentVersion, String previousVerion) throws IOException {
        Path currentNewsFile = newsDir.resolve(convertToNewsFileName(currentVersion));

        if (Files.exists(currentNewsFile)) {
            return;
        }

        Path previousNewsFile = newsDir.resolve(convertToNewsFileName(previousVerion));
        if (Files.exists(previousNewsFile)) {
            Files.copy(previousNewsFile, currentNewsFile);
        } else {
            Files.createFile(currentNewsFile);
        }
    }

    private String replaceVars(String string) {
        return string.replace(VERSION_TOKEN, currentVersion)
                .replace(VERSION_HYPHENS_TOKEN, currentVersionHyphens)
                .replace(DATE_TOKEN, date);
    }

    private static String convertToNewsFileName(String version) {
        int idx = version.lastIndexOf('.');
        return version.substring(0, idx).replace('.', '_') + ".xml";
    }
}
