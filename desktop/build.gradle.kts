plugins {
    kotlin("jvm")
    application
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()
val fxPlatform = when {
    currentOs.isWindows -> "win"
    currentOs.isLinux   -> "linux"
    currentOs.isMacOsX  -> "mac"
    else -> error("Unsupported OS")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":service"))
    implementation(libs.kotlinx.coroutines.core)

    // JavaFX (platform-specific)
    implementation("${libs.javafx.base.get()}:$fxPlatform")
    implementation("${libs.javafx.graphics.get()}:$fxPlatform")
    implementation("${libs.javafx.controls.get()}:$fxPlatform")
    implementation("${libs.javafx.web.get()}:$fxPlatform")
    implementation("org.openjfx:javafx-media:${libs.versions.javafx.get()}:$fxPlatform")
}

application {
    mainClass.set("com.varynx.desktop.VarynxDesktopKt")
    applicationName = "VARYNX Desktop"
}

// ── jpackage app-image ──

tasks.register<Exec>("jpackage") {
    dependsOn("installDist")

    val installDir = layout.buildDirectory.dir("install/VARYNX Desktop")
    val outputDir  = layout.buildDirectory.dir("jpackage")
    val iconFile   = file("src/main/resources/icons/varynx-desktop.ico")

    doFirst {
        outputDir.get().asFile.deleteRecursively()
        outputDir.get().asFile.mkdirs()
    }

    val args = mutableListOf(
        "jpackage",
        "--type", "app-image",
        "--name", "VARYNX Desktop",
        "--app-version", "2.0.0",
        "--vendor", "VARYNX",
        "--description", "VARYNX Guardian — Desktop Control Center",
        "--input",      installDir.get().asFile.resolve("lib").absolutePath,
        "--main-jar",   "desktop.jar",
        "--main-class", "com.varynx.desktop.VarynxDesktopKt",
        "--dest",       outputDir.get().asFile.absolutePath
    )
    if (iconFile.exists()) args.addAll(listOf("--icon", iconFile.absolutePath))
    commandLine(args)
}

// ── Inno Setup installer ──

tasks.register<Exec>("installer") {
    dependsOn("jpackage", ":service:fatJar")

    val issFile  = file("installer.iss")
    val isccPath = "C:\\Users\\corey\\AppData\\Local\\Programs\\Inno Setup 6\\iscc.exe"

    doFirst {
        require(issFile.exists())       { "installer.iss not found" }
        require(File(isccPath).exists()) { "Inno Setup not found at $isccPath" }
    }

    workingDir = projectDir
    commandLine(isccPath, issFile.absolutePath)
}
