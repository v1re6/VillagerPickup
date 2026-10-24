package dev.villagerpickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PickupListener implements Listener {

    private final VillagerPickupPlugin plugin;
    private final VillagerItem items;

    public PickupListener(VillagerPickupPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
    }

    // ================================================================== picking up

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        boolean holdingOurs = items.read(player.getInventory().getItem(hand)) != null;

        // A picked-up villager must never act as a vanilla spawn egg (e.g. spawning a baby).
        if (!(event.getRightClicked() instanceof Villager villager)) {
            if (holdingOurs) event.setCancelled(true);
            return;
        }
        if (!player.isSneaking()) {
            if (holdingOurs) event.setCancelled(true);
            return;
        }
        if (!player.hasPermission("villagerpickup.use")) return;

        // Don't hijack name tags and leads.
        Material inMainHand = player.getInventory().getItemInMainHand().getType();
        if (inMainHand == Material.NAME_TAG || inMainHand == Material.LEAD) return;

        event.setCancelled(true); // also stops the trade window from opening
        if (hand != EquipmentSlot.HAND) return;

        pickUp(player, villager);
    }

    private void pickUp(Player player, Villager villager) {
        if (!villager.isValid() || villager.isDead()) return;

        if (villager.isTrading()) {
            bar(player, "Someone is trading with this villager.", NamedTextColor.RED);
            return;
        }
        if (villager.isLeashed()) {
            bar(player, "Remove the lead first.", NamedTextColor.RED);
            return;
        }

        PlayerInventory inv = player.getInventory();
        if (inv.firstEmpty() == -1) {
            bar(player, "Your inventory is full.", NamedTextColor.RED);
            return;
        }

        EntitySnapshot snapshot = villager.createSnapshot();
        if (snapshot == null) {
            bar(player, "This villager can't be picked up.", NamedTextColor.RED);
            return;
        }

        ItemStack item = items.create(villager, snapshot);
        if (!inv.addItem(item).isEmpty()) {
            bar(player, "Your inventory is full.", NamedTextColor.RED);
            return;
        }

        villager.remove();
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        bar(player, "Picked up villager.", NamedTextColor.GREEN);
    }

    // ================================================================== placing

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        VillagerItem.Stored stored = items.read(event.getItem());
        if (stored == null) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        Block clicked = event.getClickedBlock();

        // Clicking a chest, door, etc. without sneaking should still use that block, like a normal egg.
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) return;

        // Our egg must never run the vanilla spawn-egg behaviour.
        boolean alreadyDenied = event.useInteractedBlock() == Event.Result.DENY;
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        if (action != Action.RIGHT_CLICK_BLOCK || clicked == null || alreadyDenied || hand == null) return;

        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!target.isPassable() || !target.getRelative(0, 1, 0).isPassable()) {
            bar(player, "Not enough room to place the villager there.", NamedTextColor.RED);
            return;
        }

        Villager placed = spawn(target, stored);
        if (placed == null) {
            bar(player, "Couldn't place the villager there.", NamedTextColor.RED);
            return;
        }

        // Use up the egg.
        ItemStack inHand = player.getInventory().getItem(hand);
        inHand.setAmount(inHand.getAmount() - 1);
        player.getInventory().setItem(hand, inHand.getAmount() <= 0 ? null : inHand);

        player.playSound(placed.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }

    /** Recreates the villager from its saved data, then double-checks job, level and XP. */
    private Villager spawn(Block target, VillagerItem.Stored stored) {
        Entity entity;
        try {
            EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(stored.snbt());
            entity = snapshot.createEntity(target.getLocation().add(0.5, 0.0, 0.5));
        } catch (IllegalArgumentException | NullPointerException ex) {
            plugin.getLogger().warning("Could not restore a villager from its item: " + ex.getMessage());
            return null;
        }

        if (!(entity instanceof Villager villager)) {
            entity.remove();
            return null;
        }
        if (!villager.isValid()) {
            return null; // another plugin cancelled the spawn
        }

        verify(villager, stored);
        return villager;
    }

    private void verify(Villager villager, VillagerItem.Stored stored) {
        // Profession
        Villager.Profession expected = null;
        NamespacedKey professionKey = NamespacedKey.fromString(String.valueOf(stored.profession()));
        if (professionKey != null) {
            expected = Registry.VILLAGER_PROFESSION.get(professionKey);
        }
        if (expected != null && villager.getProfession() != expected) {
            villager.setProfession(expected);
        }

        // Level
        if (villager.getVillagerLevel() != stored.level()) {
            villager.setVillagerLevel(stored.level());
        }

        // XP. A villager with a job but no trade XP isn't locked in and would lose its job
        // without a workstation, so (optionally) give it a sliver of XP to make it stick.
        int xp = stored.xp();
        boolean hasJob = expected != null && VillagerItem.hasJob(expected);
        if (hasJob && plugin.lockUnlockedJobs() && !stored.locked()) {
            xp = Math.max(xp, 1);
        }
        if (villager.getVillagerExperience() != xp) {
            villager.setVillagerExperience(xp);
        }
    }

    // ================================================================== misc protection

    /** Dispensers would spawn a plain vanilla villager and lose all the data. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (items.read(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    private static void bar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }
}
