package ch.geeq;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * @author weby@we-bb.com [Nicolas Glassey]
 * @version 1.0.0
 * @since 3/25/18
 */
public class Cut extends JavaPlugin implements Listener {
    private static Plugin plugin;
    private ProtocolManager pmgr;
  
    private HashMap<Material, Integer> ticks = new HashMap<>();
    private HashMap<Material, List<Integer>> dataValues = new HashMap<>();
    private HashMap<Block, Integer> hits = new HashMap<>();
    private HashMap<Block, Integer> digging = new HashMap<>();
    private Set<Block> toRemove = new HashSet<>();
    private HashMap<Block, Integer> toAdd = new HashMap<>();
    
    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        reloadConfiguration();
        pmgr = ProtocolLibrary.getProtocolManager();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        init();
        launch();
    }
    
    private boolean isValid(Block b)
    {
        return isValid(b.getType(), b.getData());
    }
    
    private boolean isValid(Material m, byte dataVal)
    {
        if(!ticks.containsKey(m)) return false;
        if(!dataValues.containsKey(m)) return true;
        
        List<Integer> data = dataValues.get(m);
        int i = (int) dataVal;
        return data.contains(i);
    }
    
    private void reloadConfiguration()
    {
        int count = 0;
        int cd = 0;
        ticks.clear();
        File f = new File(getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection breakTimes = config.getConfigurationSection("breaktime");
        ConfigurationSection dataValue = config.getConfigurationSection("datavalues");
        Set<String> materials = breakTimes.getKeys(false);
        Bukkit.getLogger().info("[FastBreak] ===== Modified break times for the following blocks =====");
        for (String mat : materials) {
            ++count;
            Material m = Material.valueOf(mat);
            int t = breakTimes.getInt(mat);
            ticks.put(m, t);
            Bukkit.getLogger().info("[FastBreak] " + m.name() + " : " + t + " ticks");
        }
        Bukkit.getLogger().info("[FastBreak] ===== Break times finished. Checking data values =====");
        if(dataValue!=null) {
            Set<String> dv = dataValue.getKeys(false);
            for (String data : dv) {
                ++cd;
                List<Integer> dataval = dataValue.getIntegerList(data);
                Bukkit.getLogger().info("[FastBreak] Data value(s) for material " + dv + " : " + dataval);
                Material m = Material.valueOf(data);
                dataValues.put(m, dataval);
            }
        }
        Bukkit.getLogger().info("[FastBreak] ===== Count : " + count + " modified break times, "+cd+" materials with only a certain data value to check =====");
    
    }
    
    @Override
    public void onDisable() {
        
        plugin = null;
        ticks.clear();
    }
    
    private void reload() {
        //Clear all blocks
        hits.clear();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender == Bukkit.getConsoleSender() || sender.hasPermission("woodcut.reload")) {
            if (cmd.getName().toLowerCase().equals("wcsm") || cmd.getLabel().equals("wcsm")) {
                reload();
            }
            if (cmd.getName().toLowerCase().equals("wcre") || cmd.getName().toLowerCase().equals("fastbreakreload") ||  cmd.getLabel().equals("wcre") || cmd.getLabel().equals("fastbreakreload")) {
                reloadConfiguration();
                sender.sendMessage(ChatColor.ITALIC + "[FastBreak] " + ChatColor.GREEN + "" + ChatColor.BOLD + "Reloaded item breaking config");
            }
        }
        return true;
    }
    
    private void startDigging(Player p, BlockPosition blockPos)
    {
        Future<Block> futureBlock = Bukkit.getScheduler().callSyncMethod(plugin, new GetBlock(blockPos, p.getWorld()));
        try {
            Block b = futureBlock.get();
            if(isValid(b)) {
                int v = 0;
                if(digging.containsKey(b))
                {
                    v = digging.get(b);
                }
                toAdd.put(b, v+1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void stopDiggingSync(Block b)
    {
        if(digging.containsKey(b))
        {
            toRemove.add(b);
            hits.put(b, 0);
            fakeBreak(b, 10);
        }
    }
    private void stopDigging(Player p, BlockPosition blockPos)
    {
        Future<Block> futureBlock = Bukkit.getScheduler().callSyncMethod(this, new GetBlock(blockPos, p.getWorld()));
        try {
            Block b = futureBlock.get();
            stopDiggingSync(b);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    private PacketAdapter blockBreakAnim;
    private PacketAdapter blockDig;
    private BukkitRunnable runnable;
  
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
//        Bukkit.broadcastMessage("OnBlockBreak");
        Block b = event.getBlock();
        stopDiggingSync(b);
    }
    
    private void init()
    {
        blockDig = new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                EnumWrappers.PlayerDigType type = event.getPacket().getPlayerDigTypes().readSafely(0);
                BlockPosition blockPos = event.getPacket().getBlockPositionModifier().readSafely(0);
                Player p = event.getPlayer();
                switch(type){
                    case START_DESTROY_BLOCK:
                        startDigging(p, blockPos);
                        break;
                    default: {
//                        Bukkit.broadcastMessage("Type : "+type.toString());
                        stopDigging(p, blockPos);
                        break;
                    }
                }
            }
        };
        
        
        
        
        blockBreakAnim = new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
            @Override
            public void onPacketSending(PacketEvent event) {
//                BlockPosition bp = event.getPacket().getBlockPositionModifier().read(0);
//                Player p = event.getPlayer();
//                Block b = p.getWorld().getBlockAt(bp.getX(), bp.getY(), bp.getZ());
/*                if (placedBlocks.contains(b)) {
        
                    int tic = ticks.containsKey(b.getType()) ? ticks.get(b.getType()) : defaultBreakTime;
                    float h = (float) 10 * (hits.containsKey(b) ? (float) hits.get(b) : (float) defaultBreakTime) / (float) tic;
                    int HIT = (int) h;
                    if (hits.get(b) != null && hits.get(b) > 0)
                        event.getPacket().getIntegers().write(1, HIT);
//                                event.getPacket().getIntegers().write(1, (int)(10*(b.getType()==Material.LADDER?((float) hits.get(b) / (float) ladder_ticks):((float) hits.get(b) / (float) planks_ticks))));
        
                    if (hits.get(b) == 0) {
                        event.getPacket().getIntegers().write(0, Integer.MAX_VALUE);
                        event.getPacket().getIntegers().write(1, 10);
                    }
                }
                */
            }
        };
        
        
        
        runnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, Integer> entry : toAdd.entrySet()) {
                    digging.put(entry.getKey(), entry.getValue());
                }
                
                toAdd.clear();
                
                for(Iterator<Map.Entry<Block, Integer>> it = digging.entrySet().iterator(); it.hasNext() ;)
                {
                    Map.Entry<Block, Integer> entry = it.next();
                    Block b = entry.getKey();
                    int amount = entry.getValue();
                    if(toRemove.contains(b)) {
                        it.remove();
                        fakeBreak(b, 10);
                        toRemove.remove(b);
                        continue;
                    }
                    Integer MAX_HITS = ticks.get(b.getType());
                    if(MAX_HITS==null || MAX_HITS==0 || amount==0) {
                        it.remove();
                        fakeBreak(b, 10);
                        continue;
                    }
                    
                    hits.putIfAbsent(b, 0);
                    Integer HITS = hits.get(b);
    
                    if(HITS >= MAX_HITS) {
                        breakBlock(b);
                    }
                    else
                    {
                        int damageValue = (int) ((float) 10 * (float) HITS / (float) MAX_HITS);
//                        Bukkit.broadcastMessage("Particle, damageValue : "+damageValue);
                        hits.put(b, HITS+1);
                        fakeBreak(b, damageValue);
                    }
                    
                }
            }
        };
    }
    
    private void breakBlock(Block b)
    {
//        Bukkit.broadcastMessage("Remove block");
        fakeParticle(b);
        fakeBreak(b, 10);
        hits.remove(b);
        toRemove.add(b);
        b.breakNaturally();
    }
    
    private PacketContainer fakeBreak = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
    private PacketContainer particles = new PacketContainer(PacketType.Play.Server.WORLD_EVENT);
    private void fakeBreak(Block b, int damageValue)
    {
        fakeBreak.getIntegers().writeSafely(0, Integer.MAX_VALUE);
        fakeBreak.getBlockPositionModifier().writeSafely(0, new BlockPosition(b.getX(), b.getY(), b.getZ()));
        fakeBreak.getIntegers().writeSafely(1, damageValue);
    
        for (Player p : Bukkit.getOnlinePlayers()) {
            fakeBreak.getIntegers().writeSafely(1, damageValue);
            try {
                pmgr.sendServerPacket(p, fakeBreak);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void fakeParticle(Block b)
    {
        particles.getIntegers().writeSafely(0, 2001);
        particles.getBooleans().writeSafely(0, false);
        particles.getBlockPositionModifier().writeSafely(0, new BlockPosition(b.getX(), b.getY(), b.getZ()));
        particles.getIntegers().writeSafely(1, b.getType().getId());
      
        for(Player p : Bukkit.getOnlinePlayers()) {
                try {
                    pmgr.sendServerPacket(p, particles);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
        }
    }
    private void launch()
    {
        pmgr.addPacketListener(blockDig);
        pmgr.addPacketListener(blockBreakAnim);
        runnable.runTaskTimer(this, 0, 1);
    }
        private void stopDigging(Player p)
    {
        stopDigging(p, null);
    }
    
    private void stopDigging(BlockPosition blockPos)
    {
        stopDigging(null, blockPos);
    }
    
    private void stopDigging(Block b)
    {
        stopDigging(b.getLocation());
    }
    
    private void stopDigging(Location l)
    {
        stopDigging(new BlockPosition(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }
    
    /*
    @EventHandler
    public void blockplace(BlockPlaceEvent event)
    {
        Block b = event.getBlockPlaced();
        if(ticks.containsKey(b.getType()))
            placed.add(b);
    }
    */
}
