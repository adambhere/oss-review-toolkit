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

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.VcsInfo

import java.io.File

/**
 * A fake [PackageManager] for projects that do not use any of the known package managers.
 */
class Unmanaged : PackageManager() {
    companion object : PackageManagerFactory<Unmanaged>("", "", emptyList()) {
        override fun create() = Unmanaged()
    }

    override fun command(workingDir: File) = throw NotImplementedError()

    /**
     * Returns an [AnalyzerResult] containing a [Project] for the passed [definitionFile], but does not perform any
     * dependency resolution.
     *
     * @param definitionFile The directory to create the project for.
     */
    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        val project = Project(
                id = Identifier(
                        provider = "Unmanaged",
                        namespace = "",
                        name = definitionFile.name,
                        version = ""
                ),
                declaredLicenses = sortedSetOf(),
                aliases = emptyList(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(definitionFile),
                homepageUrl = "",
                scopes = sortedSetOf()
        )

        return AnalyzerResult(Main.allowDynamicVersions, project, sortedSetOf())
    }
}
