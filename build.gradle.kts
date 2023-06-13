plugins {
    kotlin("jvm") version embeddedKotlinVersion
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("org.scijava:pom-scijava:31.1.0"))

    //api("sc.iview:sciview")
    implementation("sc.iview:sciview")
    implementation("net.imagej:imagej-common")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}