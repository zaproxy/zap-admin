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

import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/** A task that checks for modifications in a git repo, commits, and creates a pull request. */
public abstract class CreatePullRequest extends DefaultTask {

    private static final String DEFAULT_GIT_BASE_BRANCH_NAME = "master";

    private final Property<String> baseBranchName;

    public CreatePullRequest() {
        ObjectFactory objects = getProject().getObjects();
        this.baseBranchName =
                objects.property(String.class).convention(DEFAULT_GIT_BASE_BRANCH_NAME);

        setGroup("ZAP");
        setDescription("Creates a pull request with modifications done in a repo.");
    }

    @Internal
    public Property<String> getBaseBranchName() {
        return baseBranchName;
    }

    @Internal
    public abstract Property<String> getBranchName();

    @Internal
    public abstract Property<GitHubUser> getUser();

    @Internal
    public abstract Property<GitHubRepo> getRepo();

    @Internal
    public abstract Property<String> getCommitSummary();

    @Internal
    public abstract Property<String> getCommitDescription();

    @TaskAction
    public void pullRequest() throws Exception {
        CreatePullRequestImpl.create(
                getRepo().get(),
                getUser().get(),
                getBranchName().get(),
                baseBranchName.get(),
                getCommitSummary().get(),
                getCommitDescription().get());
    }
}
