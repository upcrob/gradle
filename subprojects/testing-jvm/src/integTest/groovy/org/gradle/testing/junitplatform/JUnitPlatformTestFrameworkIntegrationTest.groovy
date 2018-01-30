/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.junitplatform

import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION

@Requires(TestPrecondition.JDK8_OR_LATER)
class JUnitPlatformTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { 
                testCompile 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    void createPassingFailingTest() {
        file('src/main/java/AppException.java').writelns(
            "public class AppException extends Exception { }"
        )

        file('src/test/java/SomeTest.java') << """
            public class SomeTest {
                @org.junit.jupiter.api.Test
                public void ${failingTestCaseName} {
                    System.err.println("some error output");
                    org.junit.jupiter.api.Assertions.fail(\"test failure message\");
                }
                @org.junit.jupiter.api.Test
                public void ${passingTestCaseName} { }
            }
        """
        file('src/test/java/SomeOtherTest.java') << """
            public class SomeOtherTest {
                @org.junit.jupiter.api.Test
                public void ${passingTestCaseName} { }
            }
        """
    }

    @Override
    void createEmptyProject() {
        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """
    }

    @Override
    void renameTests() {
        def newTest = file("src/test/java/NewTest.java")
        file('src/test/java/SomeOtherTest.java').renameTo(newTest)
        newTest.text = newTest.text.replaceAll("SomeOtherTest", "NewTest")
    }

    @Override
    String getTestTaskName() {
        return "test"
    }

    @Override
    String getPassingTestCaseName() {
        return "pass()"
    }

    @Override
    String getFailingTestCaseName() {
        return "fail()"
    }
}
