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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.zaproxy.gradle.ReleaseState.VersionChange;

/** Task that updates the ZAP version in files. */
public abstract class UpdateZapVersionWebsiteData extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @InputFiles
    public abstract ConfigurableFileCollection getDataFiles();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void update() {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        if (!isNewMainRelease(releaseState)) {
            return;
        }

        VersionChange mainRelease = releaseState.getMainRelease();
        String previousVersion = mainRelease.getPreviousVersion();
        String currentVersion = mainRelease.getCurrentVersion();

        String previousVersionNoPatch = removePatchVersion(previousVersion);
        String currentVersionNoPatch = removePatchVersion(currentVersion);

        WorkQueue workQueue = getWorkerExecutor().noIsolation();

        for (File file : getDataFiles()) {
            workQueue.submit(
                    ReplaceVersions.class,
                    parameters -> {
                        parameters.getFile().set(file);

                        parameters.getPreviousVersion().set(previousVersion);
                        parameters.getCurrentVersion().set(currentVersion);

                        parameters.getPreviousVersionNoPatch().set(previousVersionNoPatch);
                        parameters.getCurrentVersionNoPatch().set(currentVersionNoPatch);
                    });
        }
    }

    private static boolean isNewMainRelease(ReleaseState releaseState) {
        VersionChange mainRelease = releaseState.getMainRelease();
        return mainRelease != null && mainRelease.isNewVersion();
    }

    public interface ReplaceVersionsParameters extends WorkParameters {

        RegularFileProperty getFile();

        Property<String> getPreviousVersion();

        Property<String> getCurrentVersion();

        Property<String> getPreviousVersionNoPatch();

        Property<String> getCurrentVersionNoPatch();
    }

    public abstract static class ReplaceVersions implements WorkAction<ReplaceVersionsParameters> {

        @Override
        public void execute() {
            try {
                Path file = getParameters().getFile().getAsFile().get().toPath();
                String contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Files.write(
                        file,
                        contents.replace(
                                        getParameters().getPreviousVersion().get(),
                                        getParameters().getCurrentVersion().get())
                                .replace(
                                        getParameters().getPreviousVersionNoPatch().get(),
                                        getParameters().getCurrentVersionNoPatch().get())
                                .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String removePatchVersion(String version) {
        int idx = version.lastIndexOf('.');
        if (idx == -1) {
            return version;
        }
        return version.substring(0, idx);
    }
}
