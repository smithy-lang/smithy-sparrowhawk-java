plugins {
    id("smithy-sparrowhawk-java.java-conventions")
    id("smithy-sparrowhawk-java.integ-test-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    smithyBuild(project(":codegen:client"))
    smithyBuild(project(":codegen:server"))
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientOutputPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientOutputPath)
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}
