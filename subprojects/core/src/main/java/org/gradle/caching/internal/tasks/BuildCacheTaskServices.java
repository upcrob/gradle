/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.gradle.StartParameter;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.BuildCacheControllerFactory;
import org.gradle.caching.internal.controller.BuildCacheControllerFactory.BuildCacheMode;
import org.gradle.caching.internal.controller.BuildCacheControllerFactory.RemoteAccessMode;
import org.gradle.caching.internal.controller.RootBuildCacheControllerRef;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.version2.BuildCacheControllerV2;
import org.gradle.caching.internal.version2.DebuggingLocalBuildCacheServiceV2;
import org.gradle.caching.internal.version2.DefaultBuildCacheControllerV2;
import org.gradle.caching.internal.version2.DirectoryLocalBuildCacheServiceV2;
import org.gradle.caching.internal.version2.LocalBuildCacheServiceV2;
import org.gradle.caching.internal.version2.TaskOutputCacheCommandFactoryV2;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.util.GradleVersion;
import org.gradle.util.Path;

import java.io.File;

import static org.gradle.cache.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.caching.internal.controller.BuildCacheControllerFactory.BuildCacheMode.DISABLED;
import static org.gradle.caching.internal.controller.BuildCacheControllerFactory.BuildCacheMode.ENABLED;
import static org.gradle.caching.internal.controller.BuildCacheControllerFactory.RemoteAccessMode.OFFLINE;
import static org.gradle.caching.internal.controller.BuildCacheControllerFactory.RemoteAccessMode.ONLINE;

@NonNullApi
public class BuildCacheTaskServices {

    private static final Path ROOT_BUILD_SRC_PATH = Path.path(":" + BuildSourceBuilder.BUILD_SRC);

    TaskOutputPacker createTaskResultPacker(FileSystem fileSystem, StreamHasher fileHasher, StringInterner stringInterner) {
        return new GZipTaskOutputPacker(new TarTaskOutputPacker(fileSystem, fileHasher, stringInterner));
    }

    TaskOutputOriginFactory createTaskOutputOriginFactory(
        Clock clock,
        InetAddressFactory inetAddressFactory,
        GradleInternal gradleInternal,
        BuildInvocationScopeId buildInvocationScopeId
    ) {
        File rootDir = gradleInternal.getRootProject().getRootDir();
        return new TaskOutputOriginFactory(clock, inetAddressFactory, rootDir, SystemProperties.getInstance().getUserName(), OperatingSystem.current().getName(), GradleVersion.current(), buildInvocationScopeId);
    }

    TaskOutputCacheCommandFactory createTaskOutputCacheCommandFactory(
        TaskOutputPacker taskOutputPacker,
        TaskOutputOriginFactory taskOutputOriginFactory,
        FileSystemMirror fileSystemMirror,
        StringInterner stringInterner
    ) {
        return new TaskOutputCacheCommandFactory(taskOutputPacker, taskOutputOriginFactory, fileSystemMirror, stringInterner);
    }

    BuildCacheController createBuildCacheController(
        ServiceRegistry serviceRegistry,
        BuildCacheConfigurationInternal buildCacheConfiguration,
        BuildOperationExecutor buildOperationExecutor,
        InstantiatorFactory instantiatorFactory,
        GradleInternal gradle,
        RootBuildCacheControllerRef rootControllerRef
    ) {
        if (isRoot(gradle) || isRootBuildSrc(gradle) || isGradleBuildTaskRoot(rootControllerRef)) {
            return doCreateBuildCacheController(serviceRegistry, buildCacheConfiguration, buildOperationExecutor, instantiatorFactory, gradle);
        } else {
            // must be an included build
            return rootControllerRef.getForNonRootBuild();
        }
    }

    TaskOutputCacheCommandFactoryV2 createTaskOutputCacheCommandFactoryV2(
        TaskOutputOriginFactory taskOutputOriginFactory,
        FileSystemMirror fileSystemMirror,
        StringInterner stringInterner,
        CacheScopeMapping cacheScopeMapping,
        CacheRepository cacheRepository
    ) {
        String cacheV2Dir = System.getProperty("cacheV2Dir");
        File target = cacheV2Dir == null
            ? cacheScopeMapping.getBaseDirectory(null, "build-cache-2", VersionStrategy.SharedCache)
            : new File(cacheV2Dir);
        PathKeyFileStore fileStore = new DefaultPathKeyFileStore(target);
        PersistentCache persistentCache = cacheRepository
            .cache(target)
            .withDisplayName("Build cache V2")
            .withLockOptions(mode(None))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .open();
        LocalBuildCacheServiceV2 local = new DebuggingLocalBuildCacheServiceV2(
            new DirectoryLocalBuildCacheServiceV2(fileStore, persistentCache)
        );
        return new TaskOutputCacheCommandFactoryV2(taskOutputOriginFactory, fileSystemMirror, stringInterner, local);
    }

    BuildCacheControllerV2 createBuildCacheControllerV2() {
        return new DefaultBuildCacheControllerV2();
    }

    private boolean isGradleBuildTaskRoot(RootBuildCacheControllerRef rootControllerRef) {
        // GradleBuild tasks operate with their own build session and tree scope.
        // Therefore, they have their own RootBuildCacheControllerRef.
        // This prevents them from reusing the build cache configuration defined by the root.
        // There is no way to detect that a Gradle instance represents a GradleBuild invocation.
        // If there were, that would be a better heuristic than this.
        return !rootControllerRef.isSet();
    }

    private boolean isRootBuildSrc(GradleInternal gradle) {
        return gradle.getIdentityPath().equals(ROOT_BUILD_SRC_PATH);
    }

    private boolean isRoot(GradleInternal gradle) {
        return gradle.getParent() == null;
    }

    private BuildCacheController doCreateBuildCacheController(ServiceRegistry serviceRegistry, BuildCacheConfigurationInternal buildCacheConfiguration, BuildOperationExecutor buildOperationExecutor, InstantiatorFactory instantiatorFactory, GradleInternal gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        Path buildIdentityPath = gradle.getIdentityPath();
        File gradleUserHomeDir = gradle.getGradleUserHomeDir();
        BuildCacheMode buildCacheMode = startParameter.isBuildCacheEnabled() ? ENABLED : DISABLED;
        RemoteAccessMode remoteAccessMode = startParameter.isOffline() ? OFFLINE : ONLINE;
        boolean logStackTraces = startParameter.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;

        return BuildCacheControllerFactory.create(
            buildOperationExecutor,
            buildIdentityPath,
            gradleUserHomeDir,
            buildCacheConfiguration,
            buildCacheMode,
            remoteAccessMode,
            logStackTraces,
            instantiatorFactory.inject(serviceRegistry)
        );
    }

}
