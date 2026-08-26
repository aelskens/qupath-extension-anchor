pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.scijava.org/content/repositories/releases") }
    }
}

// QuPath version this extension is built against. Verified stable release (2026-03-02).
// APIs the align/sync/overlay feature depends on are unchanged from 0.6.0.
qupath {
    version = "0.7.0"
}

plugins {
    id("io.github.qupath.qupath-extension-settings") version "0.2.1"
}
