package io.github.fereshtehdev.springinspector.document.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlWriterTest {

    private final String html = HtmlWriter.write(Samples.document());

    @Test
    void writesOneCompleteSelfContainedPage() {
        assertThat(html)
                .startsWith("<!DOCTYPE html>\n<html lang=\"en\">")
                .contains("<meta charset=\"utf-8\">")
                .contains("<title>shop: project document</title>")
                .contains("<h1>shop: project document</h1>")
                .endsWith("</main>\n</body>\n</html>\n");
        assertThat(html)
                .doesNotContain("<script")
                .doesNotContain("<link")
                .doesNotContain("src=")
                .doesNotContain("@import")
                .doesNotContain("url(");
    }

    @Test
    void listsTheSectionsAndLinksToThem() {
        assertThat(html)
                .contains("<li><a href=\"#section-1\">1. Overview</a></li>")
                .contains("<li><a href=\"#section-2\">2. Architecture</a></li>")
                .contains("<h2 id=\"section-1\">1. Overview</h2>")
                .contains("<h2 id=\"section-2\">2. Architecture</h2>")
                .contains("<h3>Building blocks</h3>");
    }

    @Test
    void escapesEverythingThatComesFromTheProject() {
        String escaped = "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;quoted&quot;";

        assertThat(html)
                .contains("<p>The shop sells things. " + escaped)
                .contains("<li>Second point with " + escaped)
                .contains("<td>" + escaped)
                .contains("<td class=\"code\">" + escaped)
                .contains("<figcaption>Parts of &lt;the&gt; code base</figcaption>")
                .contains("<title>Parts of &lt;the&gt; code base</title>");
    }

    @Test
    void writesTablesWithHeaderCells() {
        assertThat(html)
                .contains("<thead><tr><th scope=\"col\">Kind</th><th scope=\"col\">Class</th></tr></thead>")
                .contains("<tr><td>Controller</td><td class=\"code\">com.acme.OrderController</td></tr>");
    }

    @Test
    void drawsFiguresAsInlineSvgWithProblemArrowsMarked() {
        assertThat(html)
                .contains("<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" viewBox=\"0 0 ")
                .contains(">web</text>")
                .contains("<text class=\"detail\"")
                .contains(">3 classes</text>")
                .contains("<path class=\"problem\" d=\"M")
                .contains("<polygon class=\"problem\" points=\"");
        assertThat(html.split("<rect ", -1)).hasSize(4);
        assertThat(html.split("<path", -1)).hasSize(4);
    }
}
