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

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/** A task that creates a pull request updating the website with the generated data. */
public abstract class UpdateWebsite extends DefaultTask {

    private static final String GITHUB_BASE_URL = "https://github.com/";

    private static final String GIT_REMOTE_ORIGIN = "origin";

    private static final String DEFAULT_GIT_BASE_BRANCH_NAME = "master";
    private static final String DEFAULT_GIT_BRANCH_NAME = "update-data";

    private final Property<String> gitBaseBranchName;
    private final Property<String> gitBranchName;

    public UpdateWebsite() {
        ObjectFactory objects = getProject().getObjects();

        this.gitBaseBranchName =
                objects.property(String.class).convention(DEFAULT_GIT_BASE_BRANCH_NAME);
        this.gitBranchName = objects.property(String.class).convention(DEFAULT_GIT_BRANCH_NAME);

        setGroup("ZAP");
        setDescription("Creates a pull request updating the website with the generated data.");
    }

    @Internal
    public Property<String> getGitBaseBranchName() {
        return gitBaseBranchName;
    }

    @Internal
    public Property<String> getGitBranchName() {
        return gitBranchName;
    }

    @Internal
    public abstract Property<GitHubUser> getGitHubUser();

    @Internal
    public abstract Property<GitHubRepo> getGitHubRepo();

    @Internal
    public abstract Property<GitHubRepo> getGitHubSourceRepo();

    @TaskAction
    public void update() throws Exception {
        GitHubRepo repo = getGitHubRepo().get();
        Repository repository =
                new FileRepositoryBuilder().setGitDir(new File(repo.getDir(), ".git")).build();
        try (Git git = new Git(repository)) {
            if (git.status().call().getModified().isEmpty()) {
                return;
            }

            GitHubUser user = getGitHubUser().get();
            URIish originUri = new URIish(GITHUB_BASE_URL + user.getName() + "/" + repo.getName());
            git.remoteSetUrl().setRemoteName(GIT_REMOTE_ORIGIN).setRemoteUri(originUri).call();

            git.checkout()
                    .setCreateBranch(true)
                    .setName(gitBranchName.get())
                    .setStartPoint(GIT_REMOTE_ORIGIN + "/" + gitBaseBranchName.get())
                    .call();

            String sourceCommitSha = getHeadCommit(getProject().getRootDir());

            String commitSummary = "Update data";
            String commitDescription =
                    "From:\n"
                            + repo.getOwner()
                            + "/"
                            + getGitHubSourceRepo().get().getName()
                            + "@"
                            + sourceCommitSha;

            PersonIdent personIdent = new PersonIdent(user.getName(), user.getEmail());
            git.commit()
                    .setAll(true)
                    .setSign(false)
                    .setAuthor(personIdent)
                    .setCommitter(personIdent)
                    .setMessage(commitSummary + "\n\n" + commitDescription + signedOffBy(user))
                    .call();

            git.push()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(
                                    user.getName(), user.getAuthToken()))
                    .setForce(true)
                    .add(gitBranchName.get())
                    .call();

            GHRepository ghRepo =
                    GitHub.connect(user.getName(), user.getAuthToken())
                            .getRepository(repo.toString());

            List<GHPullRequest> pulls =
                    ghRepo.queryPullRequests()
                            .base(gitBaseBranchName.get())
                            .head(user.getName() + ":" + gitBranchName.get())
                            .state(GHIssueState.OPEN)
                            .list()
                            .asList();
            if (pulls.isEmpty()) {
                createPullRequest(user, ghRepo, commitSummary, commitDescription);
            } else {
                pulls.get(0).setBody(commitDescription);
            }
        }
    }

    private static String signedOffBy(GitHubUser user) {
        return "\n\nSigned-off-by: " + user.getName() + " <" + user.getEmail() + ">";
    }

    private void createPullRequest(
            GitHubUser user, GHRepository ghRepo, String title, String description)
            throws IOException {
        ghRepo.createPullRequest(
                title,
                user.getName() + ":" + gitBranchName.get(),
                gitBaseBranchName.get(),
                description);
    }

    private static String getHeadCommit(File repoDir) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .build()
                .exactRef("HEAD")
                .getObjectId()
                .getName();
    }
}
