package com.jiec.ghc.githubauth

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Logs in to the bundled GitHub plugin by reusing the local GitHub CLI's OAuth token
 * (`gh auth token`). Useful when the organization's OAuth App access restrictions block
 * the "JetBrains IDE Integration" app but allow the GitHub CLI app.
 */
internal class LoginWithGhCliAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val owner = project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess()
        try {
            val login = runWithModalProgressBlocking(owner, "Logging in to GitHub with gh CLI token") {
                val token = fetchGhToken()
                GithubTokenLogin.login(token)
            }
            Messages.showInfoMessage(project, "Logged in to github.com as '$login' using the GitHub CLI token.", "GHC GitHub Auth")
        }
        catch (pce: ProcessCanceledException) {
            throw pce
        }
        catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                (ex.message ?: ex.toString()) +
                "\n\nMake sure the GitHub CLI is installed and authenticated: run 'gh auth login' in a terminal first.",
                "GHC GitHub Auth: gh CLI Login Failed"
            )
        }
    }

    private suspend fun fetchGhToken(): String = withContext(Dispatchers.IO) {
        val command = GeneralCommandLine("gh", "auth", "token", "--hostname", "github.com")
        val output = try {
            ExecUtil.execAndGetOutput(command, 15_000)
        }
        catch (ex: Exception) {
            throw IOException("Failed to run 'gh auth token': ${ex.message}", ex)
        }
        val token = output.stdout.trim()
        if (output.exitCode != 0 || token.isEmpty()) {
            throw IOException("'gh auth token' failed (exit ${output.exitCode}): ${output.stderr.trim().ifEmpty { "no output" }}")
        }
        token
    }
}
