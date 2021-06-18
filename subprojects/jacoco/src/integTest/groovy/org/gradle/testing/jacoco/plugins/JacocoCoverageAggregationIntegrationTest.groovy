/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture

class JacocoCoverageAggregationIntegrationTest extends AbstractIntegrationSpec {

    private static final AGGREGATION_TASK_NAME = "jacocoAggregatedTestReport"

    def setup() {
        writeClassWithTest("lib1", "Lib1")
        writeClassWithTest("lib2", "Lib2")
        writeClassWithTest("app", "App")
        settingsFile << """
            include("lib1", "lib2", "app")
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """
        file("lib1/build.gradle") << """
            plugins {
                id("java-library")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
        file("lib2/build.gradle") << """
            plugins {
                id("java-library")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
        file("app/build.gradle") << """
            plugins {
                id("application")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
    }

    def "aggregates unit test results for the app and its library dependencies"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", AggregatedJacocoReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 62
    }

    def "aggregates unit test results for the app and its library dependencies transitively"() {
        given:
        file("lib1/build.gradle") << """
            dependencies {
                implementation(project(":lib2"))
            }
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", AggregatedJacocoReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 62
    }

    def "excludes external dependency classes from aggregated unit test results"() {
        given:
        file("lib1/build.gradle") << """
            dependencies {
                 implementation("commons-io:commons-io:2.6")
            }
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
                implementation("junit:junit:4.13")
            }
            tasks.register("${AGGREGATION_TASK_NAME}", AggregatedJacocoReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 62
    }

    def "aggregates test results using a dedicated aggregation subproject"() {
        given:
        settingsFile << """
            include("aggregation")
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
        """
        file("aggregation/build.gradle") << """
            plugins {
                id("jacoco")
            }
            dependencies {
                jacocoAggregation(project(":app"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", AggregatedJacocoReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("aggregation", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 62
    }

    def "assemble does not execute tests"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", AggregatedJacocoReport)
        """

        when:
        succeeds("assemble")

        then:
        notExecuted(":app:test", ":lib1:test", ":lib2:test")
    }

    private JacocoReportFixture htmlReport(String project, String reportName) {
        return new JacocoReportFixture(file(project + "/build/reports/jacoco/${reportName}/html"))
    }

    private static String configureJUnitPlatform() {
        return """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
            }
            tasks.named("test") {
                useJUnitPlatform()
            }
        """
    }

    private void writeClassWithTest(String subproject, String className) {
        file("${subproject}/src/main/java/com/example/${className}.java") << """
            package com.example;
            public class ${className} {
                public String get${className}() {
                    return "${className}";
                }
                void untested() {
                    System.out.println();
                }
            }
        """
        file("${subproject}/src/test/java/com/example/${className}Test.java") << """
            package com.example;
            import org.junit.jupiter.api.Test;
            class ${className}Test {
                @Test
                void returns${className}() {
                    new ${className}().get${className}();
                }
            }
        """
    }
}

