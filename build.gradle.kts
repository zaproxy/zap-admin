import org.zaproxy.gradle.UpdateAddOnZapVersionsEntries
import org.zaproxy.gradle.UpdateDailyZapVersionsEntries

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

    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")
    testImplementation("org.assertj:assertj-core:3.11.1")
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

sourceSets["test"].output.dir(mapOf("builtBy" to copyZapVersions), zapVersionsDir)

spotless {
    java {
        licenseHeaderFile("$rootDir/docs/headers/license.java")

        googleJavaFormat().aosp()
    }
}

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

    register<UpdateDailyZapVersionsEntries>("updateDailyRelease") {
        into.setFrom(fileTree(rootDir).matching { include("ZapVersions*.xml") })
        baseDownloadUrl.set("https://github.com/zaproxy/zaproxy/releases/download/w")
        checksumAlgorithm.set("SHA-256")
    }

    register<UpdateAddOnZapVersionsEntries>("updateAddOnRelease") {
        into.setFrom(files("ZapVersions-dev.xml", "ZapVersions-2.8.xml", "ZapVersions-2.9.xml"))
        checksumAlgorithm.set("SHA-256")
    }
}
