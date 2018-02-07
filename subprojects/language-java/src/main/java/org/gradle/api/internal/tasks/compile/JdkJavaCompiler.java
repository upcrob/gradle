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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.reflect.SourcepathIgnoringProxy;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class JdkJavaCompiler implements Compiler<JavaCompileSpec>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkJavaCompiler.class);
    private final Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory;
    private volatile URLClassLoader processorClassloader;

    public JdkJavaCompiler(Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory) {
        this.javaHomeBasedJavaCompilerFactory = javaHomeBasedJavaCompilerFactory;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        LOGGER.info("Compiling with JDK Java compiler API.");

        JavaCompiler.CompilationTask task = createCompileTask(spec);
        boolean success;
        try {
            success = task.call();
        } finally {
            cleanup();
        }
        if (!success) {
            throw new CompilationFailedException();
        }

        return WorkResults.didWork(true);
    }

    private JavaCompiler.CompilationTask createCompileTask(JavaCompileSpec spec) {
        List<String> options = new JavaCompilerArgumentsBuilder(spec).build();
        JavaCompiler compiler = javaHomeBasedJavaCompilerFactory.create();
        MinimalJavaCompileOptions compileOptions = spec.getCompileOptions();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, compileOptions.getEncoding() != null ? Charset.forName(compileOptions.getEncoding()) : null);
        Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromFiles(spec.getSource());
        StandardJavaFileManager fileManager = standardFileManager;
        if (JavaVersion.current().isJava9Compatible() && emptySourcepathIn(options)) {
            fileManager = (StandardJavaFileManager) SourcepathIgnoringProxy.proxy(standardFileManager, StandardJavaFileManager.class);
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);

        List<AnnotationProcessorDeclaration> annotationProcessors = spec.getEffectiveAnnotationProcessors();
        if (annotationProcessors != null) {
            task.setProcessors(instantiateProcessors(annotationProcessors, spec.getAnnotationProcessorPath()));
        }
        return task;
    }

    private List<Processor> instantiateProcessors(List<AnnotationProcessorDeclaration> declaredProcessors, List<File> annotationProcessorPath) {
        if (declaredProcessors == null) {
            return Collections.emptyList();
        }
        processorClassloader = new URLClassLoader(getUrls(annotationProcessorPath));
        List<Processor> processors = new ArrayList<Processor>(declaredProcessors.size());
        for (AnnotationProcessorDeclaration declaredProcessor : declaredProcessors) {
            try {
                Class<?> processorClass = processorClassloader.loadClass(declaredProcessor.getClassName());
                Object processor = processorClass.newInstance();
                processors.add((Processor) processor);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        return processors;
    }

    private URL[] getUrls(List<File> classpath) {
        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = classpath.get(i).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return urls;
    }

    private void cleanup() {
        CompositeStoppable.stoppable(processorClassloader).stop();
    }

    private static boolean emptySourcepathIn(List<String> options) {
        Iterator<String> optionsIter = options.iterator();
        while (optionsIter.hasNext()) {
            String current = optionsIter.next();
            if (current.equals("-sourcepath") || current.equals("--source-path")) {
                return optionsIter.next().isEmpty();
            }
        }
        return false;
    }
}
