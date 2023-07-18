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
    implementation(platform("org.scijava:pom-scijava:35.1.1"))

    implementation("net.imagej:imagej-common")

    //api("sc.iview:sciview")
    implementation("sc.iview:sciview")
    implementation("org.yaml:snakeyaml:[1.29, 1.33]!!")

    implementation("org.mastodon:mastodon:1.0.0-beta-27-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}