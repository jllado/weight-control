package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jllado.weightcontrol.api.dto.ReflectionDtos.PagedReflectionOverviewResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReflectionControllerTest {

    @Test
    void archiveUsesTheAuthenticatedOwnerAndRequestedPage() throws Exception {
        var owner = new User();
        var currentUser = mock(CurrentUserService.class);
        var reflections = mock(DashboardReflectionService.class);
        when(currentUser.requireUser()).thenReturn(owner);
        when(reflections.getPagedOverview(owner, 1)).thenReturn(new PagedReflectionOverviewResponse(null, null, true, List.of(), 1, 10, 13, 2));
        var mvc = MockMvcBuilders.standaloneSetup(new ReflectionController(reflections, currentUser)).build();

        mvc.perform(get("/api/reflections?page=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(13));

        verify(reflections).getPagedOverview(owner, 1);
    }

    @Test
    void archiveDefaultsToTheFirstPageForHome() throws Exception {
        var owner = new User();
        var currentUser = mock(CurrentUserService.class);
        var reflections = mock(DashboardReflectionService.class);
        when(currentUser.requireUser()).thenReturn(owner);
        when(reflections.getPagedOverview(owner, 0)).thenReturn(new PagedReflectionOverviewResponse(null, null, true, List.of(), 0, 10, 0, 0));
        var mvc = MockMvcBuilders.standaloneSetup(new ReflectionController(reflections, currentUser)).build();

        mvc.perform(get("/api/reflections"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0));

        verify(reflections).getPagedOverview(owner, 0);
    }
}
