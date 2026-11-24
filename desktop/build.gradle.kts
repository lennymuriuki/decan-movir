import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.imageio.ImageIO
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.jetbrains.compose)
}

val desktopVersion: String =
  (providers.gradleProperty("appVersionName").orNull
      ?: System.getenv("APP_VERSION_NAME")
      ?: "1.1.0")
    .removePrefix("v")

val osName = System.getProperty("os.name").lowercase()
val requestedOs = providers.gradleProperty("targetOs").orNull?.lowercase()
val effectiveOs =
  requestedOs
    ?: when {
      osName.contains("win") -> "windows"
      osName.contains("mac") -> "macos"
      else -> "linux"
    }

val jfxClassifier =
  when (effectiveOs) {
    "windows" -> "win"
    "macos" -> if (System.getProperty("os.arch").lowercase().contains("aarch64")) "mac-aarch64" else "mac"
    else -> "linux"
  }

val javafxVersion = libs.versions.javafx.get()

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  when (effectiveOs) {
    "windows" -> implementation(compose.desktop.windows_x64)
    "macos" -> implementation(compose.desktop.macos_x64)
    else -> implementation(compose.desktop.currentOs)
  }

  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation(compose.components.resources)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.swing)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.org.json)

  implementation("org.openjfx:javafx-base:$javafxVersion:$jfxClassifier")
  implementation("org.openjfx:javafx-graphics:$javafxVersion:$jfxClassifier")
  implementation("org.openjfx:javafx-controls:$javafxVersion:$jfxClassifier")
  implementation("org.openjfx:javafx-media:$javafxVersion:$jfxClassifier")
  implementation("org.openjfx:javafx-swing:$javafxVersion:$jfxClassifier")

  testImplementation(kotlin("test"))
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
  testImplementation(compose.uiTest)
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}

tasks.register("generateDesktopIcon") {
  val sourcePng = rootProject.file("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
  val outDir = project.file("src/main/resources/icons")
  inputs.file(sourcePng)
  outputs.dir(outDir)
  doLast {
    if (!sourcePng.exists()) {
      logger.warn("Source launcher PNG not found at ${sourcePng.absolutePath}")
      return@doLast
    }
    outDir.mkdirs()
    val srcImage = ImageIO.read(sourcePng)

    fun scaleImage(src: BufferedImage, size: Int): BufferedImage {
      val dst = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val g2 = dst.createGraphics()
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.drawImage(src, 0, 0, size, size, null)
      g2.dispose()
      return dst
    }

    val png256 = scaleImage(srcImage, 256)
    ImageIO.write(png256, "png", outDir.resolve("decan-movie.png"))

    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val frames =
      sizes.map { sz ->
        val img = scaleImage(srcImage, sz)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        sz to baos.toByteArray()
      }

    fun writeLeShort(out: DataOutputStream, v: Int) {
      out.writeByte(v and 0xFF)
      out.writeByte((v ushr 8) and 0xFF)
    }

    fun writeLeInt(out: DataOutputStream, v: Int) {
      out.writeByte(v and 0xFF)
      out.writeByte((v ushr 8) and 0xFF)
      out.writeByte((v ushr 16) and 0xFF)
      out.writeByte((v ushr 24) and 0xFF)
    }

    val icoFile = outDir.resolve("decan-movie.ico")
    icoFile.outputStream().buffered().use { raw ->
      val out = DataOutputStream(raw)
      writeLeShort(out, 0) // Reserved
      writeLeShort(out, 1) // Type 1 = ICO
      writeLeShort(out, frames.size)

      var offset = 6 + (frames.size * 16)
      for ((sz, bytes) in frames) {
        out.writeByte(if (sz >= 256) 0 else sz)
        out.writeByte(if (sz >= 256) 0 else sz)
        out.writeByte(0)
        out.writeByte(0)
        writeLeShort(out, 1)
        writeLeShort(out, 32)
        writeLeInt(out, bytes.size)
        writeLeInt(out, offset)
        offset += bytes.size
      }
      for ((_, bytes) in frames) {
        out.write(bytes)
      }
      out.flush()
    }
  }
}

tasks.named("processResources") {
  dependsOn("generateDesktopIcon")
}

compose.desktop {
  application {
    mainClass = "com.decanmovie.desktop.MainKt"

    jvmArgs +=
      listOf(
        "-Dfile.encoding=UTF-8",
        "-XX:+UseG1GC",
        "-XX:MaxRAMPercentage=25.0",
        "-XX:MinHeapFreeRatio=15",
        "-XX:MaxHeapFreeRatio=35",
      )

    nativeDistributions {
      targetFormats(TargetFormat.Exe, TargetFormat.Msi)
      packageName = "Decan Movie"
      packageVersion = desktopVersion
      description = "Decan Movie — Cinema Desktop Streaming Player for Movies, Series, Anime & Dramas"
      vendor = "Decan Movie"
      copyright = "Copyright (c) 2026 Decan Movie"

      modules(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.net.http",
        "java.sql",
        "java.xml",
        "jdk.unsupported",
      )

      windows {
        console = false
        dirChooser = true
        perUserInstall = true
        shortcut = true
        menu = true
        menuGroup = "Decan Movie"
        upgradeUuid = "A8F6C9E4-4E2A-4D7F-9B15-31E9D2C87F10"
        iconFile.set(project.file("src/main/resources/icons/decan-movie.ico"))
      }
    }
  }
}

tasks.register<Zip>("packagePortableZip") {
  group = "distribution"
  description = "Packages the jpackage Windows/Desktop app-image into Decan-Movie-Portable.zip"
  dependsOn("createDistributable")
  archiveFileName.set("Decan-Movie-Portable.zip")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  from(layout.buildDirectory.dir("compose/binaries/main/app"))
}
