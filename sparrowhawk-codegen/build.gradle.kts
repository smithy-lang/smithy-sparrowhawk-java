plugins {
    id("smithy-sparrowhawk-java.module-conventions")
    id("smithy-sparrowhawk-java.integ-test-conventions")
}

description = "This module provides the codegen functionality for Smithy Sparrowhawk"
group = "software.amazon.smithy.java.codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Sparrowhawk"
extra["moduleName"] = "software.amazon.smithy.java.sparrowhawk.codegen"

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":sparrowhawk-traits"))
    implementation(project(":sparrowhawk-types"))
}
