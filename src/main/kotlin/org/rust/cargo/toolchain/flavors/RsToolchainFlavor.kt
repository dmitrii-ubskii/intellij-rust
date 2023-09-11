/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Rustc
import org.rust.cargo.util.hasExecutable
import org.rust.cargo.util.pathToExecutable
import java.nio.file.Path

abstract class RsToolchainFlavor {

    fun suggestHomePaths(): Sequence<Path> = getHomePathCandidates().filter { isValidToolchainPath(it) }

    fun suggestBazelPaths(projectPath: Path?): Sequence<Path> {
        return if (projectPath == null) emptySequence() else getBazelPathCandidates(projectPath).filter { isValidToolchainPath(it) }
    }

    protected abstract fun getHomePathCandidates(): Sequence<Path>

    private fun getBazelPathCandidates(projectPath: Path): Sequence<Path> {
        if (projectPath.fileName.toString() in listOf(".ijwb", ".clwb")) { // Bazel project root
            val sourcesRoot = projectPath.parent
            val toolchainPath = RsToolchainBase.findToolchainInBazelProject(sourcesRoot.toFile()) ?: return emptySequence()
            return sequenceOf(toolchainPath)
        } else {
            return emptySequence()
        }
    }

    /**
     * Flavor is added to result in [getApplicableFlavors] if this method returns true.
     * @return whether this flavor is applicable.
     */
    protected open fun isApplicable(): Boolean = true

    /**
     * Checks if the path is the name of a Rust toolchain of this flavor.
     *
     * @param path path to check.
     * @return true if paths points to a valid home.
     */
    protected open fun isValidToolchainPath(path: Path): Boolean {
        return path.isDirectory() &&
            hasExecutable(path, Rustc.NAME) &&
            hasExecutable(path, Cargo.NAME)
    }

    protected open fun hasExecutable(path: Path, toolName: String): Boolean = path.hasExecutable(toolName)

    protected open fun pathToExecutable(path: Path, toolName: String): Path = path.pathToExecutable(toolName)

    companion object {
        private val EP_NAME: ExtensionPointName<RsToolchainFlavor> =
            ExtensionPointName.create("org.rust.toolchainFlavor")

        fun getApplicableFlavors(): List<RsToolchainFlavor> =
            EP_NAME.extensionList.filter { it.isApplicable() }

        fun getFlavor(path: Path): RsToolchainFlavor? =
            getApplicableFlavors().find { flavor -> flavor.isValidToolchainPath(path) }
    }
}
