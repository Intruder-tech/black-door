package io.github.intruder_tech.blackdoor.core.menu;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** An immutable chest-style menu. Titles are kept short (1.8 limits titles to 32 characters). */
public record Menu(String title, int rows, List<MenuItem> items) {

    public static final int MAX_TITLE = 32;

    public Menu {
        Objects.requireNonNull(title, "title");
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("title too long: " + title);
        }
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6");
        }
        items = List.copyOf(items);
        Set<Integer> used = new HashSet<>();
        for (MenuItem item : items) {
            if (item.slot() < 0 || item.slot() >= rows * 9 || !used.add(item.slot())) {
                throw new IllegalArgumentException("bad or duplicate slot " + item.slot());
            }
        }
    }

    public Optional<MenuItem> itemAt(int slot) {
        return items.stream().filter(i -> i.slot() == slot).findFirst();
    }
}
