import org.zaproxy.gradle.CreatePullRequest
import org.zaproxy.gradle.CustomXmlConfiguration
import org.zaproxy.gradle.GenerateReleaseStateLastCommit
import org.zaproxy.gradle.GenerateWebsiteAddonsData
import org.zaproxy.gradle.GenerateWebsiteMainReleaseData
import org.zaproxy.gradle.GenerateWebsiteWeeklyReleaseData
import org.zaproxy.gradle.GitHubUser
import org.zaproxy.gradle.GitHubRepo
import org.zaproxy.gradle.HandleMainRelease
import org.zaproxy.gradle.HandleWeeklyRelease
import org.zaproxy.gradle.UpdateAddOnZapVersionsEntries
import org.zaproxy.gradle.UpdateDailyZapVersionsEntries
import org.zaproxy.gradle.UpdateMainZapVersionsEntries
import org.zaproxy.gradle.UpdateWebsite
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
}

val noAddOnsZapVersions = "ZapVersions.xml"
val latestZapVersions = file("ZapVersions-2.10.xml")

val ghUser = GitHubUser("zapbot", "12745184+zapbot@users.noreply.github.com", System.getenv("ZAPBOT_TOKEN"))
val adminRepo = GitHubRepo("zaproxy", "zap-admin", rootDir)

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

    register<UpdateMainZapVersionsEntries>("updateMainRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/v@@VERSION@@/")
        windowsFileName.set("ZAP_@@VERSION_UNDERSCORES@@_windows.exe")
        linuxFileName.set("ZAP_@@VERSION@@_Linux.tar.gz")
        macFileName.set("ZAP_@@VERSION@@.dmg")
        releaseNotes.set("Bug fix and enhancement release.")
        releaseNotesUrl.set("https://www.zaproxy.org/docs/desktop/releases/@@VERSION@@/")
        checksumAlgorithm.set("SHA-256")
    }

    register<UpdateDailyZapVersionsEntries>("updateDailyRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/w")
        checksumAlgorithm.set("SHA-256")
    }

    register<CreatePullRequest>("createPullRequestDailyRelease") {
        description = "Creates a pull request to update the daily release."

        user.set(ghUser)
        repo.set(adminRepo)
        branchName.set("update-daily-relase")

        commitSummary.set("Update weekly release")
        commitDescription.set(provider {
            val zapVersionsXml = CustomXmlConfiguration(latestZapVersions)
            val dailyVersion = zapVersionsXml.getString("core.daily-version")
            "Update weekly release to version $dailyVersion."
        })
    }

    register<UpdateAddOnZapVersionsEntries>("updateAddOnRelease") {
        into.setFrom(files("ZapVersions-dev.xml", latestZapVersions))
        checksumAlgorithm.set("SHA-256")
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


val updateZapVersionWebsiteData by tasks.registering(UpdateZapVersionWebsiteData::class) {
    releaseState.set(generateReleaseStateLastCommit.map { it.releaseState.get() })
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

val updateWebsite by tasks.registering(UpdateWebsite::class) {
    dependsOn(copyWebsiteGeneratedData)
    dependsOn(updateZapVersionWebsiteData)

    gitHubUser.set(ghUser)
    gitHubRepo.set(websiteRepo)

    gitHubSourceRepo.set(adminRepo)
}

val generateReleaseStateLastCommit by tasks.registering(GenerateReleaseStateLastCommit::class) {
    zapVersionsPath.set(noAddOnsZapVersions)
    releaseState.set(file("$buildDir/release_state_last_commit.json"))
}

val handleWeeklyRelease by tasks.registering(HandleWeeklyRelease::class) {
    releaseState.set(generateReleaseStateLastCommit.map { it.releaseState.get() })

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)

    eventType.set("release-weekly-docker")
}

val handleMainRelease by tasks.registering(HandleMainRelease::class) {
    releaseState.set(generateReleaseStateLastCommit.map { it.releaseState.get() })

    gitHubUser.set(ghUser)
    gitHubRepo.set(zaproxyRepo)

    eventType.set("release-main-docker")
}

tasks.register("handleRelease") {
    dependsOn(updateWebsite)
    dependsOn(handleWeeklyRelease)
    dependsOn(handleMainRelease)
}

