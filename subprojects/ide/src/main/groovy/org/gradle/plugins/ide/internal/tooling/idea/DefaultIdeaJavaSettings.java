/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.api.JavaVersion;
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaRuntime;

import java.io.Serializable;

public class DefaultIdeaJavaSettings implements Serializable {
    private JavaVersion sourceLanguageLevel;
    private JavaVersion targetBytecodeVersion;
    private DefaultJavaRuntime targetRuntime;

    public DefaultIdeaJavaSettings setSourceLanguageLevel(JavaVersion sourceLanguageLevel) {
        this.sourceLanguageLevel = sourceLanguageLevel;
        return this;
    }

    public DefaultIdeaJavaSettings setTargetBytecodeVersion(JavaVersion targetBytecodeVersion) {
        this.targetBytecodeVersion = targetBytecodeVersion;
        return this;
    }

    public DefaultIdeaJavaSettings setJavaSDK(DefaultJavaRuntime targetRuntime) {
        this.targetRuntime = targetRuntime;
        return this;
    }

    public JavaVersion getSourceLanguageLevel() {
        return sourceLanguageLevel;
    }

    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    public DefaultJavaRuntime getJavaSDK() {
        return targetRuntime;
    }
}
