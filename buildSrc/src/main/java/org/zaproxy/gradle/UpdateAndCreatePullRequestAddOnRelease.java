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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.options.Option;

public abstract class UpdateAndCreatePullRequestAddOnRelease extends CreatePullRequest
        implements AddOnZapVersionsUpdater {

    private final Property<String> envVar;
    private StringBuilder commitDescription;

    public UpdateAndCreatePullRequestAddOnRelease() {
        this.envVar = getProject().getObjects().property(String.class);
        this.commitDescription = new StringBuilder();

        getCommitSummary().set("Release add-on(s)");
        getCommitDescription().set(getProject().provider(commitDescription::toString));

        setDescription(
                "Updates ZapVersions and creates a pull request to release an add-on (or several).");
    }

    @Option(
            option = "envVar",
            description = "The name of the env var that has the add-on release data.")
    public void setEnvVar(String name) {
        getEnvVar().set(name);
    }

    @Input
    public Property<String> getEnvVar() {
        return envVar;
    }

    @Override
    public void pullRequest() throws Exception {
        commitDescription.append("Release the following add-ons:");

        List<String> releasedAddOns = new ArrayList<>();
        String data = System.getenv(getEnvVar().get());
        for (AddOnReleaseData.Release release : AddOnReleaseData.read(data).getAddOns()) {
            String downloadUrl = release.getUrl();

            Path addOn = downloadAddOn(this, downloadUrl);
            calculateChecksum(addOn, release.getChecksum());
            updateAddOn(
                    addOn,
                    downloadUrl,
                    LocalDate.now(),
                    addOnEntry ->
                            releasedAddOns.add(
                                    "\n - "
                                            + addOnEntry.getName()
                                            + " version "
                                            + addOnEntry.getVersion()));
        }
        releasedAddOns.stream().sorted().forEach(commitDescription::append);

        super.pullRequest();
    }
}
