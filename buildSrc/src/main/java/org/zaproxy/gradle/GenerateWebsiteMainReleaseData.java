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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.zaproxy.gradle.ReleaseData.ReleaseFile;

/** A task that generates the main release data for the website. */
public class GenerateWebsiteMainReleaseData extends AbstractGenerateWebsiteReleaseData {

    private static final String CORE_VERSION_ELEMENT = "core.version";
    private static final String TAG_PREFIX = "v";

    private final Property<String> ghUserName;
    private final Property<String> ghUserAuthToken;

    private final Property<String> ghBaseUserName;
    private final Property<String> ghBaseRepo;

    public GenerateWebsiteMainReleaseData() {
        super("main");

        ObjectFactory objects = getProject().getObjects();
        this.ghUserName = objects.property(String.class);
        this.ghUserAuthToken = objects.property(String.class);
        this.ghBaseUserName = objects.property(String.class);
        this.ghBaseRepo = objects.property(String.class);

        // Execute always, in case release assets have changed.
        getOutputs().upToDateWhen(task -> false);
    }

    @Internal
    public Property<String> getGhUserName() {
        return ghUserName;
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

    @Override
    protected List<ReleaseFile> createReleaseFiles(XMLConfiguration zapVersionsXml)
            throws IOException {
        GHRepository repo =
                createGitHubConnection(ghUserName.getOrNull(), ghUserAuthToken.getOrNull())
                        .getRepository(ghBaseUserName.get() + "/" + ghBaseRepo.get());

        List<GHAsset> assets =
                repo.getReleaseByTagName(
                                TAG_PREFIX + zapVersionsXml.getString(CORE_VERSION_ELEMENT))
                        .getAssets();

        List<ReleaseFile> releaseFiles = new ArrayList<>();

        GHAsset asset = getAsset(assets, "windows.exe");
        releaseFiles.add(
                new ReleaseFile(
                        "Windows (64) Installer",
                        "win-64-i",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, "windows-x32.exe");
        releaseFiles.add(
                new ReleaseFile(
                        "Windows (32) Installer",
                        "win-32-i",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, "unix.sh");
        releaseFiles.add(
                new ReleaseFile(
                        "Linux Installer",
                        "nix-i",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, "Linux.tar.gz");
        releaseFiles.add(
                new ReleaseFile(
                        "Linux Package",
                        "nix-p",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, ".dmg");
        releaseFiles.add(
                new ReleaseFile(
                        "MacOS Installer",
                        "osx-i",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, "Crossplatform.zip");
        releaseFiles.add(
                new ReleaseFile(
                        "Cross Platform Package",
                        "cp-p",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        asset = getAsset(assets, "Core.zip");
        releaseFiles.add(
                new ReleaseFile(
                        "Core Cross Platform Package",
                        "cp-c",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        return releaseFiles;
    }

    private GHAsset getAsset(List<GHAsset> assets, String suffix) {
        return assets.stream().filter(asset -> asset.getName().endsWith(suffix)).findFirst().get();
    }

    private GitHub createGitHubConnection(String userName, String authToken) throws IOException {
        if (authToken == null || authToken.isEmpty()) {
            return GitHub.connectAnonymously();
        }
        return GitHub.connect(userName, authToken);
    }
}
