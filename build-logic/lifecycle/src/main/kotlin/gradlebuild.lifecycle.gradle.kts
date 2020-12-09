/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import gradlebuild.basics.BuildEnvironment
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.timerTask

// Lifecycle tasks used to to fan out the build into multiple builds in a CI pipeline.

val ciGroup = "CI Lifecycle"

val compileAllBuild = "compileAllBuild"

val sanityCheck = "sanityCheck"

val quickTest = "quickTest"

val platformTest = "platformTest"

val allVersionsCrossVersionTest = "allVersionsCrossVersionTest"

val allVersionsIntegMultiVersionTest = "allVersionsIntegMultiVersionTest"

val soakTest = "soakTest"

val smokeTest = "smokeTest"


setupTimeoutMonitorOnCI()
setupGlobalState()

tasks.registerDistributionsPromotionTasks()

tasks.registerEarlyFeedbackRootLifecycleTasks()

/**
 * Print all stacktraces of running JVMs on the machine upon timeout. Helps us diagnose deadlock issues.
 */
fun setupTimeoutMonitorOnCI() {
    if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
        val timer = Timer(true).apply {
            schedule(
                timerTask {
                    exec {
                        commandLine(
                            "${System.getProperty("java.home")}/bin/java",
                            project.layout.projectDirectory.file("subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/timeout/JavaProcessStackTracesMonitor.java").asFile,
                            project.layout.projectDirectory.asFile.absolutePath
                        )
                    }
                },
                determineTimeoutMillis()
            )
        }
        gradle.buildFinished {
            timer.cancel()
        }
    }
}

fun determineTimeoutMillis() = when {
    isRequestedTask(compileAllBuild) || isRequestedTask(sanityCheck) || isRequestedTask(quickTest) -> Duration.ofMinutes(30).toMillis()
    isRequestedTask(smokeTest) -> Duration.ofHours(1).plusMinutes(30).toMillis()
    else -> Duration.ofHours(2).plusMinutes(45).toMillis()
}

fun setupGlobalState() {
    if (needsToUseTestVersionsPartial()) {
        globalProperty("testVersions" to "partial")
    }
    if (needsToUseTestVersionsAll()) {
        globalProperty("testVersions" to "all")
    }
}

fun needsToUseTestVersionsPartial() = isRequestedTask(platformTest)

fun needsToUseTestVersionsAll() = isRequestedTask(allVersionsCrossVersionTest)
    || isRequestedTask(allVersionsIntegMultiVersionTest)
    || isRequestedTask(soakTest)

fun TaskContainer.registerEarlyFeedbackRootLifecycleTasks() {
    named(compileAllBuild) {
        description = "Initialize CI Pipeline by priming the cache before fanning out"
        group = ciGroup
        gradle.includedBuild("subprojects").task(":base-services:createBuildReceipt")
    }

    named(sanityCheck) {
        description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
        group = "verification"
        dependsOn(
            gradle.includedBuild("build-logic-commons").task(":check"),
            gradle.includedBuild("build-logic").task(":check"),
            gradle.includedBuild("subprojects").task(":docs:checkstyleApi"),
            gradle.includedBuild("subprojects").task(":internal-build-reports:allIncubationReportsZip"),
            gradle.includedBuild("subprojects").task(":architecture-test:checkBinaryCompatibility"),
            gradle.includedBuild("subprojects").task(":docs:javadocAll"),
            gradle.includedBuild("subprojects").task(":architecture-test:test"),
            gradle.includedBuild("subprojects").task(":tooling-api:toolingApiShadedJar"),
            gradle.includedBuild("subprojects").task(":performance:verifyPerformanceScenarioDefinitions"),
            ":checkSubprojectsInfo"
        )
    }
}

/**
 * Task that are called by the (currently separate) promotion build running on CI.
 */
fun TaskContainer.registerDistributionsPromotionTasks() {
    register("packageBuild") {
        description = "Build production distros and smoke test them"
        group = "build"
        dependsOn(
            gradle.includedBuild("subprojects").task(":distributions-full:verifyIsProductionBuildEnvironment"),
            gradle.includedBuild("subprojects").task(":distributions-full:buildDists"),
            gradle.includedBuild("subprojects").task(":distributions-integ-tests:forkingIntegTest"),
            gradle.includedBuild("subprojects").task(":docs:releaseNotes"),
            gradle.includedBuild("subprojects").task(":docs:incubationReport"),
            gradle.includedBuild("subprojects").task(":docs:checkDeadInternalLinks")
        )
    }
}

fun globalProperty(pair: Pair<String, Any>) {
    val propertyName = pair.first
    val value = pair.second
    if (hasProperty(propertyName)) {
        val otherValue = property(propertyName)
        if (value.toString() != otherValue.toString()) {
            throw RuntimeException("Attempting to set global property $propertyName to two different values ($value vs $otherValue)")
        }
    }
    extra.set(propertyName, value)
}

fun isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
    || gradle.startParameter.taskNames.any { it.contains(":$taskName") }
