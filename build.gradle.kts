plugins {
    kotlin("kapt") version "1.9.21"
    kotlin("jvm") version embeddedKotlinVersion
}

group = "org.mastodon"
version = "0.9"

repositories {
    mavenCentral()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("org.scijava:pom-scijava:37.0.0"))
    implementation("org.yaml:snakeyaml:[1.29, 1.33]!!")

    implementation("net.imagej:imagej")

    //api("sc.iview:sciview")
    implementation("sc.iview:sciview")

    implementation("org.mastodon:mastodon:1.0.0-beta-28")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    val scijavaCommonVersion = "2.94.2"                 //TODO: shouldn't be updated?? look how this is done in scenery/sciview
    kapt("org.scijava:scijava-common:$scijavaCommonVersion") {
        exclude("org.lwjgl")
    }
}

tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath); into("deps")
}

tasks.test {
    useJUnitPlatform()
}