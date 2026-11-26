package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.DiscordLockAuthManager
import com.example.data.local.NexusConfigPrefs
import com.example.data.local.NexusDatabase
import com.example.data.local.NexusPreferences
import com.example.data.model.AccessTier
import com.example.data.model.AdminRoleBinding
import com.example.data.model.AppEntry
import com.example.data.model.BoardCardInstance
import com.example.data.model.BoardLayoutMode
import com.example.data.model.BoardZone
import com.example.data.model.CardEntry
import com.example.data.model.CardOrientation
import com.example.data.model.CardPackEntry
import com.example.data.model.ChainStackItem
import com.example.data.model.ChatChannelType
import com.example.data.model.DeckEntry
import com.example.data.model.DiscordResource
import com.example.data.model.LoginNotificationEntry
import com.example.data.model.RpCharacter
import com.example.data.model.RpChatMessage
import com.example.data.model.SourceAuditReport
import com.example.data.model.StandardTurnFlowFlag
import com.example.data.model.TabletopLogEntry
import com.example.data.model.TabletopRuleEngineMode
import com.example.data.model.TabletopState
import com.example.data.model.TurnPhase
import com.example.data.model.UserAuthSession
import com.example.data.model.WikiArticle
import com.example.data.network.GitHubStoreService
import com.example.data.network.NexusNetworkClient
import com.example.data.network.SourceAuditorService
import com.example.data.repository.NexusRepository
import com.example.data.router.AppLaunchRouter
import com.example.data.router.AppRouteDecision
import com.example.data.util.BigIntMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DiscordBroadcastDraft(
    val username: String = "Nexus Arbiter",
    val avatarUrl: String = "",
    val title: String = "",
    val body: String = "",
    val footer: String = "Sent from Project MPV RP Master Hub",
    val accentHex: Int = 0x00E5FF,
    val fields: List<Pair<String, String>> = emptyList()
)

