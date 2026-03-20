repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":kconfig-core"))
    compileOnly(libs.paper.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}
