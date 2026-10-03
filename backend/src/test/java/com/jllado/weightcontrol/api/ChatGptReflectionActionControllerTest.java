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
            {"title":"Private title","summary":"Private summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"],"meals":{"summary":"Partial meal evidence","nextAction":"Record portions"}}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.summary").value("Partial meal evidence"))
            .andExpect(jsonPath("$.meals.nextAction").value("Record portions"));
        verify(reflections).save(eq(user), eq(LocalDate.of(2026, 8, 20)), argThat(request ->
            request.meals().summary().equals("Partial meal evidence") && request.workouts() == null));

        verify(notifications).recordGptAction(user, "Reflection saved", "/reflections");
        verify(events).publishEvent(new GptActionNotificationService.GptActionCompleted(1L, 50L, "Weight Control Coach", "Reflection saved", "/reflections", "GPT_ACTION:test"));
        clearInvocations(notifications, events);
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-20").contentType("application/json").content("""
            {"title":"Title","summary":"Summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"],"workouts":{"summary":"Summary"}}
            """)).andExpect(status().isBadRequest());
        verifyNoInteractions(notifications, events);
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
                .content(weeklyPayload.replace("\"confirmed\":true", "\"confirmed\":false")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/reflections/2026-08-14?target=WEEKLY").contentType("application/json").content("""
            {"title":"Daily payload","summary":"Not a weekly reflection","positiveSignals":["gain"],"watchouts":["gap"],"nextActions":["continue"]}
            """)).andExpect(status().isBadRequest());
    }
}
