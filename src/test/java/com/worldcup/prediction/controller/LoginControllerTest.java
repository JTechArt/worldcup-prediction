package com.worldcup.prediction.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPage_returnsOk_andLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPage_withErrorParam_addsErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Authentication failed. Please try again."));
    }

    @Test
    void loginPage_withDisabledParam_addsDisabledAttribute() throws Exception {
        mockMvc.perform(get("/login").param("disabled", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Your account has been disabled. Please contact an administrator."));
    }
}
