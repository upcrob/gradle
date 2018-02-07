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

package org.gradle.test.fixtures.junitplatform

import groovy.io.FileType

class JUnitPlatformTestRewriter {
    static final String LATEST_JUPITER_VERSION = '5.1.0-RC1'
    static final String LATEST_VINTAGE_VERSION = '5.1.0-RC1'
    static Map replacements = ['org.junit.Test': 'org.junit.jupiter.api.Test',
                               'org.junit.Before;': 'org.junit.jupiter.api.BeforeEach;',
                               'org.junit.After;': 'org.junit.jupiter.api.AfterEach;',
                               '@org.junit.Before ': '@org.junit.jupiter.api.BeforeEach ',
                               '@org.junit.After ': '@org.junit.jupiter.api.AfterEach ',
                               '@org.junit.Before\n': '@org.junit.jupiter.api.BeforeEach\n',
                               '@org.junit.After\n': '@org.junit.jupiter.api.AfterEach\n',
                               'org.junit.BeforeClass': 'org.junit.jupiter.api.BeforeAll',
                               'org.junit.AfterClass': 'org.junit.jupiter.api.AfterAll',
                               'org.junit.Ignore': 'org.junit.jupiter.api.Disabled',
                               '@Before\n': '@BeforeEach\n',
                               '@After\n': '@AfterEach\n',
                               '@Before ': '@BeforeEach ',
                               '@After ': '@AfterEach ',
                               '@BeforeClass': '@BeforeAll',
                               '@AfterClass': '@AfterAll',
                               '@Ignore': '@Disabled',
                               'import org.junit.*': 'import org.junit.jupiter.api.*',
                               'org.junit.Assume': 'org.junit.jupiter.api.Assumptions',
                               'org.junit.Assert': 'org.junit.jupiter.api.Assertions',
                               'junit.framework.Assert': 'org.junit.jupiter.api.Assertions',
                               'Assert.': 'Assertions.',
                               'Assume.': 'Assumptions.',
    ]

    static rewriteWithJupiter(File projectDir) {
        rewriteBuildFileWithJupiter(projectDir)
        rewriteJavaFilesWithJupiterAnno(projectDir)
    }

    static replaceCategoriesWithTags(File projectDir) {
        // http://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4-categories-support
        File buildFile = new File(projectDir, 'build.gradle')
        if (buildFile.exists()) {
            String text = buildFile.text
            text = text.replace('excludeCategories', 'excludeTags')
            text = text.replace('includeCategories', 'includeTags')
            buildFile.text = text
        }
    }

    static rewriteWithVintage(File projectDir) {
        rewriteBuildFileWithVintage(projectDir)
    }

    static rewriteBuildFileWithJupiter(File buildFile) {
        rewriteBuildFileInDir(buildFile, "org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}")
    }

    static rewriteBuildFileWithVintage(File buildFile) {
        rewriteBuildFileInDir(buildFile, "org.junit.vintage:junit-vintage-engine:${LATEST_VINTAGE_VERSION}")
    }

    static rewriteJavaFilesWithJupiterAnno(File rootProject) {
        rootProject.traverse(type: FileType.FILES, nameFilter: ~/.*\.(java|groovy)/) {
            String text = it.text
            replacements.each { key, value ->
                text = text.replace(key, value)
            }
            it.text = text
        }
    }

    static rewriteBuildFileInDir(File dir, String dependencies) {
        def dirs = [dir]
        dirs.addAll(dir.listFiles().findAll { it.isDirectory() })
        dirs.each {
            File buildFile = new File(it, 'build.gradle')
            if (buildFile.exists()) {
                rewriteBuildFile(buildFile, dependencies)
            }
        }
    }

    static rewriteBuildFile(File buildFile, String dependenciesReplacement) {
        String text = buildFile.text
        // compile/testCompile
        text = text.replaceFirst(/ompile ['"]junit:junit:4\.12['"]/, "ompile '${dependenciesReplacement}'")
        if (!text.contains('useTestNG')) {
            // we only hack build with JUnit 4
            // See IncrementalTestIntegrationTest.executesTestsWhenTestFrameworkChanges
            if (text.contains("useJUnit {")) {
                text = text.replace('useJUnit {', 'useJUnitPlatform {')
            } else if (text.contains('useJUnit()')) {
                text = text.replace('useJUnit()', 'useJUnitPlatform()')
            } else if (text.contains('test {')) {
                text = text.replace('test {', '''
                    test {
                    useJUnitPlatform()
                    ''')
            } else {
                text += '''
                    test {
                        useJUnitPlatform()
                    }
                '''
            }
        }
        buildFile.text = text
    }
}
