import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.get

open class ZapTask : JavaExec() {

    init {
        group = "ZAP"
        classpath = project.configurations["runtimeClasspath"] +
            project.extensions.getByType(JavaPluginExtension::class.java).sourceSets["main"].output
        dependsOn("classes")
    }
}
