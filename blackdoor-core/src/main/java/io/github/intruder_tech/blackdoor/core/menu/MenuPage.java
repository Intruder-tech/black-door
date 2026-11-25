package io.github.intruder_tech.blackdoor.core.menu;

/** Builds a menu from current state; rebuilt on every refresh so it always shows live values. */
@FunctionalInterface
public interface MenuPage {

    Menu build(MenuSession session);
}
