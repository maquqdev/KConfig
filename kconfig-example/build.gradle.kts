repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":kconfig-core"))
    implementation(project(":kconfig-bukkit"))
    compileOnly(libs.paper.api)
}
