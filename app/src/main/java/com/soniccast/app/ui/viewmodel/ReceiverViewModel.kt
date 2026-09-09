package com.soniccast.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soniccast.app.data.audio.AudioPlayer
import com.soniccast.app.data.audio.AudioRecorder
import com.soniccast.app.data.storage.LatestMessageStore
import com.soniccast.app.data.transport.DynamicGroupState
import com.soniccast.app.data.transport.ReceptionManager
import com.soniccast.app.data.transport.ReceptionState
import com.soniccast.app.data.transport.ReceiverState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private val latestMessageStore = LatestMessageStore.getInstance(application)

    private val receptionManager = ReceptionManager(
        audioRecorder = audioRecorder,
        audioPlayer = audioPlayer,
        latestMessageStore = latestMessageStore
    )

    val receiverState: StateFlow<ReceiverState> = receptionManager.receiverState
    val receptionState: StateFlow<ReceptionState?> = receptionManager.receptionState
    val dynamicGroupState: StateFlow<DynamicGroupState> = receptionManager.dynamicGroupState
    val isCalibrating: StateFlow<Boolean> = receptionManager.isCalibrating
    val audioRms: StateFlow<Float> = receptionManager.audioRms

    private val _simulatePacketLoss = MutableStateFlow(false)
    val simulatePacketLoss: StateFlow<Boolean> = _simulatePacketLoss.asStateFlow()

    private val _logList = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logList.asStateFlow()

    init {
        viewModelScope.launch {
            receptionManager.logs.collect { newLog ->
                _logList.value = (_logList.value + newLog).takeLast(40)
            }
        }
    }

    fun startListening() {
        receptionManager.startListening()
    }

    fun toggleSimulatePacketLoss(enabled: Boolean) {
        _simulatePacketLoss.value = enabled
        receptionManager.simulatePacketLoss = enabled
    }

    override fun onCleared() {
        super.onCleared()
        receptionManager.stop()
    }
}
