package org.opensources.courses.feature.homeassistant.presentation

import androidx.annotation.StringRes
import org.opensources.courses.R
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig
import org.opensources.courses.feature.lists.domain.ShoppingList

/** User-facing results: never a stack trace, always a sentence. */
enum class HaMessage(
    @param:StringRes val text: Int,
    val isError: Boolean,
) {
    SAVED(R.string.ha_saved, false),
    TOKEN_SCANNED(R.string.ha_token_scanned, false),
    INVALID_TOKEN_QR(R.string.ha_error_invalid_token_qr, true),
    SCANNER_UNAVAILABLE(R.string.ha_error_scanner_unavailable, true),
    TEST_OK(R.string.ha_test_success, false),
    SYNC_OK(R.string.ha_sync_success, false),
    SYNC_OFFLINE(R.string.ha_sync_offline, true),
    SYNC_SKIPPED(R.string.ha_sync_skipped, true),
    ENABLE_REQUIRES_CONFIG(R.string.ha_enable_requires_config, true),
    INVALID_URL(R.string.ha_error_invalid_url, true),
    MISSING_TOKEN(R.string.ha_error_missing_token, true),
    UNREACHABLE(R.string.ha_error_unreachable, true),
    UNAUTHORIZED(R.string.ha_error_unauthorized, true),
    NOT_FOUND(R.string.ha_error_not_found, true),
    REJECTED(R.string.ha_error_rejected, true),
    PROTOCOL(R.string.ha_error_protocol, true),
    SYNC_RETRY(R.string.sync_error_protocol, true),
    ;

    companion object {
        fun from(kind: HaErrorKind): HaMessage =
            when (kind) {
                HaErrorKind.INVALID_URL -> INVALID_URL
                HaErrorKind.UNREACHABLE -> UNREACHABLE
                HaErrorKind.UNAUTHORIZED -> UNAUTHORIZED
                HaErrorKind.NOT_FOUND -> NOT_FOUND
                HaErrorKind.REJECTED -> REJECTED
                HaErrorKind.PROTOCOL -> PROTOCOL
            }

        fun from(outcome: SyncOutcome): HaMessage =
            when (outcome) {
                SyncOutcome.Success -> SYNC_OK
                SyncOutcome.Offline -> SYNC_OFFLINE
                SyncOutcome.Skipped -> SYNC_SKIPPED
                is SyncOutcome.Failure ->
                    when (outcome.reason) {
                        SyncFailure.UNREACHABLE -> UNREACHABLE
                        SyncFailure.UNAUTHORIZED -> UNAUTHORIZED
                        SyncFailure.PROTOCOL -> SYNC_RETRY
                    }
            }
    }
}

sealed interface HaActionStatus {
    data object Idle : HaActionStatus

    data object Running : HaActionStatus

    data class Done(
        val message: HaMessage,
    ) : HaActionStatus
}

sealed interface RemoteListsState {
    data object NotLoaded : RemoteListsState

    data object Loading : RemoteListsState

    data class Loaded(
        val lists: List<HaTodoList>,
    ) : RemoteListsState

    data class Failed(
        val message: HaMessage,
    ) : RemoteListsState
}

data class HaSettingsUiState(
    val config: HomeAssistantConfig = HomeAssistantConfig.Default,
    val lists: List<ShoppingList> = emptyList(),
    val trackedEntityIds: Set<String> = emptySet(),
    val connection: HaActionStatus = HaActionStatus.Idle,
    val sync: HaActionStatus = HaActionStatus.Idle,
    val remoteLists: RemoteListsState = RemoteListsState.NotLoaded,
    val pickerListId: String? = null,
    /** Lists that existed before Home Assistant was set up and still wait for the user's choice. */
    val setupListIds: List<String> = emptyList(),
) {
    /** The list chosen by the user, otherwise the next list of the first setup. */
    val pickerList: ShoppingList?
        get() =
            pickerListId?.let { id -> lists.firstOrNull { it.id == id } }
                ?: setupListIds.firstNotNullOfOrNull { id -> lists.firstOrNull { it.id == id } }

    val isSetupPicker: Boolean get() = pickerListId == null && pickerList != null

    /** Home Assistant lists offered in the picker, according to the selected mode. */
    val pickerOptions: List<HaTodoList>
        get() {
            val loaded = (remoteLists as? RemoteListsState.Loaded)?.lists.orEmpty()
            return if (config.listMode == HaListMode.APP_CREATED_ONLY) loaded.filter { it.entityId in trackedEntityIds } else loaded
        }

    fun remoteName(entityId: String): String =
        (remoteLists as? RemoteListsState.Loaded)?.lists?.firstOrNull { it.entityId == entityId }?.name ?: entityId
}
