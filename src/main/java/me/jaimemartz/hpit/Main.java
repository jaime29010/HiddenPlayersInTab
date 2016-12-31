package me.jaimemartz.hpit;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

public final class Main extends JavaPlugin implements Listener {
    private Map<UUID, String> cursors = new HashMap<>();
    private Set<UUID> quiting = new HashSet<>();
    private ProtocolManager manager;

    @Override
    public void onEnable() {
        manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player receiver = event.getPlayer();
                PacketContainer packet = event.getPacket();
                if (packet.getType() == PacketType.Play.Server.PLAYER_INFO) {
                    //getLogger().info(" ");
                    //getLogger().info("sending to " + receiver.getName());
                    //printFields(packet.getHandle());

                    WrappedGameProfile profile = packet.getGameProfiles().read(0);
                    if (profile == null) return;

                    if (packet.getIntegers().read(0).equals(4)) {
                        if (quiting.contains(profile.getUUID())) {
                            //getLogger().info("packet with profile uuid " + profile.getUUID() + " is ignored");
                            return;
                        }

                        Player other = getServer().getPlayer(profile.getUUID());
                        if (other != null && !receiver.canSee(other)) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        });

        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.TAB_COMPLETE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                String[] array = packet.getStringArrays().read(0);
                Set<String> completions = new HashSet<>();
                completions.addAll(Arrays.asList(array));
                String cursor = cursors.remove(event.getPlayer().getUniqueId());
                String last = cursor.substring(cursor.lastIndexOf(' ') + 1);
                getServer().getOnlinePlayers().forEach(other -> {
                    if (other.getName().startsWith(last)) {
                        completions.add(other.getName());
                    }
                });
                packet.getStringArrays().write(0, completions.toArray(new String[completions.size()]));
            }
        });

        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.TAB_COMPLETE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                String cursor = event.getPacket().getStrings().read(0);
                cursors.put(event.getPlayer().getUniqueId(), cursor);
                //getLogger().info(String.format("cursor value: \"%s\"", cursor));
            }
        });

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        quiting.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        PacketContainer destroyPacket = manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntegerArrays().write(0, new int[] {
                player.getEntityId()
        });

        PacketContainer addInfoPacket = manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        addInfoPacket.getIntegers().write(0, 0);
        addInfoPacket.getStrings().write(0, player.getPlayerListName());
        addInfoPacket.getIntegers().write(1, player.getGameMode().ordinal());
        addInfoPacket.getIntegers().write(2, 10);
        addInfoPacket.getGameProfiles().write(0, WrappedGameProfile.fromPlayer(player));

        PacketContainer removeInfoPacket = manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        removeInfoPacket.getIntegers().write(0, 1);
        removeInfoPacket.getStrings().write(0, player.getPlayerListName());
        removeInfoPacket.getGameProfiles().write(0, WrappedGameProfile.fromPlayer(player));

        quiting.add(player.getUniqueId());
        getServer().getScheduler().runTaskLater(this, () -> {
            getServer().getOnlinePlayers().forEach(other -> {
                try {
                    //manager.sendServerPacket(other, destroyPacket);
                    manager.sendServerPacket(other, addInfoPacket);
                    manager.sendServerPacket(other, removeInfoPacket);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
            getServer().getScheduler().runTaskLater(this, () -> {
                quiting.remove(player.getUniqueId());
            }, 20 * 30);
        }, 20);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testhide")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                getServer().getOnlinePlayers().stream().filter(other -> other != player).forEach(other -> {
                    other.hidePlayer(player);
                    player.hidePlayer(other);
                });
            }
        }
        return true;
    }

    public void printFields(Object object) {
        System.out.println(String.format("Object %s fields:", object));
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
                System.out.println(String.format("field %s, value %s", field.getName(), field.get(object)));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
