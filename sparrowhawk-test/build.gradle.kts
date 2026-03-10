plugins {
    id("smithy-sparrowhawk-java.module-conventions")
    id("smithy-sparrowhawk-java.integ-test-conventions")
    id("software.amazon.smithy.gradle.smithy-base")

}

group = "software.amazon.smithy.java.sparrowhawk"

extra["displayName"] = "Smithy :: Java :: Codegen :: Sparrowhawk Test"
extra["moduleName"] = "software.amazon.smithy.java.sparrowhawk.test"

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":sparrowhawk-traits"))
    implementation(project(":sparrowhawk-types"))
    implementation(project(":sparrowhawk-codegen"))

}

tasks {
    compileJava {
        dependsOn(smithyBuild)
    }
}

afterEvaluate {
    val generatedCode = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "sparrowhawk-java-codegen")
    sourceSets {
        main {
            java {
                srcDir(generatedCode)
            }
        }
    }
}

// Helps Intellij IDE's discover smithy models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

// Disable spotbugs for this project
tasks.named("spotbugsMain") {
    enabled = false
}
