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
package org.zaproxy.gradle.crowdin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.eclipse.jgit.api.Git;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.zaproxy.gradle.CreatePullRequestImpl;
import org.zaproxy.gradle.GitHubRepo;
import org.zaproxy.gradle.GitHubUser;

/** A task to deploy the resulting Crowdin translations into Git repositories. */
public abstract class DeployCrowdinTranslations extends DefaultTask {

    private static final String PACKAGES_DIR_TOKEN = "%packages_dir%";
    private static final String OWNER = "zaproxy";

    public DeployCrowdinTranslations() {
        DirectoryProperty buildDirectory = getProject().getLayout().getBuildDirectory();
        getTranslationsPackageDirectory()
                .convention(buildDirectory.dir("crowdinTranslationPackages"));
        getRepositoriesDirectory()
                .convention(buildDirectory.dir("deployCrowdinTranslationsRepos").get().getAsFile());
        getBaseBranchName().convention("main");
    }

    @InputFile
    public abstract RegularFileProperty getDeployConfiguration();

    @InputDirectory
    public abstract DirectoryProperty getTranslationsPackageDirectory();

    @Input
    public abstract Property<File> getRepositoriesDirectory();

    @Internal
    public abstract Property<String> getBaseBranchName();

    @Internal
    public abstract Property<String> getBranchName();

    @Internal
    public abstract Property<GitHubUser> getUser();

    @Internal
    public abstract Property<String> getCommitSummary();

    @Internal
    public abstract Property<String> getCommitDescription();

    @TaskAction
    void executeTasks() throws Exception {
        Path reposDir = getRepositoriesDirectory().get().toPath();
        String packagesDir =
                getTranslationsPackageDirectory()
                        .get()
                        .getAsFile()
                        .toPath()
                        .toAbsolutePath()
                        .toString();

        for (BuildEntry buildEntry : readBuildEntries()) {
            Path repoDir = reposDir.resolve(buildEntry.getRepo());
            GitHubRepo ghRepo = new GitHubRepo(OWNER, buildEntry.getRepo(), repoDir.toFile());

            getProject().mkdir(repoDir);
            String cloneUrl =
                    CreatePullRequestImpl.GITHUB_BASE_URL
                            + ghRepo.getOwner()
                            + "/"
                            + ghRepo.getName()
                            + ".git";
            Git.cloneRepository().setURI(cloneUrl).setDirectory(repoDir.toFile()).call();

            runTasks(repoDir, buildEntry.getTasks(), packagesDir);

            CreatePullRequestImpl.create(
                    ghRepo,
                    getUser().get(),
                    getBranchName().get(),
                    getBaseBranchName().get(),
                    getCommitSummary().get(),
                    getCommitDescription().get());
        }
    }

    private void runTasks(Path repoDir, List<BuildTask> tasks, String packagesDir) {
        for (BuildTask task : tasks) {
            List<String> execArgs = new ArrayList<>(2);
            execArgs.add(task.getName());
            if (task.getArgs() != null && !task.getArgs().isEmpty()) {
                task.getArgs().replaceAll(arg -> replacePackagesDirToken(arg, packagesDir));
                execArgs.addAll(task.getArgs());
            }
            runGradle(repoDir, execArgs);
        }
    }

    private static String replacePackagesDirToken(String arg, String packagesDir) {
        return arg.replace(PACKAGES_DIR_TOKEN, packagesDir);
    }

    private void runGradle(Path repoDir, List<String> args) {
        List<String> execArgs = new ArrayList<>();
        execArgs.add("-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m");
        execArgs.add("-q");
        execArgs.addAll(args);
        getProject()
                .exec(
                        spec -> {
                            spec.environment(System.getenv());
                            spec.setWorkingDir(repoDir);
                            spec.setExecutable(gradleWrapper());
                            spec.args(execArgs);
                        })
                .assertNormalExitValue();
    }

    private List<BuildEntry> readBuildEntries() throws IOException {
        File file = getDeployConfiguration().get().getAsFile();
        return new ObjectMapper(new YAMLFactory())
                .readValue(file, new TypeReference<List<BuildEntry>>() {});
    }

    private static String gradleWrapper() {
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            return "./gradlew";
        }
        return "gradlew.bat";
    }
}
