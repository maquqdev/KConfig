import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "club.skidware"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    apply(plugin = "jacoco")

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set("YAML configuration library for Kotlin")
                    url.set("https://skidware.club")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("maquq")
                            name.set("maquqdev")
                        }
                    }
                }
            }
        }

        repositories {
            mavenLocal()
        }
    }
}

project(":kconfig-example") {
    tasks.matching { it.name.contains("publish", ignoreCase = true) }.configureEach {
        enabled = false
    }
}

project(":kconfig-test") {
    tasks.matching { it.name.contains("publish", ignoreCase = true) }.configureEach {
        enabled = false
    }
}
