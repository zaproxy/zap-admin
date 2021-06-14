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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.commons.configuration.ConfigurationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/**
 * A task that generates the release state of the last commit, allowing to know what was released
 * (if anything).
 */
public abstract class GenerateReleaseStateLastCommit extends DefaultTask {

    private static final String CORE_ELEMENT = "core.";
    private static final String MAIN_VERSION_ELEMENT = CORE_ELEMENT + "version";
    private static final String DAILY_VERSION_ELEMENT = CORE_ELEMENT + "daily-version";

    private static final String ADDON_ELEMENT = "addon";
    private static final String ADDON_VERSION_ELEMENT = "version";

    private static final String GIT_DIR = ".git";
    private static final String HEAD_REF = "HEAD";

    public GenerateReleaseStateLastCommit() {
        getGitDir().value(getProject().getLayout().getProjectDirectory().dir(GIT_DIR));
    }

    @InputDirectory
    public abstract DirectoryProperty getGitDir();

    @Input
    public abstract Property<String> getZapVersionsPath();

    @Input
    public abstract Property<String> getZapVersionsAddOnsPath();

    @OutputFile
    public abstract RegularFileProperty getReleaseState();

    @TaskAction
    void generate() {
        File gitDir = getGitDir().get().getAsFile();

        ReleaseState releaseState = new ReleaseState();
        readVersions(
                gitDir,
                getZapVersionsPath().get(),
                (previousVersions, currentVersions) -> {
                    updateState(
                            MAIN_VERSION_ELEMENT,
                            previousVersions,
                            currentVersions,
                            releaseState::setMainRelease);
                    updateState(
                            DAILY_VERSION_ELEMENT,
                            previousVersions,
                            currentVersions,
                            releaseState::setWeeklyRelease);
                });
        readVersions(
                gitDir,
                getZapVersionsAddOnsPath().get(),
                (previousVersions, currentVersions) -> {
                    updateAddOnsState(previousVersions, currentVersions, releaseState);
                });

        releaseState.write(getReleaseState().get().getAsFile());
    }

    private static void readVersions(
            File projectDir,
            String pathZapVersions,
            BiConsumer<ZapXmlConfiguration, ZapXmlConfiguration> versionsConsumer) {
        try (Repository repository = createRepository(projectDir);
                RevWalk walk = new RevWalk(repository)) {
            RevCommit headCommit = walk.parseCommit(getHead(repository).getObjectId());

            walk.markStart(headCommit);
            ZapXmlConfiguration currentVersions = null;
            Iterator<RevCommit> it = walk.iterator();
            if (it.hasNext()) {
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(walk.parseTree(it.next().getTree().getId()));
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilterGroup.createFromStrings(pathZapVersions));
                    if (treeWalk.next()) {
                        currentVersions =
                                createXmlConfiguration(repository, treeWalk.getObjectId(0));
                    }
                }
            }
            if (currentVersions == null) {
                throw new TaskException("File not found in the current commit: " + pathZapVersions);
            }

            RevCommit parent;
            if (isMergeCommit(headCommit)) {
                parent =
                        getCommonAncestor(
                                repository,
                                headCommit.getParent(0).getId(),
                                headCommit.getParent(1).getId());
            } else {
                parent = headCommit.getParent(0);
            }

            ZapXmlConfiguration previousVersions = currentVersions;
            Optional<DiffEntry> diffResult =
                    isFileChanged(repository, parent, headCommit, pathZapVersions);
            if (diffResult.isPresent()) {
                previousVersions =
                        createXmlConfiguration(
                                repository, diffResult.get().getOldId().toObjectId());
            }

