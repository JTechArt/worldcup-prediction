package com.worldcup.prediction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FreemarkerEmailRendererTest {

    FreemarkerEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_33);
        cfg.setClassForTemplateLoading(FreemarkerEmailRendererTest.class, "/templates/email");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        renderer = new FreemarkerEmailRenderer(cfg);
    }

    @Test
    void renderApproval_containsFirstName() {
        String html = renderer.render("approval.ftlh", Map.of(
                "title", "Welcome",
                "firstName", "Alice",
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("Alice"));
        assertTrue(html.contains("Start Predicting"));
        assertTrue(html.contains("https://example.com"));
    }

    @Test
    void renderRejection_containsFirstName() {
        String html = renderer.render("rejection.ftlh", Map.of(
                "title", "Registration Update",
                "firstName", "Bob",
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("Bob"));
        assertTrue(html.contains("https://example.com"));
    }

    @Test
    void renderInvitation_containsInviterName() {
        String html = renderer.render("invitation.ftlh", Map.of(
                "title", "You're Invited",
                "inviterName", "Admin Jane",
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("Admin Jane"));
        assertTrue(html.contains("Join the Game"));
    }

    @Test
    void renderPredictionWindowOpen_listsMatches() {
        var matches = List.of(
                Map.of("label", "Mexico vs Canada", "kickoff", "11 Jun 19:00"),
                Map.of("label", "USA vs Brazil", "kickoff", "11 Jun 22:00")
        );
        String html = renderer.render("prediction-window-open.ftlh", Map.of(
                "title", "Predictions Open",
                "firstName", "Alice",
                "matches", matches,
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("Mexico vs Canada"));
        assertTrue(html.contains("USA vs Brazil"));
    }

    @Test
    void renderPredictionReminder_showsHoursLeft() {
        var matches = List.of(Map.of("label", "Mexico vs Canada", "kickoff", "11 Jun 19:00"));
        String html = renderer.render("prediction-reminder.ftlh", Map.of(
                "title", "Reminder",
                "firstName", "Alice",
                "matches", matches,
                "hoursLeft", "3",
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("3 Hours Left"));
        assertTrue(html.contains("Make Your Prediction"));
    }

    @Test
    void renderLeaderboardDigest_showsRankAndTopEntries() {
        var topEntries = List.of(
                Map.of("rank", 1, "name", "Alice", "points", 25),
                Map.of("rank", 2, "name", "Bob", "points", 20)
        );
        var matchResults = List.of(
                Map.of("label", "Mexico vs Canada", "score", "2 - 1")
        );
        String html = renderer.render("leaderboard-digest.ftlh", Map.of(
                "title", "Leaderboard Update",
                "firstName", "Alice",
                "rank", 1,
                "points", 25,
                "topEntries", topEntries,
                "matchResults", matchResults,
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("#1"));
        assertTrue(html.contains("25 points"));
        assertTrue(html.contains("Alice"));
        assertTrue(html.contains("2 - 1"));
    }

    @Test
    void renderResultsPublished_containsMatchInfo() {
        String html = renderer.render("results-published.ftlh", Map.of(
                "title", "Results Published",
                "firstName", "Alice",
                "matchLabel", "Mexico vs Canada",
                "score", "2 - 1",
                "appUrl", "https://example.com"
        ));
        assertTrue(html.contains("Mexico vs Canada"));
        assertTrue(html.contains("2 - 1"));
    }

    @Test
    void renderInvalidTemplate_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () ->
                renderer.render("nonexistent.ftlh", Map.of()));
    }
}
