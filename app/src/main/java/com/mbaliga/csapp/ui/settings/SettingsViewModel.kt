package com.mbaliga.csapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.github.GitHubIssuePollRepository
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.settings.AppSettingsStore
import com.mbaliga.csapp.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val githubOwner: String = "",
    val githubRepo: String = "",
    val playPackageName: String = "",
    val hasGithubToken: Boolean = false,
    val hasPlayKey: Boolean = false,
    val statusMessage: String? = null,
    val isPolling: Boolean = false,
)

class SettingsViewModel(
    private val credentialStore: CredentialStore,
    private val settingsStore: AppSettingsStore,
    private val gitHubIssuePollRepository: GitHubIssuePollRepository,
    private val playReviewRepository: PlayReviewRepository,
    private val incidentRepository: IncidentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState() = SettingsUiState(
        githubOwner = settingsStore.githubOwner.orEmpty(),
        githubRepo = settingsStore.githubRepo.orEmpty(),
        playPackageName = settingsStore.playPackageName.orEmpty(),
        hasGithubToken = credentialStore.hasGithubToken(),
        hasPlayKey = credentialStore.hasPlayServiceAccountKey(),
    )

    fun saveGithubRepo(owner: String, repo: String) {
        settingsStore.githubOwner = owner
        settingsStore.githubRepo = repo
        _uiState.value = _uiState.value.copy(githubOwner = owner, githubRepo = repo, statusMessage = "GitHub repo saved")
    }

    fun saveGithubToken(token: String) {
        credentialStore.putGithubToken(token)
        _uiState.value = _uiState.value.copy(hasGithubToken = true, statusMessage = "GitHub token saved (encrypted)")
    }

    fun savePlayPackageName(packageName: String) {
        settingsStore.playPackageName = packageName
        _uiState.value = _uiState.value.copy(playPackageName = packageName, statusMessage = "Play package name saved")
    }

    fun savePlayServiceAccountKey(json: String) {
        credentialStore.putPlayServiceAccountKeyJson(json)
        _uiState.value = _uiState.value.copy(hasPlayKey = true, statusMessage = "Play service-account key saved (encrypted)")
    }

    fun pollGithubNow() {
        val owner = settingsStore.githubOwner
        val repo = settingsStore.githubRepo
        if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Set a GitHub owner/repo first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPolling = true)
            try {
                gitHubIssuePollRepository.pollRepo(owner, repo)
                incidentRepository.runClustering()
                _uiState.value = _uiState.value.copy(statusMessage = "GitHub poll complete")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "GitHub poll failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isPolling = false)
            }
        }
    }

    fun pollPlayNow() {
        val packageName = settingsStore.playPackageName
        if (packageName.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Set a Play package name first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPolling = true)
            try {
                playReviewRepository.pollRecentReviews(packageName)
                incidentRepository.runClustering()
                _uiState.value = _uiState.value.copy(statusMessage = "Play poll complete")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Play poll failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isPolling = false)
            }
        }
    }
}