            versionsConsumer.accept(previousVersions, currentVersions);
        } catch (IOException e) {
            throw new TaskException(
                    "An error occurred while using the Git repository: " + e.getMessage(), e);
        }
    }

    private static Repository createRepository(File projectDir) {
        try {
            return new FileRepositoryBuilder().setGitDir(projectDir).build();
        } catch (IOException e) {
            throw new TaskException("Failed to read the Git repository: " + e.getMessage(), e);
        }
    }

    private static Ref getHead(Repository repository) {
        Ref head;
        try {
            head = repository.findRef(HEAD_REF);
        } catch (IOException e) {
            throw new TaskException(
                    String.format(
                            "Failed to get the ref %s from the Git repository: %s",
                            HEAD_REF, e.getMessage()),
                    e);
        }
        if (head == null) {
            throw new TaskException(
                    String.format("No ref %s found in the Git repository.", HEAD_REF));
        }
        return head;
    }

    private static boolean isMergeCommit(RevCommit commit) {
        return commit.getParentCount() > 1;
    }

    private static ZapXmlConfiguration createXmlConfiguration(
            Repository repository, ObjectId objectId) {
        ZapXmlConfiguration config = new ZapXmlConfiguration();
        try {
            config.load(repository.open(objectId).openStream());
        } catch (ConfigurationException | IOException e) {
            throw new TaskException(
                    "Failed to read the file from the Git repository: " + e.getMessage(), e);
        }
        return config;
    }

    private static void updateState(
            String versionElement,
            ZapXmlConfiguration previousVersions,
            ZapXmlConfiguration currentVersions,
            Consumer<ReleaseState.VersionChange> consumer) {
        consumer.accept(
                new ReleaseState.VersionChange(
                        previousVersions.getString(versionElement),
                        currentVersions.getString(versionElement)));
    }

    private static void updateAddOnsState(
            ZapXmlConfiguration previousVersions,
            ZapXmlConfiguration currentVersions,
            ReleaseState releaseState) {
        if (previousVersions == null) {
            return;
        }

        List<ReleaseState.AddOnChange> addOns = new ArrayList<>();
        String[] addOnIds = currentVersions.getStringArray(ADDON_ELEMENT);
        for (String id : addOnIds) {
            String addOnKey = ADDON_ELEMENT + "_" + id;
            String currentVersion = getAddOnVersion(currentVersions, addOnKey);
            String previousVersion = getAddOnVersion(previousVersions, addOnKey);
            if (!currentVersion.equals(previousVersion)) {
                addOns.add(new ReleaseState.AddOnChange(id, previousVersion, currentVersion));
            }
        }
        releaseState.setAddOns(addOns);
    }

    private static String getAddOnVersion(ZapXmlConfiguration versions, String addOnKey) {
        return versions.getString(addOnKey + "." + ADDON_VERSION_ELEMENT, null);
    }

    private static RevCommit getCommonAncestor(
            Repository repository, ObjectId commitA, ObjectId commitB) {
        List<RevCommit> treeA = walkTree(repository, commitA, 50);
        List<RevCommit> treeB = walkTree(repository, commitB, 50);

        Set<RevCommit> common = new HashSet<>(treeA);
        for (RevCommit commit : treeB) {
            if (!common.add(commit)) {
                return commit;
            }
        }

        throw new TaskException("Common ancestor not found between " + commitA + " and " + commitB);
    }

    private static List<RevCommit> walkTree(Repository repository, ObjectId start, int count) {
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            revWalk.markStart(revWalk.parseCommit(start));
            for (RevCommit commit : revWalk) {
                commits.add(commit);
                if (commits.size() >= count) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new TaskException(
                    "An error occurred while traversing the commit tree: " + e.getMessage(), e);
        }
        return commits;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId)
            throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevTree tree = walk.parseTree(walk.parseCommit(objectId).getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            return treeParser;
        }
    }

    private static Optional<DiffEntry> isFileChanged(
            Repository repository, RevCommit commitA, RevCommit commitB, String filePath) {
        try (Git git = new Git(repository);
                ObjectReader reader = git.getRepository().newObjectReader()) {
            AbstractTreeIterator oldTree = prepareTreeParser(repository, commitA);
            AbstractTreeIterator newTree = prepareTreeParser(repository, commitB);
            return git.diff().setOldTree(oldTree).setNewTree(newTree).call().stream()
                    .filter(
                            e ->
                                    e.getChangeType() == DiffEntry.ChangeType.MODIFY
                                            && e.getNewPath().equals(filePath))
                    .findFirst();

        } catch (GitAPIException | IOException e) {
            throw new TaskException(
                    "An error occurred while diffing the commits: " + e.getMessage(), e);
        }
    }
}
