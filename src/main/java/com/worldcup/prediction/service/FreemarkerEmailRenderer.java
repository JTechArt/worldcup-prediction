package com.worldcup.prediction.service;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class FreemarkerEmailRenderer {

    private final Configuration freemarkerConfig;

    public FreemarkerEmailRenderer(
            @Qualifier("freemarkerEmailConfiguration") Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    public String render(String templateName, Map<String, Object> model) {
        try {
            var template = freemarkerConfig.getTemplate(templateName);
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException e) {
            log.error("Failed to render email template {}: {}", templateName, e.getMessage(), e);
            throw new RuntimeException("Email template rendering failed: " + templateName, e);
        }
    }
}
