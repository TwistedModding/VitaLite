plugins {
    id("java")
}

group = "com.tonic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.ow2.asm:asm-util:9.2")
    implementation("org.ow2.asm:asm-commons:9.2")

    implementation("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")

    implementation("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("org.bouncycastle:bctls-jdk18on:1.81")
    implementation("org.bouncycastle:bcutil-jdk18on:1.81")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")

    implementation("com.formdev:flatlaf:2.4")
}

tasks.test {
    useJUnitPlatform()
}