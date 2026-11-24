package com.wangzan101.mika.core.whatsapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The explicit "send it to my WhatsApp" gate: English and Chinese requests pass, negated and reading requests do not. */
class WhatsappOwnerRequestTest {
    @Test fun englishExplicitSendMatches() {
        assertTrue(explicitWhatsappOwnerRequest("summarize my emails and send the result to my WhatsApp"))
        assertTrue(explicitWhatsappOwnerRequest("Check the parcel status and push it to my whatsapp"))
    }

    @Test fun chineseExplicitSendMatches() {
        assertTrue(explicitWhatsappOwnerRequest("把今天的新闻摘要发到我的WhatsApp"))
        assertTrue(explicitWhatsappOwnerRequest("查一下快递进度,结果发我 WhatsApp"))
    }

    @Test fun longRequestStillMatches() {
        assertTrue(explicitWhatsappOwnerRequest(
            "find the cheapest flight to Lisbon next Friday, compare the two airlines, and when you're done send the summary to my WhatsApp so I can read it on the train",
        ))
    }

    @Test fun negatedRequestsDoNotMatch() {
        assertFalse(explicitWhatsappOwnerRequest("don't send anything to my WhatsApp"))
        assertFalse(explicitWhatsappOwnerRequest("do not push the result to my whatsapp"))
        assertFalse(explicitWhatsappOwnerRequest("不要把结果推送到我的WhatsApp"))
    }

    @Test fun readingWhatsappIsNotSending() {
        assertFalse(explicitWhatsappOwnerRequest("what's new in my WhatsApp?"))
        assertFalse(explicitWhatsappOwnerRequest("read my WhatsApp messages from Anna"))
    }

    @Test fun otherChannelsAndUnrelatedGoalsDoNotMatch() {
        assertFalse(explicitWhatsappOwnerRequest("send the result to my email"))
        assertFalse(explicitWhatsappOwnerRequest("what's the weather today"))
    }
}
