/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.status

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.configurable.RsExternalLinterConfigurable
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.*
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.ide.icons.RsIcons
import org.rust.ide.notifications.RsExternalLinterTooltipService
import org.rust.openapiext.showSettingsDialog
import java.awt.event.MouseEvent
import javax.swing.JComponent

class RsExternalLinterWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = RsExternalLinterWidget.ID
    override fun getDisplayName(): String = "Rust External Linter"
    override fun isAvailable(project: Project): Boolean = project.hasCargoProject
    override fun createWidget(project: Project): StatusBarWidget = RsExternalLinterWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class RsExternalLinterWidget(private val project: Project) : TextPanel.WithIconAndArrows(), CustomStatusBarWidget {
    private var statusBar: StatusBar? = null

    private val linter: ExternalLinter get() = project.rustSettings.externalLinter
    private val turnedOn: Boolean get() = project.rustSettings.runExternalLinterOnTheFly

    var inProgress: Boolean = false
        set(value) {
            field = value
            update()
        }

    init {
        setTextAlignment(CENTER_ALIGNMENT)
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                project.showSettingsDialog<RsExternalLinterConfigurable>()
                return true
            }
        }.installOn(this, true)
        border = StatusBarWidget.WidgetBorder.WIDE

        project.messageBus.connect(this).subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC, object : RustSettingsListener {
            override fun rustSettingsChanged(e: RustSettingsChangedEvent) {
                if (e.isChanged(State::externalLinter) || e.isChanged(State::runExternalLinterOnTheFly)) {
                    update()
                }
            }
        })

        project.service<RsExternalLinterTooltipService>().showTooltip(this)
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        update()
        statusBar.updateWidget(ID())
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getComponent(): JComponent = this

    fun update() {
        text = linter.title
        toolTipText = "${linter.title} on the fly analysis is turned " + if (turnedOn) "on" else "off"
        icon = when {
            !turnedOn -> RsIcons.GEAR_OFF
            inProgress -> RsIcons.GEAR_ANIMATED
            else -> RsIcons.GEAR
        }
        UIUtil.invokeLaterIfNeeded(this::repaint)
    }

    companion object {
        const val ID: String = "rustExternalLinterWidget"
    }
}