/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.asTextOrEmpty
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet

import okhttp3.Request

@Suppress("LargeClass", "TooManyFunctions")
class NPM : PackageManager() {
    companion object : PackageManagerFactory<NPM>(
            "https://www.npmjs.com/",
            "JavaScript",
            listOf("package.json")
    ) {
        private const val DEFINITELY_TYPED_VCS_URL = "https://github.com/DefinitelyTyped/DefinitelyTyped.git"

        override fun create() = NPM()

        val npm: String
        val yarn: String

        init {
            if (OS.isWindows) {
                npm = "npm.cmd"
                yarn = "yarn.cmd"
            } else {
                npm = "npm"
                yarn = "yarn"
            }
        }

        fun expandShortcutURL(url: String): String {
            // A hierarchical URI looks like
            //     [scheme:][//authority][path][?query][#fragment]
            // where a server-based "authority" has the syntax
            //     [user-info@]host[:port]
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                // Fall back to returning the original URL.
                return url
            }

            val path = uri.schemeSpecificPart
            return if (!path.isNullOrEmpty() && listOf(uri.authority, uri.query, uri.fragment).all { it == null }) {
                // See https://docs.npmjs.com/files/package.json#repository.
                when (uri.scheme) {
                    null -> "https://github.com/$path.git"
                    "gist" -> "https://gist.github.com/$path"
                    "bitbucket" -> "https://bitbucket.org/$path.git"
                    "gitlab" -> "https://gitlab.com/$path.git"
                    else -> url
                }
            } else {
                url
            }
        }
    }

    override fun command(workingDir: File) = if (File(workingDir, "yarn.lock").isFile) yarn else npm

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // We do not actually depend on any features specific to an NPM 5.x or Yarn version, but we still want to
        // stick to fixed versions to be sure to get consistent results.
        checkCommandVersion(npm, Requirement.buildNPM("5.5.1"), ignoreActualVersion = Main.ignoreVersions)
        checkCommandVersion(yarn, Requirement.buildNPM("1.3.2"), ignoreActualVersion = Main.ignoreVersions)

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val modulesDir = File(workingDir, "node_modules")

        var tempModulesDir: File? = null
        try {
            // Temporarily move away any existing "node_modules" directory within the same filesystem to ensure
            // the move can be performed atomically.
            if (modulesDir.isDirectory) {
                val tempDir = createTempDir(Main.TOOL_NAME, ".tmp", workingDir)
                tempModulesDir = File(tempDir, "node_modules")
                log.warn { "'$modulesDir' already exists, temporarily moving it to '$tempModulesDir'." }
                Files.move(modulesDir.toPath(), tempModulesDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }

            // Actually installing the dependencies is the easiest way to get the meta-data of all transitive
            // dependencies (i.e. their respective "package.json" files). As npm (and yarn) use a global cache,
            // the same dependency is only ever downloaded once.
            installDependencies(workingDir)

            val packages = parseInstalledModules(workingDir)

            val dependencies = Scope("dependencies", true,
                    parseDependencies(definitionFile, "dependencies", packages))
            val devDependencies = Scope("devDependencies", false,
                    parseDependencies(definitionFile, "devDependencies", packages))

            // TODO: add support for peerDependencies, bundledDependencies, and optionalDependencies.

            return parseProject(definitionFile, sortedSetOf(dependencies, devDependencies),
                    packages.values.toSortedSet())
        } finally {
            // Delete node_modules folder to not pollute the scan.
            log.info { "Deleting temporary '$modulesDir'..." }
            modulesDir.safeDeleteRecursively()

            // Restore any previously existing "node_modules" directory.
            if (tempModulesDir != null) {
                log.info { "Restoring original '$modulesDir' directory from '$tempModulesDir'..." }
                Files.move(tempModulesDir.toPath(), modulesDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!tempModulesDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempModulesDir.parent}' directory.")
                }
            }
        }
    }

    private fun parseInstalledModules(rootDirectory: File): Map<String, Package> {
        val packages = mutableMapOf<String, Package>()
        val nodeModulesDir = File(rootDirectory, "node_modules")

        log.info { "Searching for package.json files in ${nodeModulesDir.absolutePath}..." }

        nodeModulesDir.walkTopDown().filter {
            it.name == "package.json" && isValidNodeModulesDirectory(nodeModulesDir, nodeModulesDirForPackageJson(it))
        }.forEach {
            log.debug { "Found module: ${it.absolutePath}" }

            @Suppress("UnsafeCast")
            val json = jsonMapper.readTree(it) as ObjectNode
            val rawName = json["name"].asText()
            val (namespace, name) = splitNamespaceAndName(rawName)
            val version = json["version"].asText()

            val declaredLicenses = sortedSetOf<String>()

            json["license"]?.let { licenseNode ->
                val type = licenseNode.textValue() ?: licenseNode["type"].asTextOrEmpty()
                declaredLicenses.add(type)
            }

            json["licenses"]?.mapNotNullTo(declaredLicenses) { licenseNode ->
                licenseNode["type"]?.asText()
            }

            var description = json["description"].asTextOrEmpty()
            var homepageUrl = json["homepage"].asTextOrEmpty()
            var downloadUrl = json["_resolved"].asTextOrEmpty()
            var hash = json["_integrity"].asTextOrEmpty()
            var vcsFromPackage = parseVcsInfo(json)

            val hashAlgorithm = HashAlgorithm.SHA1
            val identifier = "$rawName@$version"

            // Download package info from registry.npmjs.org.
            // TODO: check if unpkg.com can be used as a fallback in case npmjs.org is down.
            log.debug { "Retrieving package info for $identifier" }
            val encodedName = if (rawName.startsWith("@")) {
                "@${URLEncoder.encode(rawName.substringAfter("@"), "UTF-8")}"
            } else {
                rawName
            }

            val pkgRequest = Request.Builder()
                    .get()
                    .url("https://registry.npmjs.org/$encodedName")
                    .build()

            OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, pkgRequest).use { response ->
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    log.debug {
                        if (response.cacheResponse() != null) {
                            "Retrieved info about $encodedName from local cache."
                        } else {
                            "Downloaded info about $encodedName from NPM registry."
                        }
                    }

                    response.body()?.let { body ->
                        val packageInfo = jsonMapper.readTree(body.string())

                        packageInfo["versions"][version]?.let { versionInfo ->
                            description = versionInfo["description"].asTextOrEmpty()
                            homepageUrl = versionInfo["homepage"].asTextOrEmpty()

                            versionInfo["dist"]?.let { dist ->
                                downloadUrl = dist["tarball"].asTextOrEmpty()
                                hash = dist["shasum"].asTextOrEmpty()
                            }

                            vcsFromPackage = parseVcsInfo(versionInfo)
                        }
                    }
                } else {
                    log.info {
                        "Could not retrieve package information for '$encodedName' " +
                                "from public NPM registry: ${response.code()} - ${response.message()}"
                    }
                }
            }

            val module = Package(
                    id = Identifier(
                            provider = "NPM",
                            namespace = namespace,
                            name = name,
                            version = version
                    ),
                    declaredLicenses = declaredLicenses,
                    description = description,
                    homepageUrl = homepageUrl,
                    binaryArtifact = RemoteArtifact(
                            url = downloadUrl,
                            hash = hash,
                            hashAlgorithm = hashAlgorithm
                    ),
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcsFromPackage,
                    vcsProcessed = processPackageVcs(vcsFromPackage)
            )

            require(module.id.name.isNotEmpty()) {
                "Generated package info for $identifier has no name."
            }

            require(module.id.version.isNotEmpty()) {
                "Generated package info for $identifier has no version."
            }

            // For TypeScript definitions there is no way to get the Git revision of the type definitions for a
            // particular NPM package version. The DefinitelyTyped project only uses a directory hierarchy of
            // "types/<package name>" without a version, and there are no tags in Git. See e.g.
            // https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/chai
            // TODO: Think about how this can be turned into a generic curation.
            packages[identifier] = if (module.id.namespace == "@types"
                    && module.vcsProcessed.url == DEFINITELY_TYPED_VCS_URL) {
                // Clear the VCS URL to directly trigger source artifact download and use the binary artifact as the
                // source artifact.
                log.info { "Falling back to source artifact for TypeScript type definition package '${module.id}'." }
                module.copy(sourceArtifact = module.binaryArtifact, vcsProcessed = VcsInfo.EMPTY)
            } else {
                module
            }
        }

        return packages
    }

    private fun isValidNodeModulesDirectory(rootModulesDir: File, modulesDir: File?): Boolean {
        if (modulesDir == null) {
            return false
        }

        var currentDir: File = modulesDir
        while (currentDir != rootModulesDir) {
            if (currentDir.name != "node_modules") {
                return false
            }

            currentDir = currentDir.parentFile.parentFile
            if (currentDir.name.startsWith("@")) {
                currentDir = currentDir.parentFile
            }
        }

        return true
    }

    private fun nodeModulesDirForPackageJson(packageJson: File): File? {
        var modulesDir = packageJson.parentFile.parentFile
        if (modulesDir.name.startsWith("@")) {
            modulesDir = modulesDir.parentFile
        }

        return modulesDir.takeIf { it.name == "node_modules" }
    }

    private fun parseDependencies(packageJson: File, scope: String, packages: Map<String, Package>)
            : SortedSet<PackageReference> {
        // Read package.json
        val json = jsonMapper.readTree(packageJson)
        val dependencies = sortedSetOf<PackageReference>()
        if (json[scope] != null) {
            log.debug { "Looking for dependencies in scope $scope" }
            val dependencyMap = json[scope]
            dependencyMap.fields().forEach { (name, _) ->
                buildTree(packageJson.parentFile, packageJson.parentFile, name, packages)?.let { dependency ->
                    dependencies.add(dependency)
                }
            }
        } else {
            log.debug { "Could not find scope $scope in ${packageJson.absolutePath}" }
        }

        return dependencies
    }

    private fun parseVcsInfo(node: JsonNode): VcsInfo {
        // See https://github.com/npm/read-package-json/issues/7 for some background info.
        val head = node["gitHead"].asTextOrEmpty()

        return node["repository"]?.let { repo ->
            val type = repo["type"].asTextOrEmpty()
            val url = repo.textValue() ?: repo["url"].asTextOrEmpty()
            VcsInfo(type, expandShortcutURL(url), head, "")
        } ?: VcsInfo("", "", head, "")
    }

    private fun buildTree(rootDir: File, startDir: File, name: String, packages: Map<String, Package>,
                          dependencyBranch: List<String> = listOf()): PackageReference? {
        log.debug { "Building dependency tree for $name from directory ${startDir.absolutePath}" }

        val nodeModulesDir = File(startDir, "node_modules")
        val moduleDir = File(nodeModulesDir, name)
        val packageFile = File(moduleDir, "package.json")

        if (packageFile.isFile) {
            log.debug { "Found package file for module $name: ${packageFile.absolutePath}" }

            val packageJson = jsonMapper.readTree(packageFile)
            val rawName = packageJson["name"].asText()
            val version = packageJson["version"].asText()
            val identifier = "$rawName@$version"

            if (dependencyBranch.contains(identifier)) {
                log.debug {
                    "Not adding circular dependency $identifier to the tree, it is already on this branch of the " +
                            "dependency tree: ${dependencyBranch.joinToString(" -> ")}"
                }
                return null
            }

            val newDependencyBranch = dependencyBranch + identifier
            val packageInfo = packages[identifier]
                    ?: throw IOException("Could not find package info for $identifier")
            val dependencies = sortedSetOf<PackageReference>()

            if (packageJson["dependencies"] != null) {
                val dependencyMap = packageJson["dependencies"]
                dependencyMap.fields().forEach { (dependencyName, _) ->
                    val dependency = buildTree(rootDir, packageFile.parentFile, dependencyName, packages,
                            newDependencyBranch)
                    if (dependency != null) {
                        dependencies.add(dependency)
                    }
                }
            }

            return packageInfo.toReference(dependencies)
        } else if (rootDir == startDir) {
            log.error { "Could not find module $name" }
            return PackageReference(Identifier("NPM", "", name, "unknown, package not installed"), sortedSetOf())
        } else {
            var parent = startDir.parentFile.parentFile

            // For scoped packages we need to go one more dir up.
            if (parent.name == "node_modules") {
                parent = parent.parentFile
            }

            log.debug {
                "Could not find package file for $name in ${startDir.absolutePath}, looking in " +
                        "${parent.absolutePath} instead"
            }

            return buildTree(rootDir, parent, name, packages, dependencyBranch)
        }
    }

    private fun parseProject(packageJson: File, scopes: SortedSet<Scope>, packages: SortedSet<Package>)
            : AnalyzerResult {
        log.debug { "Parsing project info from ${packageJson.absolutePath}." }

        val json = jsonMapper.readTree(packageJson)

        val rawName = json["name"].asTextOrEmpty()
        val (namespace, name) = splitNamespaceAndName(rawName)
        if (name.isBlank()) {
            log.warn { "'$packageJson' does not define a name." }
        }

        val version = json["version"].asTextOrEmpty()
        if (version.isBlank()) {
            log.warn { "'$packageJson' does not define a version." }
        }

        val declaredLicenses = sortedSetOf<String>()
        setOf(json["license"]).mapNotNullTo(declaredLicenses) {
            it?.asText()
        }

        val homepageUrl = json["homepage"].asTextOrEmpty()

        val projectDir = packageJson.parentFile

        val vcsFromPackage = parseVcsInfo(json)

        val project = Project(
                id = Identifier(
                        provider = NPM.toString(),
                        namespace = namespace,
                        name = name,
                        version = version
                ),
                declaredLicenses = declaredLicenses,
                aliases = emptyList(),
                vcs = vcsFromPackage,
                vcsProcessed = processProjectVcs(projectDir, vcsFromPackage),
                homepageUrl = homepageUrl,
                scopes = scopes
        )

        return AnalyzerResult(Main.allowDynamicVersions, project, packages)
    }

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File) {
        if (!Main.allowDynamicVersions) {
            val lockFiles = listOf("npm-shrinkwrap.json", "package-lock.json", "yarn.lock").filter {
                File(workingDir, it).isFile
            }
            when (lockFiles.size) {
                0 -> throw IllegalArgumentException(
                        "No lockfile found in $workingDir, dependency versions are unstable.")
                1 -> log.debug { "Found lock file '${lockFiles.first()}'." }
                else -> throw IllegalArgumentException(
                        "$workingDir contains multiple lockfiles. It is ambiguous which one to use.")
            }
        }

        val managerCommand = command(workingDir)
        log.debug { "Using '$managerCommand' to install ${javaClass.simpleName} dependencies." }

        // Install all NPM dependencies to enable NPM to list dependencies.
        ProcessCapture(workingDir, managerCommand, "install", "--ignore-scripts").requireSuccess()

        // TODO: capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        // platforms except for Mac.
    }

    private fun splitNamespaceAndName(rawName: String): Pair<String, String> {
        val name = rawName.substringAfterLast("/")
        val namespace = rawName.removeSuffix(name).removeSuffix("/")
        return Pair(namespace, name)
    }
}
