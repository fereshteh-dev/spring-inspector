package io.github.fereshtehdev.springinspector.intellij.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatchTextTest {

    static final String FILE = "src/main/java/demo/Tokens.java";
    static final String SOURCE = """
            package demo;

            import java.util.Random;

            class Tokens {
                String next() {
                    return Long.toHexString(new Random().nextLong());
                }
            }
            """;
    static final String PATCH = """
            --- a/src/main/java/demo/Tokens.java
            +++ b/src/main/java/demo/Tokens.java
            @@ -1,4 +1,4 @@
             package demo;
            \s
            -import java.util.Random;
            +import java.security.SecureRandom;
            \s
            @@ -6,3 +6,3 @@
                 String next() {
            -        return Long.toHexString(new Random().nextLong());
            +        return Long.toHexString(new SecureRandom().nextLong());
                 }
            """;

    @Test
    void appliesEveryHunkAndKeepsTheRest() {
        assertThat(PatchText.apply(SOURCE, PATCH, FILE))
                .contains(SOURCE.replace("import java.util.Random;", "import java.security.SecureRandom;")
                        .replace("new Random()", "new SecureRandom()"));
    }

    @Test
    void ignoresWindowsLineEndingsInThePatch() {
        assertThat(PatchText.apply(SOURCE, PATCH.replace("\n", "\r\n"), FILE)).isPresent();
    }

    @Test
    void keepsAFileWithoutAFinalLineBreakThatWay() {
        String source = "a\nb";
        String patch = "--- a/x.txt\n+++ b/x.txt\n@@ -2 +2 @@\n-b\n+c\n\\ No newline at end of file\n";

        assertThat(PatchText.apply(source, patch, "x.txt")).contains("a\nc");
    }

    @Test
    void insertsIntoAnEmptyRange() {
        String patch = "--- a/x.txt\n+++ b/x.txt\n@@ -1,0 +2,1 @@\n+inserted\n";

        assertThat(PatchText.apply("first\nsecond\n", patch, "x.txt")).contains("first\ninserted\nsecond\n");
    }

    @Test
    void refusesAPatchForChangedCodeAnotherFileOrNoPatchAtAll() {
        String edited = SOURCE.replace("new Random().nextLong()", "new Random().nextInt()");

        assertThat(PatchText.apply(edited, PATCH, FILE)).isEmpty();
        assertThat(PatchText.apply(SOURCE, PATCH, "src/main/java/demo/Other.java")).isEmpty();
        assertThat(PatchText.apply(SOURCE, "not a patch", FILE)).isEmpty();
        assertThat(PatchText.apply(SOURCE, "--- a/" + FILE + "\n+++ b/" + FILE + "\nnonsense\n", FILE))
                .isEmpty();
        assertThat(PatchText.apply("x\n", "--- a/x.txt\n+++ b/x.txt\n@@ -5 +5 @@\n-x\n+y\n", "x.txt"))
                .isEmpty();
        assertThat(PatchText.apply("x\n", "--- a/x.txt\n+++ b/x.txt\n@@ -1 +1 @@\n?x\n", "x.txt"))
                .isEmpty();
    }
}
