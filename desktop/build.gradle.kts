import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

// Version shown in installers. Override with: ./gradlew :desktop:packageDistributionForCurrentOS -PappVersion=1.2.0
// (installers require MAJOR.MINOR.PATCH with MAJOR >= 1)
val appVersion = providers.gradleProperty("appVersion").orNull ?: "1.0.0"

kotlin {
  sourceSets.main {
    // Re-use the platform-independent code of the Android app (catalog, MovieBox engine, crypto,
    // SRT parser, models). Everything Android/Firebase specific is excluded here and replaced by
    // desktop equivalents in desktop/src/main/kotlin.
    kotlin.srcDir("../app/src/main/java")
    kotlin.exclude(
      "com/example/MainActivity.kt",
      "com/example/auth/**",
      "com/example/notifications/**",
      "com/example/data/firebase/**",
      "com/example/data/cache/**",
      "com/example/data/settings/**",
      "com/example/data/download/**",
      "com/example/data/model/SavedMovieEntry.kt",
      "com/example/data/model/UserProfile.kt",
      "com/example/data/remote/GitHubUpdateRepository.kt",
      "com/example/ui/components/**",
      "com/example/ui/navigation/**",
      "com/example/ui/screens/**",
      "com/example/ui/viewmodel/**",
      "com/example/ui/theme/Theme.kt",
      "com/example/ui/theme/Type.kt",
    )
  }
}

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

  implementation(libs.kotlinx.coroutines.core)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinxCoroutinesCore.get()}")

  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.org.json)

  // Embedded video playback (requires VLC installed on the user's computer).
  implementation(libs.vlcj)

  testImplementation(libs.junit)
}

compose.desktop {
  application {
    mainClass = "com.example.desktop.MainKt"
    jvmArgs += listOf("-Xmx1g")

    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
      packageName = "DecanMovie"
      packageVersion = appVersion
      description = "Decan Movie - stream and download movies, series and anime"
      vendor = "Decan"
      modules("java.sql", "jdk.unsupported", "java.naming")

      windows {
        menuGroup = "Decan Movie"
        shortcut = true
        dirChooser = true
        perUserInstall = true
        upgradeUuid = "6f1c2d7e-4a1b-4c55-9d3e-0a8f5b2c7e11"
        iconFile.set(project.file("icons/icon.ico"))
      }
      macOS {
        bundleID = "com.aistudio.decanmovie.desktop"
        iconFile.set(project.file("icons/icon.icns"))
      }
      linux {
        iconFile.set(project.file("icons/icon.png"))
        shortcut = true
      }
    }
  }
}
