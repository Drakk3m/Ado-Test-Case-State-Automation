package com.dentalwings.approvalbot.ui;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ConfigUiPageController.class, ConfigUiApiController.class})
class ConfigUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationLocalConfigService configService;

    @MockBean
    private AdoConfigDiscoveryService discoveryService;

    @Test
    void uiPageAndModelEndpointReturnOk() throws Exception {
        when(configService.load()).thenReturn(new ConfigUiModel());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/config-ui/model"))
                .andExpect(status().isOk());
    }

    @Test
    void discoveryEndpointReturnsSafeOptionsWithoutSecrets() throws Exception {
        when(discoveryService.listFieldOptions(any(), any(), any()))
                .thenReturn(ConfigLookupResult.valid(List.of(
                        new ConfigSelectorOption("Custom.ApproverTech", "Approver Tech", "identity", "ADO")
                )));

        mockMvc.perform(post("/api/config-ui/discovery/fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "STMN-Group",
                                  "project": "ADOnis 2.0 Test Project",
                                  "workItemType": "Test Case"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Custom.ApproverTech")))
                .andExpect(content().string(containsString("Approver Tech")))
                .andExpect(content().string(containsString("\"optionCount\":1")))
                .andExpect(content().string(not(containsString("secret-pat"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void workItemTypeDiscoveryEndpointReturnsSelectorOptionShapeUsedByJavascript() throws Exception {
        when(discoveryService.listWorkItemTypeOptions(any(), any()))
                .thenReturn(ConfigLookupResult.valid(List.of(
                        new ConfigSelectorOption("Test Case", "Test Case", "", "ADO")
                )));

        mockMvc.perform(post("/api/config-ui/discovery/work-item-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "STMN-Group",
                                  "project": "ADOnis 2.0 Test Project"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"VALID\"")))
                .andExpect(content().string(containsString("\"values\"")))
                .andExpect(content().string(containsString("\"value\":\"Test Case\"")))
                .andExpect(content().string(containsString("\"displayName\":\"Test Case\"")))
                .andExpect(content().string(containsString("\"optionCount\":1")))
                .andExpect(content().string(not(containsString("secret-pat"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void emptyDiscoveryEndpointResponseIsWarningWithOptionCountZero() throws Exception {
        when(discoveryService.listWorkItemTypeOptions(any(), any()))
                .thenReturn(ConfigLookupResult.valid(List.of()));

        mockMvc.perform(post("/api/config-ui/discovery/work-item-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "STMN-Group",
                                  "project": "ADOnis 2.0 Test Project"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"WARNING\"")))
                .andExpect(content().string(containsString("\"optionCount\":0")))
                .andExpect(content().string(containsString("no options")))
                .andExpect(content().string(not(containsString("secret-pat"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }
}
