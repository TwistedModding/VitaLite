plugins {
    id("java")
}

group = "com.tonic.packetmapper"
version = "1.11.15"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.runelite.net")
        content {
            includeGroupByRegex("net\\.runelite.*")
        }
    }
    mavenCentral()
}

val runeLiteVersion = "latest.release"

dependencies {
    compileOnly(project(":base-api"))
    compileOnly("net.runelite:client:$runeLiteVersion")
    compileOnly("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val TaskContainer.publishToMavenLocal: TaskProvider<DefaultTask>
    get() = named<DefaultTask>("publishToMavenLocal")

tasks.test {
    useJUnitPlatform()
}