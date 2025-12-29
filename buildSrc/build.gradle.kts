plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless")
    id("org.zaproxy.common")
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    if (JavaVersion.current().getMajorVersion() >= "21") {
       options.compilerArgs = options.compilerArgs + "-Xlint:-this-escape"
    }
}

dependencies {
    implementation("commons-configuration:commons-configuration:1.10")
    implementation("commons-codec:commons-codec:1.20.0")

    val flexmarkVersion = "0.64.8"
    implementation("com.vladsch.flexmark:flexmark-java:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-yaml-front-matter:$flexmarkVersion")

    implementation("com.vladsch.flexmark:flexmark-html2md-converter:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-anchorlink:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-profile-pegdown:$flexmarkVersion")

    implementation("org.apache.commons:commons-lang3:3.19.0")
    val jgitVersion = "7.1.0.202411261347-r"
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.archive:$jgitVersion")
    implementation("org.kohsuke:github-api:1.326")
    // Include annotations used by the above library to avoid compiler warnings.
    compileOnly("com.google.code.findbugs:findbugs-annotations:3.0.1")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.18") {
        exclude(group = "org.jenkins-ci")
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    implementation("org.snakeyaml:snakeyaml-engine:2.6")
    implementation("org.zaproxy:zap:2.17.0")
}

spotless {
    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
    }
}
