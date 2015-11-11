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

package org.gradle.language.base.plugins
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import org.gradle.util.TestUtil
import spock.lang.Specification

class LanguageBasePluginTest extends Specification {
    DefaultProject project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(LanguageBasePlugin)
    }

    def "adds a 'binaries' container to the project model"() {
        expect:
        project.modelRegistry.find(ModelPath.path("binaries"), ModelTypes.modelMap(BinarySpec)) != null
    }

    def "adds a 'sources' container to the project model"() {
        expect:
        project.modelRegistry.find(ModelPath.path("sources"), ModelType.of(ProjectSourceSet)) != null
    }

    def "copies binary tasks into task container"() {
        def tasks = Mock(TaskContainer)
        def binaries = Mock(ModelMap)
        def binary = Mock(BinarySpecInternal)
        def binaryTasks = new DefaultBinaryTasksCollection(binary, Mock(ITaskFactory))
        def someTask = Mock(Task) { getName() >> "someTask" }
        def buildTask = Mock(Task) { getName() >> "lifecycleTask" }
        binaryTasks.add(someTask)

        when:
        def rules = new LanguageBasePlugin.Rules()
        rules.copyBinaryTasksToTaskContainer(tasks, binaries)

        then:
        binaries.iterator() >> [binary].iterator()
        binary.name >> "binaryName"
        binary.toString() >> "binary foo"
        binary.getTasks() >> binaryTasks
        binary.getBuildTask() >> buildTask

        and:
        1 * tasks.addAll(binaryTasks)
        1 * tasks.add(buildTask)
    }
}
