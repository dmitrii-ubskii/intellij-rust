/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.rust.ide.inspections.import.lastElement
import org.rust.ide.refactoring.move.common.ModToMove
import org.rust.ide.refactoring.move.common.RsModDeclUsage
import org.rust.ide.refactoring.move.common.RsMoveCommonProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

// see overview of move refactoring in comment for `RsMoveCommonProcessor`
class RsMoveFilesOrDirectoriesProcessor(
    private val project: Project,
    filesOrDirectoriesToMove: Array<out PsiElement /* PsiDirectory or RsFile */>,
    newParent: PsiDirectory,
    private val targetMod: RsMod,
    private val moveCallback: MoveCallback?,
    doneCallback: Runnable
) : MoveFilesOrDirectoriesProcessor(
    project,
    filesOrDirectoriesToMove,
    newParent,
    true,
    true,
    true,
    null,  // we use `moveCallback` directly in `performRefactoring`
    doneCallback
) {

    private val filesToMove: Set<RsFile> = filesOrDirectoriesToMove
        .map {
            // we checked that `adjustForMove` returns not null in `RsMoveFilesOrDirectoriesHandler#canMove`
            it.adjustForMove()
                ?: error("File or directory $it can't be moved")
        }
        .toSet()

    private val elementsToMove = filesToMove.map { ModToMove(it) }
    private val commonProcessor: RsMoveCommonProcessor = RsMoveCommonProcessor(project, elementsToMove, targetMod)

    init {
        for (file in filesToMove) {
            val modName = file.modName ?: continue
            if (targetMod.getChildModule(modName) != null) {
                throw IncorrectOperationException("Cannot move. Mod with same crate relative path already exists")
            }
        }
    }

    override fun findUsages(): Array<out UsageInfo> = commonProcessor.findUsages()

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = MultiMap<PsiElement, String>()
        checkSingleModDeclaration(usages)
        return commonProcessor.preprocessUsages(usages, conflicts) && showConflicts(conflicts, usages)
    }

    private fun checkSingleModDeclaration(usages: Array<UsageInfo>) {
        val modDeclarationsByFile = usages
            .filterIsInstance<RsModDeclUsage>()
            .groupBy { it.file }
        for (file in filesToMove) {
            val modDeclarations = modDeclarationsByFile[file]
                ?: error("Can't move ${file.name}.\nIt is not included in module tree")
            if (modDeclarations.size > 1) {
                error("Can't move ${file.name}.\nIt is declared in more than one parent modules")
            }
        }
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val oldModDeclarations = usages.filterIsInstance<RsModDeclUsage>()
        commonProcessor.performRefactoring(usages) {
            moveFilesAndModDeclarations(oldModDeclarations)
            // after move `RsFile`s remain valid
            elementsToMove
        }
        moveCallback?.refactoringCompleted()
    }

    private fun moveFilesAndModDeclarations(oldModDeclarations: List<RsModDeclUsage>) {
        moveModDeclaration(oldModDeclarations)
        super.performRefactoring(emptyArray())

        for (file in filesToMove) {
            check(!file.crateRelativePath.isNullOrEmpty())
            { "${file.name} had correct crateRelativePath before moving mod-declaration, but empty/null after move" }
        }
    }

    private fun moveModDeclaration(oldModDeclarationsAll: List<RsModDeclUsage>) {
        val psiFactory = RsPsiFactory(project)
        for ((_ /* file */, oldModDeclarations) in oldModDeclarationsAll.groupBy { it.file }) {
            val oldModDeclaration = oldModDeclarations.single().element
            commonProcessor.updateMovedItemVisibility(oldModDeclaration)
            val newModDeclaration = oldModDeclaration.copy()
            oldModDeclaration.delete()
            targetMod.insertModDecl(psiFactory, newModDeclaration)
        }
    }
}

private fun RsMod.insertModDecl(psiFactory: RsPsiFactory, modDecl: PsiElement) {
    val anchor = childrenOfType<RsModDeclItem>().lastElement ?: childrenOfType<RsUseItem>().lastElement
    if (anchor != null) {
        addAfter(modDecl, anchor)
    } else {
        val firstItem = itemsAndMacros.firstOrNull { it !is RsAttr && it !is RsVis }
            ?: (this as? RsModItem)?.rbrace
        addBefore(modDecl, firstItem)
    }

    if (modDecl.nextSibling == null) {
        addAfter(psiFactory.createNewline(), modDecl)
    }
}