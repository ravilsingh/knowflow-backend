package com.panscience.assistant;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeEngineTest {
    private final KnowledgeEngine engine = new KnowledgeEngine(new LlmAnswerService());

    @Test void returnsGroundedAnswerWithPageReferencesAndSeparateDecisionModels() {
        Organization org = new Organization(); org.id = "org-a"; org.name = "North University";
        KnowledgeChunk chunk = chunk("org-a", "Scholarship_Rules.pdf", 4,
                "Merit scholarship eligibility requires GPA of at least 3.5 and full-time enrollment. Students may not combine merit and sports scholarships.");
        var result = engine.answer(org, "What is the merit scholarship eligibility and GPA requirement?", List.of(chunk));
        assertTrue(result.answered());
        assertEquals("Scholarship_Rules.pdf", result.sources().get(0).document());
        assertEquals(4, result.sources().get(0).page());
        assertTrue(result.decisions().containsKey("classification"));
        assertTrue(result.decisions().containsKey("conditionChecks"));
        assertTrue(result.decisions().containsKey("evidenceScore"));
        assertTrue(result.decisions().containsKey("combinedOutcome"));
    }

    @Test void neverUsesAnotherOrganizationsChunks() {
        Organization orgA = new Organization(); orgA.id = "org-a"; orgA.name = "North University";
        KnowledgeChunk orgBOnly = chunk("org-b", "South_Only.pdf", 2, "SOUTH-ONLY-BETA-2846 private grant amount is 9000 dollars.");
        var result = engine.answer(orgA, "What is SOUTH-ONLY-BETA-2846 grant amount?", List.of(orgBOnly));
        assertFalse(result.answered());
        assertTrue(result.sources().isEmpty());
        assertTrue(result.answer().contains("couldn't find"));
    }

    @Test void reportsKnowledgeGapInsteadOfInventing() {
        Organization org = new Organization(); org.id = "org-a"; org.name = "North University";
        var result = engine.answer(org, "What is the campus parking policy?", List.of(
                chunk("org-a", "Scholarship.pdf", 1, "Merit scholarship requires GPA 3.5.")));
        assertFalse(result.answered());
        assertEquals("KNOWLEDGE_GAP", result.decisions().get("classification"));
    }

    private KnowledgeChunk chunk(String tenant, String file, int page, String text) {
        KnowledgeChunk c = new KnowledgeChunk(); c.organizationId = tenant; c.fileName = file; c.page = page; c.text = text; return c;
    }
}
