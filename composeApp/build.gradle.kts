import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {

        commonMain.dependencies {
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor2)
            implementation(libs.trixnity.client)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.slf4j.simple)
            // Repeat KMP deps here to guarantee the JVM artifact variant
            // is included in the desktop run task's runtime classpath.
            implementation(libs.trixnity.client)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.trixnity.client.repository.exposed)
            implementation(libs.h2.database)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.grapevine.grapevine"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.grapevine.grapevine"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.grapevine.grapevine.MainKt"

        jvmArgs("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.grapevine.grapevine"
            packageVersion = "1.0.0"

            // Drop the actual icon files in composeApp/icons/ using these exact names —
            // packaging tasks (packageDmg/packageMsi/packageDeb) resolve them lazily, so it's
            // safe to leave unpopulated until then; only running those specific tasks needs them.
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}

configurations.all {
    exclude(group = "androidx.sqlite", module = "sqlite3mc-bundled")
    resolutionStrategy {
        // Compose Multiplatform 1.10.3 (ui-graphics) pulls kotlinx-datetime 0.7.1, which silently
        // outranks our declared 0.6.2 and wins Gradle's conflict resolution project-wide. Trixnity
        // 3.6.0 was compiled against the pre-stdlib-migration kotlinx.datetime.Clock API — 0.7.1
        // removes the concrete Clock$System class (replaced by a typealias to kotlin.time.Clock),
        // so Trixnity's sync loop throws NoClassDefFoundError on every single sync call, silently
        // (only visible with an SLF4J backend attached) — which is why the room/member/key stores
        // never populated. Force back to 0.6.2, which still has the class Trixnity needs.
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
    }
}
