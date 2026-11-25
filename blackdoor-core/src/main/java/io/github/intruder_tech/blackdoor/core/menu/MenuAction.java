package io.github.intruder_tech.blackdoor.core.menu;

/** What happens when a viewer clicks an item. Runs on the viewer's owning thread. */
@FunctionalInterface
public interface MenuAction {

    void onClick(MenuSession session);
}
