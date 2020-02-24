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

    compile("org.yaml:snakeyaml:1.25")
    compile("org.zaproxy:zap:2.7.0")
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