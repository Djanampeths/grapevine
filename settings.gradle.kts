rootProject.name = "grapevine"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        //google {
        //    mavenContent {
        //        includeGroupAndSubgroups("androidx")
        //        includeGroupAndSubgroups("com.android")
        //        includeGroupAndSubgroups("com.google")
        //    }
        //}
        google()
        mavenCentral()
        maven("https://gitlab.com/api/v4/projects/47538655/packages/maven")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")