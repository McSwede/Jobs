package com.gamingmesh.jobs.commands.list;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.commands.Cmd;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.i18n.Language;

import net.Zrips.CMILib.Colors.CMIChatColor;
import net.Zrips.CMILib.Container.PageInfo;
import net.Zrips.CMILib.Messages.CMIMessages;
import net.Zrips.CMILib.RawMessages.RawMessage;

public class browse implements Cmd {

    @Override
    public Boolean perform(Jobs plugin, CommandSender sender, final String[] args) {
        boolean senderIsPlayer = sender instanceof Player;

        if (Jobs.getGCManager().BrowseUseNewLook) {
            if (Jobs.getJobs().isEmpty()) {
                Language.sendMessage(sender, "command.browse.error.nojobs");
                return true;
            }

            List<Job> jobList = new ArrayList<>(Jobs.getJobs());
            if (senderIsPlayer && Jobs.getGCManager().JobsGUIOpenOnBrowse) {
                try {
                    plugin.getGUIManager().openJobsBrowseGUI((Player) sender);
                } catch (Throwable e) {
                    ((Player) sender).closeInventory();
                }

                return true;
            }

            int page = 1;
            if (senderIsPlayer) {
                for (String one : args) {
                    if (one.startsWith("-p:")) {
                        try {
                            page = Integer.parseInt(one.substring("-p:".length()));
                        } catch (Exception e) {
                        }
                    }
                }
            }

            Job j = null;
            for (String one : args) {
                if (one.startsWith("-j:")) {
                    j = Jobs.getJob(one.substring("-j:".length()));
                    continue;
                }
            }

            if (senderIsPlayer) {
                if (j == null) {
                    PageInfo pi = new PageInfo(Jobs.getGCManager().getBrowseAmountToShow(), jobList.size(), page);
                    Language.sendMessage(sender, "command.browse.output.newHeader", "[amount]", jobList.size());
                    for (Job one : jobList) {
                        if (!pi.isEntryOk())
                            continue;
                        if (pi.isBreak())
                            break;

                        RawMessage rm = new RawMessage();

                        String hoverMsg = "";

                        if (!one.getDescription().isEmpty())
                            hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.description", "[description]", one.getDescription().replaceAll("/n|\n", ""));
                        else {
                            for (String desc : one.getFullDescription()) {
                                hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.description", "[description]", desc);
                            }
                        }

                        int maxLevel = one.getMaxLevel(sender);
                        if (maxLevel > 0) {
                            if (!hoverMsg.isEmpty())
                                hoverMsg += " \n";
                            hoverMsg += Jobs.getLanguage().getMessage("command.info.help.newMax", "[max]", maxLevel);
                        }

                        if (Jobs.getGCManager().ShowTotalWorkers) {
                            if (!hoverMsg.isEmpty())
                                hoverMsg += " \n";
                            hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.totalWorkers", "[amount]", one.getTotalPlayers());

                        }

                        if (Jobs.getGCManager().useDynamicPayment && Jobs.getGCManager().ShowPenaltyBonus) {
                            if (!hoverMsg.isEmpty())
                                hoverMsg += " \n";

                            int bonus = (int) (one.getBonus() * 100);
                            if (bonus < 0)
                                hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.penalty", "[amount]", bonus * -1);
                            else
                                hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.bonus", "[amount]", bonus);
                        }

                        if (!hoverMsg.isEmpty())
                            hoverMsg += " \n";
                        hoverMsg += Jobs.getLanguage().getMessage("command.browse.output.click");

                        rm.addText(Jobs.getLanguage().getMessage("command.browse.output.list", "[place]", pi.getPositionForOutput(),
                            "[jobname]", one.getName())).addHover(hoverMsg).addCommand("jobs browse -j:" + one.getName());

                        rm.show(sender);
                    }
                    pi.autoPagination(sender, "jobs browse", "-p:");
                } else {

                    Language.sendMessage(sender, "command.browse.output.jobHeader", "[jobname]", j.getName());

                    int maxLevel = j.getMaxLevel(sender);
                    if (maxLevel > 0)
                        Language.sendMessage(sender, "command.info.help.newMax", "[max]", maxLevel);

                    if (Jobs.getGCManager().ShowTotalWorkers)
                        Language.sendMessage(sender, "command.browse.output.totalWorkers", "[amount]", j.getTotalPlayers());

                    if (Jobs.getGCManager().useDynamicPayment && Jobs.getGCManager().ShowPenaltyBonus) {
                        int bonus = (int) (j.getBonus() * 100);
                        if (bonus < 0)
                            Language.sendMessage(sender, "command.browse.output.penalty", "[amount]", bonus * -1);
                        else
                            Language.sendMessage(sender, "command.browse.output.bonus", "[amount]", bonus);
                    }

                    for (String one : j.getFullDescription()) {
                        Language.sendMessage(sender, "command.browse.output.description", "[description]", one);
                    }

                    RawMessage rm = new RawMessage();
                    rm.addText(Jobs.getLanguage().getMessage("command.browse.output.detailed"))
                        .addHover(Jobs.getLanguage().getMessage("command.browse.output.detailed")).addCommand("jobs info " + j.getName());
                    rm.show(sender);
                    rm.clear();
                    rm.addText(Jobs.getLanguage().getMessage("command.browse.output.chooseJob"))
                        .addHover(Jobs.getLanguage().getMessage("command.browse.output.chooseJobHover"))
                        .addCommand("jobs join " + j.getName() + " -needConfirmation").show(sender);
                }
            } else {
                if (j == null) {
                    Language.sendMessage(sender, "command.browse.output.console.newHeader", "[amount]", jobList.size(), "\\n", "\n");
                    for (Job one : jobList) {
                        String msg = "";

                        if (!one.getDescription().isEmpty())
                            msg += Jobs.getLanguage().getMessage("command.browse.output.console.description", "[description]", one.getDescription().replaceAll("/n|\n", ""));
                        else {
                            for (String desc : one.getFullDescription()) {
                                msg += Jobs.getLanguage().getMessage("command.browse.output.console.description", "[description]", desc);
                            }
                        }

                        int maxLevel = one.getMaxLevel(sender);
                        if (maxLevel > 0)
                            msg += Jobs.getLanguage().getMessage("command.browse.output.console.newMax", "[max]", maxLevel);

                        if (Jobs.getGCManager().ShowTotalWorkers)
                            msg += Jobs.getLanguage().getMessage("command.browse.output.console.totalWorkers", "[amount]", one.getTotalPlayers());

                        if (Jobs.getGCManager().useDynamicPayment && Jobs.getGCManager().ShowPenaltyBonus) {
                            int bonus = (int) (one.getBonus() * 100);
                            if (bonus < 0)
                                msg += Jobs.getLanguage().getMessage("command.browse.output.console.penalty", "[amount]", bonus * -1);
                            else
                                msg += Jobs.getLanguage().getMessage("command.browse.output.console.bonus", "[amount]", bonus);
                        }

                        msg += Jobs.getLanguage().getMessage("command.browse.output.console.list", "[jobname]", one.getName());

                        CMIMessages.sendMessage(sender, msg);
                    }
                } else {
                    Language.sendMessage(sender, "command.browse.output.jobHeader", "[jobname]", j.getName());

                    int maxLevel = j.getMaxLevel(sender);
                    if (maxLevel > 0)
                        Language.sendMessage(sender, "command.info.help.newMax", "[max]", maxLevel);

                    if (Jobs.getGCManager().ShowTotalWorkers)
                        sender.sendMessage(Jobs.getLanguage().getMessage("command.browse.output.totalWorkers", "[amount]", j.getTotalPlayers()));

                    if (Jobs.getGCManager().useDynamicPayment && Jobs.getGCManager().ShowPenaltyBonus) {
                        int bonus = (int) (j.getBonus() * 100);
                        if (bonus < 0)
                            Language.sendMessage(sender, "command.browse.output.penalty", "[amount]", bonus * -1);
                        else
                            Language.sendMessage(sender, "command.browse.output.bonus", "[amount]", bonus);
                    }

                    for (String one : j.getFullDescription()) {
                        Language.sendMessage(sender, "command.browse.output.description", "[description]", one);
                    }
                }
            }
        } else {
            List<String> lines = new ArrayList<>();
            for (Job job : Jobs.getJobs()) {
                if (Jobs.getGCManager().getHideJobsWithoutPermission()) {
                    if (!Jobs.getCommandManager().hasJobPermission(sender, job))
                        continue;
                }

                StringBuilder builder = new StringBuilder();
                builder.append("  ");
                builder.append(job.getChatColor().toString());
                builder.append(job.getName());

                int maxLevel = job.getMaxLevel(sender);
                if (maxLevel > 0) {
                    builder.append(CMIChatColor.WHITE.toString());
                    builder.append(Jobs.getLanguage().getMessage("command.info.help.max"));
                    builder.append(maxLevel);
                }

                if (Jobs.getGCManager().ShowTotalWorkers)
                    builder.append(Jobs.getLanguage().getMessage("command.browse.output.totalWorkers", "[amount]", job.getTotalPlayers()));

                if (Jobs.getGCManager().useDynamicPayment && Jobs.getGCManager().ShowPenaltyBonus) {
                    int bonus = (int) (job.getBonus() * 100);
                    if (bonus < 0)
                        builder.append(Jobs.getLanguage().getMessage("command.browse.output.penalty", "[amount]", bonus * -1));
                    else
                        builder.append(Jobs.getLanguage().getMessage("command.browse.output.bonus", "[amount]", bonus));
                }

                lines.add(builder.toString());

                if (!job.getDescription().isEmpty())
                    lines.add("  - " + job.getDescription().replaceAll("/n|\n", ""));
                else {
                    for (String desc : job.getFullDescription()) {
                        lines.add("  - " + desc);
                    }
                }
            }

            if (lines.isEmpty()) {
                Language.sendMessage(sender, "command.browse.error.nojobs");
                return true;
            }

            if (senderIsPlayer && Jobs.getGCManager().JobsGUIOpenOnBrowse) {
                try {
                    plugin.getGUIManager().openJobsBrowseGUI((Player) sender);
                } catch (Throwable e) {
                    ((Player) sender).closeInventory();
                }

                return true;
            }

            if (Jobs.getGCManager().JobsGUIShowChatBrowse) {
                Language.sendMessage(sender, "command.browse.output.header");
                lines.forEach(sender::sendMessage);
                Language.sendMessage(sender, "command.browse.output.footer");
            }
        }
        return true;
    }
}
