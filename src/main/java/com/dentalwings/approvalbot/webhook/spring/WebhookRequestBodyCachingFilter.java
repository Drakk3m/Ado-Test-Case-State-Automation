package com.dentalwings.approvalbot.webhook.spring;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WebhookRequestBodyCachingFilter extends OncePerRequestFilter
{

    private static final String WEBHOOK_PATH = "/api/ado/webhooks/work-item-updated";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        if (request instanceof ContentCachingRequestWrapper)
        {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new ContentCachingRequestWrapper(request), response);
    }
}

