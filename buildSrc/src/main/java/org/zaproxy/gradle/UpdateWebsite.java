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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/** A task that creates a pull request updating the website with the generated data. */
public class UpdateWebsite extends DefaultTask {

    private static final String GITHUB_BASE_URL = "https://github.com/";

    private static final String GIT_REMOTE_ORIGIN = "origin";

    private static final String DEFAULT_GIT_BASE_BRANCH_NAME = "master";
    private static final String DEFAULT_GIT_BRANCH_NAME = "update-data";

    private final RegularFileProperty websiteRepo;

    private final Property<String> gitBaseBranchName;
    private final Property<String> gitBranchName;

    private final Property<String> ghUserName;
    private final Property<String> ghUserEmail;
    private final Property<String> ghUserAuthToken;

    private final Property<String> ghBaseUserName;
    private final Property<String> ghBaseRepo;
    private final Property<String> ghSourceRepo;

    public UpdateWebsite() {
        ObjectFactory objects = getProject().getObjects();

        this.websiteRepo = objects.fileProperty();

        this.gitBaseBranchName =
                objects.property(String.class).convention(DEFAULT_GIT_BASE_BRANCH_NAME);
        this.gitBranchName = objects.property(String.class).convention(DEFAULT_GIT_BRANCH_NAME);

        this.ghUserName = objects.property(String.class);
        this.ghUserEmail = objects.property(String.class);
        this.ghUserAuthToken = objects.property(String.class);

        this.ghBaseUserName = objects.property(String.class);
        this.ghBaseRepo = objects.property(String.class);
        this.ghSourceRepo = objects.property(String.class);

        setGroup("ZAP");
        setDescription("Creates a pull request updating the website with the generated data.");
    }

    @Internal
    public RegularFileProperty getWebsiteRepo() {
        return websiteRepo;
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
    public Property<String> getGhUserName() {
        return ghUserName;
    }

    @Internal
    public Property<String> getGhUserEmail() {
        return ghUserEmail;
    }

    @Internal
    public Property<String> getGhUserAuthToken() {
        return ghUserAuthToken;
    }

    @Internal
    public Property<String> getGhBaseUserName() {
        return ghBaseUserName;
    }

    @Internal
    public Property<String> getGhBaseRepo() {
        return ghBaseRepo;
    }

    @Internal
    public Property<String> getGhSourceRepo() {
        return ghSourceRepo;
    }

    @TaskAction
    public void update() throws Exception {
        Repository repository =
                new FileRepositoryBuilder()
                        .setGitDir(new File(websiteRepo.get().getAsFile(), ".git"))
                        .build();
        try (Git git = new Git(repository)) {
            if (git.status().call().getModified().isEmpty()) {
                return;
            }

            URIish originUri =
                    new URIish(GITHUB_BASE_URL + ghUserName.get() + "/" + ghBaseRepo.get());
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
                            + ghBaseUserName.get()
                            + "/"
                            + ghSourceRepo.get()
                            + "@"
                            + sourceCommitSha;

            PersonIdent personIdent = new PersonIdent(ghUserName.get(), ghUserEmail.get());
            git.commit()
                    .setAll(true)
                    .setSign(false)
                    .setAuthor(personIdent)
                    .setCommitter(personIdent)
                    .setMessage(commitSummary + "\n\n" + commitDescription + signedOffBy())
                    .call();

            git.push()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(
                                    ghUserName.get(), ghUserAuthToken.get()))
                    .setForce(true)
                    .add(gitBranchName.get())
                    .call();

            createPullRequest(commitSummary, commitDescription);
        }
    }

    private String signedOffBy() {
        return "\n\nSigned-off-by: " + ghUserName.get() + " <" + ghUserEmail.get() + ">";
    }

    private void createPullRequest(String title, String description) throws IOException {
        GHRepository ghRepo =
                GitHub.connect(ghUserName.get(), ghUserAuthToken.get())
                        .getRepository(ghBaseUserName.get() + "/" + ghBaseRepo.get());
        ghRepo.createPullRequest(
                title,
                ghUserName.get() + ":" + gitBranchName.get(),
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
