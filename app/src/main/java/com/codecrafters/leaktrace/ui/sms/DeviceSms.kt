package com.codecrafters.leaktrace.ui.sms

/** Device inbox data is transient, never persisted or submitted by the inbox. */
class DeviceSms(val id: Long, val threadId: Long, val sender: String?, val body: String, val timestamp: Long)
fun groupSms(messages: List<DeviceSms>): List<List<DeviceSms>> = messages
    .sortedByDescending { it.timestamp }.groupBy { it.threadId }.values.toList()

/** A check is created only from the message the person tapped. */
data class SelectedSmsCheck(val body: String, val sender: String?, val sendToServer: Boolean)
fun selectedSmsCheck(message: DeviceSms, serverAnalysisAvailable: Boolean): SelectedSmsCheck =
    SelectedSmsCheck(message.body, message.sender?.take(64), serverAnalysisAvailable && message.body.isNotBlank() && message.body.length <= SmsDraftState.MAX_SMS_LENGTH)
