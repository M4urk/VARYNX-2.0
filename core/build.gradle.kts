plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "com.varynx.core"
        compileSdk = 36
        minSdk = 34
    }

    jvm("desktop")

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting

        // Shared JVM source set — used by both Android and Desktop
        val jvmCommon by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
        }

        val desktopMain by getting {
            dependsOn(jvmCommon)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}
