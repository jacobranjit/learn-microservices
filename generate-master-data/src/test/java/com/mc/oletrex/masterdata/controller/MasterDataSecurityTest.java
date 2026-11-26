package com.mc.oletrex.masterdata.controller;

import com.mc.oletrex.masterdata.dto.MasterDataSummary;
import com.mc.oletrex.masterdata.dto.PopulateReport;
import com.mc.oletrex.masterdata.dto.StepReport;
import com.mc.oletrex.masterdata.security.JwtRolesConverter;
import com.mc.oletrex.masterdata.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.masterdata.security.SecurityConfig;
import com.mc.oletrex.masterdata.security.TestTokens;
import com.mc.oletrex.masterdata.service.MasterDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MasterDataController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class MasterDataSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MasterDataService service;

    @Test
    void adminCanReadSummaryAndPopulate() throws Exception {
        when(service.summary()).thenReturn(new MasterDataSummary("classpath:master-data/", 1, 2, 3, 4));
        StepReport step = new StepReport(0, 0, 0, 0);
        when(service.populate()).thenReturn(new PopulateReport(true, "s", 1, step, step, step, step, List.of()));

        mvc.perform(get("/api/master-data").with(TestTokens.admin())).andExpect(status().isOk());
        mvc.perform(post("/api/master-data/populate").with(TestTokens.admin())).andExpect(status().isOk());
    }

    @Test
    void userIsForbidden() throws Exception {
        mvc.perform(get("/api/master-data").with(TestTokens.user(42)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post("/api/master-data/populate").with(TestTokens.user(42))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/master-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post("/api/master-data/populate")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
