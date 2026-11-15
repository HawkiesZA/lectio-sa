plugins {
    application
    kotlin("jvm")
}

application {
    mainClass.set("za.co.hawkiesza.lectiosa.contenttool.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    workingDir(rootProject.projectDir)
}
