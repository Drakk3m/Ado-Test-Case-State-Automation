package com.dentalwings.approvalbot.ui;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
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
    void yamlPreviewUsesLocalValidationWithoutStructuralDiscovery() throws Exception {
        var validation = new ConfigValidationResult();
        when(configService.previewLocalDraft(any()))
                .thenReturn(new AdoConfigPreview("ado:\n", validation, true, true));

        mockMvc.perform(post("/api/config-ui/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ado:")));

        verify(configService).previewLocalDraft(any());
        verifyNoInteractions(discoveryService);
    }

    @Test
    void explicitValidationEndpointRunsStrictConfigValidation() throws Exception {
        var validation = new ConfigValidationResult();
        when(configService.validate(any())).thenReturn(validation);

        mockMvc.perform(post("/api/config-ui/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(configService).validate(any());
        verify(configService, never()).previewLocalDraft(any());
        verifyNoInteractions(discoveryService);
    }

    @Test
    void saveDoesNotRepeatAdoValidationForResponsePreview() throws Exception {
        var validation = new ConfigValidationResult();
        when(configService.save(any())).thenReturn(java.nio.file.Path.of("application-local.yml"));
        when(configService.previewLocalDraft(any()))
                .thenReturn(new AdoConfigPreview("ado:\n", validation, true, true));

        mockMvc.perform(post("/api/config-ui/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(configService).save(any());
        verify(configService).previewLocalDraft(any());
        verify(configService, never()).preview(any());
        verifyNoInteractions(discoveryService);
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
    void projectDiscoveryEndpointReturnsSelectorOptionShapeUsedByJavascript() throws Exception {
        when(discoveryService.listProjectOptions(any()))
                .thenReturn(ConfigLookupResult.valid(List.of(
                        new ConfigSelectorOption("ADOnis 2.0 Test Project", "ADOnis 2.0 Test Project", "", "ADO"),
                        new ConfigSelectorOption("RART", "RART", "", "ADO")
                )));

        mockMvc.perform(post("/api/config-ui/discovery/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "STMN-Group"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"VALID\"")))
                .andExpect(content().string(containsString("\"values\"")))
                .andExpect(content().string(containsString("\"value\":\"ADOnis 2.0 Test Project\"")))
                .andExpect(content().string(containsString("\"displayName\":\"ADOnis 2.0 Test Project\"")))
                .andExpect(content().string(containsString("\"value\":\"RART\"")))
                .andExpect(content().string(containsString("\"displayName\":\"RART\"")))
                .andExpect(content().string(containsString("\"optionCount\":2")))
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
    void userSearchEndpointReturnsSelectorOptionShapeWithoutSecrets() throws Exception {
        when(discoveryService.searchIdentityOptions(any(), any(), any()))
                .thenReturn(ConfigLookupResult.valid(List.of(
                        new ConfigSelectorOption(
                                "sme@example.test",
                                "SME Sandbox <sme@example.test>",
                                "sme@example.test",
                                "project-scope",
                                "aad.user-1",
                                "/api/config-ui/discovery/users/avatar?organization=STMN-Group&descriptor=aad.user-1",
                                true
                        )
                )));

        mockMvc.perform(post("/api/config-ui/discovery/users/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "STMN-Group",
                                  "project": "ADOnis 2.0 Test Project",
                                  "query": "sme"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"VALID\"")))
                .andExpect(content().string(containsString("\"value\":\"sme@example.test\"")))
                .andExpect(content().string(containsString("\"displayName\":\"SME Sandbox <sme@example.test>\"")))
                .andExpect(content().string(containsString("\"optionCount\":1")))
                .andExpect(content().string(not(containsString("secret-pat"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void userAvatarEndpointProxiesCachedImageWithoutExposingCredentials() throws Exception {
        when(discoveryService.loadIdentityAvatar("STMN-Group", "aad.user-1"))
                .thenReturn(java.util.Optional.of(new IdentityAvatar(
                        new byte[]{1, 2, 3},
                        "image/png;api-version=7.1",
                        true
                )));

        mockMvc.perform(get("/api/config-ui/discovery/users/avatar")
                        .param("organization", "STMN-Group")
                .param("descriptor", "aad.user-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png;api-version=7.1"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
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
