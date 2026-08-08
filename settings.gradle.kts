pluginManagement {
  repositories {
    // !!! ЗЕРКАЛО ПЕРВЫМ ДЛЯ УСКОРЕНИЯ !!!
    maven {
      url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
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

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // !!! ЗЕРКАЛО ПЕРВЫМ ДЛЯ УСКОРЕНИЯ !!!
    maven {
      url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "DiscoveryFM"
include(":app")