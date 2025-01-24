package com.gamingmesh.jobs.commands.list;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.commands.Cmd;
import com.gamingmesh.jobs.commands.JobsCommands;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.container.blockOwnerShip.BlockTypes;
import com.gamingmesh.jobs.i18n.Language;

import net.Zrips.CMILib.Locale.LC;
import net.Zrips.CMILib.Messages.CMIMessages;

public class clearownership implements Cmd {

    @Override
    public Boolean perform(Jobs plugin, final CommandSender sender, final String[] args) {
        JobsPlayer jPlayer = null;
        String location = null;

        for (String one : args) {

            if (!one.contains(":") && jPlayer == null) {

                jPlayer = Jobs.getPlayerManager().getJobsPlayer(args[0]);

                if (jPlayer != null) {
                    if (!sender.getName().equalsIgnoreCase(one) && !Jobs.hasPermission(sender, "jobs.command.admin.clearownership", true))
                        return true;
                    continue;
                }
            }

            if (one.contains(":") && location == null) {
                location = one;
            }
        }

        if (jPlayer == null && sender instanceof Player)
            jPlayer = Jobs.getPlayerManager().getJobsPlayer((Player) sender);

        if (jPlayer == null) {
            if (args.length >= 1)
                CMIMessages.sendMessage(sender, LC.info_NoInformation);
            else
                return false;
            return null;
        }

        final UUID uuid = jPlayer.getUniqueId();
        final Map<BlockTypes, Integer> amounts = new WeakHashMap<>();

        String l = location;

        for (BlockTypes type : BlockTypes.values()) {
            if (location == null)
                plugin.getBlockOwnerShip(type).ifPresent(ownerShip -> amounts.put(type, ownerShip.clear(uuid)));
            else {
                plugin.getBlockOwnerShip(type).ifPresent(ownerShip -> amounts.put(type, ownerShip.remove(uuid, l)));
            }
        }

        Language.sendMessage(sender, "command.clearownership.output.cleared", "[furnaces]", amounts.getOrDefault(BlockTypes.FURNACE, 0), "[brewing]", amounts.getOrDefault(
            BlockTypes.BREWING_STAND, 0), "[smoker]", amounts.getOrDefault(BlockTypes.SMOKER, 0), "[blast]", amounts.getOrDefault(BlockTypes.BLAST_FURNACE, 0));

        Bukkit.dispatchCommand(sender, JobsCommands.LABEL + " " + ownedblocks.class.getSimpleName() + (sender.getName().equals(jPlayer.getName()) ? "" : jPlayer.getName()));

        return true;
    }
}
