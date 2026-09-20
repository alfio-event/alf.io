import ch.digitalfondue.jfiveparse.Element
import ch.digitalfondue.jfiveparse.HtmlSerializer
import ch.digitalfondue.jfiveparse.Node
import ch.digitalfondue.jfiveparse.NodeMatcher
import ch.digitalfondue.jfiveparse.Parser
import ch.digitalfondue.jfiveparse.Selector
import ch.digitalfondue.mjml4j.Mjml4j
import com.github.gradle.node.NodeExtension
import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.variant.VariantComputer
import com.github.gradle.node.variant.computeNodeDir
import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

buildscript {

    dependencies {
        classpath(libs.license.maven.plugin)
        classpath(libs.postgresql.driver)
        // for processing the index.html at build time
        classpath(libs.jfiveparse)
        // for processing the mjml templates at build time
        classpath(libs.mjml4j)
    }


    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.lombok)
    java
    idea
    `project-report`
    alias(libs.plugins.kordamp.jacoco)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.hierynomus.license)
    alias(libs.plugins.gradle.release)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.sonarqube)
    // id("net.ltgt.errorprone") version "3.1.0"
    alias(libs.plugins.gradle.node)
    alias(libs.plugins.forbiddenapis)
}

// see the comment next to "flyway" in gradle/libs.versions.toml
extra["flyway.version"] = libs.versions.flyway.get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// MJML email templates translations to HTML
val nodeExtension = project.extensions.getByType<NodeExtension>()

node {
    download = true
    version = "22.22.3"
    pnpmVersion = "11.1.2"
}

// task groups for the tasks declared by this build
val FRONTEND_GROUP = "frontend"
val DISTRIBUTION_GROUP = "distribution"

//as pointed out by @facundofarias, we should validate minimum javac version
tasks.register("validate") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks that the JDK running the build is recent enough."
    //check JDK version
    val javaVersion = JavaVersion.current()
    if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
        throw GradleException("A Java JDK 25+ is required to build the project.")
    }
}

val profile = project.findProperty("profile")?.toString() ?: "dev"

// default settings
var datasourceUrl = "jdbc:postgresql://localhost:5432/alfio"
var datasourceUsername = "postgres"
var datasourcePassword = "password"
//var springProfilesActive = "dev,demo"
var springProfilesActive = "dev"

when (profile) {
    "docker-test" -> {
        datasourceUrl = "jdbc:postgresql://0.0.0.0:5432/postgres"
        datasourceUsername = "postgres"
        datasourcePassword = "postgres"
    }
    "travis" -> {
        springProfilesActive = "travis"
    }
}

configurations {
    // not created by any of the applied plugins, but referenced below
    maybeCreate("providedCompile")
    maybeCreate("providedRuntime")

    configureEach {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {

    annotationProcessor(libs.spring.boot.configuration.processor)

    implementation(libs.java.jwt)
    implementation(libs.jackson.dataformat.csv)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.properties.migrator) {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation(libs.spring.session.jdbc)
    implementation(libs.npjt.extra)
    implementation(libs.jmustache)
    implementation(libs.lat.long.to.timezone)
    implementation(libs.openhtmltopdf.core)
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.jfiveparse)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.stripe.java)
    implementation(libs.paypal.checkout.sdk)
    implementation(libs.gson)
    implementation(libs.gson.javatime.serialisers) {
        exclude(module = "gson")
    }
    implementation(libs.commons.lang3)
    implementation(libs.commons.text)
    implementation(libs.commons.collections4)
    implementation(libs.commons.codec)
    implementation(libs.biweekly)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.passkit4j) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcmail-jdk15on")
    }
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcmail)
    implementation(libs.caffeine)
    implementation(libs.scribejava.core)
    implementation(libs.vatchecker)
    implementation(libs.basicxlsx)
    implementation(libs.imgscalr)
    implementation(libs.rhino.runtime)
    implementation(libs.google.auth.oauth2.http)
    implementation(libs.spring.boot.starter.webmvc) {
        exclude(module = "spring-boot-starter-logging")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        exclude(group = "org.hibernate.validator")
    }
    implementation(libs.spring.boot.starter.security) {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation(libs.spring.security.oauth2.client)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.spring.boot.starter.mail) {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation(libs.spring.boot.starter.restclient)
    implementation("org.springframework.boot:spring-boot@jar") {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-autoconfigure@jar") {
        exclude(module = "spring-boot-starter-logging")
    }
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.spring.boot.starter.jetty) {
        exclude(group = "org.eclipse.jetty.websocket", module = "websocket-server")
        exclude(group = "org.eclipse.jetty.websocket", module = "javax-websocket-server-impl")
    }
    implementation(libs.joda.money)


    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "spring-boot-starter-logging")
    }
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.springdoc.openapi.starter.webmvc.ui)
    testImplementation(libs.openapi.diff.core) {
        exclude(group = "org.mozilla", module = "rhino")
        exclude(group = "io.swagger.core.v3")
    }
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.selenium.java)

    //errorprone("com.google.errorprone:error_prone_core:2.24.0")
}

