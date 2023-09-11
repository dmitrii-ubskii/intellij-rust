/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.project.model.CargoProjectActionBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.ide.notifications.confirmLoadingUntrustedProject
import org.rust.openapiext.saveAllDocuments

class RefreshCargoProjectsAction : CargoProjectActionBase() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (!project.confirmLoadingUntrustedProject()) return

        saveAllDocuments()
        if (project.toolchain == null || !project.hasCargoProject) {
            guessAndSetupRustProject(project, explicitRequest = true)
        } else {
            project.cargoProjects.refreshAllProjects()
        }
    }
}
