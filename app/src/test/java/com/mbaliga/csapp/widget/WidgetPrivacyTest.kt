package com.mbaliga.csapp.widget

import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.widget.WidgetPrivacy.Member
import org.junit.Assert.assertEquals
import org.junit.Test

/** A home screen is public: incident titles only when something shows they aren't review or mail text. */
class WidgetPrivacyTest {
    private fun shown(title: String, vararg members: Member) = WidgetPrivacy.shownTitle(title, members = members.toList())
    private fun withheld(r: WithheldReason) = ShownTitle.Withheld(r)

    @Test fun `API review incidents keep the generic star title`() {
        assertEquals(ShownTitle.Text("4★ review"), shown("4★ review", Member(SignalType.PLAY_REVIEW, "4★ review", "The app crashes whenever I open it")))
        assertEquals(ShownTitle.Text("?★ review"), shown("?★ review", Member(SignalType.PLAY_REVIEW, "?★ review", "")))
    }

    @Test fun `a backfilled review's own title is withheld`() {
        val m = Member(SignalType.PLAY_REVIEW, "Worst update ever, Jane here", "I lost all my photos")
        assertEquals(withheld(WithheldReason.QUOTES_REVIEW), shown("Worst update ever, Jane here", m))
    }

    @Test fun `a title taken from a review body (blank review title) is withheld, also when truncated`() {
        val body = "Since the last update the camera button does nothing at all and I have tried reinstalling twice already"
        val m = Member(SignalType.PLAY_REVIEW, "", body.take(160))
        assertEquals(withheld(WithheldReason.QUOTES_REVIEW), shown(body.take(77) + "...", m))
    }

    @Test fun `an email subject is withheld`() {
        val m = Member(SignalType.EMAIL, "Refund for order 1234 - Priya", "Hello, I would like a refund")
        assertEquals(withheld(WithheldReason.QUOTES_EMAIL), shown("Refund for order 1234 - Priya", m))
        assertEquals(withheld(WithheldReason.QUOTES_EMAIL), shown("  refund for ORDER 1234 -   priya ", m))
    }

    @Test fun `GitHub issue and manual signal titles still in the incident pass`() {
        val gh = Member(SignalType.GITHUB_ISSUE, "Crash on launch (Android 15)", "Steps to reproduce")
        assertEquals(ShownTitle.Text("Crash on launch (Android 15)"), shown("Crash on launch (Android 15)", gh))
        val review = Member(SignalType.PLAY_REVIEW, "5★ review", "love it")
        val gh2 = Member(SignalType.GITHUB_ISSUE, "Camera crash after 2.3 update", "")
        assertEquals(ShownTitle.Text("Camera crash after 2.3 update"), shown("Camera crash after 2.3 update", review, gh2))
        val long = "Sync stops after the phone sleeps for more than an hour and then the queue never drains again"
        assertEquals(ShownTitle.Text(long.take(77) + "..."), shown(long.take(77) + "...", Member(SignalType.GITHUB_ISSUE, long, "")))
        val manual = Member(SignalType.MANUAL, "", "Export button greyed out on tablets")
        assertEquals(ShownTitle.Text("Export button greyed out on tablets"), shown("Export button greyed out on tablets", manual))
    }

    @Test fun `manual incidents and accepted AI titles pass, unless they quote review or mail text`() {
        assertEquals(ShownTitle.Text("Login loop"), WidgetPrivacy.shownTitle("Login loop", isManual = true))
        assertEquals(ShownTitle.Text("Photo loss after update"), WidgetPrivacy.shownTitle("Photo loss after update", acceptedTitles = listOf("photo loss after  update")))
        // An accepted triage that kept the old title (the model gave none) or echoed a subject line.
        assertEquals(
            withheld(WithheldReason.QUOTES_EMAIL),
            WidgetPrivacy.shownTitle("Refund for order 5531 - Jane Doe", acceptedTitles = listOf("Refund for order 5531 - Jane Doe"), quotedElsewhere = SignalType.EMAIL),
        )
    }

    @Test fun `a title whose anchor left the incident is withheld`() {
        // The email anchor was split out; only a GitHub issue with another title remains.
        val gh = Member(SignalType.GITHUB_ISSUE, "Refunds fail with error 500", "")
        assertEquals(withheld(WithheldReason.QUOTES_EMAIL), WidgetPrivacy.shownTitle("Refund for order 5531 - Jane Doe", members = listOf(gh), quotedElsewhere = SignalType.EMAIL))
        // Every signal moved out: nothing vouches for the title.
        assertEquals(withheld(WithheldReason.UNVERIFIED), WidgetPrivacy.shownTitle("Worst update ever, Jane here"))
        assertEquals(withheld(WithheldReason.UNVERIFIED), shown("Login loop"))
    }

    @Test fun `any title holding an email address is withheld, whatever its source`() {
        val gh = Member(SignalType.GITHUB_ISSUE, "Contact me at a.person@example.com", "")
        assertEquals(withheld(WithheldReason.CONTAINS_ADDRESS), shown("Contact me at a.person@example.com", gh))
        assertEquals(withheld(WithheldReason.CONTAINS_ADDRESS), WidgetPrivacy.shownTitle("Write to a.person@example.com", isManual = true))
        assertEquals(ShownTitle.Text("Mention @octocat in the changelog"), WidgetPrivacy.shownTitle("Mention @octocat in the changelog", isManual = true))
    }

    @Test fun `the LIKE prefix matches what clustering stored, with wildcards escaped`() {
        assertEquals("Crash on launch%", WidgetPrivacy.likePrefix("  Crash on launch... "))
        assertEquals("100\\% \\_done\\\\%", WidgetPrivacy.likePrefix("100% _done\\"))
        assertEquals(null, WidgetPrivacy.likePrefix("  ...  "))
    }

    @Test fun `blank titles are passed through for the untitled label`() {
        assertEquals(ShownTitle.Text(""), shown("   ", Member(SignalType.PLAY_REVIEW, "", "")))
    }
}
