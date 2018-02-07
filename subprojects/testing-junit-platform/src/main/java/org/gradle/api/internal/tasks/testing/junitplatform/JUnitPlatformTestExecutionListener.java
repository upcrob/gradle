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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.junit.GenericJUnitTestEventAdapter;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import static org.gradle.api.internal.tasks.testing.junit.JUnitTestEventAdapter.getIgnoredMethodsFromIgnoredClass;
import static org.gradle.api.internal.tasks.testing.junitplatform.VintageTestNameAdapter.*;

public class JUnitPlatformTestExecutionListener implements TestExecutionListener {
    private final GenericJUnitTestEventAdapter<String> adapter;
    private final IdGenerator<?> idGenerator;
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.adapter = new GenericJUnitTestEventAdapter<>(resultProcessor, clock);
        this.idGenerator = idGenerator;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        this.currentTestPlan = null;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (isClass(testIdentifier)) {
            if (isVintageEngine(testIdentifier)) {
                processIgnoredClass(testIdentifier);
            } else {
                currentTestPlan.getChildren(testIdentifier).forEach(child -> executionSkipped(child, reason));
            }
        } else if (isMethod(testIdentifier)) {
            adapter.testIgnored(getDescriptor(testIdentifier));
        }
    }

    private void processIgnoredClass(TestIdentifier id) {
        getIgnoredMethodsFromIgnoredClass(id.getClass().getClassLoader(), className(id), idGenerator).forEach(adapter::testIgnored);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!testIdentifier.isTest()) {
            return;
        }
        adapter.testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (isLeafMethodOrFailedContainer(testIdentifier, testExecutionResult)) {
            if (!testIdentifier.isTest()) {
                // only leaf methods triggered start events previously
                // so here we need to add the missing start events
                adapter.testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
            }
            switch (testExecutionResult.getStatus()) {
                case SUCCESSFUL:
                    adapter.testFinished(testIdentifier.getUniqueId());
                    break;
                case FAILED:
                    adapter.testFailure(testIdentifier.getUniqueId(), getDescriptor(testIdentifier), testExecutionResult.getThrowable().get());
                    adapter.testFinished(testIdentifier.getUniqueId());
                    break;
                case ABORTED:
                    adapter.testAssumptionFailure(testIdentifier.getUniqueId());
                    adapter.testFinished(testIdentifier.getUniqueId());
                    break;
                default:
                    throw new AssertionError("Invalid Status: " + testExecutionResult.getStatus());
            }
        }
    }

    private boolean isLeafMethodOrFailedContainer(TestIdentifier testIdentifier, TestExecutionResult result) {
        // Generally, there're 4 kinds of identifier:
        // 1. JUnit test engine, which we don't consider at all
        // 2. A container (class or repeated tests). It is not tracked unless it fails/aborts.
        // 3. A test "leaf" method. It's always tracked.
        return testIdentifier.isTest() || isFailedContainer(testIdentifier, result);
    }

    private boolean isFailedContainer(TestIdentifier testIdentifier, TestExecutionResult result) {
        return result.getStatus() != TestExecutionResult.Status.SUCCESSFUL && testIdentifier.isContainer();
    }

    private TestDescriptorInternal getDescriptor(final TestIdentifier test) {
        if (isMethod(test)) {
            return new DefaultTestDescriptor(idGenerator.generateId(), className(test), test.getDisplayName());
        } else if (isVintageDynamicTest(test)) {
            UniqueId uniqueId = UniqueId.parse(test.getUniqueId());
            return new DefaultTestDescriptor(idGenerator.generateId(), vintageDynamicClassName(uniqueId), vintageDynamicMethodName(uniqueId));
        } else if (isClass(test)) {
            return new DefaultTestDescriptor(idGenerator.generateId(), className(test), "classMethod");
        } else {
            return null;
        }
    }

    private boolean isMethod(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof MethodSource;
    }

    private boolean isClass(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof ClassSource;
    }

    private String className(TestIdentifier testIdentifier) {
        if (testIdentifier.getSource().get() instanceof MethodSource) {
            return MethodSource.class.cast(testIdentifier.getSource().get()).getClassName();
        } else if (testIdentifier.getSource().get() instanceof ClassSource) {
            return ClassSource.class.cast(testIdentifier.getSource().get()).getClassName();
        } else {
            return null;
        }
    }
}
