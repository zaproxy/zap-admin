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
import java.util.NoSuchElementException;
import java.util.Optional;
import org.apache.commons.configuration.XMLConfiguration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.zaproxy.gradle.ReleaseData.ReleaseFile;

/** A task that generates the main release data for the website. */
public abstract class GenerateWebsiteMainReleaseData extends AbstractGenerateWebsiteReleaseData {

    private static final String CORE_VERSION_ELEMENT = "core.version";
    private static final String TAG_PREFIX = "v";

    public GenerateWebsiteMainReleaseData() {
        super("main");

        // Execute always, in case release assets have changed.
        getOutputs().upToDateWhen(task -> false);
    }

    @Internal
    public abstract Property<GitHubUser> getGitHubUser();

    @Internal
    public abstract Property<GitHubRepo> getGitHubRepo();

    @Override
    protected List<ReleaseFile> createReleaseFiles(XMLConfiguration zapVersionsXml)
            throws IOException {
        GitHubUser user = getGitHubUser().get();
        GHRepository repo =
                createGitHubConnection(user.getName(), user.getAuthToken())
                        .getRepository(getGitHubRepo().get().toString());

        String version = zapVersionsXml.getString(CORE_VERSION_ELEMENT);
        List<GHAsset> assets = repo.getReleaseByTagName(TAG_PREFIX + version).getAssets();

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

        asset = getAsset(assets, version + ".dmg");
        releaseFiles.add(
                new ReleaseFile(
                        "macOS (Intel - amd64) Installer",
                        "osx-i",
                        toMegaBytes(asset.getSize()),
                        asset.getBrowserDownloadUrl()));

        Optional<GHAsset> optionalAsset = getOptionalAsset(assets, "_aarch64.dmg");
        if (optionalAsset.isPresent()) {
            asset = optionalAsset.get();
            releaseFiles.add(
                    new ReleaseFile(
                            "macOS (Apple Silicon - aarch64) Installer",
                            "osx-aarch64-i",
                            toMegaBytes(asset.getSize()),
                            asset.getBrowserDownloadUrl()));
        }

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
        return getOptionalAsset(assets, suffix)
                .orElseThrow(() -> new NoSuchElementException("No asset with suffix: " + suffix));
    }

    private Optional<GHAsset> getOptionalAsset(List<GHAsset> assets, String suffix) {
        return assets.stream().filter(asset -> asset.getName().endsWith(suffix)).findFirst();
    }

    private GitHub createGitHubConnection(String userName, String authToken) throws IOException {
        if (authToken == null || authToken.isEmpty()) {
            return GitHub.connectAnonymously();
        }
        return GitHub.connect(userName, authToken);
    }
}
