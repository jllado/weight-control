package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.InAppNotificationService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InAppNotificationControllerTest {

    @Mock
    private InAppNotificationService service;
    @Mock
    private CurrentUserService currentUserService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InAppNotificationController(service, currentUserService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .build();
        user = new User();
        user.setId(1L);
    }

    @Test
    void pendingReturnsOwnedNotificationsWithActionUrls() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.findPending(user)).thenReturn(List.of(
            notification(),
            measurementNotification(11L, InAppNotificationType.WEIGHT),
            measurementNotification(12L, InAppNotificationType.BLOOD_PRESSURE),
            appUpdateNotification()
        ));

        mockMvc.perform(get("/api/notifications/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].type").value("ROUTINE"))
            .andExpect(jsonPath("$[0].title").value("Routine reminder"))
            .andExpect(jsonPath("$[0].message").value("Meditation"))
            .andExpect(jsonPath("$[0].actionUrl").value("/?routineReminderId=20&routineReminderDate=2026-08-20&notificationId=10"))
            .andExpect(jsonPath("$[1].type").value("WEIGHT"))
            .andExpect(jsonPath("$[1].actionUrl").value("/?measurementReminder=weight&measurementReminderDate=2026-08-20&notificationId=11"))
            .andExpect(jsonPath("$[2].type").value("BLOOD_PRESSURE"))
            .andExpect(jsonPath("$[2].actionUrl").value("/?measurementReminder=blood-pressure&measurementReminderDate=2026-08-20&notificationId=12"))
            .andExpect(jsonPath("$[3].type").value("APP_UPDATE"))
            .andExpect(jsonPath("$[3].message").value("Allow workout exercise reordering"))
            .andExpect(jsonPath("$[3].actionUrl").value("/"));
    }

    @Test
    void dismissUsesTheCurrentUser() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);

        mockMvc.perform(post("/api/notifications/10/dismiss"))
            .andExpect(status().isNoContent());

        verify(service).dismiss(user, 10L);
    }

    private InAppNotification notification() {
        Routine routine = new Routine();
        routine.setId(20L);
        InAppNotification notification = new InAppNotification();
        notification.setId(10L);
        notification.setUser(user);
        notification.setType(InAppNotificationType.ROUTINE);
        notification.setRoutine(routine);
        notification.setReminderDate(LocalDate.of(2026, 8, 20));
        notification.setTitle("Routine reminder");
        notification.setMessage("Meditation");
        notification.setAvailableAt(OffsetDateTime.parse("2026-08-20T07:30:00+02:00"));
        return notification;
    }

    private InAppNotification measurementNotification(Long id, InAppNotificationType type) {
        InAppNotification notification = new InAppNotification();
        notification.setId(id);
        notification.setUser(user);
        notification.setType(type);
        notification.setReminderDate(LocalDate.of(2026, 8, 20));
        notification.setTitle(type == InAppNotificationType.WEIGHT ? "Weight reminder" : "Blood pressure reminder");
        notification.setMessage(type == InAppNotificationType.WEIGHT ? "Record your weight." : "Record your blood pressure.");
        notification.setAvailableAt(OffsetDateTime.parse("2026-08-20T05:00:00+02:00"));
        return notification;
    }

    private InAppNotification appUpdateNotification() {
        InAppNotification notification = new InAppNotification();
        notification.setId(13L);
        notification.setUser(user);
        notification.setType(InAppNotificationType.APP_UPDATE);
        notification.setReminderDate(LocalDate.of(2026, 8, 18));
        notification.setTitle("Weight Control update available");
        notification.setMessage("Allow workout exercise reordering");
        notification.setAvailableAt(OffsetDateTime.parse("2026-08-18T21:45:00+02:00"));
        return notification;
    }
}
