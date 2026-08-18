package com.codegym.mathclass.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LaTeXSanitizerTest {

    @Test
    @DisplayName("isSafe should return true for valid LaTeX content")
    void isSafe_validLaTeX_returnsTrue() {
        String safeContent = "Cho tam giác $ABC$ có $AB = 5\\text{ cm}$, $AC = 7\\text{ cm}$. Tính \\frac{1}{2}";
        assertThat(LaTeXSanitizer.isSafe(safeContent)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\\input{secret.txt}",
            "\\include{header}",
            "\\write18{rm -rf /}",
            "\\openin\\file",
            "\\verbatiminput{passwd}"
    })
    @DisplayName("isSafe should return false for dangerous LaTeX commands")
    void isSafe_dangerousCommands_returnsFalse(String dangerousText) {
        assertThat(LaTeXSanitizer.isSafe(dangerousText)).isFalse();
        assertThat(LaTeXSanitizer.findDangerousCommand(dangerousText)).isNotNull();
    }

    @Test
    @DisplayName("normalizeKatexDelimiters should convert \\(...\\) and \\[...\\] to dollar signs")
    void normalizeKatexDelimiters_standardLaTeX_convertsToDollars() {
        String input = "Biểu thức \\(x^2 + 1\\) và \\[y = \\frac{a}{b}\\]";
        String expected = "Biểu thức $x^2 + 1$ và $$y = \\frac{a}{b}$$";

        assertThat(LaTeXSanitizer.normalizeKatexDelimiters(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalizeKatexDelimiters should fix nested dollars caused by AI like $lấy $\\pi \\approx 3.14$$")
    void normalizeKatexDelimiters_nestedDollarsFromAi_repairsToParentheses() {
        String input = "Cho hình tròn $(O; R)$ với $R = 5\\text{ cm}$. Tính diện tích $S$ của hình tròn đó $lấy $\\pi \\approx 3.14$$.";
        String expected = "Cho hình tròn $(O; R)$ với $R = 5\\text{ cm}$. Tính diện tích $S$ của hình tròn đó (lấy $\\pi \\approx 3.14$).";

        assertThat(LaTeXSanitizer.normalizeKatexDelimiters(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalizeKatexDelimiters should preserve valid parenthetical notes with KaTeX math")
    void normalizeKatexDelimiters_validParentheticalNotes_remainsUnchanged() {
        String input = "Cho hình tròn $(O; R)$ với $R = 5\\text{ cm}$. Tính diện tích $S$ của hình tròn đó (lấy $\\pi \\approx 3.14$).";

        assertThat(LaTeXSanitizer.normalizeKatexDelimiters(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("normalizeKatexDelimiters should convert raw parenthesized math without dollars")
    void normalizeKatexDelimiters_rawParenthesizedMath_convertsToDollars() {
        String input = "Hãy tính bán kính (R = 3\\text{ cm}) và công thức (S = \\pi R^2)";
        String expected = "Hãy tính bán kính $R = 3\\text{ cm}$ và công thức $S = \\pi R^2$";

        assertThat(LaTeXSanitizer.normalizeKatexDelimiters(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalizeKatexDelimiters should handle null or blank input gracefully")
    void normalizeKatexDelimiters_nullOrBlank_returnsEmptyString() {
        assertThat(LaTeXSanitizer.normalizeKatexDelimiters(null)).isEmpty();
        assertThat(LaTeXSanitizer.normalizeKatexDelimiters("   ")).isEmpty();
    }

    @Test
    @DisplayName("extractCleanLatex should remove markdown code fences and outer delimiters")
    void extractCleanLatex_markdownCodeFence_stripsFenceAndDelimiters() {
        String input1 = "```latex\n\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n```";
        assertThat(LaTeXSanitizer.extractCleanLatex(input1))
                .isEqualTo("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}");

        String input2 = "$$\\int_{0}^{1} x^2 dx$$";
        assertThat(LaTeXSanitizer.extractCleanLatex(input2))
                .isEqualTo("\\int_{0}^{1} x^2 dx");

        String input3 = "\\[ y = ax + b \\]";
        assertThat(LaTeXSanitizer.extractCleanLatex(input3))
                .isEqualTo("y = ax + b");

        String input4 = "\\( x^2 + y^2 = 1 \\)";
        assertThat(LaTeXSanitizer.extractCleanLatex(input4))
                .isEqualTo("x^2 + y^2 = 1");

        assertThat(LaTeXSanitizer.extractCleanLatex(null)).isEmpty();
        assertThat(LaTeXSanitizer.extractCleanLatex("  ")).isEmpty();
    }
}

