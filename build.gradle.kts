plugins {
    kotlin("jvm") version "1.4.10"
}

group = "xyz.acrylicstyle"
version = "1.0.2"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repo2.acrylicstyle.xyz/") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.ow2.asm", "asm", "8.0.1")
    implementation("org.ow2.asm", "asm-commons", "8.0.1")
    implementation("org.ow2.asm", "asm-tree", "8.0.1")
    implementation("me.tongfei", "progressbar", "0.8.1")
    implementation("xyz.acrylicstyle", "java-util-all", "0.13.2")
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

    withType<ProcessResources> {
        filteringCharset = "UTF-8"
        from(sourceSets.main.get().resources.srcDirs) {
            include("**")
        }
        from(projectDir) { include("LICENSE") }
    }

    withType<Jar> {
        manifest {
            attributes(
                "Main-Class" to "xyz.acrylicstyle.parameterRemapper.ParameterRemapperApp"
            )
        }
        from(configurations.getByName("implementation").apply { isCanBeResolved = true }.map { if (it.isDirectory) it else zipTree(it) })
    }
}
