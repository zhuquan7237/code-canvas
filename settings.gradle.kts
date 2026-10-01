// The Aliyun mirrors below exist for the developer machine sitting behind the
// GFW. On CI they are not just redundant (the runner reaches Google's and Maven
// Central's own repositories) but actively harmful: when the mirror answers 502
// Gradle disables that repository and the entire resolve fails, even though the
// artifact is sitting right there upstream. So gate them — mirrors on a
// workstation, upstream on CI.
pluginManagement {
    val ci = !System.getenv("GITHUB_ACTIONS").isNullOrBlank() || !System.getenv("CI").isNullOrBlank()
    repositories {
        if (!ci) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val ci = !System.getenv("GITHUB_ACTIONS").isNullOrBlank() || !System.getenv("CI").isNullOrBlank()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!ci) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "code-canvas"
include(":app")
