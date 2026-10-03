package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklySummaryControllerTest {

    @Test
    void hidesEmailRecipientFromOtherAccountsWhileKeepingTheirArchiveAccess() {
        User other = new User();
        other.setEmail("other@example.com");
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireUser()).thenReturn(other);
        WeeklySummaryController controller = new WeeklySummaryController(mock(WeeklySummaryService.class), currentUser, properties());

        var config = controller.config();

        assertFalse(config.canSend());
        assertNull(config.recipientEmail());
        assertEquals(DayOfWeek.MONDAY, config.deliveryDay());
        assertEquals(LocalTime.of(8, 0), config.deliveryTime());
        assertEquals("Europe/Madrid", config.timeZone());
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "owner@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "", ""),
            new AppProperties.WeeklySummary(true, "owner@example.com", "owner@example.com", "sender@example.com", "https://weight.example")
        );
    }
}
