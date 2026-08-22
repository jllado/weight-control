package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationRequest;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseSource;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationReminderTime;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MedicationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MedicationControllerTest {

    @Mock
    private MedicationService service;
    @Mock
    private CurrentUserService currentUserService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new MedicationController(service, currentUserService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
        user = new User();
        user.setId(1L);
        lenient().when(currentUserService.requireUser()).thenReturn(user);
    }

    @Test
    void createReturnsTheMedicationSchedule() throws Exception {
        Medication medication = medication();
        when(service.create(any(User.class), any(MedicationRequest.class))).thenReturn(medication);

        mockMvc.perform(post("/api/medications")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Vitamin D",
                      "doseAmount": 1,
                      "doseUnit": "tablet",
                      "notes": "With food",
                      "startDate": "2026-08-01",
                      "endDate": "2026-12-31",
                      "repeatEvery": 2,
                      "repeatUnit": "WEEK",
                      "reminderTimes": ["08:00", "20:00"],
                      "active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.repeatEvery").value(2))
            .andExpect(jsonPath("$.repeatUnit").value("WEEK"))
            .andExpect(jsonPath("$.reminderTimes[0]").value("08:00:00"))
            .andExpect(jsonPath("$.reminderTimes[1]").value("20:00:00"));
    }

    @Test
    void createRejectsMissingReminderTimes() throws Exception {
        mockMvc.perform(post("/api/medications")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Vitamin D",
                      "doseAmount": 1,
                      "doseUnit": "tablet",
                      "startDate": "2026-08-01",
                      "endDate": "2026-12-31",
                      "repeatEvery": 1,
                      "repeatUnit": "DAY",
                      "reminderTimes": [],
                      "active": true
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void doseActionsUseTheCurrentUserAndExactTimes() throws Exception {
        MedicationDose dose = dose();
        OffsetDateTime takenAt = OffsetDateTime.parse("2026-08-22T06:03:00Z");
        when(service.findDose(user, 20L)).thenReturn(dose);
        when(service.takeDose(user, 20L, takenAt)).thenReturn(dose);
        when(service.snoozeDose(user, 20L, 30)).thenReturn(OffsetDateTime.parse("2026-08-22T08:30:00+02:00"));

        mockMvc.perform(get("/api/medications/doses/20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.medicationName").value("Vitamin D"));
        mockMvc.perform(post("/api/medications/doses/20/take")
                .contentType("application/json")
                .content("{\"takenAt\":\"2026-08-22T08:03:00+02:00\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/medications/doses/20/snooze")
                .contentType("application/json")
                .content("{\"minutes\":30}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextReminderAt").value("2026-08-22T08:30:00+02:00"));

        verify(service).takeDose(user, 20L, takenAt);
        verify(service).snoozeDose(user, 20L, 30);
    }

    private Medication medication() {
        Medication medication = new Medication();
        medication.setId(10L);
        medication.setUser(user);
        medication.setName("Vitamin D");
        medication.setDoseAmount(BigDecimal.ONE);
        medication.setDoseUnit("tablet");
        medication.setNotes("With food");
        medication.setStartDate(LocalDate.of(2026, 8, 1));
        medication.setEndDate(LocalDate.of(2026, 12, 31));
        medication.setRepeatEvery(2);
        medication.setRepeatUnit(MedicationRepeatUnit.WEEK);
        medication.setActive(true);
        medication.getReminderTimes().add(reminder(medication, LocalTime.of(8, 0)));
        medication.getReminderTimes().add(reminder(medication, LocalTime.of(20, 0)));
        return medication;
    }

    private MedicationDose dose() {
        MedicationDose dose = new MedicationDose();
        dose.setId(20L);
        dose.setMedication(medication());
        dose.setScheduledAt(OffsetDateTime.parse("2026-08-22T08:00:00+02:00"));
        dose.setStatus(MedicationDoseStatus.PENDING);
        dose.setSource(MedicationDoseSource.SCHEDULED);
        dose.setMedicationName("Vitamin D");
        dose.setDoseAmount(BigDecimal.ONE);
        dose.setDoseUnit("tablet");
        return dose;
    }

    private MedicationReminderTime reminder(Medication medication, LocalTime time) {
        MedicationReminderTime reminder = new MedicationReminderTime();
        reminder.setMedication(medication);
        reminder.setReminderTime(time);
        return reminder;
    }
}
