package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.core.command.BdSender;

/** One viewer's open menu. All methods are safe to call from any thread. */
public interface MenuSession {

    BdSender viewer();

    /** Replaces the current page and shows it. */
    void show(MenuPage page);

    /** Rebuilds and redraws the current page. */
    void refresh();

    void close();
}
