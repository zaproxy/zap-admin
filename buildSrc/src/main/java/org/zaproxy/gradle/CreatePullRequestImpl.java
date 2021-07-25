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
import java.util.List;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class CreatePullRequestImpl {

    public static final String GITHUB_BASE_URL = "https://github.com/";

    private static final String GIT_REMOTE_ORIGIN = "origin";

    public static void create(
            GitHubRepo ghRepo,
            GitHubUser ghUser,
            String branchName,
            String baseBranchName,
            String commitSummary,
            String commitDescription)
            throws Exception {
        Repository repository =
                new FileRepositoryBuilder().setGitDir(new File(ghRepo.getDir(), ".git")).build();
        try (Git git = new Git(repository)) {
            Status status = git.status().call();
            if (!status.hasUncommittedChanges() && status.getUntracked().isEmpty()) {
                return;
            }

            URIish originUri =
                    new URIish(GITHUB_BASE_URL + ghUser.getName() + "/" + ghRepo.getName());
            git.remoteSetUrl().setRemoteName(GIT_REMOTE_ORIGIN).setRemoteUri(originUri).call();

            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(GIT_REMOTE_ORIGIN + "/" + baseBranchName)
                    .call();

            if (!status.getUntracked().isEmpty()) {
                AddCommand add = git.add();
                status.getUntracked().forEach(add::addFilepattern);
                add.call();
            }

            PersonIdent personIdent = new PersonIdent(ghUser.getName(), ghUser.getEmail());
            git.commit()
                    .setAll(true)
                    .setSign(false)
                    .setAuthor(personIdent)
                    .setCommitter(personIdent)
                    .setMessage(
                            commitSummary + "\n\n" + commitDescription + signedOffBy(personIdent))
                    .call();

            git.push()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(
                                    ghUser.getName(), ghUser.getAuthToken()))
                    .setForce(true)
                    .add(branchName)
                    .call();

            GHRepository ghRepository =
                    GitHub.connect(ghUser.getName(), ghUser.getAuthToken())
                            .getRepository(ghRepo.toString());

            List<GHPullRequest> pulls =
                    ghRepository
                            .queryPullRequests()
                            .base(baseBranchName)
                            .head(ghUser.getName() + ":" + branchName)
                            .state(GHIssueState.OPEN)
                            .list()
                            .asList();
            if (pulls.isEmpty()) {
                ghRepository.createPullRequest(
                        commitSummary,
                        ghUser.getName() + ":" + branchName,
                        baseBranchName,
                        commitDescription);
            } else {
                pulls.get(0).setBody(commitDescription);
            }
        }
    }

    private static String signedOffBy(PersonIdent personIdent) {
        return "\n\nSigned-off-by: "
                + personIdent.getName()
                + " <"
                + personIdent.getEmailAddress()
                + ">";
    }
}
