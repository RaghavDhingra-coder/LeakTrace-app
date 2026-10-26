package com.codecrafters.leaktrace.ui.sms

import org.junit.Assert.*
import org.junit.Test

class DeviceSmsTest {
    @Test fun inboxGroupsNewestConversationFirstWithoutCombiningBodies() {
        val old = DeviceSms(1,10,"sender-a","first",1)
        val latest = DeviceSms(2,20,"sender-b","second",3)
        val same = DeviceSms(3,10,"sender-a","third",2)
        val groups = groupSms(listOf(old,latest,same))
        assertEquals(listOf(2L,3L),groups.map { it.first().id })
        assertEquals(listOf("third","first"),groups[1].map { it.body })
    }
    @Test fun selectingOneMessageAnalyzesOnlyItsBodyLocally() {
        val selected = DeviceSms(2,20,"sender-b","Urgent! Enter your password at http://192.0.2.1",3)
        val model = SmsInvestigationViewModel()
        model.onTextChange("Unselected private message")
        model.onTextChange(selected.body)
        model.analyze()
        assertEquals(selected.body,model.draft.value.text)
        assertTrue(model.analysis.value is SmsAnalysisState.Complete)
        assertFalse(selected.toString().contains(selected.body))
        val selectedOnly = selectedSmsCheck(selected, true)
        assertEquals(selected.body, selectedOnly.body)
        assertEquals("sender-b", selectedOnly.sender)
        assertTrue(selectedOnly.sendToServer)
        assertFalse(selectedSmsCheck(selected, false).sendToServer)
        model.clear()
        assertEquals("",model.draft.value.text)
    }
}
