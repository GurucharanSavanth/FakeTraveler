import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.room)
}

android {
    namespace = "cl.coders.faketraveler"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "cl.coders.faketraveler"
        minSdk = 26
        targetSdk = 36
        versionCode = 240
        versionName = "2.4.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable += listOf(
            "MissingTranslation",
            "OldTargetApi",
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "BatteryLife",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas/")
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // ── Desugaring ──
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── Legacy view-system (existing Java code) ──
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.flexbox)
    implementation(libs.recyclerview)
    implementation(libs.activity)

    // ── Lifecycle ──
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime.compose)

    // ── WorkManager ──
    implementation(libs.work.runtime)
    implementation(libs.work.runtime.ktx)

    // ── Room (KSP compiler for Java + Kotlin sources) ──
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── DataStore ──
    implementation(libs.datastore.preferences)
    implementation(libs.datastore)

    // ── Compose ──
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Navigation ──
    implementation(libs.navigation.compose)

    // ── Glance (widgets) ──
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // ── Koin DI ──
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // ── Ktor networking ──
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // ── kotlinx-datetime ──
    implementation(libs.kotlinx.datetime)

    // ── MapLibre ──
    implementation(libs.maplibre)

    // ── TFLite ──
    implementation(libs.tflite)

    // ── Play Services Location ──
    implementation(libs.play.services.location)

    // ── Protobuf ──
    implementation(libs.protobuf.javalite)

    // ── Testing ──
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.arch.core.testing)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}
