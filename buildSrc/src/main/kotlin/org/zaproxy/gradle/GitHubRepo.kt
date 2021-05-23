package org.zaproxy.gradle

import java.io.File

data class GitHubRepo(val owner: String, val name: String, val dir: File) {

    override fun toString() = "$owner/$name"
}
