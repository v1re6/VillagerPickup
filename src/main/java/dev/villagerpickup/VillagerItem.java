package dev.villagerpickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Turns a villager into an item (a villager spawn egg carrying its full data) and back. */
public final class VillagerItem {

    /** What we remember about a picked-up villager. */
    public record Stored(String snbt, String profession, int level, int xp, boolean locked) {}

    private static final String[] LEVEL_NAMES = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};

    private final NamespacedKey dataKey;
    private final NamespacedKey professionKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey lockedKey;

    public VillagerItem(VillagerPickupPlugin plugin) {
        dataKey = new NamespacedKey(plugin, "villager_data");
        professionKey = new NamespacedKey(plugin, "profession");
        levelKey = new NamespacedKey(plugin, "level");
        xpKey = new NamespacedKey(plugin, "xp");
        lockedKey = new NamespacedKey(plugin, "locked");
    }

    // ------------------------------------------------------------------ villager -> item

    public ItemStack create(Villager villager, EntitySnapshot snapshot) {
        String professionId = villager.getProfession().getKey().toString();
        int level = villager.getVillagerLevel();
        int xp = villager.getVillagerExperience();
        boolean locked = isLockedIn(villager);

        ItemStack item = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();

        String professionName = pretty(villager.getProfession().getKey().getKey());
        boolean hasJob = hasJob(villager.getProfession());
        String title = (villager.isAdult() ? "" : "Baby ")
                + (hasJob ? professionName + " Villager" : "Villager");
        meta.displayName(line(title, NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        Component customName = villager.customName();
        if (customName != null) {
            lore.add(Component.text("Name: ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(customName));
        }
        lore.add(line("Profession: " + (hasJob ? professionName : pretty(villager.getProfession().getKey().getKey())),
                NamedTextColor.GRAY));
        if (hasJob) {
            int idx = Math.max(1, Math.min(level, LEVEL_NAMES.length)) - 1;
            lore.add(line("Level " + level + " (" + LEVEL_NAMES[idx] + ")", NamedTextColor.GRAY));
            lore.add(line(locked ? "Job locked in" : "Not traded with yet",
                    locked ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        }
        lore.add(line("Biome: " + pretty(villager.getVillagerType().getKey().getKey()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("Right-click a block to place", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        meta.setMaxStackSize(1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(dataKey, PersistentDataType.STRING, snapshot.getAsString());
        pdc.set(professionKey, PersistentDataType.STRING, professionId);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(xpKey, PersistentDataType.INTEGER, xp);
        pdc.set(lockedKey, PersistentDataType.BOOLEAN, locked);

        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ item -> data

    /** Returns the stored villager data, or null if the item isn't one of ours. */
    public Stored read(ItemStack item) {
        if (item == null || item.getType() != Material.VILLAGER_SPAWN_EGG || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String snbt = pdc.get(dataKey, PersistentDataType.STRING);
        if (snbt == null) return null;

        String profession = pdc.get(professionKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        Integer xp = pdc.get(xpKey, PersistentDataType.INTEGER);
        Boolean locked = pdc.get(lockedKey, PersistentDataType.BOOLEAN);

        return new Stored(
                snbt,
                profession,
                level == null ? 1 : level,
                xp == null ? 0 : xp,
                locked != null && locked);
    }

    // ------------------------------------------------------------------ helpers

    public static boolean hasJob(Villager.Profession profession) {
        return profession != Villager.Profession.NONE && profession != Villager.Profession.NITWIT;
    }

    /**
     * A villager is "locked in" once it has been traded with: it has trade XP, has levelled up,
     * or one of its trades has been used. In vanilla such a villager keeps its profession for good.
     */
    public static boolean isLockedIn(Villager villager) {
        if (!hasJob(villager.getProfession())) return false;
        if (villager.getVillagerExperience() > 0 || villager.getVillagerLevel() > 1) return true;
        return villager.getRecipes().stream().anyMatch(recipe -> recipe.getUses() > 0);
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
