package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import com.jllado.weightcontrol.service.GptActionNotificationService;
import com.jllado.weightcontrol.service.InAppNotificationService;
import com.jllado.weightcontrol.service.WeeklyReflectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionSection;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatGptReflectionActionControllerTest {

    @Test
    void successfulReflectionSaveNotifiesWithoutIncludingReflectionContent() throws Exception {
        var reflections = mock(DashboardReflectionService.class);
        var currentUser = mock(CurrentUserService.class);
        var notifications = mock(InAppNotificationService.class);
        var events = mock(ApplicationEventPublisher.class);
        var user = new User();
        user.setId(1L);
        when(currentUser.requireUser()).thenReturn(user);
        var reflection = new DashboardReflection();
        reflection.setWindowEnd(LocalDate.of(2026, 8, 20));
        reflection.setMealsSummary("Partial meal evidence");
        reflection.setMealsNextAction("Record portions");
        when(reflections.save(eq(user), any(), any())).thenReturn(reflection);
        var notification = new InAppNotification();
        notification.setId(50L);
        notification.setTitle("Weight Control Coach");
        notification.setDeduplicationKey("GPT_ACTION:test");
        when(notifications.recordGptAction(user, "Reflection saved", "/reflections")).thenReturn(notification);
        var controller = new ChatGptReflectionActionController(reflections, currentUser, new GptActionNotificationService(notifications, events), mock(WeeklyReflectionService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
            .build();

        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20").contentType("application/json").content("""
            {"title":"Private title","summary":"Private summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"],"meals":{"summary":"Partial meal evidence","nextAction":"Record portions"},"workouts":{"summary":"No workouts recorded","nextAction":"Review training"}}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.summary").value("Partial meal evidence"))
            .andExpect(jsonPath("$.meals.nextAction").value("Record portions"));
        verify(reflections).save(eq(user), eq(LocalDate.of(2026, 8, 20)), argThat(request ->
            request.meals().summary().equals("Partial meal evidence") && request.workouts().summary().equals("No workouts recorded")));

        verify(notifications).recordGptAction(user, "Reflection saved", "/reflections");
        verify(events).publishEvent(new GptActionNotificationService.GptActionCompleted(1L, 50L, "Weight Control Coach", "Reflection saved", "/reflections", "GPT_ACTION:test"));
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20?target=DAILY").contentType("application/json")
            .content(dailyPayload("A".repeat(120))))
            .andExpect(status().isOk());
        verify(reflections).save(eq(user), eq(LocalDate.of(2026, 8, 20)), argThat(request -> request.nextActions().getFirst().length() == 120));
        clearInvocations(notifications, events);
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20").contentType("application/json").content("""
            {"title":"Title","summary":"Summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"],"workouts":{"summary":"Summary"}}
            """)).andExpect(status().isBadRequest());
        for (String target : java.util.List.of("", "?target=DAILY")) {
            for (String section : java.util.List.of("\"meals\":null,", "", "\"meals\":{\"summary\":\" \",\"nextAction\":\"Action\"},")) {
                mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20" + target).contentType("application/json").content(
                    "{\"title\":\"Title\",\"summary\":\"Summary\",\"positiveSignals\":[\"Positive\"],\"watchouts\":[\"Watch\"],\"nextActions\":[\"Action\"],"
                    + section + "\"workouts\":{\"summary\":\"No evidence\",\"nextAction\":\"Review training\"}}"))
                    .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(notifications, events);
    }

    @Test
    void rejectedDailyTextNamesTheFieldAndLimitWithoutLeakingValuesOrRunningWrites() throws Exception {
        var reflections = mock(DashboardReflectionService.class);
        var weekly = mock(WeeklyReflectionService.class);
        var currentUser = mock(CurrentUserService.class);
        var notifications = mock(GptActionNotificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ChatGptReflectionActionController(
            reflections, currentUser, notifications, weekly)).build();
        for (String target : java.util.List.of("", "?target=DAILY")) {
            for (int length : java.util.List.of(121, 128)) {
                String rejected = "A".repeat(length);
                mvc.perform(post("/api/chatgpt-actions/reflections/2026-10-08" + target)
                    .contentType("application/json").content(dailyPayload(rejected)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("nextActions[0] size must be between 0 and 120"))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("nextActions[0]"))
                    .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 120"))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains(rejected)));
            }
        }
        verifyNoInteractions(reflections, weekly, currentUser, notifications);
    }

    @Test
    void validationReportsNestedMissingAndMultipleFieldsForDailyAndWeeklyRequests() throws Exception {
        var reflections = mock(DashboardReflectionService.class);
        var weekly = mock(WeeklyReflectionService.class);
        var currentUser = mock(CurrentUserService.class);
        var notifications = mock(GptActionNotificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ChatGptReflectionActionController(
            reflections, currentUser, notifications, weekly)).build();
        var mapper = new ObjectMapper();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(dailyPayload("Action"));
        payload.put("summary", "S".repeat(201));
        payload.remove("workouts");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.get("meals")).put("nextAction", "N".repeat(121));
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-10-08?target=DAILY")
            .contentType("application/json").content(mapper.writeValueAsString(payload)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.contains("meals.nextAction", "summary", "workouts")))
            .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 120"))
            .andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 200"))
            .andExpect(jsonPath("$.errors[2].message").value("must not be null"));
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-10-09?target=WEEKLY")
            .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems("confirmed", "bodyComposition", "nextWeekActions")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Weekly reflection save requires exact user confirmation")));
        verifyNoInteractions(reflections, weekly, currentUser, notifications);
    }

    private String dailyPayload(String nextAction) {
        return """
            {"title":"Title","summary":"Summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["%s"],"meals":{"summary":"Partial evidence","nextAction":"Record portions"},"workouts":{"summary":"No workouts recorded","nextAction":"Review training"}}
            """.formatted(nextAction);
    }

    @Test
    void weeklyTargetUsesSeparateSavedSummaryContractAndRejectsDailyPayload() throws Exception {
        var reflections = mock(DashboardReflectionService.class);
        var currentUser = mock(CurrentUserService.class);
        var notifications = mock(InAppNotificationService.class);
        var events = mock(ApplicationEventPublisher.class);
        var weeklyService = mock(WeeklyReflectionService.class);
        var user = new User();
        user.setId(1L);
        when(currentUser.requireUser()).thenReturn(user);
        var section = new WeeklyReflectionSection("Recorded gains and an unknown gap.", "Review next week.");
        when(weeklyService.save(eq(user), eq(LocalDate.of(2026, 8, 14)), any())).thenReturn(new WeeklyReflectionResponse(
            LocalDate.of(2026, 8, 14), Instant.parse("2026-08-17T07:00:00Z"), "ChatGPT", "Weekly review", "Summary",
            section, section, section, section, section, section, java.util.List.of("Continue")
        ));
        var notification = new InAppNotification();
        notification.setId(51L);
        notification.setTitle("Weight Control Coach");
        notification.setDeduplicationKey("GPT_ACTION:weekly");
        when(notifications.recordGptAction(user, "Weekly reflection saved", "/weekly-summaries?date=2026-08-14")).thenReturn(notification);
        var controller = new ChatGptReflectionActionController(reflections, currentUser, new GptActionNotificationService(notifications, events), weeklyService);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
            .build();

        String weeklyPayload = """
            {"confirmed":true,"title":"Weekly review","summary":"Summary","bodyComposition":{"summary":"Body","nextAction":"Review"},"bloodPressure":{"summary":"BP","nextAction":"Review"},"routines":{"summary":"Routines","nextAction":"Review"},"nutrition":{"summary":"Nutrition","nextAction":"Review"},"trainingRecovery":{"summary":"Training","nextAction":"Review"},"goalProgress":{"summary":"Goals","nextAction":"Review"},"nextWeekActions":["Continue"]}
            """;
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-14?target=WEEKLY").contentType("application/json").content(weeklyPayload)).andExpect(status().isOk())
            .andExpect(jsonPath("$.fridayDate").value("2026-08-14"))
            .andExpect(jsonPath("$.bodyComposition.summary").value("Recorded gains and an unknown gap."));

        verify(weeklyService).save(eq(user), eq(LocalDate.of(2026, 8, 14)), any());
        verifyNoInteractions(reflections);
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-14?target=WEEKLY").contentType("application/json")
            .content(weeklyPayload.replace("\"summary\":\"Summary\"", "\"summary\":\"" + "S".repeat(500) + "\"")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-14?target=WEEKLY").contentType("application/json")
                .content(weeklyPayload.replace("\"confirmed\":true", "\"confirmed\":false")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-14?target=WEEKLY").contentType("application/json").content("""
            {"title":"Daily payload","summary":"Not a weekly reflection","positiveSignals":["gain"],"watchouts":["gap"],"nextActions":["continue"]}
            """)).andExpect(status().isBadRequest());
    }
}
