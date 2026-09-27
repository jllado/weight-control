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
        var controller = new ChatGptReflectionActionController(reflections, currentUser, new GptActionNotificationService(notifications, events));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

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
}
