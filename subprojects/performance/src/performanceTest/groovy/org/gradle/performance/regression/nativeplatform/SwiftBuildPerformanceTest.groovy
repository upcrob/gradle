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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.mutator.ApplyChangeToNativeSourceFileMutator
import spock.lang.Ignore
import spock.lang.Unroll
import spock.util.environment.OperatingSystem

class SwiftBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.minimumVersion = '4.5'
        runner.targetVersions = ["4.6-20180201071900+0000"]
        if (OperatingSystem.current.linux) {
            def toolchain = new File(runner.workingDir, "toolchain.gradle")
            toolchain << """
                allprojects { p ->
                    apply plugin: SwiftCompilerPlugin
                
                    model {
                        toolChains {
                            swiftc(Swiftc) {
                                path file('/opt/swift/latest/usr/bin')
                            }
                        }
                    }
                }
            """
            runner.args.add("-I${toolchain.absolutePath}")
        }
    }

    @Unroll
    def "clean assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.runs = iterations
        runner.warmUpRuns = iterations

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                       | maxMemory | iterations
        'mediumSwiftMulti'                | '1G'      | null
    }

    @Unroll
    def "up-to-date assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | maxMemory
        'mediumSwiftMulti' | '1G'
    }

    @Ignore
    @Unroll
    def "assemble with #changeType file change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.addBuildExperimentListener(new ApplyChangeToNativeSourceFileMutator(fileToChange))

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject   | maxMemory | fileToChange
        "mediumSwiftMulti" | '1g'      | 'project101/src/main/public/project101lib4.h'
        changeType = fileToChange.endsWith('.h') ? 'header' : 'source'
    }

}