sourceSets {
    main {
        resources {
            srcDir("src/main/webapp")
        }
    }
}

// -- license configuration

license {
    header = rootProject.file("config/HEADER")
    strictCheck = true
    isIgnoreFailures = false
    mapping(
        mapOf(
            "java" to "JAVADOC_STYLE",
            "sql" to "DOUBLEDASHES_STYLE"
        )
    )
    (this as ExtensionAware).extra["year"] = "2014-" + Year.now().toString()
    include("**/*.java")
    include("**/*.sql")
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "alfio-event")
        property("sonar.projectKey", "alfio-event_alf.io")
        property("sonar.login", System.getenv("SONARCLOUD_TOKEN"))
        property("sonar.gradle.skipCompile", "true")
    }
}


// Jackson 2 is on the classpath because java-jwt, biweekly, passkit4j and scribejava need it.
// alf.io itself is on Jackson 3: both are visible to the compiler and `ObjectMapper` exists in
// both, so an accidental Jackson 2 import compiles and silently uses an unconfigured mapper.
tasks.withType<CheckForbiddenApis>().configureEach {
    bundledSignatures = emptySet()
    signatures = listOf(
        "@defaultMessage alf.io is on Jackson 3: use tools.jackson.* (or alfio.util.Json)",
        "com.fasterxml.jackson.databind.**",
        "com.fasterxml.jackson.core.**",
        "com.fasterxml.jackson.dataformat.**",
        "com.fasterxml.jackson.datatype.**"
    )
}

tasks.processResources {

    doLast {

        val gradleProperties = File(destinationDir, "application.properties")
        val properties = Properties()

        check(gradleProperties.isFile)

        gradleProperties.reader().use { properties.load(it) }
        properties["alfio.version"] = project.version.toString()
        properties["alfio.build-ts"] = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
        gradleProperties.writer().use { properties.store(it, null) }
    }
}

tasks.compileTestJava {
//    options.errorprone.disable("UnusedVariable",
//        "MixedMutabilityReturnType",
//        "MissingOverride",
//        "ImmutableEnumChecker", // not too useful, as it does not take into account the actual value of the field
//        "AlmostJavadoc",
//        "MissingSummary",
//        "EscapedEntity",
//        "EmptyBlockTag",
//        "SameNameButDifferent"
//    )
}

tasks.compileJava {
    options.compilerArgs = mutableListOf("-parameters", "-Xlint:all,-serial,-processing")

    // both checks are problematic with lombok code
//    options.errorprone.disable("UnusedVariable",
//        "MixedMutabilityReturnType",
//        "MissingOverride",
//        "ImmutableEnumChecker", // not too useful, as it does not take into account the actual value of the field
//        "AlmostJavadoc",
//        "MissingSummary",
//        "EscapedEntity",
//        "EmptyBlockTag",
//        "SameNameButDifferent",
//        "ReturnValueIgnored"
//    )

    dependsOn(tasks.processResources)
}

