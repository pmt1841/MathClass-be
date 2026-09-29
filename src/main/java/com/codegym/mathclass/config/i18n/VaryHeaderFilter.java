package com.codegym.mathclass.config.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;

@Component
public class VaryHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response);

        if (!response.isCommitted()) {
            String contentType = response.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                Collection<String> varyHeaders = response.getHeaders("Vary");
                boolean alreadyContains = varyHeaders.stream()
                        .anyMatch(v -> v != null && v.toLowerCase().contains("accept-language"));
                if (!alreadyContains) {
                    response.addHeader("Vary", "Accept-Language");
                }
            }
        }
    }
}
