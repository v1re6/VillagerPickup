package dev.villagerpickup;

import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerPickupPlugin extends JavaPlugin {

    private VillagerItem items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new VillagerItem(this);
        getServer().getPluginManager().registerEvents(new PickupListener(this), this);
    }

    public VillagerItem items() {
        return items;
    }

    public boolean lockUnlockedJobs() {
        return getConfig().getBoolean("lock-unlocked-jobs", true);
    }
}