//propagate the system properties to the tests
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        info.events = setOf(TestLogEvent.FAILED)
        showStandardStreams = project.hasProperty("verbose")
    }
    reports {
        junitXml.required = true  // Enable JUnit XML report
        html.required = true      // Enable HTML report (optional)
    }
    doFirst {
        val props = System.getProperties().entries.associate { (key, value) -> key.toString() to value }.toMutableMap()
        props.remove("java.endorsed.dirs")
        setSystemProperties(props)
    }
}

springBoot {
    mainClass = "alfio.config.SpringBootLauncher"
}

tasks.bootRun {
    dependsOn("copyFrontendDev", "frontendAdminPnpmInstall")
    finalizedBy("killViteDevServer")
    val externalConfig = File("./custom.jvmargs")
    val opts = mutableListOf(
        "-Dspring.profiles.active=$springProfilesActive",
        "-Ddatasource.url=$datasourceUrl",
        "-Ddatasource.username=$datasourceUsername",
        "-Ddatasource.password=$datasourcePassword",
        "-Dalfio.version=${project.version}",
        "-Dalfio.build-ts=${ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}"
    )
    if (externalConfig.exists()) {
        opts += externalConfig.readLines()
    }
    jvmArgs = opts
    classpath(layout.buildDirectory.dir("index-transformed"), layout.buildDirectory.dir("frontend-dev"))

    doFirst {
        logger.lifecycle("Starting Vite dev server in frontend/admin ...")
        val viteDir = file("${project.projectDir}/frontend/admin")
        // node.pnpmCommand is a bare command name, which ProcessBuilder can only resolve through the
        // inherited PATH. That PATH is the one gradle was started with, so it works from a login shell
        // but not from the IDE, where it fails with "Cannot run program pnpm". Use the toolchain the
        // node plugin downloaded instead, and put node on the PATH of the spawned process, since the
        // pnpm entry point is a "#!/usr/bin/env node" script and vite needs node as well.
        val variantComputer = VariantComputer()
        val nodeBinDir = variantComputer.computeNodeBinDir(computeNodeDir(nodeExtension), nodeExtension.resolvedPlatform).get().asFile
        val pnpmBinDir = variantComputer.computePnpmBinDir(variantComputer.computePnpmDir(nodeExtension), nodeExtension.resolvedPlatform)
        val pnpmExe = variantComputer.computePnpmExec(nodeExtension, pnpmBinDir).get()

        val viteProcessBuilder = ProcessBuilder(pnpmExe, "run", "dev")
            .directory(viteDir)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
        val inheritedPath = System.getenv("PATH")
        val absolutePath = if (inheritedPath.isNullOrEmpty()) {
            nodeBinDir.absolutePath
        } else {
            nodeBinDir.absolutePath + File.pathSeparator + inheritedPath
        }
        viteProcessBuilder.environment()["PATH"] = absolutePath
        val viteProcess = viteProcessBuilder.start()
        project.extra.set("viteDevProcess", viteProcess)
    }
}

tasks.register("killViteDevServer") {
    group = ApplicationPlugin.APPLICATION_GROUP
    description = "Stops the Vite dev server started by bootRun."
    doLast {
        val proc = project.extra.properties["viteDevProcess"] as Process?
        if (proc != null && proc.isAlive) {
            logger.lifecycle("Stopping Vite dev server ...")
            proc.descendants().forEach { it.destroyForcibly() }
            proc.destroyForcibly()
        }
    }
}

tasks.register<Copy>("copyFrontendDev") {
    group = FRONTEND_GROUP
    description = "Copies the public frontend bundle where bootRun can serve it from."
    dependsOn("publicFrontendIndexTransform")
    from("frontend/dist/")
    into(layout.buildDirectory.dir("frontend-dev/resources/"))
}

// -- code-coverage

// 0.8.11 (the version the kordamp plugin defaults to) cannot instrument
// class file major version 69 (Java 25)
config {
    coverage {
        jacoco {
            toolVersion = "0.8.15"
        }
    }
}

