plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.varynx.tools.meshtest.MeshValidationRunnerKt")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":core"))
}
