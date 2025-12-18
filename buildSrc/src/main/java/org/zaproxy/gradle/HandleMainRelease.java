/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
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

import java.util.Map;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.zaproxy.gradle.ReleaseState.VersionChange;

/**
 * Task that handles a main release, if any.
 *
 * <p>Sends a repository dispatch to release the main and nightly Docker images.
 */
public abstract class HandleMainRelease extends SendRepositoryDispatch {

    @InputFile
    public abstract RegularFileProperty getReleaseState();

    @Input
    public abstract Property<String> getEventTypeNightly();

    @Override
    void send() {
        ReleaseState releaseState = ReleaseState.read(getReleaseState().getAsFile().get());
        if (isNewMainRelease(releaseState)) {
            super.send();
            sendRepositoryDispatch(getEventTypeNightly().get(), Map.of());
        }
    }

    private static boolean isNewMainRelease(ReleaseState releaseState) {
        VersionChange mainRelease = releaseState.getMainRelease();
        return mainRelease != null && mainRelease.isNewVersion();
    }
}
