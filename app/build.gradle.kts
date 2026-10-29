import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
}

layout.buildDirectory.set(file("C:/builds/PersonalDocumentFinder/app_out"))

android {
    namespace = "com.example.personaldocumentfinder"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.personaldocumentfinder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)

    // ML Kit OCR
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

abstract class CopyDebugApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceApkDir: DirectoryProperty

    @get:OutputFile
    abstract val destinationApk: RegularFileProperty

    @TaskAction
    fun copyApk() {
        val apkDir = sourceApkDir.get().asFile
        val apkFile = apkDir.listFiles { file -> file.extension == "apk" }?.firstOrNull()
            ?: throw GradleException("No APK found in directory: ${apkDir.absolutePath}")

        val dest = destinationApk.get().asFile
        if (apkFile.canonicalPath == dest.canonicalPath) {
            logger.info("Source and destination APK paths are identical; skipping copy.")
            return
        }

        dest.parentFile.mkdirs()
        apkFile.copyTo(dest, overwrite = true)
        dest.setLastModified(apkFile.lastModified())
    }
}

val copyDebugApk = tasks.register<CopyDebugApkTask>("copyDebugApk") {
    dependsOn("assembleDebug")
    destinationApk.set(layout.projectDirectory.file("build/outputs/apk/debug/app-debug.apk"))
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        copyDebugApk.configure {
            sourceApkDir.set(variant.artifacts.get(SingleArtifact.APK))
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}
