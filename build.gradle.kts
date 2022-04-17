plugins {
    kotlin("jvm") version "1.6.20"
}

group = "xyz.acrylicstyle"
version = "1.0.4"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repo.acrylicstyle.xyz/repository/maven-public/") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.20")
    implementation("org.ow2.asm", "asm", "9.2")
    implementation("org.ow2.asm", "asm-commons", "9.2")
    implementation("org.ow2.asm", "asm-tree", "9.2")
    implementation("me.tongfei", "progressbar", "0.8.1")
    implementation("xyz.acrylicstyle.util", "common", "0.16.4")
    implementation("xyz.acrylicstyle.util", "collection", "0.16.4")
    implementation("xyz.acrylicstyle.util", "all", "0.16.4") {
        exclude("org.ow2.asm", "asm")
        exclude("org.ow2.asm", "asm-commons")
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "utf-8"
    }

    withType<Jar> {
        manifest {
            attributes(
                "Main-Class" to "xyz.acrylicstyle.parameterRemapper.ParameterRemapperApp"
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(configurations.getByName("implementation").apply { isCanBeResolved = true }.map { if (it.isDirectory) it else zipTree(it) })
    }
}