tasks.jacocoTestReport {
    group = "Reporting"
    description = "Generate Jacoco coverage reports after running tests."
    additionalSourceDirs.from(project.files(sourceSets.main.get().allSource.srcDirs))
    sourceDirectories.from(project.files(sourceSets.main.get().allSource.srcDirs))
    classDirectories.from(project.files(sourceSets.main.get().output))
    reports {
        xml.required = true
        html.required = true
        csv.required = true
    }
}

tasks.register<Copy>("dockerize") {
    group = DISTRIBUTION_GROUP
    description = "Prepares the Dockerfile used to build the alf.io image."
    from("src/main/dist/Dockerfile")
    into(layout.buildDirectory.dir("dockerize"))
    filter(mapOf("tokens" to mapOf("ALFIO_VERSION" to project.version.toString())), ReplaceTokens::class.java)
}

val frontendPnpmInstall = tasks.register<PnpmTask>("frontendPnpmInstall") {
    group = FRONTEND_GROUP
    description = "Installs the public frontend dependencies."
    args = listOf("--prefix", "${project.projectDir}/frontend", "ci")
}

val frontendBuild = tasks.register<PnpmTask>("frontendBuild") {
    group = FRONTEND_GROUP
    description = "Builds the public (Angular) frontend."
    dependsOn(frontendPnpmInstall)
    args = listOf("--prefix", "${project.projectDir}/frontend", "run", "build")
    outputs.dir("${project.projectDir}/frontend/dist")
}

val frontendAdminPnpmInstall = tasks.register<PnpmTask>("frontendAdminPnpmInstall") {
    group = FRONTEND_GROUP
    description = "Installs the admin frontend dependencies."
    args = listOf("--prefix", "${project.projectDir}/frontend/admin", "ci")
}

val frontendAdminBuild = tasks.register<PnpmTask>("frontendAdminBuild") {
    group = FRONTEND_GROUP
    description = "Builds the admin (Lit/Vite) frontend."
    dependsOn(frontendAdminPnpmInstall)
    args = listOf("--prefix", "${project.projectDir}/frontend/admin", "run", "build")
    outputs.dir("${project.projectDir}/frontend/admin/dist")
}

tasks.clean {
    doFirst {
        delete("${project.projectDir}/frontend/dist")
        delete("${project.projectDir}/frontend/admin/dist")
    }
}

val publicFrontendIndexTransform = tasks.register<FrontendIndexTransformTask>("publicFrontendIndexTransform") {
    group = FRONTEND_GROUP
    description = "Rewrites the public frontend index.html so that its assets are served under frontend-public/."
    dependsOn(frontendBuild)
    basePath.set("frontend-public/")
    indexHtml.set(layout.projectDirectory.file("frontend/dist/alfio-public-frontend/index.html"))
    indexHtmlTransformed.set(layout.buildDirectory.file("index-transformed/alfio-public-frontend-index.html"))
}

//val adminFrontendIndexTransform = tasks.register<FrontendIndexTransformTask>("adminFrontendIndexTransform") {
//    group = FRONTEND_GROUP
//    description = "Rewrites the admin frontend index.html so that its assets are served under frontend-admin/."
//    dependsOn(frontendBuild)
//    basePath.set("frontend-admin/")
//    indexHtml.set(layout.projectDirectory.file("frontend/dist/alfio-admin-frontend/index.html"))
//    indexHtmlTransformed.set(layout.buildDirectory.file("index-transformed/alfio-admin-frontend-index.html"))
//}

tasks.register<Copy>("distribution") {
    group = DISTRIBUTION_GROUP
    description = "Assembles the Docker build context: Dockerfile + alfio-boot.jar."
    from(project.layout.buildDirectory.file("libs/alfio-${project.version}-boot.jar"))
    rename { "alfio-boot.jar" }
    into(layout.buildDirectory.dir("dockerize"))
    dependsOn(publicFrontendIndexTransform, tasks.build, tasks.named("dockerize"))
}

tasks.register<Copy>("clever") {
    group = DISTRIBUTION_GROUP
    description = "Assembles the boot jar for a Clever Cloud deployment."
    from(project.layout.buildDirectory.file("libs/alfio-${project.version}-boot.jar"))
    rename { "alfio-boot.jar" }
    into(project.layout.buildDirectory.dir("clevercloud"))
    dependsOn(publicFrontendIndexTransform/*, adminFrontendIndexTransform*/, tasks.build)
}

