/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

/**
 * Smoke test building gradle/gradle with configuration cache enabled.
 *
 * gradle/gradle requires Java >=9 and <=11 to build, see {@link GradleBuildJvmSpec}.
 */
@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
class GradleBuildConfigurationCacheSmokeTest extends AbstractGradleceptionSmokeTest {

    def "can build gradle with configuration cache enabled"() {
        given:
        def supportedTasks = [
            // todo broken by kotlin upgrade
            // ":distributions-full:binDistributionZip",
            ":distribution-core:tooling-api:publishLocalPublicationToLocalRepository",
            // ":configuration-cache:embeddedIntegTest", "--tests=org.gradle.configurationcache.ConfigurationCacheIntegrationTest"
        ]

        when:
        configurationCacheRun(supportedTasks, 0)

        then:
        result.output.count("Calculating task graph as no configuration cache is available") == 1

        when: "reusing the configuration cache in the same daemon"
        configurationCacheRun(supportedTasks, 0)

        then:
        result.output.count("Reusing configuration cache") == 1
        // result.task(":distributions-full:binDistributionZip").outcome == TaskOutcome.UP_TO_DATE
        result.task(":distribution-core:tooling-api:publishLocalPublicationToLocalRepository").outcome == TaskOutcome.SUCCESS
        // result.task(":configuration-cache:embeddedIntegTest").outcome == TaskOutcome.UP_TO_DATE

        when:
        run(["clean"])

        and: "reusing the configuration cache in a different daemon"
        configurationCacheRun(supportedTasks + ["--info"], 1)

        then:
        result.output.count("Reusing configuration cache") == 1
        result.output.contains("Starting build in new daemon")

        /*
        and:
        file("subprojects/distributions-full/build/distributions").allDescendants().count { it ==~ /gradle-.*-bin.zip/ } == 1
        result.task(":configuration-cache:embeddedIntegTest").outcome == TaskOutcome.SUCCESS
        assertTestClassExecutedIn("subprojects/configuration-cache", "org.gradle.configurationcache.ConfigurationCacheIntegrationTest")
        */
    }

    private TestExecutionResult assertTestClassExecutedIn(String subProjectDir, String testClass) {
        new DefaultTestExecutionResult(file(subProjectDir), "build", "", "", "embeddedIntegTest")
            .assertTestClassesExecuted(testClass)
    }

    private void configurationCacheRun(List<String> tasks, int daemonId) {
        run(
            tasks + [
                "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
                "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn".toString(), // TODO remove
            ],
            // use a unique testKitDir per daemonId other than 0 as 0 means default daemon.
            daemonId != 0 ? file("test-kit/$daemonId") : null
        )
    }
}