class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NexusDatabase.getInstance(application)
    private val preferences = NexusPreferences(application)
    private val networkClient = NexusNetworkClient()
    private val gitHubStoreService = GitHubStoreService()
    private val authManager = DiscordLockAuthManager()
    private val appLaunchRouter = AppLaunchRouter(gitHubStoreService)
    private val sourceAuditorService = SourceAuditorService(networkClient, gitHubStoreService)

    private val repository = NexusRepository(
        context = application,
        dao = database.nexusDao(),
        preferences = preferences,
        networkClient = networkClient,
        gitHubStoreService = gitHubStoreService
    )

    val configPrefs: StateFlow<NexusConfigPrefs> = repository.configPrefs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), NexusConfigPrefs()
    )
    val webhookUrl: StateFlow<String> = repository.webhookUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )
    val adminRoleBindings: StateFlow<List<AdminRoleBinding>> = repository.adminRoleBindings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val loginNotifications: StateFlow<List<LoginNotificationEntry>> = repository.loginNotifications.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _authSession = MutableStateFlow(UserAuthSession())
    val authSession: StateFlow<UserAuthSession> = _authSession.asStateFlow()

    // Local-only Developer Preview Mode override (usable only when authenticated as OWNER)
    private val _previewOverrideTier = MutableStateFlow<AccessTier?>(null)
    val previewOverrideTier: StateFlow<AccessTier?> = _previewOverrideTier.asStateFlow()

    val effectiveTier: StateFlow<AccessTier> = combine(_authSession, _previewOverrideTier) { session, override ->
        authManager.effectiveTier(session, override)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccessTier.PUBLIC)

    private val allRawApps: StateFlow<List<AppEntry>> = repository.apps.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /**
     * STRICT STEALTH HIDING: strips hiddenFromUnauthorized apps (MPV-Notebook-Source-Auditor)
     * from the reactive stream before it ever reaches the Hub UI, search, filters, or drawers.
     */
    val visibleApps: StateFlow<List<AppEntry>> = combine(allRawApps, effectiveTier) { appsList, tier ->
        authManager.filterVisibleApps(appsList, tier)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cardPacks: StateFlow<List<CardPackEntry>> = repository.cardPacks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val cards: StateFlow<List<CardEntry>> = repository.cards.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val decks: StateFlow<List<DeckEntry>> = repository.decks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val wikiArticles: StateFlow<List<WikiArticle>> = repository.wikiArticles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val rpCharacters: StateFlow<List<RpCharacter>> = repository.rpCharacters.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val discordResources: StateFlow<List<DiscordResource>> = repository.discordResources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedNavIndex = MutableStateFlow(0)
    val selectedNavIndex: StateFlow<Int> = _selectedNavIndex.asStateFlow()

    private val _highlightedCardId = MutableStateFlow<String?>(null)
    val highlightedCardId: StateFlow<String?> = _highlightedCardId.asStateFlow()

    private val _highlightedLoreId = MutableStateFlow<String?>(null)
    val highlightedLoreId: StateFlow<String?> = _highlightedLoreId.asStateFlow()

    private val _broadcastDraft = MutableStateFlow(DiscordBroadcastDraft())
    val broadcastDraft: StateFlow<DiscordBroadcastDraft> = _broadcastDraft.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Case 2 In-App GitHub Release Download Sheet state
    private val _pendingDownloadSheet = MutableStateFlow<AppRouteDecision.ShowGitHubDownloadSheet?>(null)
    val pendingDownloadSheet: StateFlow<AppRouteDecision.ShowGitHubDownloadSheet?> = _pendingDownloadSheet.asStateFlow()

    // Modals controlled from top bar / auth prompts
    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    private val _showNotificationsDialog = MutableStateFlow(false)
    val showNotificationsDialog: StateFlow<Boolean> = _showNotificationsDialog.asStateFlow()

    private val _showOwnerAuditorSheet = MutableStateFlow(false)
    val showOwnerAuditorSheet: StateFlow<Boolean> = _showOwnerAuditorSheet.asStateFlow()

    // Owner/Admin Source Auditor state
    private val _auditDocText = MutableStateFlow(
        "# Project MPV Master Canon Snapshot\n\n" +
            "Current Canon Standard: Filter 1-84\n" +
            "- Standard Match Turn Flow: End Turn and Extra Turn only.\n" +
            "- Standard Match Turn Flow: End Turn and Extra Turn only.\n" +
            "- Filters 19-21 live strictly on Avatar/Memorial Forge dossiers.\n"
    )
    val auditDocText: StateFlow<String> = _auditDocText.asStateFlow()

    private val _auditReport = MutableStateFlow<SourceAuditReport?>(null)
    val auditReport: StateFlow<SourceAuditReport?> = _auditReport.asStateFlow()

    // Strict IC Chat / OOC Normal / OOC Ruling-Call separated streams
    private val _chatMessages = MutableStateFlow(
        listOf(
            RpChatMessage(
                id = "seed-ic-1",
                channel = ChatChannelType.IC_CHAT,
                senderName = "Kael Voss",
                senderTier = "Vanguard",
                body = "The Astral Lattice is calibrated. Lock your lanes — Duel start!",
                timestampLabel = "IC-01"
            ),
            RpChatMessage(
                id = "seed-ooc-1",
                channel = ChatChannelType.OOC_NORMAL,
                senderName = "Hub Coordinator",
                senderTier = "PUBLIC",
                body = "Welcome to Project MPV RP Master Hub. Use OOC Normal for table talk and OOC Ruling-Call for judge checks.",
                timestampLabel = "OOC-01"
            ),
            RpChatMessage(
                id = "seed-ruling-1",
                channel = ChatChannelType.OOC_RULING_CALL,
                senderName = "Canon Judge",
                senderTier = "CANON_ARCHITECT_ADMIN",
                body = "Reminder: Standard match cards allow ONLY End Turn / Extra Turn flags (zero time-travel flags). Filters 19-21 belong exclusively on Avatar/Memorial Forge dossiers.",
                timestampLabel = "RULE-01"
            )
        )
    )
    val chatMessages: StateFlow<List<RpChatMessage>> = _chatMessages.asStateFlow()

    // ONE SINGLE UNIFIED TABLETOP STATE STORE
    private val _tabletopState = MutableStateFlow(
        TabletopState(
            engineMode = TabletopRuleEngineMode.FLAGSHIP_ULTIMATE_WIP,
            layoutMode = BoardLayoutMode.YUGIOH_5_ZONE,
            selectedPieColors = emptySet(), // 20-Color Pie starts with 0 default colors selected!
            zones = buildZonesForLayout(BoardLayoutMode.YUGIOH_5_ZONE)
        )
    )
    val tabletopState: StateFlow<TabletopState> = _tabletopState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadBundledRegistries()
            // Restore persisted session
            val prefs = repository.configPrefs.first()
            if (prefs.activeSessionJson.isNotBlank()) {
                _authSession.value = authManager.deserializeSession(prefs.activeSessionJson)
            }
            // Load starter deck into the single unified tabletop
            val loadedDecks = repository.decks.first { it.isNotEmpty() }
            val loadedCards = repository.cards.first { it.isNotEmpty() }
            loadedDecks.firstOrNull()?.let { starter ->
                loadDeckInternal(starter, loadedCards)
            }
        }
    }

    // --- Navigation & Dialogs ---

    fun selectTab(index: Int) {
        _selectedNavIndex.value = index.coerceIn(0, 4)
    }

    fun clearStatusMessage() { _statusMessage.value = null }
    fun showStatus(message: String) { _statusMessage.value = message }
    fun openAuthDialog() { _showAuthDialog.value = true }
    fun dismissAuthDialog() { _showAuthDialog.value = false }
    fun openNotificationsDialog() {
        _showNotificationsDialog.value = true
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }
    fun dismissNotificationsDialog() { _showNotificationsDialog.value = false }

    fun openOwnerAuditorSheet() {
        if (!effectiveTier.value.isAdminOrOwner) {
            showStatus("Authorized Owner or Admin session required.")
            return
        }
        _showOwnerAuditorSheet.value = true
    }
    fun dismissOwnerAuditorSheet() { _showOwnerAuditorSheet.value = false }
    fun dismissDownloadSheet() { _pendingDownloadSheet.value = null }

    fun openCardInVault(cardId: String) {
        _highlightedCardId.value = cardId
        _selectedNavIndex.value = 1
    }
    fun clearHighlightedCard() { _highlightedCardId.value = null }

    fun openLoreOrCharacter(targetId: String) {
        _highlightedLoreId.value = targetId
        _selectedNavIndex.value = 3
    }
    fun clearHighlightedLore() { _highlightedLoreId.value = null }

    fun prepareBroadcastAndNavigate(draft: DiscordBroadcastDraft) {
        _broadcastDraft.value = draft
        _selectedNavIndex.value = 4
    }

    fun updateBroadcastDraft(transform: (DiscordBroadcastDraft) -> DiscordBroadcastDraft) {
        _broadcastDraft.update(transform)
    }

    // =========================================================================
    // SATELLITE DISCOVERY & EXACT 3-CASE APP LAUNCH / DOWNLOAD ROUTER
    // =========================================================================

    fun syncSatelliteRepos() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.syncGitHubSatellites()
            _isBusy.value = false
            res.fold(
                onSuccess = { count ->
                    showStatus("GitHub sync complete ($count nexus-app.json manifests discovered).")
                },
                onFailure = { err ->
                    showStatus(err.message ?: "GitHub sync unavailable right now.")
                }
            )
        }
    }

    fun isAuthorizedToLaunch(app: AppEntry): Boolean {
        return authManager.isAuthorizedToLaunch(app, effectiveTier.value)
    }

    fun isInstalledOnDevice(context: Context, app: AppEntry): Boolean {
        return appLaunchRouter.isInstalledOnDevice(context, app)
    }

    fun onAppCardTapped(context: Context, app: AppEntry) {
        if (app.launchInApp) {
            selectTab(if (app.id.contains("vault", true)) 1 else 2)
            return
        }
        if (!authManager.isAuthorizedToLaunch(app, effectiveTier.value)) {
            showStatus("\"${app.name}\" requires ${app.accessTier} access. Sign in with Discord + Email.")
            _showAuthDialog.value = true
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val decision = appLaunchRouter.resolveRoute(context, app, _authSession.value)
            _isBusy.value = false
            when (decision) {
                is AppRouteDecision.LaunchInstalled -> {
                    appLaunchRouter.executeInstalledLaunch(context, decision.intent).fold(
                        onSuccess = {
                            val handoffNote = if (decision.sessionHandoffIncluded) {
                                " with verified Discord + Drive-email session handoff"
                            } else ""
                            showStatus("Launched installed ${app.name}$handoffNote.")
                        },
                        onFailure = { showStatus(it.message ?: "Launch failed.") }
                    )
                }
                is AppRouteDecision.ShowGitHubDownloadSheet -> {
                    _pendingDownloadSheet.value = decision
                }
                is AppRouteDecision.OpenWebInApp -> {
                    appLaunchRouter.openUrlInCustomTab(context, decision.targetUrl).fold(
                        onSuccess = {
                            val msg = if (decision.sessionHandoffIncluded) {
                                "Opened ${app.name} Web Portal with verified Admin handoff."
                            } else {
                                "Opened ${app.name} in-app browser."
                            }
                            showStatus(msg)
                        },
                        onFailure = { showStatus(it.message ?: "Unable to open web portal.") }
                    )
                }
                is AppRouteDecision.RouteError -> showStatus(decision.message)
            }
        }
    }

    fun openGitHubReleaseAssetDownload(context: Context, downloadUrl: String) {
        appLaunchRouter.openUrlInCustomTab(context, downloadUrl).fold(
            onSuccess = {
                dismissDownloadSheet()
                showStatus("Opened direct GitHub Release download.")
            },
            onFailure = { showStatus(it.message ?: "Unable to open GitHub Release link.") }
        )
    }

    fun openWebFallbackFromDownloadSheet(context: Context, app: AppEntry) {
        val base = app.webUrl.ifBlank { app.externalUrl }
        val target = if (app.id == AppLaunchRouter.AUDITOR_APP_ID && _authSession.value.resolvedTier.isAdminOrOwner) {
            appLaunchRouter.appendAuditorSessionHandoff(base, _authSession.value)
        } else base
        dismissDownloadSheet()
        appLaunchRouter.openUrlInCustomTab(context, target).onFailure {
            showStatus(it.message ?: "Unable to open web fallback.")
        }
    }

    fun launchDedicatedSourceAuditor(context: Context) {
        val auditor = allRawApps.value.firstOrNull { it.id == AppLaunchRouter.AUDITOR_APP_ID }
        if (auditor == null || !authManager.isAppVisibleToEffectiveTier(auditor, effectiveTier.value)) {
            showStatus("Source Auditor requires Owner or allowlisted Admin tier.")
            return
        }
        dismissOwnerAuditorSheet()
        onAppCardTapped(context, auditor)
    }

    // =========================================================================
    // DISCORD OAUTH2 + PKCE, EMAIL ROLE CHECK & OWNER LOGIN ALERTS
    // =========================================================================

    fun startDiscordOAuthPkce(context: Context) {
        val clientId = configPrefs.value.discordClientId
        authManager.launchDiscordOAuthPkce(context, clientId).fold(
            onSuccess = { showStatus(it) },
            onFailure = { showStatus(it.message ?: "Configure Discord OAuth2 Client ID.") }
        )
    }

    fun startGoogleDriveSignIn(context: Context) {
        val clientId = configPrefs.value.googleDriveClientId
        val hintEmail = _authSession.value.email
        authManager.launchGoogleDriveFileOAuth(context, clientId, hintEmail).fold(
            onSuccess = { showStatus(it) },
            onFailure = { showStatus(it.message ?: "Configure Google Drive OAuth2 Client ID.") }
        )
    }

    fun handleIncomingDeepLink(uri: Uri) {
        when {
            uri.scheme == "nexus-master-hub" && uri.host == "open" -> {
                val tabParam = uri.getQueryParameter("tab")
                val targetIndex = resolveDeepLinkTabIndex(tabParam)
                if (targetIndex != null) {
                    selectTab(targetIndex)
                    val label = when (targetIndex) {
                        0 -> "Hub"
                        1 -> "Card Vault"
                        2 -> "Tabletop"
                        3 -> "Lore & Wiki"
                        else -> "Discord RP"
                    }
                    showStatus("Opened $label via Nexus Master Hub deep link.")
                } else {
                    showStatus("Returned to Project MPV RP Master Hub.")
                }
            }
            uri.scheme == "nexus-master-hub" && uri.host == "oauth" && uri.path == "/discord" -> {
                authManager.consumeDiscordCallback(uri).fold(
                    onSuccess = { (code, _) ->
                        showStatus("Discord OAuth2 PKCE code verified (${code.take(6)}...). Confirm your email to finish role sync.")
                        _showAuthDialog.value = true
                    },
                    onFailure = { showStatus(it.message ?: "Discord OAuth2 callback error.") }
                )
            }
            uri.scheme == "nexus-master-hub" && uri.host == "oauth" && uri.path == "/google" -> {
                authManager.consumeGoogleCallback(uri).fold(
                    onSuccess = { (code, _) ->
                        val updated = _authSession.value.copy(
                            googleDriveConnected = true,
                            googleDriveEmail = _authSession.value.email.ifBlank { configPrefs.value.ownerEmail },
                            googleAccessToken = code
                        )
                        _authSession.value = updated
                        viewModelScope.launch { repository.saveActiveSessionJson(authManager.serializeSession(updated)) }
                        showStatus("Google Drive (drive.file) connected for ${updated.googleDriveEmail}.")
                    },
                    onFailure = { showStatus(it.message ?: "Google Drive callback error.") }
                )
            }
        }
    }

    /**
     * Connects a Discord account with ANY Drive-enabled email address (not Gmail-only),
     * verifies Owner (DimitriWhisenhunt@gmail.com) or allowlisted Admin roles, saves locally,
     * and dispatches Owner login alerts to the Notification Screen, Discord Webhook, and Email relay.
     */
    fun connectDiscordAccountWithEmail(
        discordHandle: String,
        discordSnowflakeId: String,
        email: String,
        hasGuildRole: Boolean
    ) {
        val cleanHandle = discordHandle.trim()
        val cleanId = discordSnowflakeId.trim()
        val cleanEmail = email.trim()
        if (cleanHandle.isBlank() || !authManager.isValidEmail(cleanEmail)) {
            showStatus("Provide a Discord handle and a valid email address (any Drive-enabled domain).")
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val prefs = configPrefs.value
            val tier = authManager.resolveTierForIdentity(
                discordUserId = cleanId,
                email = cleanEmail,
                ownerEmail = prefs.ownerEmail,
                bindings = adminRoleBindings.value,
                hasGuildRole = hasGuildRole
            )
            val newSession = UserAuthSession(
                isSignedIn = true,
                discordUserId = cleanId.ifBlank { "discord-${UUID.randomUUID().toString().take(8)}" },
                discordHandle = cleanHandle,
                email = cleanEmail,
                resolvedTier = tier,
                googleDriveConnected = _authSession.value.googleDriveConnected,
                googleDriveEmail = _authSession.value.googleDriveEmail.ifBlank { cleanEmail },
                googleAccessToken = _authSession.value.googleAccessToken,
                sessionNonce = UUID.randomUUID().toString(),
                issuedAtEpochMs = System.currentTimeMillis()
            )
            _authSession.value = newSession
            _previewOverrideTier.value = null
            repository.saveActiveSessionJson(authManager.serializeSession(newSession))

            val (discordAlertStatus, emailAlertStatus) = sourceAuditorService.dispatchOwnerLoginAlerts(
                session = newSession,
                ownerEmail = prefs.ownerEmail,
                discordWebhookUrl = prefs.webhookUrl,
                gatekeeperWorkerUrl = prefs.gatekeeperWorkerUrl
            )
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            repository.recordLoginNotification(
                LoginNotificationEntry(
                    id = UUID.randomUUID().toString(),
                    discordHandle = newSession.discordHandle,
                    discordUserId = newSession.discordUserId,
                    email = newSession.email,
                    resolvedTier = newSession.resolvedTier.name,
                    timestampLabel = stamp,
                    discordAlertStatus = discordAlertStatus,
                    emailAlertStatus = emailAlertStatus,
                    isRead = false
                )
            )
            _isBusy.value = false
            _showAuthDialog.value = false
            showStatus("Signed in as $cleanHandle (${tier.label}). Owner login alert recorded.")
        }
    }

    fun signOutDiscord() {
        viewModelScope.launch {
            _authSession.value = UserAuthSession()
            _previewOverrideTier.value = null
            _showOwnerAuditorSheet.value = false
            repository.saveActiveSessionJson("")
            showStatus("Signed out. Stealth Admin tools hidden.")
        }
    }

    fun setDeveloperPreviewTier(tier: AccessTier?) {
        if (_authSession.value.resolvedTier != AccessTier.OWNER) return
        _previewOverrideTier.value = tier
        showStatus(
            if (tier == null) "Developer Preview disabled (Live OWNER mode)."
            else "Developer Preview active: viewing as ${tier.name}."
        )
    }

    fun addAdminRoleBinding(identifier: String, identifierType: String, tier: AccessTier, label: String) {
        val clean = identifier.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.saveAdminRoleBinding(
                AdminRoleBinding(
                    id = UUID.randomUUID().toString(),
                    identifier = clean,
                    identifierType = identifierType,
                    assignedTier = tier,
                    label = label.ifBlank { clean }
                )
            )
            showStatus("Added ${tier.label} binding for $clean.")
        }
    }

    fun deleteAdminRoleBinding(id: String) {
        viewModelScope.launch {
            repository.deleteAdminRoleBinding(id)
            showStatus("Removed Admin role binding.")
        }
    }

    fun deleteLoginNotification(id: String) {
        viewModelScope.launch { repository.deleteLoginNotification(id) }
    }

    fun saveOAuthAndWorkerConfig(
        discordClientId: String,
        googleDriveClientId: String,
        gatekeeperWorkerUrl: String,
        masterGoogleDocId: String
    ) {
        viewModelScope.launch {
            repository.saveOAuthAndWorkerConfig(
                discordClientId = discordClientId,
                googleDriveClientId = googleDriveClientId,
                gatekeeperWorkerUrl = gatekeeperWorkerUrl,
                masterGoogleDocId = masterGoogleDocId
            )
            showStatus("Saved OAuth2 & Gatekeeper configuration locally.")
        }
    }

    // =========================================================================
    // OWNER / ADMIN SOURCE AUDITOR & DIFF FIXER
    // =========================================================================

    fun updateAuditDocText(text: String) { _auditDocText.value = text }

    fun fetchMasterDocFromDrive(docId: String, accessTokenOverride: String = "") {
        viewModelScope.launch {
            _isBusy.value = true
            val token = accessTokenOverride.ifBlank { _authSession.value.googleAccessToken }
            val result = sourceAuditorService.fetchGoogleDocContent(docId, token)
            _isBusy.value = false
            result.fold(
                onSuccess = { text ->
                    _auditDocText.value = text
                    showStatus("Loaded Master Google Doc ($docId) via drive.file.")
                },
                onFailure = { showStatus(it.message ?: "Unable to fetch Google Doc.") }
            )
        }
    }

    fun runSourceAudit(docId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = sourceAuditorService.runSourceAudit(
                docId = docId.ifBlank { configPrefs.value.masterGoogleDocId.ifBlank { "master-canon-doc" } },
                currentDocText = _auditDocText.value,
                gatekeeperWorkerUrl = configPrefs.value.gatekeeperWorkerUrl
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { report ->
                    _auditReport.value = report
                    showStatus("Audit complete: ${report.findings.size} finding(s) across ${report.reposChecked} GitHub repos.")
                },
                onFailure = { showStatus(it.message ?: "Source audit failed.") }
            )
        }
    }

    fun approveAndWriteAuditToDrive(docId: String, accessTokenOverride: String = "") {
        val report = _auditReport.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val token = accessTokenOverride.ifBlank { _authSession.value.googleAccessToken }
            val res = sourceAuditorService.approveAndWriteToGoogleDrive(
                docId = docId.ifBlank { report.docId },
                accessToken = token,
                approvedText = report.proposedDocumentText
            )
            _isBusy.value = false
            res.fold(
                onSuccess = { msg ->
                    _auditDocText.value = report.proposedDocumentText
                    showStatus(msg)
                },
                onFailure = { err ->
                    // Apply locally to draft if Drive OAuth token hasn't been authorized yet, while clearly telling Owner
                    _auditDocText.value = report.proposedDocumentText
                    showStatus("${err.message} Proposed fix staged in local editor.")
                }
            )
        }
    }

    // =========================================================================
    // STRICTLY SEPARATED IC / OOC NORMAL / OOC RULING-CALL CHAT
    // =========================================================================

    fun postChatMessage(channel: ChatChannelType, body: String) {
        val clean = body.trim()
        if (clean.isBlank()) return
        val session = _authSession.value
        val sender = if (session.isSignedIn && session.discordHandle.isNotBlank()) {
            session.discordHandle
        } else "Guest Duelist"
        val stamp = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val msg = RpChatMessage(
            id = UUID.randomUUID().toString(),
            channel = channel,
            senderName = sender,
            senderTier = effectiveTier.value.name,
            body = clean,
            timestampLabel = stamp
        )
        _chatMessages.update { listOf(msg) + it }
    }

    // --- Hub / Vault / Lore / Discord Standard Mutations ---

    fun togglePinApp(app: AppEntry) {
        viewModelScope.launch { repository.togglePinApp(app.id, app.isPinned) }
    }

    fun saveUserApp(app: AppEntry) {
        viewModelScope.launch {
            repository.saveUserApp(app)
            showStatus("Saved \"${app.name}\" to local Hub registry.")
        }
    }

    fun deleteUserApp(id: String) {
        viewModelScope.launch {
            repository.deleteUserApp(id)
            showStatus("Removed custom app manifest.")
        }
    }

    fun saveCard(card: CardEntry) {
        viewModelScope.launch {
            repository.saveCard(card)
            showStatus("Saved card \"${card.name}\" (${card.codeString}).")
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            repository.deleteCard(id)
            showStatus("Deleted custom card.")
        }
    }

    fun importFederatedPackUrl(url: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importFederatedPackFromUrl(url)
            _isBusy.value = false
            result.fold(
                onSuccess = { imported ->
                    val msg = "Imported \"${imported.pack.name}\" (${imported.cards.size} cards)."
                    showStatus(msg)
                    onDone(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to import card pack."
                    showStatus(msg)
                    onDone(false, msg)
                }
            )
        }
    }

    fun importFederatedPackJson(rawJson: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = repository.importFederatedPackFromRawJson(rawJson)
            result.fold(
                onSuccess = { imported ->
                    val msg = "Imported \"${imported.pack.name}\" (${imported.cards.size} cards)."
                    showStatus(msg)
                    onDone(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Invalid JSON."
                    showStatus(msg)
                    onDone(false, msg)
                }
            )
        }
    }

    fun saveDeck(deck: DeckEntry) {
        viewModelScope.launch {
            repository.saveDeck(deck)
            showStatus("Saved deck \"${deck.name}\" (${deck.cardIds.size} cards).")
        }
    }

    fun deleteDeck(id: String) {
        viewModelScope.launch {
            repository.deleteDeck(id)
            showStatus("Deleted deck.")
        }
    }

    fun saveWikiArticle(article: WikiArticle) {
        viewModelScope.launch {
            repository.saveWikiArticle(article)
            showStatus("Saved wiki entry \"${article.title}\".")
        }
    }

    fun saveRpCharacter(character: RpCharacter) {
        viewModelScope.launch {
            repository.saveRpCharacter(character)
            showStatus("Saved Avatar/Memorial dossier \"${character.name}\".")
        }
    }

    fun deleteRpCharacter(id: String) {
        viewModelScope.launch {
            repository.deleteRpCharacter(id)
            showStatus("Deleted RP persona.")
        }
    }

    fun saveDiscordResource(resource: DiscordResource) {
        viewModelScope.launch {
            repository.saveDiscordResource(resource)
            showStatus("Added \"${resource.name}\" to Discord directory.")
        }
    }

    fun deleteDiscordResource(id: String) {
        viewModelScope.launch {
            repository.deleteDiscordResource(id)
            showStatus("Removed custom Discord entry.")
        }
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            repository.saveWebhookUrl(url)
            showStatus(if (url.isBlank()) "Cleared saved webhook URL." else "Saved Discord Webhook locally.")
        }
    }

    fun sendDiscordBroadcast(
        webhookUrlInput: String,
        draft: DiscordBroadcastDraft,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.broadcastDiscordRp(
                webhookUrl = webhookUrlInput,
                username = draft.username,
                avatarUrl = draft.avatarUrl,
                title = draft.title,
                description = draft.body,
                footer = draft.footer,
                colorDecimal = draft.accentHex,
                fields = draft.fields
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { msg ->
                    repository.saveWebhookUrl(webhookUrlInput)
                    showStatus(msg)
                    onComplete(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Broadcast error."
                    showStatus(msg)
                    onComplete(false, msg)
                }
            )
        }
    }

    // =========================================================================
    // SINGLE UNIFIED TABLETOP ENGINE ACTIONS (ONE STATE STORE)
    // =========================================================================

    fun setTabletopEngineMode(mode: TabletopRuleEngineMode) {
        _tabletopState.update { current ->
            current.copy(
                engineMode = mode,
                eventLog = appendLog(current, "Switched Tabletop track -> ${mode.label}.")
            )
        }
    }

    fun setTurnFlowFlag(flag: StandardTurnFlowFlag) {
        _tabletopState.update { current ->
            current.copy(
                activeTurnFlowFlag = flag,
                eventLog = appendLog(current, "Match Turn-Flow Flag set -> ${flag.label} (zero time-travel flags).")
            )
        }
    }

    fun togglePieColor(colorName: String) {
        _tabletopState.update { current ->
            val updated = if (colorName in current.selectedPieColors) {
                current.selectedPieColors - colorName
            } else {
                current.selectedPieColors + colorName
            }
            current.copy(selectedPieColors = updated)
        }
    }

    fun clearPieColors() {
        _tabletopState.update { it.copy(selectedPieColors = emptySet()) }
    }

    fun switchBoardLayout(mode: BoardLayoutMode) {
        _tabletopState.update { current ->
            val placedCards = current.zones.mapNotNull { it.cardInstance }
            val newZones = buildZonesForLayout(mode).toMutableList()
            placedCards.forEachIndexed { idx, inst ->
                if (idx < newZones.size) {
                    newZones[idx] = newZones[idx].copy(cardInstance = inst)
                }
            }
            val overflowToHand = if (placedCards.size > newZones.size) {
                placedCards.drop(newZones.size)
            } else emptyList()

            current.copy(
                layoutMode = mode,
                zones = newZones,
                hand = current.hand + overflowToHand,
                inspectingZoneId = null,
                eventLog = appendLog(current, "Switched unified board to ${mode.label}.")
            )
        }
    }

    fun loadDeckIntoTabletop(deck: DeckEntry, navigateToBoard: Boolean = false) {
        loadDeckInternal(deck, cards.value)
        if (navigateToBoard) _selectedNavIndex.value = 2
        showStatus("Loaded \"${deck.name}\" into Tabletop.")
    }

    fun loadSingleCardIntoHand(card: CardEntry, navigateToBoard: Boolean = true) {
        _tabletopState.update { current ->
            val inst = BoardCardInstance(UUID.randomUUID().toString(), card)
            current.copy(
                hand = current.hand + inst,
                selectedHandInstanceId = inst.instanceId,
                eventLog = appendLog(current, "Added \"${card.name}\" directly to Hand.")
            )
        }
        if (navigateToBoard) _selectedNavIndex.value = 2
    }

    private fun loadDeckInternal(deck: DeckEntry, allCards: List<CardEntry>) {
        val cardMap = allCards.associateBy { it.id }
        val resolved = deck.cardIds.mapNotNull { cardMap[it] }.ifEmpty { allCards.take(8) }
        val shuffled = resolved.shuffled()
        val openingHandCount = minOf(4, shuffled.size)
        val openingHand = shuffled.take(openingHandCount).map {
            BoardCardInstance(UUID.randomUUID().toString(), it)
        }
        val remainingLibrary = shuffled.drop(openingHandCount)
        val defaultLp = if (deck.ruleset.contains("Magic", true)) "20" else "8000"

        _tabletopState.update { current ->
            current.copy(
                activeDeckId = deck.id,
                activeDeckName = deck.name,
                lifePoints = defaultLp,
                resourcePool = "3",
                turnNumber = 1,
                currentPhase = TurnPhase.MAIN,
                activeTurnFlowFlag = StandardTurnFlowFlag.END_TURN,
                library = remainingLibrary,
                hand = openingHand,
                graveyard = emptyList(),
                zones = buildZonesForLayout(current.layoutMode),
                selectedHandInstanceId = null,
                inspectingZoneId = null,
                chainStack = emptyList(),
                eventLog = listOf(
                    TabletopLogEntry(
                        id = UUID.randomUUID().toString(),
                        turn = 1,
                        phase = TurnPhase.MAIN.shortCode,
                        message = "Loaded \"${deck.name}\" (${resolved.size} cards), drew $openingHandCount."
                    )
                )
            )
        }
    }

    fun drawCard() {
        _tabletopState.update { current ->
            if (current.library.isEmpty()) {
                return@update current.copy(eventLog = appendLog(current, "Deck is empty — no cards left to draw."))
            }
            val top = current.library.first()
            val inst = BoardCardInstance(UUID.randomUUID().toString(), top)
            current.copy(
                library = current.library.drop(1),
                hand = current.hand + inst,
                eventLog = appendLog(current, "Drew \"${top.name}\" (${current.library.size - 1} left).")
            )
        }
    }

    fun shuffleLibrary() {
        _tabletopState.update { current ->
            current.copy(
                library = current.library.shuffled(),
                eventLog = appendLog(current, "Shuffled deck (${current.library.size} cards).")
            )
        }
    }

    fun selectHandCard(instanceId: String?) {
        _tabletopState.update { current ->
            current.copy(
                selectedHandInstanceId = if (current.selectedHandInstanceId == instanceId) null else instanceId,
                inspectingZoneId = null
            )
        }
    }

    fun onZoneTapped(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val selectedId = current.selectedHandInstanceId
            if (selectedId != null && zone.cardInstance == null) {
                val handCard = current.hand.find { it.instanceId == selectedId } ?: return@update current
                current.copy(
                    zones = current.zones.map { if (it.zoneId == zoneId) it.copy(cardInstance = handCard) else it },
                    hand = current.hand.filterNot { it.instanceId == selectedId },
                    selectedHandInstanceId = null,
                    inspectingZoneId = zoneId,
                    eventLog = appendLog(current, "Deployed \"${handCard.card.name}\" to ${zone.label}.")
                )
            } else if (zone.cardInstance != null) {
                if (current.inspectingZoneId == zoneId) {
                    val toggled = if (zone.cardInstance.orientation == CardOrientation.ATTACK_UNTAPPED) {
                        CardOrientation.DEFENSE_TAPPED
                    } else CardOrientation.ATTACK_UNTAPPED
                    current.copy(
                        zones = current.zones.map {
                            if (it.zoneId == zoneId) it.copy(cardInstance = zone.cardInstance.copy(orientation = toggled)) else it
                        },
                        eventLog = appendLog(current, "Rotated \"${zone.cardInstance.card.name}\" -> ${toggled.label}.")
                    )
                } else {
                    current.copy(inspectingZoneId = zoneId, selectedHandInstanceId = null)
                }
            } else {
                current.copy(inspectingZoneId = null)
            }
        }
    }

    fun quickDeploySelectedHandCard() {
        _tabletopState.update { current ->
            val selectedId = current.selectedHandInstanceId ?: return@update current
            val openZone = current.zones.firstOrNull { it.cardInstance == null }
                ?: return@update current.copy(eventLog = appendLog(current, "No open zones available."))
            val handCard = current.hand.find { it.instanceId == selectedId } ?: return@update current
            current.copy(
                zones = current.zones.map { if (it.zoneId == openZone.zoneId) it.copy(cardInstance = handCard) else it },
                hand = current.hand.filterNot { it.instanceId == selectedId },
                selectedHandInstanceId = null,
                inspectingZoneId = openZone.zoneId,
                eventLog = appendLog(current, "Quick-deployed \"${handCard.card.name}\" to ${openZone.label}.")
            )
        }
    }

    fun toggleZoneCardOrientation(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            val next = if (inst.orientation == CardOrientation.ATTACK_UNTAPPED) {
                CardOrientation.DEFENSE_TAPPED
            } else CardOrientation.ATTACK_UNTAPPED
            current.copy(
                zones = current.zones.map {
                    if (it.zoneId == zoneId) it.copy(cardInstance = inst.copy(orientation = next)) else it
                },
                eventLog = appendLog(current, "Set \"${inst.card.name}\" to ${next.label}.")
            )
        }
    }

    fun adjustZoneCardCounters(zoneId: String, delta: Int) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            val updatedCount = (inst.counters + delta).coerceAtLeast(0)
            current.copy(
                zones = current.zones.map {
                    if (it.zoneId == zoneId) it.copy(cardInstance = inst.copy(counters = updatedCount)) else it
                },
                eventLog = appendLog(current, "\"${inst.card.name}\" counters -> $updatedCount.")
            )
        }
    }

    fun returnZoneCardToHand(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            current.copy(
                zones = current.zones.map { if (it.zoneId == zoneId) it.copy(cardInstance = null) else it },
                hand = current.hand + inst.copy(orientation = CardOrientation.ATTACK_UNTAPPED, counters = 0),
                inspectingZoneId = null,
                eventLog = appendLog(current, "Returned \"${inst.card.name}\" to Hand.")
            )
        }
    }

    fun sendZoneCardToGraveyard(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            current.copy(
                zones = current.zones.map { if (it.zoneId == zoneId) it.copy(cardInstance = null) else it },
                graveyard = listOf(inst.card) + current.graveyard,
                inspectingZoneId = null,
                eventLog = appendLog(current, "Sent \"${inst.card.name}\" to Graveyard/Exile.")
            )
        }
    }

    fun adjustLifePoints(delta: String) {
        _tabletopState.update { current ->
            val updated = BigIntMath.add(current.lifePoints, delta)
            current.copy(
                lifePoints = updated,
                eventLog = appendLog(current, "LP adjusted by $delta -> ${BigIntMath.formatWithCommas(updated)}.")
            )
        }
    }

    fun multiplyLifePoints(factor: String) {
        _tabletopState.update { current ->
            val updated = BigIntMath.multiply(current.lifePoints, factor)
            current.copy(
                lifePoints = updated,
                eventLog = appendLog(current, "LP multiplied by x$factor -> ${BigIntMath.formatWithCommas(updated)}.")
            )
        }
    }

    fun setLifePointsExact(exactRaw: String) {
        if (!BigIntMath.isValidBigInt(exactRaw)) {
            showStatus("Enter a valid integer (any number of digits).")
            return
        }
        val normalized = BigIntMath.parseOrZero(exactRaw).toString()
        _tabletopState.update { current ->
            current.copy(
                lifePoints = normalized,
                eventLog = appendLog(current, "LP set to ${BigIntMath.formatWithCommas(normalized)}.")
            )
        }
    }

    fun adjustResourcePool(delta: String) {
        _tabletopState.update { current ->
            val added = BigIntMath.add(current.resourcePool, delta)
            val clamped = if (BigIntMath.parseOrZero(added).signum() < 0) "0" else added
            current.copy(
                resourcePool = clamped,
                eventLog = appendLog(current, "Resource pool -> ${BigIntMath.formatWithCommas(clamped)}.")
            )
        }
    }

    fun setResourcePoolExact(exactRaw: String) {
        if (!BigIntMath.isValidBigInt(exactRaw)) return
        val normalized = BigIntMath.parseOrZero(exactRaw).toString()
        _tabletopState.update { current ->
            current.copy(
                resourcePool = normalized,
                eventLog = appendLog(current, "Resource pool set to ${BigIntMath.formatWithCommas(normalized)}.")
            )
        }
    }

    fun selectPhase(phase: TurnPhase) {
        _tabletopState.update { current ->
            current.copy(
                currentPhase = phase,
                eventLog = appendLog(current, "Entered ${phase.label} Phase.")
            )
        }
    }

    fun advancePhase() {
        _tabletopState.update { current ->
            val phases = TurnPhase.entries
            val nextIdx = (phases.indexOf(current.currentPhase) + 1) % phases.size
            val nextTurn = if (nextIdx == 0) current.turnNumber + 1 else current.turnNumber
            val nextPhase = phases[nextIdx]
            current.copy(
                turnNumber = nextTurn,
                currentPhase = nextPhase,
                eventLog = appendLog(
                    current.copy(turnNumber = nextTurn, currentPhase = nextPhase),
                    if (nextIdx == 0) {
                        "Turn $nextTurn started (${current.activeTurnFlowFlag.label}) — Draw Phase."
                    } else "Entered ${nextPhase.label} Phase."
                )
            )
        }
    }

    fun pushChainLink(title: String, detail: String, sourceCardId: String? = null) {
        if (title.isBlank()) return
        _tabletopState.update { current ->
            val linkNum = current.chainStack.size + 1
            val item = ChainStackItem(UUID.randomUUID().toString(), linkNum, title.trim(), detail.trim(), sourceCardId)
            current.copy(
                chainStack = current.chainStack + item,
                eventLog = appendLog(current, "Chain/Stack Link $linkNum added: \"${item.title}\".")
            )
        }
    }

    fun resolveTopChainLink() {
        _tabletopState.update { current ->
            val top = current.chainStack.lastOrNull() ?: return@update current
            current.copy(
                chainStack = current.chainStack.dropLast(1),
                eventLog = appendLog(current, "Resolved Link ${top.linkNumber}: \"${top.title}\" (LIFO).")
            )
        }
    }

    fun clearChainStack() {
        _tabletopState.update { current ->
            if (current.chainStack.isEmpty()) return@update current
            current.copy(chainStack = emptyList(), eventLog = appendLog(current, "Cleared Chain/Stack."))
        }
    }

    fun toggleEventLogExpanded() {
        _tabletopState.update { it.copy(isEventLogExpanded = !it.isEventLogExpanded) }
    }

    private fun appendLog(state: TabletopState, message: String): List<TabletopLogEntry> {
        val entry = TabletopLogEntry(UUID.randomUUID().toString(), state.turnNumber, state.currentPhase.shortCode, message)
        return (listOf(entry) + state.eventLog).take(60)
    }

    private fun buildZonesForLayout(mode: BoardLayoutMode): List<BoardZone> {
        return when (mode) {
            BoardLayoutMode.YUGIOH_5_ZONE -> listOf(
                BoardZone("ygo-m1", "M1", "FRONT"),
                BoardZone("ygo-m2", "M2", "FRONT"),
                BoardZone("ygo-m3", "M3", "FRONT"),
                BoardZone("ygo-m4", "M4", "FRONT"),
                BoardZone("ygo-m5", "M5", "FRONT"),
                BoardZone("ygo-s1", "S/T 1", "BACK"),
                BoardZone("ygo-s2", "S/T 2", "BACK"),
                BoardZone("ygo-s3", "S/T 3", "BACK"),
                BoardZone("ygo-s4", "S/T 4", "BACK"),
                BoardZone("ygo-s5", "S/T 5", "BACK"),
                BoardZone("ygo-field", "Field", "SPECIAL"),
                BoardZone("ygo-emz", "Extra", "SPECIAL")
            )
            BoardLayoutMode.SPEED_DUEL_3_ZONE -> listOf(
                BoardZone("spd-m1", "M1", "FRONT"),
                BoardZone("spd-m2", "M2", "FRONT"),
                BoardZone("spd-m3", "M3", "FRONT"),
                BoardZone("spd-s1", "S/T 1", "BACK"),
                BoardZone("spd-s2", "S/T 2", "BACK"),
                BoardZone("spd-s3", "S/T 3", "BACK"),
                BoardZone("spd-skill", "Skill / Field", "SPECIAL")
            )
            BoardLayoutMode.MTG_BATTLEFIELD -> listOf(
                BoardZone("mtg-c1", "Creature 1", "FRONT"),
                BoardZone("mtg-c2", "Creature 2", "FRONT"),
                BoardZone("mtg-c3", "Creature 3", "FRONT"),
                BoardZone("mtg-pw", "Planeswalker", "FRONT"),
                BoardZone("mtg-l1", "Land 1", "BACK"),
                BoardZone("mtg-l2", "Land 2", "BACK"),
                BoardZone("mtg-l3", "Land 3", "BACK"),
                BoardZone("mtg-art", "Artifact/Ench", "BACK"),
                BoardZone("mtg-cmd", "Command", "SPECIAL")
            )
            BoardLayoutMode.HYBRID_6_LANE -> listOf(
                BoardZone("hyb-l1", "Lane 1", "FRONT"),
                BoardZone("hyb-l2", "Lane 2", "FRONT"),
                BoardZone("hyb-l3", "Lane 3", "FRONT"),
                BoardZone("hyb-l4", "Lane 4", "BACK"),
                BoardZone("hyb-l5", "Lane 5", "BACK"),
                BoardZone("hyb-l6", "Lane 6", "BACK"),
                BoardZone("hyb-omni", "Omni-Nexus", "SPECIAL")
            )
        }
    }

    companion object {
        fun resolveDeepLinkTabIndex(tabParam: String?): Int? {
            return when (tabParam?.trim()?.lowercase()) {
                "apps", "hub" -> 0
                "cards", "vault" -> 1
                "tabletop", "board" -> 2
                "wiki", "lore" -> 3
                "rp", "discord" -> 4
                else -> null
            }
        }
    }
}
