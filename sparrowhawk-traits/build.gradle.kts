plugins {
    id("smithy-sparrowhawk-java.module-conventions")
}

description = "This module provides traits that are currently unique to the Sparrowhawk format"

extra["displayName"] = "Smithy :: Java :: Sparrowhawk Traits"
extra["moduleName"] = "software.amazon.smithy.java.sparrowhawk.traits"

dependencies {
    api(libs.smithy.model)
}
