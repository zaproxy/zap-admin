import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.zaproxy.gradle.CreateNewsMainRelease
import org.zaproxy.gradle.CreatePullRequest
import org.zaproxy.gradle.CustomXmlConfiguration
import org.zaproxy.gradle.DownloadReleasedAddOns
import org.zaproxy.gradle.GenerateReleaseStateLastCommit
import org.zaproxy.gradle.GenerateWebsiteAddonsData
import org.zaproxy.gradle.GenerateWebsiteMainReleaseData
import org.zaproxy.gradle.GenerateWebsitePages
import org.zaproxy.gradle.GenerateWebsiteSbomPages
import org.zaproxy.gradle.GenerateWebsiteWeeklyReleaseData
import org.zaproxy.gradle.GitHubRepo
import org.zaproxy.gradle.GitHubUser
import org.zaproxy.gradle.HandleMainRelease
import org.zaproxy.gradle.HandleWeeklyRelease
import org.zaproxy.gradle.UpdateAddOnZapVersionsEntries
import org.zaproxy.gradle.UpdateAndCreatePullRequestAddOnRelease
import org.zaproxy.gradle.UpdateDailyZapVersionsEntries
import org.zaproxy.gradle.UpdateFlathubData
import org.zaproxy.gradle.UpdateGettingStartedWebsitePage
import org.zaproxy.gradle.UpdateMainZapVersionsEntries
import org.zaproxy.gradle.UpdateZapMgmtScriptsData
import org.zaproxy.gradle.UpdateZapVersionWebsiteData
import org.zaproxy.gradle.crowdin.DeployCrowdinTranslations
import java.util.Optional

plugins {
    java
    id("com.diffplug.spotless")
    id("org.zaproxy.common")
    id("net.ltgt.errorprone") version "4.1.0"
    id("org.zaproxy.crowdin") version "0.6.0"
}

apply(from = "$rootDir/gradle/ci.gradle.kts")

crowdin {
    credentials {
        token.set(System.getenv("CROWDIN_AUTH_TOKEN"))
    }

    configuration {
        file.set(file("gradle/crowdin.yml"))
    }
}

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.36.0")

    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.kohsuke:github-api:1.326")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18") {
        exclude(group = "org.jenkins-ci")
    }
    compileOnly("com.github.spotbugs:spotbugs-annotations:3.1.12")
    implementation("net.sf.json-lib:json-lib:2.4:jdk15")
    implementation("org.zaproxy:zap:2.16.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

val zapVersionsDir = layout.buildDirectory.dir("ZapVersionsTests")
val copyZapVersions =
    tasks.register<Copy>("copyZapVersions") {
        from(rootDir)
        into(zapVersionsDir)
        include("ZapVersions*.xml")
    }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

sourceSets["test"].output.dir(mapOf("builtBy" to copyZapVersions), zapVersionsDir)

spotless {
    format("properties", { targetExclude("buildSrc/**", "gradle/**") })

    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
    }
}

val noAddOnsZapVersions = "ZapVersions.xml"
val devZapVersions = "ZapVersions-dev.xml"
val nameLatestZapVersions = "ZapVersions-2.17.xml"
val latestZapVersions = file(nameLatestZapVersions)

val ghUser = GitHubUser("zapbot", "12745184+zapbot@users.noreply.github.com", System.getenv("ZAPBOT_TOKEN"))
val adminRepo = GitHubRepo("zaproxy", "zap-admin", rootDir)

val addOnsZapVersions = files(devZapVersions, latestZapVersions)
val defaultChecksumAlgorithm = "SHA-256"

