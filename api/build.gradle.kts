plugins {
    id("java")
    id("maven-publish")
}

group = "com.tonic"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven { url = uri("https://repo.runelite.net") }
    mavenCentral()
    jcenter()
}

val runeLiteVersion = "latest.release"

dependencies {
    compileOnly("net.runelite:client:$runeLiteVersion")

    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")

    testImplementation("junit:junit:4.12")
    testImplementation("net.runelite:client:$runeLiteVersion")
    testImplementation("net.runelite:jshell:$runeLiteVersion")

    implementation("org.json:json:20230227")
    implementation("org.roaringbitmap:RoaringBitmap:0.9.44")
    implementation("org.benf:cfr:0.152")

    // IntelliJ’s @MagicConstant, etc.
    compileOnly("com.intellij:annotations:12.0")          // org.intellij.lang.annotations.MagicConstant :contentReference[oaicite:0]{index=0}

    // JetBrains’ @Range, @NotNull, @Nullable, etc.
    compileOnly("org.jetbrains:annotations:26.0.2")
}