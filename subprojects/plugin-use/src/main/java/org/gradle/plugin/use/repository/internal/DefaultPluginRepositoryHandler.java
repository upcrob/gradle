/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use.repository.internal;

import com.google.common.collect.Iterators;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler;
import org.gradle.api.internal.plugins.repositories.GradlePluginPortal;
import org.gradle.api.internal.plugins.repositories.IvyPluginRepository;
import org.gradle.api.internal.plugins.repositories.MavenPluginRepository;
import org.gradle.api.internal.plugins.repositories.PluginRepository;
import org.gradle.authentication.Authentication;
import org.gradle.internal.Factory;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.use.resolve.service.internal.PluginResolutionServiceResolver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultPluginRepositoryHandler implements PluginRepositoryHandler {
    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;
    private final FileResolver fileResolver;
    private final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;
    private final VersionSelectorScheme versionSelectorScheme;
    private final PluginResolutionServiceResolver pluginResolutionServiceResolver;
    private final Instantiator instantiator;
    private final List<PluginRepository> repositories;

    public DefaultPluginRepositoryHandler(
        PluginResolutionServiceResolver pluginResolutionServiceResolver, FileResolver fileResolver,
        Factory<DependencyResolutionServices> dependencyResolutionServicesFactory,
        VersionSelectorScheme versionSelectorScheme, Instantiator instantiator,
        AuthenticationSchemeRegistry authenticationSchemeRegistry) {
        this.pluginResolutionServiceResolver = pluginResolutionServiceResolver;
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.repositories = new ArrayList<PluginRepository>();
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
    }

    @Override
    public MavenPluginRepository maven(Action<? super MavenPluginRepository> configurationAction) {
        AuthenticationContainer authenticationContainer = makeAuthenticationContainer(instantiator, authenticationSchemeRegistry);
        AuthenticationSupportedInternal delegate = new AuthenticationSupporter(instantiator, authenticationContainer);
        DefaultMavenPluginRepository mavenPluginRepository = instantiator.newInstance(
            DefaultMavenPluginRepository.class, fileResolver, dependencyResolutionServicesFactory.create(), versionSelectorScheme, delegate);
        configurationAction.execute(mavenPluginRepository);
        add(mavenPluginRepository);
        return mavenPluginRepository;
    }

    @Override
    public IvyPluginRepository ivy(Action<? super IvyPluginRepository> configurationAction) {
        AuthenticationContainer authenticationContainer = makeAuthenticationContainer(instantiator, authenticationSchemeRegistry);
        AuthenticationSupportedInternal delegate = new AuthenticationSupporter(instantiator, authenticationContainer);
        DefaultIvyPluginRepository ivyPluginRepository = instantiator.newInstance(
            DefaultIvyPluginRepository.class, fileResolver, dependencyResolutionServicesFactory.create(), versionSelectorScheme, delegate);
        configurationAction.execute(ivyPluginRepository);
        add(ivyPluginRepository);
        return ivyPluginRepository;
    }

    @Override
    public GradlePluginPortal gradlePluginPortal() {
        DefaultGradlePluginPortal gradlePluginPortal = new DefaultGradlePluginPortal(pluginResolutionServiceResolver);
        for (PluginRepository pluginRepository : repositories) {
            if (((PluginRepositoryInternal) pluginRepository).getName().equals(gradlePluginPortal.getName())) {
                throw new IllegalArgumentException("Cannot add Gradle Plugin Portal more than once");
            }
        }
        repositories.add(gradlePluginPortal);
        return gradlePluginPortal;
    }

    @Override
    public Iterator<PluginRepository> iterator() {
        return Iterators.unmodifiableIterator(repositories.iterator());
    }

    private void add(BackedByArtifactRepository pluginRepository) {
        pluginRepository.setPosition(repositories.size() + 1);
        repositories.add((PluginRepositoryInternal) pluginRepository);
    }


    private AuthenticationContainer makeAuthenticationContainer(Instantiator instantiator, AuthenticationSchemeRegistry authenticationSchemeRegistry) {
        DefaultAuthenticationContainer container = instantiator.newInstance(DefaultAuthenticationContainer.class, instantiator);

        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            container.registerBinding(e.getKey(), e.getValue());
        }

        return container;
    }
}
