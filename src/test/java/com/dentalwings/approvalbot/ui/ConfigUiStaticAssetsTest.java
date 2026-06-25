package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigUiStaticAssetsTest {

    @Test
    void javascriptUsesAdoDiscoveryEndpointsForSelectorOptions() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("/api/config-ui/discovery/projects")
                .contains("/api/config-ui/discovery/validate-project")
                .contains("/api/config-ui/discovery/work-item-types")
                .contains("/api/config-ui/discovery/fields")
                .contains("/api/config-ui/discovery/states")
                .contains("select data-field=\"fields.approvedBySme\"")
                .contains("select data-field=\"fields.approvedBySqa\"");
    }

    @Test
    void javascriptClearsDependentSelectionsWhenParentsChange() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("clearChildSelections(project)")
                .contains("clearDiscovery(index, \"project\")")
                .contains("clearDiscovery(index, \"type\")")
                .contains("project.fields.approvedBySme = \"\"")
                .contains("project.fields.approvedBySqa = \"\"");
    }

    @Test
    void javascriptRefreshesYamlPreviewFromSelectorChanges() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("schedulePreview()")
                .contains("previewDraft(false)")
                .contains("/api/config-ui/preview");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