release {
    buildTasks = listOf("distribution")
    git {
        requireBranch.set("")
        pushToRemote.set("origin")
        signTag.set(true)
    }
}

tasks.bootJar {
    dependsOn(publicFrontendIndexTransform)//, adminFrontendIndexTransform)
    archiveClassifier.set("boot")
    from(frontendBuild) {
        into("BOOT-INF/classes/resources/")
    }
    from(frontendAdminBuild) {
        into("BOOT-INF/classes/resources/alfio-admin-frontend/")
    }
    from(publicFrontendIndexTransform) {
        rename("alfio-public-frontend-index.html", "BOOT-INF/classes/alfio-public-frontend-index.html")
    }
    /*from(adminFrontendIndexTransform) {
        rename("alfio-admin-frontend-index.html", "BOOT-INF/classes/alfio-admin-frontend-index.html")
    }*/
    val bowerDir = "resources/bower_components"
    val excludesFile = File("./lib_exclude")
    if (excludesFile.exists()) {
        exclude(excludesFile.readLines().map { bowerDir + it })
    }
}

// MJML email templates translations to HTML

val mjml4jToHtml = tasks.register<Mjml4jTransformTask>("mjml4jToHtml") {
    group = BasePlugin.BUILD_GROUP
    description = "Renders the MJML email templates to HTML mustache templates."
    files.from(
        fileTree("${layout.projectDirectory}/src/main/resources/alfio/mjml/").matching {
            include("**/*.mjml")
            include { !it.isDirectory }
        }
    )
    outputDir.set(layout.buildDirectory.dir("generated/resources/alfio/templates/"))
}

// We build HTML templates from MJML source files and then save them under "build/generated/resources" in order to be
// included in the final artifact.
// TODO should we do the same for plaintext templates? See https://gist.github.com/brasilikum/3cd515bad5541ca6c76873faf10445c2
tasks.processResources {
    dependsOn(mjml4jToHtml)
}
sourceSets.main.get().output.dir(mapOf("builtBy" to mjml4jToHtml), layout.buildDirectory.dir("generated/resources/"))

// transform index.html
abstract class FrontendIndexTransformTask : DefaultTask() {

    @get:InputFile
    abstract val indexHtml: RegularFileProperty

    @get:OutputFile
    abstract val indexHtmlTransformed: RegularFileProperty

    @get:Input
    abstract val basePath: Property<String>

    init {
        basePath.convention("frontend-public/")
    }


    @Suppress("UNCHECKED_CAST")
    @TaskAction
    fun doWork() {
        val indexDoc = indexHtml.get().asFile.inputStream().use { resource ->
            Parser().parse(InputStreamReader(resource, StandardCharsets.UTF_8))
        }

        val scriptNodes = Selector.select().element("script").toMatcher() as NodeMatcher<Node>

        indexDoc.getAllNodesMatching(scriptNodes).filterIsInstance<Element>().forEach {
            it.setAttribute("src", basePath.get() + it.getAttribute("src"))
        }

        val cssNodes = Selector.select().element("link").attrValEq("rel", "stylesheet").toMatcher() as NodeMatcher<Node>
        indexDoc.getAllNodesMatching(cssNodes).filterIsInstance<Element>().forEach {
            it.setAttribute("href", basePath.get() + it.getAttribute("href"))
        }

        indexHtmlTransformed.get().asFile.writeText(HtmlSerializer.serialize(indexDoc), StandardCharsets.UTF_8)
    }
}

abstract class Mjml4jTransformTask : DefaultTask() {

    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun doWork() {
        val target = outputDir.get().asFile
        files.asFileTree.visit {
            if (!file.isDirectory) {
                val templateOutput = Mjml4j.render(file.readText(StandardCharsets.UTF_8))
                val outputName = file.name.substring(0, file.name.lastIndexOf('.')) + ".ms"
                File(target, outputName).writeText(templateOutput, StandardCharsets.UTF_8)
            }
        }
    }
}
