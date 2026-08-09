package com.codegym.mathclass.aiconfig.validator;

import com.codegym.mathclass.exception.InvalidVariableException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SystemPromptValidator {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    /**
     * Trích xuất toàn bộ tên biến xuất hiện trong prompt dạng {{variable_name}}
     */
    public Set<String> extractVariables(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptySet();
        }
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /**
     * Kiểm tra tính hợp lệ của biến trong prompt với danh sách allowedVariables
     */
    public void validateVariables(String content, List<String> allowedVariables) {
        if (!StringUtils.hasText(content)) {
            return;
        }

        List<String> allowed = allowedVariables != null ? allowedVariables : Collections.emptyList();
        Set<String> usedVariables = extractVariables(content);
        List<String> invalidVariables = new ArrayList<>();

        for (String var : usedVariables) {
            if (!allowed.contains(var)) {
                invalidVariables.add(var);
            }
        }

        if (!invalidVariables.isEmpty()) {
            throw new InvalidVariableException(
                    String.format("Biến %s không được phép sử dụng cho System Prompt này. Danh sách biến hợp lệ bao gồm: %s",
                            invalidVariables.stream().map(v -> "{{" + v + "}}").toList(),
                            allowed)
            );
        }
    }

    /**
     * Hỗ trợ chuyển đổi allowedVariables dạng chuỗi (phân cách bởi phẩy) thành List<String>
     */
    public List<String> parseAllowedVariables(String allowedVariablesStr) {
        if (!StringUtils.hasText(allowedVariablesStr)) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedVariablesStr.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
