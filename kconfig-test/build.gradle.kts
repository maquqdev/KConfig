dependencies {
    testImplementation(project(":kconfig-core"))
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.snakeyaml)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.strikt.core)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