tasks {
    register<ZapTask>("generateReleaseNotes") {
        description = "Generates release notes."
        mainClass.set("org.zaproxy.admin.GenerateReleaseNotes")
    }

    register<ZapTask>("listDownloadCounts") {
        description = "Lists download counts."
        mainClass.set("org.zaproxy.admin.CountDownloads")
    }

    register<ZapTask>("pendingAddOnReleases") {
        description = "Reports the add-ons that are pending a release of new version."
        mainClass.set("org.zaproxy.admin.PendingAddOnReleases")
    }

    register<ZapTask>("generateHelpAddOn") {
        description = "Generates the basic help files for an add-on."
        mainClass.set("org.zaproxy.admin.HelpGenerator")
        standardInput = System.`in`
    }

    register<ZapTask>("checkLatestReleaseNotes") {
        description = "Checks the latest release notes do not contain issues from previous ones."
        mainClass.set("org.zaproxy.admin.CheckLatestReleaseNotes")
    }

    register<CreateNewsMainRelease>("createNewsMainRelease") {
        newsDir.set(File(rootDir, "files/news"))
        previousVersion.set(
            provider {
                val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
                zapVersionsXml.getString("core.version")
            },
        )

        item.set("ZAP @@VERSION@@ is available now")
        link.set("https://www.zaproxy.org/download/")
    }

    register<UpdateMainZapVersionsEntries>("updateMainRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/v@@VERSION@@/")
        windows32FileName.set("ZAP_@@VERSION_UNDERSCORES@@_windows-x32.exe")
        windows64FileName.set("ZAP_@@VERSION_UNDERSCORES@@_windows.exe")
        linuxFileName.set("ZAP_@@VERSION@@_Linux.tar.gz")
        macFileName.set("ZAP_@@VERSION@@.dmg")
        releaseNotes.set("Bug fix and enhancement release.")
        releaseNotesUrl.set("https://www.zaproxy.org/docs/desktop/releases/@@VERSION@@/")
        checksumAlgorithm.set(defaultChecksumAlgorithm)
    }

    register<CreatePullRequest>("createPullRequestMainRelease") {
        description = "Creates a pull request to update the main release."

        user.set(ghUser)
        repo.set(adminRepo)
        branchName.set("update-main-release")

        commitSummary.set("Update main release")
        commitDescription.set(
            provider {
                val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
                val mainVersion = zapVersionsXml.getString("core.version")
                "Update main release to version $mainVersion."
            },
        )
    }

    register<UpdateDailyZapVersionsEntries>("updateDailyRelease") {
        into.setFrom(
            fileTree(rootDir).matching {
                include(noAddOnsZapVersions, devZapVersions)
            },
        )
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/w")
        checksumAlgorithm.set(defaultChecksumAlgorithm)
    }

    register<CreatePullRequest>("createPullRequestDailyRelease") {
        description = "Creates a pull request to update the daily release."

        user.set(ghUser)
        repo.set(adminRepo)
        branchName.set("update-daily-release")

        commitSummary.set("Update weekly release")
        commitDescription.set(
            provider {
                val zapVersionsXml = CustomXmlConfiguration(file(noAddOnsZapVersions))
                val dailyVersion = zapVersionsXml.getString("core.daily-version")
                "Update weekly release to version $dailyVersion."
            },
        )
    }

    register<UpdateAddOnZapVersionsEntries>("updateAddOnRelease") {
        into.setFrom(addOnsZapVersions)
        checksumAlgorithm.set(defaultChecksumAlgorithm)
    }

    register<UpdateAndCreatePullRequestAddOnRelease>("updateAndCreatePullRequestAddOnRelease") {
        into.setFrom(addOnsZapVersions)
        checksumAlgorithm.set(defaultChecksumAlgorithm)

        user.set(ghUser)
        repo.set(adminRepo)
        branchName.set("add-on-release")
    }

    register<DeployCrowdinTranslations>("deployCrowdinTranslations") {
        deployConfiguration.set(file("src/main/crowdin-tasks.yml"))

        user.set(ghUser)
        branchName.set("crowdin-update")

        commitSummary.set("Update localized resources")
        commitDescription.set("Update resources from Crowdin.")
    }
}

val zaproxyRepo = GitHubRepo("zaproxy", "zaproxy", file("$rootDir/../zaproxy"))
val websiteGeneratedDataComment = "# This file is automatically updated by $adminRepo repo."

