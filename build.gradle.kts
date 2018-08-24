buildscript {
    repositories {
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    dependencies {
        classpath(if (JavaVersion.current() == JavaVersion.VERSION_1_8) "net.ltgt.gradle:gradle-errorprone-plugin:0.0.16" else "net.ltgt.gradle:gradle-errorprone-javacplugin-plugin:0.3")
    }
}

plugins {
    java
    id("com.diffplug.gradle.spotless") version "3.13.0"
}

apply(plugin = if (JavaVersion.current() == JavaVersion.VERSION_1_8) "net.ltgt.errorprone" else "net.ltgt.errorprone-javacplugin")

tasks {
    "wrapper"(Wrapper::class) {
        gradleVersion = "4.8"
        distributionType = Wrapper.DistributionType.ALL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

repositories {
    mavenLocal()
    mavenCentral()
}

buildDir = file("buildGradle")

dependencies {
    compile("net.sf.json-lib:json-lib:2.4:jdk15")
    compile("org.zaproxy:zap:2.7.0")

    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")
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

    sourceSets {
        "test" {
            output.dir(mapOf("builtBy" to copyZapVersions), zapVersionsDir)
        }
    }
}

spotless {
    java {
        licenseHeaderFile("$rootDir/docs/headers/license.java")

        googleJavaFormat().aosp()
    }
}

tasks {
    "generateReleaseNotes"(ZapTask::class) {
        description = "Generates release notes."
        main = "org.zaproxy.admin.GenerateReleaseNotes"
    }

    "listDownloadCounts"(ZapTask::class) {
        description = "Lists download counts."
        main = "org.zaproxy.admin.CountDownloads"
    }

    "pendingAddOnReleases"(ZapTask::class) {
        description = "Reports the add-ons that are pending a release of new version."
        main = "org.zaproxy.admin.PendingAddOnReleases"
    }

    "generateHelpAddOn"(ZapTask::class) {
        description = "Generates the basic help files for an add-on."
        main = "org.zaproxy.admin.HelpGenerator"
        standardInput = System.`in`
    }

    "reportAddOnsMissingHelp"(ZapTask::class) {
        description = "Reports the add-ons that do not have help pages."
        main = "org.zaproxy.admin.HelpReportMissing"
    }

    "checkLatestReleaseNotes"(ZapTask::class) {
        description = "Checks the latest release notes do not contain issues from previous ones."
        main = "org.zaproxy.admin.CheckLatestReleaseNotes"
    }
}
