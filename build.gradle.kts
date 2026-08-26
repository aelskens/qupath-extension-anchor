plugins {
    // Apply shadow BEFORE qupath-conventions: the conventions plugin configures the shadowJar task
    // (including JavaFX platform handling), so the shadow plugin must already be present.
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

// Version comes from the VERSION file, or from the git tag when built in CI (tag "vX.Y.Z").
val githubTag = System.getenv("GITHUB_REF_NAME")
val releaseVersion = if (githubTag != null && githubTag.startsWith("v")) {
    githubTag.removePrefix("v")
} else {
    file("VERSION").readText().trim()
}

qupathExtension {
    name = "qupath-extension-anchor"
    group = "io.github.qupath"
    version = releaseVersion
    description = "Anchor: multi-image landmark annotation, view alignment and synchronization for QuPath"
    automaticModule = "io.github.qupath.extension.anchor"
}

repositories {
    maven { url = uri("https://maven.scijava.org/content/repositories/releases") }
    mavenCentral()
}

dependencies {
    // Provided by QuPath at runtime (not bundled into the extension jar).
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // Nonlinear (thin-plate spline) warping. Pure Java (pulls in jitk-tps + jama), no native binary.
    // Not on QuPath's classpath, so use `implementation` -> bundled into the shadow jar. Build the
    // distributable with `./gradlew shadowJar`. imglib2 core is provided by QuPath (qupath-imglib2).
    implementation("net.imglib2:imglib2-realtransform:4.0.4")

    // Apache Commons Math (org.apache.commons.math3.*) - used by the transform/ module for
    // least-squares affine and SVD-based rigid/similarity fits. qupath-core depends on it only at
    // *runtime* scope, so it is NOT on the compile classpath transitively; declare it explicitly.
    // Uses the `shadow` configuration (compile-visible, not duplicated into the jar) since the
    // QuPath app already provides it at runtime. Version pinned to match qupath-core (3.6.1).
    shadow("org.apache.commons:commons-math3:3.6.1")

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    // Gradle 9 no longer puts the JUnit Platform launcher on the test classpath automatically;
    // declare it explicitly. Version is aligned via the junit-bom that junit-jupiter imports.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