val generateWebsiteMainReleaseData by tasks.registering(GenerateWebsiteMainReleaseData::class) {
    zapVersions.set(latestZapVersions)
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(layout.buildDirectory.file("c_main_files.yml"))

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)
}

val generateWebsiteWeeklyReleaseData by tasks.registering(GenerateWebsiteWeeklyReleaseData::class) {
    zapVersions.set(file(noAddOnsZapVersions))
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(layout.buildDirectory.file("e_weekly_files.yml"))
}

val generateWebsiteAddonsData by tasks.registering(GenerateWebsiteAddonsData::class) {
    zapVersions.set(latestZapVersions)
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(layout.buildDirectory.file("addons.yaml"))
    websiteUrl.set("https://www.zaproxy.org/")
}

val websiteRepo = GitHubRepo("zaproxy", "zaproxy-website", file("$rootDir/../zaproxy-website"))
val siteDir = file("${websiteRepo.dir}/site")

val generateReleaseStateLastCommit by tasks.registering(GenerateReleaseStateLastCommit::class) {
    zapVersionsPath.set(noAddOnsZapVersions)
    zapVersionsAddOnsPath.set(nameLatestZapVersions)
    releaseState.set(layout.buildDirectory.file("release_state_last_commit.json"))
}

val releaseStateData = generateReleaseStateLastCommit.map { it.releaseState.get() }
val addOnsHelpWebsite = file("src/main/addons-help-website.txt")

val downloadReleasedAddOns by tasks.registering(DownloadReleasedAddOns::class) {
    releaseState.set(releaseStateData)
    zapVersions.set(latestZapVersions)
    allowedAddOns.set(addOnsHelpWebsite)
    outputDir.set(layout.buildDirectory.dir("releasedAddOns"))
}

val generateWebsitePages by tasks.registering(GenerateWebsitePages::class) {
    allowedAddOns.set(addOnsHelpWebsite)
    addOns.from(downloadReleasedAddOns.map { fileTree(it.outputDir).matching { include("*.zap") } })

    helpAddOnRegex.set("^help(?:_[a-zA-Z_]+)?")
    siteUrl.set("https://www.zaproxy.org/")
    baseUrlPath.set("/docs/desktop/")
    addOnsDirName.set("addons")
    pageType.set("userguide")
    redirectPageType.set("_default")
    redirectPageLayout.set("redirect")
    sectionPageName.set("_index.md")
    imagesDirName.set("images")
    noticeGeneratedPage.set("This page was generated from the add-on.")

    outputDir.set(layout.buildDirectory.dir("websiteHelpPages"))
}

val updateGettingStartedWebsitePage by tasks.registering(UpdateGettingStartedWebsitePage::class) {
    addOn.set(
        downloadReleasedAddOns.map {
            var files = fileTree(it.outputDir).matching { include("gettingStarted*.zap") }.files
            if (files.isEmpty()) Optional.empty() else Optional.of(files.first())
        },
    )
    filenameRegex.set("ZAPGettingStartedGuide-.+\\.pdf")
    gettingStartedPage.set(file("$siteDir/content/getting-started/index.md"))
    pdfDirectory.set(file("$siteDir/static/pdf/"))
}

val generateWebsiteSbomPages by tasks.registering(GenerateWebsiteSbomPages::class) {
    releaseState.set(releaseStateData)
    zapVersions.set(latestZapVersions)
    outputDir.set(layout.buildDirectory.dir("websiteSbomPages"))
}

val updateZapVersionWebsiteData by tasks.registering(UpdateZapVersionWebsiteData::class) {
    releaseState.set(releaseStateData)
    val downloadDir = "$siteDir/data/download"
    dataFiles.from(files("$downloadDir/b_details.yml", "$downloadDir/c_main.yml", "$downloadDir/g_latest.yml"))
}

