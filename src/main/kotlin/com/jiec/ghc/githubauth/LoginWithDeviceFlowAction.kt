package com.jiec.ghc.githubauth

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.awt.datatransfer.StringSelection

/**
 * Logs in to the bundled GitHub plugin via the GitHub OAuth Device Flow, using an OAuth
 * app client id supplied by the user (bring-your-own-app). Device flow requires no client
 * secret, so an organization can allow-list its own app and use it here.
 */
internal class LoginWithDeviceFlowAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val properties = PropertiesComponent.getInstance()

        val clientId = Messages.showInputDialog(
            project,
            "OAuth app client id (device flow enabled, no secret needed).\n" +
            "Use an app your organization has allow-listed.",
            "GitHub Device Flow Login",
            null,
            properties.getValue(CLIENT_ID_PROPERTY, ""),
            null
        )?.trim()
        if (clientId.isNullOrEmpty()) return
        properties.setValue(CLIENT_ID_PROPERTY, clientId)

        val owner = project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess()
        try {
            // Step 1 (fast): get the device + user code.
            val code = runWithModalProgressBlocking(owner, "Requesting GitHub device code") {
                GithubDeviceFlow.requestDeviceCode(clientId)
            }

            // Step 2 (on EDT): hand the code to the user, then poll while they approve.
            CopyPasteManager.getInstance().setContents(StringSelection(code.userCode))
            BrowserUtil.browse(code.verificationUri)
            val login = runWithModalProgressBlocking(
                owner,
                "Enter code ${code.userCode} at ${code.verificationUri} (copied to clipboard)"
            ) {
                val token = GithubDeviceFlow.pollForToken(clientId, code)
                GithubTokenLogin.login(token)
            }
            Messages.showInfoMessage(project, "Logged in to github.com as '$login' via device flow.", "GHC GitHub Auth")
        }
        catch (pce: ProcessCanceledException) {
            throw pce
        }
        catch (ex: Exception) {
            Messages.showErrorDialog(project, ex.message ?: ex.toString(), "GHC GitHub Auth: Device Flow Login Failed")
        }
    }

    companion object {
        private const val CLIENT_ID_PROPERTY = "com.jiec.ghc.githubauth.clientId"
    }
}
