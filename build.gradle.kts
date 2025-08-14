import java.net.URI

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
    id("maven-publish")
}

group = "com.tonic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = URI("https://repo.runelite.net")
    }
}

// Publishing configuration for root project
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "vitalite" // or whatever you want the root artifact named
        }
    }
}

// Apply maven-publish to all subprojects
subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    group = "com.tonic"
    version = "1.0-SNAPSHOT"

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                // artifactId defaults to the subproject name (api, remapper, utilities)
            }
        }
    }
}

// Custom task to clean and publish everything
tasks.register("cleanAndPublishAll") {
    description = "Cleans and publishes all projects to Maven Local"

    // Clean all projects first
    dependsOn(tasks.clean)
    subprojects.forEach {
        dependsOn(it.tasks.named("clean"))
    }

    // Then publish all projects
    dependsOn(tasks.publishToMavenLocal)
    subprojects.forEach {
        dependsOn(it.tasks.named("publishToMavenLocal"))
    }
}

tasks {
    build {
        finalizedBy("shadowJar")
    }

    // Regular jar task - not really needed since you're using shadowJar
    jar {
        manifest {
            attributes(mutableMapOf("Main-Class" to "com.tonic.Main"))
        }
    }

    shadowJar {
        archiveClassifier.set("shaded")
        isZip64 = true

        manifest {
            attributes(
                "Main-Class" to "com.tonic.Main",
                "Multi-Release" to "true"
            )
        }

        mergeServiceFiles()

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("module-info.class")

        transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
            resource = "META-INF/services/javax.swing.LookAndFeel"
        }

        transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
            resource = "META-INF/services/java.nio.file.spi.FileSystemProvider"
        }
    }
}

val TaskContainer.publishToMavenLocal: TaskProvider<DefaultTask>
    get() = named<DefaultTask>("publishToMavenLocal")

dependencies {
    compileOnly("net.runelite:api:latest.release")

    implementation("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    implementation("org.ow2.asm:asm:9.2")
    implementation("org.ow2.asm:asm-util:9.2")
    implementation("org.ow2.asm:asm-commons:9.2")

    implementation("io.netty:netty-all:5.0.0.Alpha2")
    implementation("com.google.code.gson:gson:2.8.9")

    implementation(project(":api"))
    implementation(project(":utilities"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.google.inject:guice:5.1.0")

    compileOnly("net.runelite:api:latest.release")
}

tasks.test {
    useJUnitPlatform()
}