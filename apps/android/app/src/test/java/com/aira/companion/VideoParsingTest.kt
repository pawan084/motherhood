package com.aira.companion

import com.aira.companion.data.AiraApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of GET /v1/videos into the UI model. Pure JSON -> data, no Android, so
 * it runs as a plain JVM unit test — and running it compiles the whole main source
 * set, which is the cheapest way to catch a Learn-feature build break.
 */
class VideoParsingTest {
    private val json =
        """
        {
          "items": [
            {"id":"preg-week-24","slug":"pregnancy-week-24","title":"Pregnancy Week 24",
             "category":"pregnancy_week_by_week","category_label":"Week by week",
             "journeys":["pregnant"],
             "timing":{"type":"gestational_week","start_week":24,"end_week":24},
             "content_format":"weekly_update","duration":{"min_seconds":120,"max_seconds":240},
             "description":"Movement and energy.","safety_level":"clinical",
             "in_app_actions":["ask_aira","save_video"],"languages":["en","hi"],
             "status":"planned","clinical_review":{"status":"pending"},
             "playable":false,"saved":true},
            {"id":"symptom-bleeding-fluid","slug":"bleeding","title":"Bleeding",
             "category":"pregnancy_symptoms","category_label":"Symptoms","journeys":["pregnant"],
             "timing":{"type":"on_demand"},"content_format":"safety_explainer",
             "duration":{"min_seconds":60,"max_seconds":120},"description":"Know the signs.",
             "safety_level":"urgent","in_app_actions":["contact_care_team"],"languages":["en"],
             "status":"planned","clinical_review":{"status":"pending"},"playable":false,"saved":false}
          ],
          "week_video": {"id":"preg-week-24","slug":"pregnancy-week-24","title":"Pregnancy Week 24",
             "category":"pregnancy_week_by_week","category_label":"Week by week","journeys":["pregnant"],
             "timing":{"type":"gestational_week","start_week":24,"end_week":24},
             "content_format":"weekly_update","duration":{"min_seconds":120,"max_seconds":240},
             "description":"Movement and energy.","safety_level":"clinical",
             "in_app_actions":["ask_aira"],"languages":["en"],"status":"planned",
             "clinical_review":{"status":"pending"},"playable":false,"saved":true},
          "categories": [{"key":"pregnancy_week_by_week","label":"Week by week"},
                         {"key":"pregnancy_symptoms","label":"Symptoms"}],
          "saved_ids": ["preg-week-24"]
        }
        """.trimIndent()

    private val result = AiraApi.parseVideos(JSONObject(json))

    @Test
    fun itemsCategoriesAndSavedIdsParse() {
        assertEquals(2, result.items.size)
        assertEquals(2, result.categories.size)
        assertEquals(setOf("preg-week-24"), result.savedIds)
    }

    @Test
    fun weekVideoCarriesTimingAndSavedFlag() {
        val wv = result.weekVideo!!
        assertEquals("preg-week-24", wv.id)
        assertEquals("gestational_week", wv.timingType)
        assertEquals(24, wv.startWeek)
        assertTrue(wv.saved)
        assertFalse(wv.playable)
    }

    @Test
    fun urgentSafetyLevelAndActionsArePreserved() {
        val urgent = result.items.first { it.id == "symptom-bleeding-fluid" }
        assertEquals("urgent", urgent.safetyLevel)
        assertEquals(listOf("contact_care_team"), urgent.inAppActions)
    }
}
