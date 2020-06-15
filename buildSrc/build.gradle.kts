plugins {
    `kotlin-dsl`
    id("com.diffplug.gradle.spotless") version "3.14.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-configuration:commons-configuration:1.9")
    implementation("commons-jxpath:commons-jxpath:1.3")
    implementation("commons-codec:commons-codec:1.11")
    val jgitVersion = "5.6.0.201912101111-r"
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.archive:$jgitVersion")
    implementation("org.kohsuke:github-api:1.106")
    implementation("org.snakeyaml:snakeyaml-engine:2.0")
    implementation("org.zaproxy:zap:2.9.0")
}

spotless {
    java {
        licenseHeaderFile("../docs/headers/license.java")

        googleJavaFormat().aosp()
    }

    kotlinGradle {
        ktlint()
    }
}
