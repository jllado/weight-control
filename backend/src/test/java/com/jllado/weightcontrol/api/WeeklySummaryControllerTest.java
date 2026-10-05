package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryArchiveResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
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

    @Test
    void archivePassesPaginationAndSelectedDateToService() {
        User owner = new User();
        LocalDate selectedFriday = LocalDate.of(2026, 4, 10);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        WeeklySummaryService service = mock(WeeklySummaryService.class);
        when(currentUser.requireUser()).thenReturn(owner);
        var response = new WeeklySummaryArchiveResponse(LocalDate.of(2026, 9, 25), true, List.of(), 2, 10, 25, 3);
        when(service.archive(owner, 2, 10, selectedFriday)).thenReturn(response);
        WeeklySummaryController controller = new WeeklySummaryController(service, currentUser, properties());

        assertEquals(response, controller.archive(2, 10, selectedFriday));
        org.mockito.Mockito.verify(service).archive(owner, 2, 10, selectedFriday);
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
