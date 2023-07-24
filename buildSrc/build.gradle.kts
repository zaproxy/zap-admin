plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless") version "6.20.0"
}

repositories {
    mavenCentral()
}

val javaVersion = JavaVersion.VERSION_11
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
    options.compilerArgs = listOf("-Xlint:all", "-Werror")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("commons-configuration:commons-configuration:1.10")
    implementation("commons-codec:commons-codec:1.11")

    val flexmarkVersion = "0.50.48"
    implementation("com.vladsch.flexmark:flexmark-java:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-yaml-front-matter:$flexmarkVersion")

    implementation("com.vladsch.flexmark:flexmark-html2md-converter:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-anchorlink:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-profile-pegdown:$flexmarkVersion")

    implementation("org.apache.commons:commons-lang3:3.12.0")
    val jgitVersion = "5.12.0.202106070339-r"
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.archive:$jgitVersion")
    implementation("org.kohsuke:github-api:1.106")
    // Include annotations used by the above library to avoid compiler warnings.
    compileOnly("com.google.code.findbugs:findbugs-annotations:3.0.1")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18") {
        exclude(group = "org.jenkins-ci")
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("org.snakeyaml:snakeyaml-engine:2.6")
    implementation("org.zaproxy:zap:2.13.0")
}

spotless {
    java {
        licenseHeaderFile("../docs/headers/license.java")

        googleJavaFormat("1.17.0").aosp()
    }

    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
    }
}
