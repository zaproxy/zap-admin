import org.zaproxy.gradle.GenerateWebsiteMainReleaseData
import org.zaproxy.gradle.GenerateWebsiteWeeklyReleaseData
import org.zaproxy.gradle.UpdateAddOnZapVersionsEntries
import org.zaproxy.gradle.UpdateDailyZapVersionsEntries
import org.zaproxy.gradle.UpdateMainZapVersionsEntries
import org.zaproxy.gradle.UpdateWebsite
import org.zaproxy.gradle.GenerateWebsiteAddonsData

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath(if (JavaVersion.current() == JavaVersion.VERSION_1_8) "net.ltgt.gradle:gradle-errorprone-plugin:0.0.16" else "net.ltgt.gradle:gradle-errorprone-javacplugin-plugin:0.5")
    }
}

plugins {
    java
    id("com.diffplug.gradle.spotless") version "3.23.0"
}

apply(from = "$rootDir/gradle/travis-ci.gradle.kts")
apply(plugin = if (JavaVersion.current() == JavaVersion.VERSION_1_8) "net.ltgt.errorprone" else "net.ltgt.errorprone-javacplugin")

tasks {
    getByName<Wrapper>("wrapper") {
        gradleVersion = "4.10"
        distributionType = Wrapper.DistributionType.ALL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

repositories {
    jcenter()
}

buildDir = file("buildGradle")

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.3.1")

    compile("org.kohsuke:github-api:1.101")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18")
    compileOnly("com.github.spotbugs:spotbugs-annotations:3.1.12")
    compile("net.sf.json-lib:json-lib:2.4:jdk15")
    compile("org.zaproxy:zap:2.7.0")

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

        googleJavaFormat().aosp()
    }
}

val latestZapVersions = file("ZapVersions-2.9.xml")

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

    register<UpdateAddOnZapVersionsEntries>("updateAddOnRelease") {
        into.setFrom(files("ZapVersions-dev.xml", latestZapVersions))
        checksumAlgorithm.set("SHA-256")
    }
}

val baseUserName = "zaproxy"
val zaproxyRepo = "zaproxy"
val adminRepo = "zap-admin"
val ghUser = GitHubUser("zapbot", "12745184+zapbot@users.noreply.github.com", System.getenv("ZAPBOT_TOKEN"))
val websiteGeneratedDataComment = "# This file is automatically updated by $baseUserName/$adminRepo repo."

val generateWebsiteMainReleaseData by tasks.registering(GenerateWebsiteMainReleaseData::class) {
    zapVersions.set(latestZapVersions)
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(file("$buildDir/c_main_files.yml"))

    ghUserName.set(ghUser.name)
    ghUserAuthToken.set(ghUser.authToken)

    ghBaseUserName.set(baseUserName)
    ghBaseRepo.set(zaproxyRepo)
}

val generateWebsiteWeeklyReleaseData by tasks.registering(GenerateWebsiteWeeklyReleaseData::class) {
    zapVersions.set(latestZapVersions)
    generatedDataComment.set(websiteGeneratedDataComment)
    into.set(file("$buildDir/e_weekly_files.yml"))
}

val generateWebsiteAddonsData by tasks.registering(GenerateWebsiteAddonsData::class) {
    zapVersions.set(latestZapVersions)
    into.set(file("$buildDir/addons.yaml"))
}

val websiteRepoName = "zaproxy-website"
val websiteRepoDir = file("$rootDir/../$websiteRepoName")
val dataDir = "$websiteRepoDir/site/data"

val copyWebsiteGeneratedData by tasks.registering(Copy::class) {
    group = "ZAP"
    description = "Copies the generated website data to the website repo."

    into("$dataDir/download")
    from(generateWebsiteMainReleaseData, generateWebsiteWeeklyReleaseData)

    into(dataDir)
    from(generateWebsiteAddonsData)
}

tasks.register<UpdateWebsite>("updateWebsite") {
    dependsOn(copyWebsiteGeneratedData)

    ghUserName.set(ghUser.name)
    ghUserEmail.set(ghUser.email)
    ghUserAuthToken.set(ghUser.authToken)

    ghBaseUserName.set(baseUserName)
    ghBaseRepo.set(websiteRepoName)
    ghSourceRepo.set(adminRepo)

    websiteRepo.set(websiteRepoDir)
}

data class GitHubUser(val name: String, val email: String, val authToken: String?)