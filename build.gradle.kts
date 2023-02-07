import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.zaproxy.gradle.CreateNewsMainRelease
import org.zaproxy.gradle.CreatePullRequest
import org.zaproxy.gradle.CustomXmlConfiguration
import org.zaproxy.gradle.DownloadReleasedAddOns
import org.zaproxy.gradle.GenerateReleaseStateLastCommit
import org.zaproxy.gradle.GenerateWebsiteAddonsData
import org.zaproxy.gradle.GenerateWebsiteMainReleaseData
import org.zaproxy.gradle.GenerateWebsitePages
import org.zaproxy.gradle.GenerateWebsiteWeeklyReleaseData
import org.zaproxy.gradle.GitHubRepo
import org.zaproxy.gradle.GitHubUser
import org.zaproxy.gradle.HandleMainRelease
import org.zaproxy.gradle.HandleWeeklyRelease
import org.zaproxy.gradle.UpdateAddOnZapVersionsEntries
import org.zaproxy.gradle.UpdateAndCreatePullRequestAddOnRelease
import org.zaproxy.gradle.UpdateDailyZapVersionsEntries
import org.zaproxy.gradle.UpdateMainZapVersionsEntries
import org.zaproxy.gradle.UpdateZapVersionWebsiteData
import org.zaproxy.gradle.crowdin.DeployCrowdinTranslations

plugins {
    java
    id("com.diffplug.spotless") version "6.14.1"
    id("net.ltgt.errorprone") version "3.0.1"
    id("org.zaproxy.crowdin") version "0.3.1"
}

apply(from = "$rootDir/gradle/ci.gradle.kts")

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

repositories {
    mavenCentral()
}

crowdin {
    credentials {
        token.set(System.getenv("CROWDIN_AUTH_TOKEN"))
    }

    configuration {
        file.set(file("gradle/crowdin.yml"))
    }
}

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.18.0")

    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.kohsuke:github-api:1.101")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18") {
        exclude(group = "org.jenkins-ci")
    }
    compileOnly("com.github.spotbugs:spotbugs-annotations:3.1.12")
    implementation("net.sf.json-lib:json-lib:2.4:jdk15")
    implementation("org.zaproxy:zap:2.12.0")

    val jupiterVersion = "5.9.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

val zapVersionsDir = file("$buildDir/ZapVersionsTests")
val copyZapVersions = tasks.create<Copy>("copyZapVersions") {
    from(rootDir)
    into(zapVersionsDir)
    include("ZapVersions*.xml")
}

java {
    val javaVersion = JavaVersion.VERSION_11
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

sourceSets["test"].output.dir(mapOf("builtBy" to copyZapVersions), zapVersionsDir)

spotless {
    java {
        licenseHeaderFile("$rootDir/docs/headers/license.java")

        googleJavaFormat("1.7").aosp()
    }

    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
    }
}

val noAddOnsZapVersions = "ZapVersions.xml"
val devZapVersions = "ZapVersions-dev.xml"
val nameLatestZapVersions = "ZapVersions-2.12.xml"
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
    into.set(file("$buildDir/c_main_files.yml"))

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)
}

val generateWebsiteWeeklyReleaseData by tasks.registering(GenerateWebsiteWeeklyReleaseData::class) {
    zapVersions.set(file(noAddOnsZapVersions))
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(file("$buildDir/e_weekly_files.yml"))
}

val generateWebsiteAddonsData by tasks.registering(GenerateWebsiteAddonsData::class) {
    zapVersions.set(latestZapVersions)
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(file("$buildDir/addons.yaml"))
    websiteUrl.set("https://www.zaproxy.org/")
}

val websiteRepo = GitHubRepo("zaproxy", "zaproxy-website", file("$rootDir/../zaproxy-website"))
val siteDir = file("${websiteRepo.dir}/site")

val generateReleaseStateLastCommit by tasks.registering(GenerateReleaseStateLastCommit::class) {
    zapVersionsPath.set(noAddOnsZapVersions)
    zapVersionsAddOnsPath.set(nameLatestZapVersions)
    releaseState.set(file("$buildDir/release_state_last_commit.json"))
}

val releaseStateData = generateReleaseStateLastCommit.map { it.releaseState.get() }
val addOnsHelpWebsite = file("src/main/addons-help-website.txt")

val downloadReleasedAddOns by tasks.registering(DownloadReleasedAddOns::class) {
    releaseState.set(releaseStateData)
    zapVersions.set(latestZapVersions)
    allowedAddOns.set(addOnsHelpWebsite)
    outputDir.set(file("$buildDir/releasedAddOns"))
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

    outputDir.set(file("$buildDir/websiteHelpPages"))
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
    }
}

updateZapVersionWebsiteData {
    mustRunAfter(copyWebsiteGeneratedData)
}

val updateWebsite by tasks.registering(CreatePullRequest::class) {
    dependsOn(copyWebsiteGeneratedData)
    dependsOn(updateZapVersionWebsiteData)

    user.set(ghUser)
    repo.set(websiteRepo)
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

tasks.register("handleRelease") {
    dependsOn(updateWebsite)
    dependsOn(handleWeeklyRelease)
    dependsOn(handleMainRelease)
}
