package com.dentalwings.approvalbot.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConfigUiPageController {

    @GetMapping("/")
    public String configPage() {
        return "index";
    }
}
