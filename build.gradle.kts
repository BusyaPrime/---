plugins {
    id("java")
    id("application")
    id("me.champeau.jmh") version "0.7.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("jacoco")
}

application {
    mainClass.set("pdelab.runtime.Main")
}

group = "pdelab"
version = "1.0.0"

dependencyLocking {
    lockAllConfigurations()
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson для парсинга JSON конфигов (чтоб не писать велосипеды)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    
    // SLF4J через Logback для трушного структурированного логирования
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // PicoCLI для создания четких и мощных CLI интерфейсов
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // JUnit 5 для тестов (без тестов на прод не пускают)
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    // JMH API и Генераторы (чтобы мерять наносекунды и доказывать перф)
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


tasks.test {
    useJUnitPlatform {
        excludeTags("slow")
    }
    finalizedBy(tasks.jacocoTestReport) // Репорт пишем железобетонно после каждого прогона тестов
    
    // Поднимаем лимиты памяти JVM для тестов
    jvmArgs("-Xmx1G", "-XX:+UseG1GC")
}

val verificationTest by tasks.registering(Test::class) {
    description = "Крутит жесткие, медленные ручные тесты на сходимость и валидацию матана."
    group = "verification"
    useJUnitPlatform {
        includeTags("slow")
    }
    jvmArgs("-Xmx2G", "-XX:+UseG1GC")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, verificationTest) // Тесты обязаны пробежать до сборки покрытия (coverage)
    executionData(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, verificationTest)
    executionData(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
    violationRules {
        rule {
            limit {
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Форсим стандартную компиляцию выплевывать высокооптимизированный байткод (high-performance ops)
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

// Собираем Fat jar-ник для запуска прямо на кластерах через shadowJar (шоб все зависимости были внутри)
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("pdelab")
    archiveVersion.set("")
    archiveClassifier.set("all")
    mergeServiceFiles()
}
