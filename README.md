# VillagerPickup (Paper 26.2, Java 25)

Shift + right-click a villager       Pick it up (it becomes a villager spawn egg holding all of its data).
Right-click a block with that egg    Place the villager back down.

What is kept: profession, level, trade XP, all trades and their use counts/discounts, biome type,
custom name, age, health, inventory, etc.

Job locking: a villager that has been traded with (trade XP, level 2+, or any used trade) is "locked in".
On placing, the plugin re-checks profession, level and XP and restores them if anything is off.
Villagers with a job that were never traded with get a sliver of trade XP on placing so they also keep
their job without a workstation (config: lock-unlocked-jobs).

Permission: villagerpickup.use (default: everyone)
Name tags and leads in your main hand are left alone, so you can still name/leash villagers.

Build (Windows): install JDK 25 and Gradle, then in this folder run:
    gradle build
Jar: build\libs\VillagerPickup-1.0.0.jar -> drop into the server's plugins folder and restart.
