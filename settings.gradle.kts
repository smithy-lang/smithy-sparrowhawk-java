pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "smithy-sparrowhawk-java"

include("sparrowhawk-codegen")
include("sparrowhawk-types")
include("sparrowhawk-traits")
