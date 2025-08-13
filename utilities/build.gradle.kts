plugins {
    id("java")
    id("maven-publish")
}

group = "com.tonic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")
    implementation("com.google.code.gson:gson:2.8.9")
}

tasks.test {
    useJUnitPlatform()
}

val TaskContainer.publishToMavenLocal: TaskProvider<DefaultTask>
    get() = named<DefaultTask>("publishToMavenLocal")