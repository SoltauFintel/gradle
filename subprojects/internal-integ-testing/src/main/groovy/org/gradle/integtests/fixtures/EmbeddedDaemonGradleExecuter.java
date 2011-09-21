/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.EmbeddedDaemonConnector;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.StreamBackedStandardOutputListener;

import java.lang.management.ManagementFactory;

public class EmbeddedDaemonGradleExecuter extends OutputScrapingGradleExecuter {

    private LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();

    protected GradleOutput doRun(boolean expectFailure) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        ExecuteBuildAction buildAction = createBuildAction();
        BuildActionParameters buildActionParameters = createBuildActionParameters();
        DaemonClient daemonClient = createClient();

        LoggingManagerInternal loggingManager = createLoggingManager(output, error);
        loggingManager.start();

        Exception failure = null;
        try {
            daemonClient.execute(buildAction, buildActionParameters);
        } catch (Exception e) {
            failure = e;
        } finally {
            daemonClient.stop();
            loggingManager.stop();
        }

        boolean didFail = failure != null;
        if (expectFailure != didFail) {
            String didOrDidntSnippet = didFail ? "DID fail" : "DID NOT fail";
            throw new RuntimeException(String.format("Gradle execution in %s %s with: %nOutput:%n%s%nError:%n%s%n-----%n", getWorkingDir(), didOrDidntSnippet, output, error), failure);
        }

        return new GradleOutput(output, error);
    }

    private DaemonClient createClient() {
        return new DaemonClient(new EmbeddedDaemonConnector(), clientMetaData(), loggingServices.get(OutputEventListener.class));
    }

    private LoggingManagerInternal createLoggingManager(StringBuilder output, StringBuilder error) {
        LoggingManagerInternal loggingManager = loggingServices.newInstance(LoggingManagerInternal.class);
        loggingManager.disableStandardOutputCapture();
        loggingManager.addStandardOutputListener(new StreamBackedStandardOutputListener(output));
        loggingManager.addStandardErrorListener(new StreamBackedStandardOutputListener(error));
        return loggingManager;
    }

    private ExecuteBuildAction createBuildAction() {
        CommandLineParser parser = new CommandLineParser();
        DefaultCommandLineConverter commandLineConverter = new DefaultCommandLineConverter();
        commandLineConverter.configure(parser);
        ParsedCommandLine commandLine = parser.parse(getAllArgs());
        return new ExecuteBuildAction(getWorkingDir(), commandLine);
    }

    private BuildActionParameters createBuildActionParameters() {
        return new DefaultBuildActionParameters(clientMetaData(), getStartTime(), System.getProperties(), getEnvironmentVars(), getWorkingDir());
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }

    private long getStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }
}