dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.snakeyaml)
    api(libs.slf4j.api)

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
