package com.monstro.v18.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundPanelTest {
    @Test
    fun lateMediaDiagnosticsKeepTheUsersExportPanelOpen() {
        val exporting = PanelVisibility().open(PanelId.EXPORT_SHEET)
        val afterProbe = exporting.openIfIdle(PanelId.MEDIA_MANAGER)
        assertEquals(setOf(PanelId.EXPORT_SHEET), afterProbe.openPanels)
    }

    @Test
    fun mediaProblemsOpenRecoveryWhenTheEditorIsIdle() {
        val afterProbe = PanelVisibility().openIfIdle(PanelId.MEDIA_MANAGER)
        assertEquals(setOf(PanelId.MEDIA_MANAGER), afterProbe.openPanels)
    }
}
