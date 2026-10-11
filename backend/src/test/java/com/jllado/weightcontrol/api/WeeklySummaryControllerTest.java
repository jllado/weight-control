package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryArchiveResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklySummaryControllerTest {

    @Test
    void returnsTheServiceConfigurationForTheAuthenticatedAccount() {
        User other = new User();
        other.setEmail("other@example.com");
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireUser()).thenReturn(other);
        WeeklySummaryService service = mock(WeeklySummaryService.class);
        var expected = new com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryConfigResponse(true, false, null, DayOfWeek.MONDAY, LocalTime.of(8, 0), "Europe/Madrid");
        when(service.config(other)).thenReturn(expected);
        WeeklySummaryController controller = new WeeklySummaryController(service, currentUser);

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
        WeeklySummaryController controller = new WeeklySummaryController(service, currentUser);

        assertEquals(response, controller.archive(2, 10, selectedFriday));
        org.mockito.Mockito.verify(service).archive(owner, 2, 10, selectedFriday);
    }

    @Test
    void scheduleRequestAcceptsWeekdayAndTimeAndRejectsInvalidInputs() throws Exception {
        User owner = new User();
        CurrentUserService currentUser = mock(CurrentUserService.class);
        WeeklySummaryService service = mock(WeeklySummaryService.class);
        when(currentUser.requireUser()).thenReturn(owner);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new WeeklySummaryController(service, currentUser))
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/weekly-summary/config")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"deliveryDay\":\"TUESDAY\",\"deliveryTime\":\"08:30\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        org.mockito.Mockito.verify(service).updateDeliverySchedule(owner,
            new com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30)));
        org.mockito.Mockito.clearInvocations(service);
        for (String body : List.of("{}", "{\"deliveryDay\":null,\"deliveryTime\":\"08:30\"}",
            "{\"deliveryDay\":\"FUNDAY\",\"deliveryTime\":\"08:30\"}", "{\"deliveryDay\":\"TUESDAY\",\"deliveryTime\":\"25:30\"}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/weekly-summary/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
