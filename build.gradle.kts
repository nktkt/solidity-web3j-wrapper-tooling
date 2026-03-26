import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

plugins {
    java
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

val junitVersion = "5.13.1"
val web3jVersion = "4.14.0"
val generatedWrapperDir = layout.buildDirectory.dir("generated/source/web3j/main/java")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf("src/main/java"))
            srcDir(generatedWrapperDir)
            include("com/example/soliditycompiler/**")
        }
    }
    named("test") {
        java {
            setSrcDirs(listOf("src/test/java"))
            include("com/example/soliditycompiler/**")
        }
    }
}

dependencies {
    implementation("org.web3j:core:$web3jVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val compileToolingJava by tasks.registering(JavaCompile::class) {
    group = "build"
    description = "Compiles the handwritten Solidity tooling classes."
    source = fileTree("src/main/java") {
        include("com/example/soliditycompiler/**/*.java")
    }
    classpath = sourceSets["main"].compileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("tooling-classes"))
    options.encoding = "UTF-8"
    options.release.set(21)
}

val toolingRuntimeClasspath = files(
    compileToolingJava.flatMap { it.destinationDirectory },
    configurations.runtimeClasspath
)

tasks.register<JavaExec>("runCompile") {
    group = "solidity"
    description = "Compiles Solidity contracts into ABI and BIN artifacts."
    dependsOn(compileToolingJava)
    classpath = toolingRuntimeClasspath
    mainClass.set("com.example.soliditycompiler.Main")
    workingDir = projectDir
    args("compile")
}

tasks.register<JavaExec>("runGenerateWrappers") {
    group = "solidity"
    description = "Generates Web3j Java wrappers from compiled ABI and BIN artifacts."
    dependsOn("runCompile")
    classpath = toolingRuntimeClasspath
    mainClass.set("com.example.soliditycompiler.Main")
    workingDir = projectDir
    args("generate-wrappers")
}

tasks.named<JavaCompile>("compileJava") {
    mustRunAfter("runGenerateWrappers")
}

tasks.register("runBuildAll") {
    group = "solidity"
    description = "Compiles Solidity, generates wrappers, and compiles Java sources including generated wrappers."
    dependsOn("runGenerateWrappers", "compileJava")
}

tasks.wrapper {
    gradleVersion = "9.2.0"
    distributionType = Wrapper.DistributionType.BIN
}
