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

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoryPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.net.URI;

abstract class AbstractPluginRepository implements AuthenticationSupported, PluginRepositoryInternal, BackedByArtifactRepository {
    private static final String REPOSITORY_PREFIX = "__pluginRepository";
    private final FileResolver fileResolver;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final VersionSelectorScheme versionSelectorScheme;
    private final AuthenticationSupportedInternal authenticationSupport;

    private String name;
    private int position;
    private Object url;
    private PluginResolver resolver;
    private ArtifactRepository artifactRepository;

    AbstractPluginRepository(
        String defaultName, FileResolver fileResolver, DependencyResolutionServices dependencyResolutionServices,
        VersionSelectorScheme versionSelectorScheme, AuthenticationSupportedInternal authenticationSupport) {
        this.authenticationSupport = authenticationSupport;
        this.fileResolver = fileResolver;
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.versionSelectorScheme = versionSelectorScheme;
        this.name = defaultName;
    }

    protected abstract ArtifactRepository createArtifactRepository(RepositoryHandler repositoryHandler);

    AuthenticationSupportedInternal authenticationSupport() {
        return authenticationSupport;
    }

    String getArtifactRepositoryName() {
        return REPOSITORY_PREFIX + position;
    }

    @Override
    public String getName() {
        return name;
    }

    public URI getUrl() {
        return fileResolver.resolveUri(url);
    }

    public void setUrl(Object url) {
        checkMutable();
        this.url = url;
    }

    @Override
    public PasswordCredentials getCredentials() {
        return authenticationSupport.getCredentials();
    }

    @Override
    public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
        return authenticationSupport.getCredentials(credentialsType);
    }

    @Override
    public void credentials(Action<? super PasswordCredentials> action) {
        checkMutable();
        authenticationSupport.credentials(action);
    }

    @Override
    public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
        checkMutable();
        authenticationSupport.credentials(credentialsType, action);
    }

    @Override
    public void authentication(Action<? super AuthenticationContainer> action) {
        checkMutable();
        authenticationSupport.authentication(action);
    }

    @Override
    public AuthenticationContainer getAuthentication() {
        return authenticationSupport.getAuthentication();
    }

    @Override
    public PluginResolver asResolver() {
        if (resolver == null) {
            prepareArtifactRepository();
            resolver = new ArtifactRepositoryPluginResolver(name + '(' + url + ')', dependencyResolutionServices, versionSelectorScheme);
        }
        return resolver;
    }

    @Override
    public void setPosition(int position) {
        checkMutable();
        this.position = position;
    }

    @Override
    public ArtifactRepository getArtifactRepository() {
        prepareArtifactRepository();
        return artifactRepository;
    }

    private void checkMutable() {
        if (artifactRepository != null) {
            throw new IllegalStateException("A plugin repository cannot be modified after it has been used to resolve plugins.");
        }
    }

    private void prepareArtifactRepository() {
        if (artifactRepository == null) {
            artifactRepository = createArtifactRepository(dependencyResolutionServices.getResolveRepositoryHandler());
        }
    }
}
