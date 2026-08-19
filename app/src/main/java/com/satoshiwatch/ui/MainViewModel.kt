package com.satoshiwatch.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satoshiwatch.R
import com.satoshiwatch.core.validation.BitcoinAddressValidator
import com.satoshiwatch.core.validation.ValidationResult
import com.satoshiwatch.data.local.entity.TransactionEntity
import com.satoshiwatch.data.local.entity.WatchedAddressEntity
import com.satoshiwatch.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lokalizovatelná zpráva pro snackbar (resource + argumenty). */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())

/** Stav a akce hlavní obrazovky (dashboard) a přidávání adres. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WatchRepository
) : ViewModel() {

    val addresses: StateFlow<List<WatchedAddressEntity>> = repository.observeAddresses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Jednorázové zprávy pro snackbar. */
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /**
     * Validuje vstup (včetně BIP-21 „bitcoin:“ URI z QR kódu) a přidá adresu.
     * [onResult] dostane null při úspěchu, jinak string resource chyby.
     */
    fun addAddress(rawInput: String, label: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            when (val result = BitcoinAddressValidator.validate(rawInput)) {
                is ValidationResult.Invalid -> onResult(result.reasonRes)
                is ValidationResult.Valid -> {
                    if (repository.isWatched(result.normalized)) {
                        onResult(R.string.msg_address_exists)
                    } else {
                        repository.addAddress(result.normalized, label.trim(), result.type)
                        _messages.tryEmit(UiMessage(R.string.msg_address_added))
                        onResult(null)
                    }
                }
            }
        }
    }

    fun removeAddress(address: String) {
        viewModelScope.launch {
            repository.removeAddress(address)
            _messages.tryEmit(UiMessage(R.string.msg_address_removed))
        }
    }

    /** Ruční synchronizace všech adres (tlačítko obnovit). */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = repository.syncAll(notify = true)
                if (result.isFullSuccess) {
                    _messages.tryEmit(UiMessage(R.string.msg_sync_done))
                } else {
                    _messages.tryEmit(
                        UiMessage(
                            R.string.msg_sync_failed,
                            listOf(result.failedAddresses, result.totalAddresses)
                        )
                    )
                }
            } catch (_: Exception) {
                _messages.tryEmit(UiMessage(R.string.msg_sync_error))
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
