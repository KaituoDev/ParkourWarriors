package fun.kaituo.parkourwarriors;

import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class ParkourWarriors extends JavaPlugin implements Listener {
    private ItemStack restartItemStack;
    private ItemStack stopItemStack;
    private FileConfiguration config;
    private Scoreboard mainScoreboard;
    private Scoreboard parkourScoreboard;
    private Objective parkourObjective;
    private Team parkourTeam;
    private Team parkourSlimesTeam;
    private HashMap<UUID, Integer> parkourPlayersTasksMap;
    private Location startLocation;
    private Advancement[] pwAdvancements;
    private BoundingBox[] pwAdvancementsCriteriaBoxes;
    private final BoundingBox endBoundingBox = new BoundingBox(-8.0, 88.0, -6.0, -9.0, 91.0, 11.0);
    private final BoundingBox extraBoundingBox1 = new BoundingBox(30.0, -350.0, -30.0, 47.0, 81.0, 18.0);
    private final BoundingBox extraBoundingBox2 = new BoundingBox(-41.0, -350.0, -42.0, -18.0, 81.0, -16.0);
    private final BoundingBox extraBoundingBox3 = new BoundingBox(-52.0, 60.0, -11.0, -9.0, 110.0, 7.0);

    @Override
    public void onEnable() {
        // Plugin startup logic
        mainScoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        parkourScoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        parkourObjective = parkourScoreboard.registerNewObjective("parkourWarriors", "dummy", ChatColor.YELLOW.toString() + ChatColor.BOLD + "  Parkour Warriors  ");
        parkourObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        parkourTeam = parkourScoreboard.registerNewTeam("parkourTeam");
        parkourTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        parkourSlimesTeam = parkourScoreboard.registerNewTeam("parkourSlimes");
        parkourSlimesTeam.setColor(ChatColor.GREEN);
        startLocation = new Location(Bukkit.getWorld("world"), -33.5, 89.0, 30.5, 45, 20);
        restartItemStack = new ItemStack(Material.NETHER_STAR);
        ItemMeta tempMeta = restartItemStack.getItemMeta();
        assert tempMeta != null;
        tempMeta.setDisplayName(ChatColor.YELLOW + "重新开始");
        restartItemStack.setItemMeta(tempMeta);
        stopItemStack = new ItemStack(Material.FIREWORK_STAR);
        tempMeta = stopItemStack.getItemMeta();
        assert tempMeta != null;
        tempMeta.setDisplayName(ChatColor.RED + "停止计时");
        stopItemStack.setItemMeta(tempMeta);
        Bukkit.getPluginManager().registerEvents(this, this);
        parkourPlayersTasksMap = new HashMap<>();
        config = this.getConfig();
        for (String s : config.getKeys(false)) {
            parkourObjective.getScore(s).setScore(config.getInt(s));
        }
        PluginCommand pwCommand = Bukkit.getPluginCommand("parkourwarriors");
        assert pwCommand != null;
        pwCommand.setExecutor(this);
        pwCommand.setTabCompleter(this);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (String entry : Objects.requireNonNull(mainScoreboard.getTeam("parkourSlime")).getEntries()) {
                if (parkourSlimesTeam.hasEntry(entry)) {
                    continue;
                }
                parkourSlimesTeam.addEntry(entry);
            }
        }, 0, 6000);
        NamespacedKey[] pwAdvancementKeys = {
                new NamespacedKey(this, "root"),                    // 0
                new NamespacedKey(this, "lv1_part1"),               // 1
                new NamespacedKey(this, "lv1_part2"),               // 2
                new NamespacedKey(this, "lv1_part3"),               // 3
                new NamespacedKey(this, "lv2_start"),               // 4
                new NamespacedKey(this, "lv2_part1"),               // 5
                new NamespacedKey(this, "lv2_part2"),               // 6
                new NamespacedKey(this, "lv2_part3"),               // 7
                new NamespacedKey(this, "lv2_part4"),               // 8
                new NamespacedKey(this, "lv2_part5"),               // 9
                new NamespacedKey(this, "lv3_start"),               // 10
                new NamespacedKey(this, "end"),                     // 11
                new NamespacedKey(this, "going_back"),              // 12
                new NamespacedKey(this, "lv1_sp1_honey"),           // 13
                new NamespacedKey(this, "lv1_sp2_double_jump"),     // 14
                new NamespacedKey(this, "lv2_sp1_quad_jump"),       // 15
                new NamespacedKey(this, "lv2_sp2_honey_again"),     // 16
                new NamespacedKey(this, "lv3_sp_double_neo")        // 17
        };
        pwAdvancements = new Advancement[pwAdvancementKeys.length];
        for (int i = 0; i < pwAdvancementKeys.length; i++) {
            pwAdvancements[i] = Bukkit.getAdvancement(pwAdvancementKeys[i]);
        }
        pwAdvancementsCriteriaBoxes = new BoundingBox[]{
                null,                                                               // root
                new BoundingBox(5.0, 75.0, 62.5, 7.0, 77.0, 64.5),                  // l1p1
                new BoundingBox(10.0, 79.0, 67.0, 11.0, 81.0, 71.0),                // l1p2
                new BoundingBox(38.0, 71.0, 7.0, 41.0, 77.0, 10.0),                 // l1p3
                new BoundingBox(39.0, 73.0, -48.0, 46.0, 78.0, -46.0),              // l2s
                new BoundingBox(16.0, 65.0, -75.0, 18.375, 68.0, -67.625),          // l2p1
                new BoundingBox(-20.0, 58.25, -90.0, -18.0, 61.0, -86.0),           // l2p2
                new BoundingBox(-20.0, 32.0, -76.0, -18.0, 35.0, -71.0),            // l2p3
                new BoundingBox(-24.0, 57.0, -67.0, -200, 60.0, -65.0),             // l2p4
                new BoundingBox(-45.0, 60.0, -48.0, -42.0, 62.0, -45.0),            // l2p5
                new BoundingBox(-38.0, 70.0, -25.0, -35.0, 72.0, -22.0),            // l3s
                null,                                                               // end
                null,                                                               // going_back
                new BoundingBox(8.0, 75.0, 57.0, 9.0, 77.5, 58.0),                  // l1sp1
                new BoundingBox(33.0, 59.0, 45.0, 36.0, 57.0, 49.0),                // l1sp2
                new BoundingBox(-15.0, 55.0, -80.0, -14.0, 58.0, -79.0),            // l2sp1
                new BoundingBox(-29.0, 68.0, -83.0, -27.0, 71.0, -82.0),            // l2sp2
                new BoundingBox(-50.0, 72.0, -31.0, -48.0, 74.0, -30.0)             // l3sp
        };
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        eventCheckLocation(event);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        eventCheckLocation(event);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction().equals(Action.PHYSICAL) && Objects.requireNonNull(event.getClickedBlock()).getLocation().equals(startLocation.getBlock().getLocation())) {
            p.getAdvancementProgress(pwAdvancements[0]).awardCriteria("impossible");
            if (!parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                sendNotification(event.getPlayer(), "使用手中的下界之星或/pw restart来重新开始");
                sendNotification(event.getPlayer(), "使用物品栏中的烟火之星或/pw stop来结束计时");
                p.getInventory().setItem(3, restartItemStack);
                p.getInventory().setItem(5, stopItemStack);
            }
            parkourObjective.getScore(p.getName()).setScore(0);
            if (parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
            }
            parkourPlayersTasksMap.put(p.getUniqueId(), Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> playersObjectiveAddOne(parkourObjective, p), 20, 20));
        } else if (event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if (p.getInventory().getItemInMainHand().equals(restartItemStack)) {
                Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                p.teleport(startLocation);
                parkourObjective.getScore(p.getName()).setScore(0);
                parkourPlayersTasksMap.put(p.getUniqueId(), Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> playersObjectiveAddOne(parkourObjective, p), 20, 20));
            } else if (p.getInventory().getItemInMainHand().equals(stopItemStack)) {
                Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                parkourPlayersTasksMap.remove(p.getUniqueId());
                if (!endBoundingBox.contains(p.getLocation().toVector())) {
                    parkourScoreboard.resetScores(p.getName());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
            Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
            parkourScoreboard.resetScores(p.getName());
            parkourPlayersTasksMap.remove(p.getUniqueId());
        }
        if (parkourTeam.hasEntry(p.getName())) {
            parkourTeam.removeEntry(p.getName());
            p.setScoreboard(mainScoreboard);
        }
    }
    
    private void playersObjectiveAddOne(Objective obj, Player p) {
        Score score = obj.getScore(p.getName());
        int value = score.getScore();
        value++;
        score.setScore(value);
    }
    
    private void eventCheckLocation(PlayerEvent event) {
        Player p = event.getPlayer();
        Location location = p.getLocation();
        double x = location.getX();
        double z = location.getZ();
        checkAdvancement(p);
        if (endBoundingBox.contains(location.toVector())) {
            if (parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                p.getAdvancementProgress(pwAdvancements[11]).awardCriteria("impossible");
                Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                parkourPlayersTasksMap.remove(p.getUniqueId());
            }
            p.getInventory().remove(restartItemStack);
            p.getInventory().remove(stopItemStack);
        } else if ((x * x + z * z > 1836.58 && x * x + z * z < 11946.49) ||
                extraBoundingBox1.contains(location.toVector()) ||
                extraBoundingBox2.contains(location.toVector()) ||
                extraBoundingBox3.contains(location.toVector())) {
            if (!parkourTeam.hasEntry(p.getName())) {
                parkourTeam.addEntry(p.getName());
                p.setScoreboard(parkourScoreboard);
            }
        } else {
            if (parkourTeam.hasEntry(p.getName())) {
                parkourTeam.removeEntry(p.getName());
                p.setScoreboard(mainScoreboard);
            }
            if (parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                parkourScoreboard.resetScores(p.getName());
                parkourPlayersTasksMap.remove(p.getUniqueId());
            }
            p.getInventory().remove(restartItemStack);
            p.getInventory().remove(stopItemStack);
        }
    }

    private void checkAdvancement(Player p) {
        Location location = p.getLocation();
        for (int i = 0; i < pwAdvancementsCriteriaBoxes.length; i++) {
            BoundingBox box = pwAdvancementsCriteriaBoxes[i];
            AdvancementProgress ap = p.getAdvancementProgress(pwAdvancements[i]);
            if (box == null) continue;
            if (box.contains(location.toVector())) ap.awardCriteria("impossible");
        }
    }
    
    private void sendError(CommandSender to, String message) {
        to.sendMessage(ChatColor.AQUA + "[ParkourWarriors] " + ChatColor.RED + message);
    }
    
    private void sendNotification(CommandSender to, String message) {
        to.sendMessage(ChatColor.AQUA + "[ParkourWarriors] " + ChatColor.YELLOW + message);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("parkourwarriors")) {            // command start
            
            if (args.length == 0) {                                                 // no args, show usage (GUARD)
                sendError(sender, "No arguments provided");
                if (sender.hasPermission("parkourwarriors.op")) {                       // op usage
                    sendNotification(sender, "\nUsage: \n/" + label + " [stop|restart] [playername]" + "\n/" + label + " leaderboard <playername> [reset|set <number>]");
                } else {                                                                // non-op usage
                    sendNotification(sender, "\nUsage: /" + label + " [stop|restart]");
                }
                return true;                                                        // exit
            }
            
            Player p = null;                                                        // target player
            switch (args[0]) {                                                      // subcommands
                case "leaderboard":                                                     // sc: leaderboard
                    
                    if (!sender.hasPermission("parkourwarriors.op")) {                      // no op, deny request (GUARD)
                        sendError(sender, "You have no permission to operate the parkour leaderboard!");
                        return true;                                                            // exit
                    }
                    if (args.length < 3) {                                                  // not enough args, show usage (GUARD)
                        sendError(sender, "Bad command");
                        sendNotification(sender, "\nUsage: /" + label + " leaderboard <playername> [reset|set <number>]");
                        return true;                                                            // exit
                    }
                    
                    String entry = args[1];                                                 // target scoreboard entry
                    switch (args[2]) {                                                      // sub-subcommands
                        case "reset":                                                           // s-sc: reset
                            if (args.length != 3) {                                                 // bad argc, show usage (GUARD)
                                sendError(sender, "Bad command");
                                sendNotification(sender, "\nUsage: /" + label + " leaderboard <playername> reset");
                                return true;                                                            // exit
                            }
                            
                            parkourScoreboard.resetScores(entry);                                   // reset score
                            
                            break;                                                                  // next sub-subcommand
                        case "set":                                                             // s-sc: set
                            
                            if (args.length != 4) {                                                 // bad argc, show usage (GUARD)
                                sendError(sender, "Bad command");
                                sendNotification(sender, "\nUsage: /" + label + " leaderboard <playername> set <number>");
                                return true;                                                            // exit
                            }
                            int value;                                                              // after value
                            try {
                                value = Integer.parseInt(args[3]);                                      // try parsing value
                            } catch (
                                    NumberFormatException e) {                                     // bad string (EXCEPTION-HANDLING)
                                sendError(sender, "You didn't provide a valid integer!");
                                return true;                                                            // exit
                            }
                            
                            parkourObjective.getScore(entry).setScore(value);                       // set score
                            
                            break;
                        default:                                                                // no sub-subcommand match, show usage
                            sendError(sender, "Bad command");
                            sendNotification(sender, "\nUsage: /" + label + " leaderboard <playername> [reset|set <number>]");
                    }
                    break;                                                                  // next subcommand
                case "stop":                                                            // sc: stop
                    switch (args.length) {
                        case 1:
                            
                            if (!(sender instanceof Player)) {
                                sendError(sender, "This command must be performed by a player!");
                                return true;
                            }
                            
                            p = (Player)sender;
                            
                            if (!parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                                sendError(sender, "Your timer has ended!");
                                return true;
                            }
                            
                            break;
                        case 2:
                            
                            if (!sender.hasPermission("parkourwarriors.op")) {
                                sendError(sender, "You have no permission to operate other player's parkour course!");
                                return true;
                            }
                            
                            p = Bukkit.getPlayer(args[1]);
                            
                            if (p == null) {
                                sendError(sender, "The player " + args[1] + " is not found!");
                                return true;
                            }
                            if (!parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                                sendError(sender, "The timer for " + p.getName() + " has ended!");
                                return true;
                            }
                            
                            break;
                        default:
                            sendError(sender, "Bad command");
                            if (sender.hasPermission("parkourwarriors.op")) {
                                sendNotification(sender, "Usage: /" + label + " stop [playername]");
                            } else {
                                sendNotification(sender, "Usage: /" + label + " stop");
                            }
                            return true;
                    }
                    
                    Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                    parkourPlayersTasksMap.remove(p.getUniqueId());
                    if (!endBoundingBox.contains(p.getLocation().toVector())) {
                        parkourScoreboard.resetScores(p.getName());
                    }
                    
                    break;
                case "restart":                                                         // sc: restart
                    // same as the above
                    switch (args.length) {
                        case 1:
                            
                            if (!(sender instanceof Player)) {
                                sendError(sender, "This command must be performed by a player!");
                                return true;
                            }
                            
                            p = (Player)sender;
                            
                            if (!parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                                sendError(sender, "Your timer has ended!");
                                return true;
                            }
                            
                            break;
                        case 2:
                            
                            if (!sender.hasPermission("parkourwarriors.op")) {
                                sendError(sender, "You have no permission to operate other player's parkour course!");
                                return true;
                            }
                            
                            p = Bukkit.getPlayer(args[1]);
                            
                            if (p == null) {
                                sendError(sender, "The player " + args[1] + " is not found!");
                                return true;
                            }
                            if (!parkourPlayersTasksMap.containsKey(p.getUniqueId())) {
                                sendError(sender, "The timer for " + p.getName() + " has ended!");
                                return true;
                            }
                            
                            break;
                        default:
                            sendError(sender, "Bad command");
                            if (sender.hasPermission("parkourwarriors.op")) {
                                sendNotification(sender, "Usage: /" + label + " restart [playername]");
                            } else {
                                sendNotification(sender, "Usage: /" + label + " restart");
                            }
                    }
                    
                    assert p != null;
                    Bukkit.getScheduler().cancelTask(parkourPlayersTasksMap.get(p.getUniqueId()));
                    p.teleport(startLocation);
                    parkourObjective.getScore(p.getName()).setScore(0);                     // set score to 0
                    Player finalP1 = p;
                    parkourPlayersTasksMap.put(p.getUniqueId(), Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> playersObjectiveAddOne(parkourObjective, finalP1), 20, 20));
                    
                    break;
                default:                                                                // no subcommand match, show usage
                    sendError(sender, "Bad command");
                    if (sender.hasPermission("parkourwarriors.op")) {                       // op usage
                        sendNotification(sender, "\nUsage:\n/" + label + " [stop|restart] [playername]" + "\n/" + label + " leaderboard <playername> [reset|set <number>]");
                    } else {                                                                // non-op usage
                        sendNotification(sender, "\nUsage: /" + label + " [stop|restart]");
                    }
            }
            return true;    // exit
        }
        return false;   // no command match, exit
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("parkourwarriors")) {            // command start
            switch (args.length) {
                case 1:
                    List<String> subcommands = new ArrayList<>();
                    subcommands.add("stop");
                    subcommands.add("restart");
                    if (sender.hasPermission("parkourwarriors.op")) {
                        subcommands.add("leaderboard");
                    }
                    return subcommands;
                case 2:
                    if (!sender.hasPermission("parkourwarriors.op")) {
                        return new ArrayList<>();
                    }
                    Set<String> players = Bukkit.getOnlinePlayers().stream().map(HumanEntity::getName).collect(Collectors.toSet());
                    players.addAll(parkourScoreboard.getEntries());
                    return new ArrayList<>(players);
                case 3:
                    if (!args[0].equals("leaderboard") || !sender.hasPermission("parkourwarriors.op")) {
                        return new ArrayList<>();
                    }
                    List<String> subSubcommands = new ArrayList<>();
                    subSubcommands.add("reset");
                    subSubcommands.add("set");
                    return subSubcommands;
                default:
                    return new ArrayList<>();
            }
        }
        return null;
    }
    
    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (String s : parkourScoreboard.getEntries()) {
            config.set(s, parkourObjective.getScore(s).getScore());
        }
        this.saveConfig();
        Bukkit.getScheduler().cancelTasks(this);
        parkourPlayersTasksMap.clear();
        parkourObjective.unregister();
        parkourTeam.unregister();
        parkourSlimesTeam.unregister();
        parkourScoreboard = null;
        HandlerList.unregisterAll((Plugin)this);
    }
}
