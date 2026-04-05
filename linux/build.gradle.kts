plugins {
    kotlin("jvm")
    application
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.varynx.linux.daemon.LinuxGuardianLoopKt")
    applicationName = "VARYNX Linux Guardian"
}
