package me.lorenzo.guimanager;


import me.lorenzo.guimanager.content.InventoryContents;
import me.lorenzo.guimanager.content.InventoryProvider;
import me.lorenzo.guimanager.opener.InventoryOpener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GuiManager {

    private String id;
    private String title;
    private InventoryType type;
    private int rows;
    private int columns;
    private boolean closeable;

    private InventoryProvider provider;
    private GuiManager parent;

    private List<InventoryListener<? extends Event>> listeners;
    private final InventoryManager parentManager;

    private GuiManager(InventoryManager parentManager) {
        this.parentManager = parentManager;
    }

    public Inventory open(Player player) { return open(player, 0); }
    public Inventory open(Player player, int page) {
        Optional<GuiManager> oldInv = this.parentManager.getInventory(player);

        oldInv.ifPresent(inv -> {
            inv.getListeners().stream()
                    .filter(listener -> listener.getType() == InventoryCloseEvent.class)
                    .forEach(listener -> ((InventoryListener<InventoryCloseEvent>) listener)
                            .accept(new InventoryCloseEvent(player.getOpenInventory())));

            this.parentManager.setInventory(player, null);
        });

        InventoryContents contents = new InventoryContents.Impl(this, player.getUniqueId());
        contents.pagination().page(page);

        this.parentManager.setContents(player, contents);

        try {
            this.provider.init(player, contents);

            // If the current inventory has been closed or replaced within the init method, returns
            if (!this.parentManager.getContents(player).equals(Optional.of(contents))) {
                return null;
            }

            InventoryOpener opener = this.parentManager.findOpener(type)
                    .orElseThrow(() -> new IllegalStateException("No opener found for the inventory type " + type.name()));
            Inventory handle = opener.open(this, player);

            this.parentManager.setInventory(player, this);

            return handle;
        } catch (Exception e) {
            this.parentManager.handleInventoryOpenError(this, player, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void close(Player player) {
        listeners.stream()
                .filter(listener -> listener.getType() == InventoryCloseEvent.class)
                .forEach(listener -> ((InventoryListener<InventoryCloseEvent>) listener)
                        .accept(new InventoryCloseEvent(player.getOpenInventory())));

        this.parentManager.setInventory(player, null);
        player.closeInventory();

        this.parentManager.setContents(player, null);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public InventoryType getType() { return type; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }

    public boolean isCloseable() { return closeable; }
    public void setCloseable(boolean closeable) { this.closeable = closeable; }

    public InventoryProvider getProvider() { return provider; }
    public Optional<GuiManager> getParent() { return Optional.ofNullable(parent); }

    public InventoryManager getParentManager() { return parentManager; }

    List<InventoryListener<? extends Event>> getListeners() { return listeners; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private String id = "unknown";
        private String title = "";
        private InventoryType type = InventoryType.CHEST;
        private int rows = 6;
        private int columns = 9;
        private boolean closeable = true;

        private InventoryManager manager;
        private InventoryProvider provider;
        private GuiManager parent;

        private List<InventoryListener<? extends Event>> listeners = new ArrayList<>();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder type(InventoryType type) {
            this.type = type;
            return this;
        }

        public Builder size(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
            return this;
        }

        public Builder closeable(boolean closeable) {
            this.closeable = closeable;
            return this;
        }

        public Builder provider(InventoryProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder parent(GuiManager parent) {
            this.parent = parent;
            return this;
        }

        public Builder listener(InventoryListener<? extends Event> listener) {
            this.listeners.add(listener);
            return this;
        }

        public Builder manager(InventoryManager manager) {
            this.manager = manager;
            return this;
        }

        public GuiManager build() {
            InventoryManager manager = this.manager != null ? this.manager : parent.getParentManager();

            GuiManager inv = new GuiManager(manager);
            inv.id = this.id;
            inv.title = this.title;
            inv.type = this.type;
            inv.rows = this.rows;
            inv.columns = this.columns;
            inv.closeable = this.closeable;
            inv.provider = this.provider;
            inv.parent = this.parent;
            inv.listeners = this.listeners;

            return inv;
        }
    }

}
