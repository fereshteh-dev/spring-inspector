package io.github.fereshtehdev.springinspector.document.content;

import java.util.List;

/** A document ready to be written: a title, a line about where it comes from, and its blocks in order. */
public record Document(String title, String subtitle, List<Block> blocks) {

    public Document {
        blocks = List.copyOf(blocks);
    }
}
