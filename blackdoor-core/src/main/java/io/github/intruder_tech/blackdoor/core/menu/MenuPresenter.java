package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.core.command.BdSender;

/** Platform service that renders menus to players (Bukkit-family servers only). */
public interface MenuPresenter {

    /** Opens the page for the viewer. Returns false if the viewer cannot see menus (e.g. console). */
    boolean open(BdSender viewer, MenuPage page);

    /** Closes every Black Door menu (on disable). */
    void closeAll();
}
