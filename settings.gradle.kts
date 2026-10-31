pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zorbi"

// Required modular architecture from Master Prompt Section 7.1
include(":core")
include(":vision")
include(":calib")
include(":gesture")
include(":input")
include(":engine")
include(":ui")
include(":data")
include(":app")
include(":fakes")

