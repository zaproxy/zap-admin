import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getPlugin

open class ZapTask : JavaExec() {

    init {
        group = "ZAP"
        classpath = project.configurations["runtimeClasspath"] +
                project.convention.getPlugin(JavaPluginConvention::class).sourceSets["main"].output
        dependsOn("classes")
    }
}
