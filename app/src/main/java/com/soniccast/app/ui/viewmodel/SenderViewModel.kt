package com.soniccast.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soniccast.app.data.audio.AudioPlayer
import com.soniccast.app.data.audio.AudioRecorder
import com.soniccast.app.data.storage.LatestMessageStore
import com.soniccast.app.data.transport.TransmissionManager
import com.soniccast.app.data.transport.TransmitterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SenderViewModel(application: Application) : AndroidViewModel(application) {

    private val audioPlayer = AudioPlayer()
    private val audioRecorder = AudioRecorder()
    private val latestMessageStore = LatestMessageStore.getInstance(application)

    private val transmissionManager = TransmissionManager(
        audioPlayer = audioPlayer,
        audioRecorder = audioRecorder,
        latestMessageStore = latestMessageStore
    )

    private val _messageInput = MutableStateFlow("Exam Hall A: Section 2 begins at 10:00 AM. Passkey: 7892")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _versionNumber = MutableStateFlow(1)
    val versionNumber: StateFlow<Int> = _versionNumber.asStateFlow()

    val transmitterState: StateFlow<TransmitterState> = transmissionManager.state
    val acksHeard: StateFlow<Int> = transmissionManager.acksHeard
    val syncRequestsHeard: StateFlow<Int> = transmissionManager.syncRequestsHeard
    val recoveredReceivers: StateFlow<Int> = transmissionManager.recoveredReceivers
    val currentBroadcastText: StateFlow<String> = transmissionManager.currentMessageText

    private val _logList = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logList.asStateFlow()

    init {
        viewModelScope.launch {
            transmissionManager.logs.collect { newLog ->
                _logList.value = (_logList.value + newLog).takeLast(40)
            }
        }
    }

    fun onMessageInputChanged(newText: String) {
        _messageInput.value = newText
    }

    fun startBroadcast() {
        val text = _messageInput.value.trim()
        if (text.isNotEmpty()) {
            transmissionManager.startBroadcast(text, _versionNumber.value)
            _versionNumber.value += 1
        }
    }

    fun retransmitSpecificPacket(packetId: Int) {
        transmissionManager.retransmitSpecificPacket(packetId)
    }

    override fun onCleared() {
        super.onCleared()
        transmissionManager.stop()
    }
}