val copyWebsiteGeneratedData by tasks.registering(Copy::class) {
    group = "ZAP"
    description = "Copies the generated website data to the website repo."

    destinationDir = siteDir
    into("data") {
        from(generateWebsiteAddonsData)
        into("download") {
            from(generateWebsiteMainReleaseData, generateWebsiteWeeklyReleaseData)
        }
    }
    into("content") {
        from(generateWebsitePages)
        from(generateWebsiteSbomPages)
    }
}

updateZapVersionWebsiteData {
    mustRunAfter(copyWebsiteGeneratedData)
}

updateGettingStartedWebsitePage {
    mustRunAfter(copyWebsiteGeneratedData)
}

val updateWebsite by tasks.registering(CreatePullRequest::class) {
    dependsOn(copyWebsiteGeneratedData)
    dependsOn(updateGettingStartedWebsitePage)
    dependsOn(updateZapVersionWebsiteData)

    user.set(ghUser)
    repo.set(websiteRepo)
    baseBranchName.set("main")
    branchName.set("update-data")

    commitSummary.set("Update data")
    commitDescription.set(
        provider {
            """
            From:
            $adminRepo@${headCommit(adminRepo.dir)}
            """.trimIndent()
        },
    )
}

fun headCommit(repoDir: File) =
    FileRepositoryBuilder()
        .setGitDir(File(repoDir, ".git"))
        .build()
        .exactRef("HEAD")
        .getObjectId()
        .getName()

val handleWeeklyRelease by tasks.registering(HandleWeeklyRelease::class) {
    releaseState.set(releaseStateData)

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)

    eventType.set("release-weekly-docker")
}

val handleMainRelease by tasks.registering(HandleMainRelease::class) {
    releaseState.set(releaseStateData)

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)

    eventType.set("release-main-docker")
}

val flathubRepo = GitHubRepo("flathub", "org.zaproxy.ZAP", file("$rootDir/../org.zaproxy.ZAP"))

val updateFlathubData by tasks.registering(UpdateFlathubData::class) {
    releaseState.set(releaseStateData)

    zapVersions.set(latestZapVersions)
    baseDirectory.set(flathubRepo.dir)
}

val updateFlathub by tasks.registering(CreatePullRequest::class) {
    dependsOn(updateFlathubData)

    user.set(ghUser)
    repo.set(flathubRepo)
    baseBranchName.set("master")
    branchName.set("update-zap")

    commitSummary.set("Update ZAP version")
    commitDescription.set(
        provider {
            """
            From:
            $adminRepo@${headCommit(adminRepo.dir)}
            """.trimIndent()
        },
    )

    mustRunAfter(handleMainRelease)
}

val zapMgmtScriptsRepo = GitHubRepo("zapbot", "zap-mgmt-scripts", file("$rootDir/../zap-mgmt-scripts"))

val updateZapMgmtScriptsData by tasks.registering(UpdateZapMgmtScriptsData::class) {
    releaseState.set(releaseStateData)

    zapVersions.set(latestZapVersions)
    baseDirectory.set(zapMgmtScriptsRepo.dir)
}

val updateZapMgmtScripts by tasks.registering(CreatePullRequest::class) {
    dependsOn(updateZapMgmtScriptsData)

    user.set(ghUser)
    repo.set(zapMgmtScriptsRepo)
    baseBranchName.set("master")
    branchName.set("update-main-release")

    commitSummary.set("Update post main release")
    commitDescription.set(
        provider {
            """
            Update GitHub tags.
            
            From:
            $adminRepo@${headCommit(adminRepo.dir)}
            """.trimIndent()
        },
    )

    mustRunAfter(handleMainRelease)
}

val handleSnapRelease by tasks.registering(HandleMainRelease::class) {
    releaseState.set(releaseStateData)

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)

    eventType.set("release-snap")

    mustRunAfter(handleMainRelease)
}

tasks.register("handleRelease") {
    dependsOn(updateWebsite)
    dependsOn(handleWeeklyRelease)
    dependsOn(handleMainRelease)
    dependsOn(updateFlathub)
    dependsOn(handleSnapRelease)
    dependsOn(updateZapMgmtScripts)
}
