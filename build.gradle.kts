import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.zaproxy.gradle.CreateNewsMainRelease
import org.zaproxy.gradle.CreatePullRequest
import org.zaproxy.gradle.CustomXmlConfiguration
import org.zaproxy.gradle.GenerateReleaseStateLastCommit
import org.zaproxy.gradle.GenerateWebsiteAddonsData
import org.zaproxy.gradle.GenerateWebsiteMainReleaseData
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

plugins {
    java
    id("com.diffplug.spotless") version "5.12.1"
    id("net.ltgt.errorprone") version "2.0.1"
}

apply(from = "$rootDir/gradle/ci.gradle.kts")

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

repositories {
    mavenCentral()
}

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.3.1")
    if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
        "errorproneJavac"("com.google.errorprone:javac:9+181-r4173-1")
    }

    implementation("org.kohsuke:github-api:1.101")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18") {
        exclude(group = "org.jenkins-ci")
    }
    compileOnly("com.github.spotbugs:spotbugs-annotations:3.1.12")
    implementation("net.sf.json-lib:json-lib:2.4:jdk15")
    implementation("org.zaproxy:zap:2.7.0")

    val jupiterVersion = "5.5.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.assertj:assertj-core:3.14.0")
}

val zapVersionsDir = file("$buildDir/ZapVersionsTests")
val copyZapVersions = tasks.create<Copy>("copyZapVersions") {
    from(rootDir)
    into(zapVersionsDir)
    include("ZapVersions*.xml")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
val nameLatestZapVersions = "ZapVersions-2.10.xml"
val latestZapVersions = file(nameLatestZapVersions)

val ghUser = GitHubUser("zapbot", "12745184+zapbot@users.noreply.github.com", System.getenv("ZAPBOT_TOKEN"))
val adminRepo = GitHubRepo("zaproxy", "zap-admin", rootDir)

val addOnsZapVersions = files("ZapVersions-dev.xml", latestZapVersions)
val defaultChecksumAlgorithm = "SHA-256"

tasks {
    register<ZapTask>("generateReleaseNotes") {
        description = "Generates release notes."
        main = "org.zaproxy.admin.GenerateReleaseNotes"
    }

    register<ZapTask>("listDownloadCounts") {
        description = "Lists download counts."
        main = "org.zaproxy.admin.CountDownloads"
    }

    register<ZapTask>("pendingAddOnReleases") {
        description = "Reports the add-ons that are pending a release of new version."
        main = "org.zaproxy.admin.PendingAddOnReleases"
    }

    register<ZapTask>("generateHelpAddOn") {
        description = "Generates the basic help files for an add-on."
        main = "org.zaproxy.admin.HelpGenerator"
        standardInput = System.`in`
    }

    register<ZapTask>("checkLatestReleaseNotes") {
        description = "Checks the latest release notes do not contain issues from previous ones."
        main = "org.zaproxy.admin.CheckLatestReleaseNotes"
    }

    register<CreateNewsMainRelease>("createNewsMainRelease") {
        newsDir.set(File(rootDir, "files/news"))
        previousVersion.set(provider {
            val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
            zapVersionsXml.getString("core.version")
        })

        item.set("ZAP @@VERSION@@ is available now")
        link.set("https://www.zaproxy.org/download/")
    }

    register<UpdateMainZapVersionsEntries>("updateMainRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/v@@VERSION@@/")
        windowsFileName.set("ZAP_@@VERSION_UNDERSCORES@@_windows.exe")
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
        commitDescription.set(provider {
            val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
            val mainVersion = zapVersionsXml.getString("core.version")
            "Update main release to version $mainVersion."
        })
    }

    register<UpdateDailyZapVersionsEntries>("updateDailyRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/w")
        checksumAlgorithm.set(defaultChecksumAlgorithm)
    }

    register<CreatePullRequest>("createPullRequestDailyRelease") {
        description = "Creates a pull request to update the daily release."

        user.set(ghUser)
        repo.set(adminRepo)
        branchName.set("update-daily-release")

        commitSummary.set("Update weekly release")
        commitDescription.set(provider {
            val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
            val dailyVersion = zapVersionsXml.getString("core.daily-version")
            "Update weekly release to version $dailyVersion."
        })
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
    zapVersions.set(latestZapVersions)
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
val dataDir = file("${websiteRepo.dir}/site/data")

val generateReleaseStateLastCommit by tasks.registering(GenerateReleaseStateLastCommit::class) {
    zapVersionsPath.set(noAddOnsZapVersions)
    zapVersionsAddOnsPath.set(nameLatestZapVersions)
    releaseState.set(file("$buildDir/release_state_last_commit.json"))
}

val releaseStateData = generateReleaseStateLastCommit.map { it.releaseState.get() }

val updateZapVersionWebsiteData by tasks.registering(UpdateZapVersionWebsiteData::class) {
    releaseState.set(releaseStateData)
    val downloadDir = "$dataDir/download"
    dataFiles.from(files("$downloadDir/b_details.yml", "$downloadDir/c_main.yml", "$downloadDir/g_latest.yml"))
}

val copyWebsiteGeneratedData by tasks.registering(Copy::class) {
    group = "ZAP"
    description = "Copies the generated website data to the website repo."

    destinationDir = dataDir
    from(generateWebsiteAddonsData)
    into("download") {
        from(generateWebsiteMainReleaseData, generateWebsiteWeeklyReleaseData)
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
    commitDescription.set(provider {
        """
        From:
        $adminRepo@${headCommit(adminRepo.dir)}
        """.trimIndent()
    })
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
