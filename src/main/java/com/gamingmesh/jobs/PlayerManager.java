/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gamingmesh.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import com.gamingmesh.jobs.api.JobsJoinEvent;
import com.gamingmesh.jobs.api.JobsLeaveEvent;
import com.gamingmesh.jobs.api.JobsLevelUpEvent;
import com.gamingmesh.jobs.container.ArchivedJobs;
import com.gamingmesh.jobs.container.Boost;
import com.gamingmesh.jobs.container.BoostMultiplier;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobCommands;
import com.gamingmesh.jobs.container.JobItems;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsMobSpawner;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.container.Log;
import com.gamingmesh.jobs.container.PlayerInfo;
import com.gamingmesh.jobs.container.PlayerPoints;
import com.gamingmesh.jobs.dao.JobsDAO;
import com.gamingmesh.jobs.dao.JobsDAOData;
import com.gamingmesh.jobs.economy.PaymentData;
import com.gamingmesh.jobs.hooks.JobsHook;
import com.gamingmesh.jobs.i18n.Language;
import com.gamingmesh.jobs.stuff.ToggleBarHandling;
import com.gamingmesh.jobs.stuff.Util;

import net.Zrips.CMILib.ActionBar.CMIActionBar;
import net.Zrips.CMILib.Container.CMINumber;
import net.Zrips.CMILib.Items.CMIItemStack;
import net.Zrips.CMILib.Items.CMIMaterial;
import net.Zrips.CMILib.Logs.CMIDebug;
import net.Zrips.CMILib.Messages.CMIMessages;
import net.Zrips.CMILib.Version.Version;
import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;

public class PlayerManager {

    private final ConcurrentMap<UUID, JobsPlayer> playersUUIDCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JobsPlayer> playersNameCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JobsPlayer> playersUUID = new ConcurrentHashMap<>();

    private final Map<UUID, PlayerInfo> playerUUIDMap = new LinkedHashMap<>();
    private final Map<Integer, PlayerInfo> playerIdMap = new LinkedHashMap<>();

    private final Jobs plugin;

    public PlayerManager(Jobs plugin) {
        this.plugin = plugin;
    }

    /**
     * @return the cached mob spawner meta name
     */
    @Deprecated
    public String getMobSpawnerMetadata() {
        return JobsMobSpawner.getMobSpawnerMetadata();
    }

    @Deprecated
    public int getMapSize() {
        return playerUUIDMap.size();
    }

    public void clearMaps() {
        playerUUIDMap.clear();
        playerIdMap.clear();
    }

    public void clearCache() {
        playersUUIDCache.clear();
        playersNameCache.clear();
        playersUUID.clear();
    }

    public void addPlayerToMap(PlayerInfo info) {

        // Checking duplicated UUID's which usually is a cause of previous bugs
        if (playerUUIDMap.containsKey(info.getUuid()) && playerUUIDMap.get(info.getUuid()).getID() != info.getID()) {
            int id = playerUUIDMap.get(info.getUuid()).getID();
            if (Jobs.getGCManager().isInformDuplicates()) {
                CMIMessages.consoleMessage("&7Duplicate! &5" + info.getName() + " &7same UUID for 2 entries in database. Please remove of one them from users table id1: &2" + id + " &7id2: &2" + info
                    .getID());
            }
            if (id < info.getID()) {
                return;
            }
        }

        playerUUIDMap.put(info.getUuid(), info);
        playerIdMap.put(info.getID(), info);
    }

    public void addPlayerToCache(JobsPlayer jPlayer) {
        playersUUIDCache.putIfAbsent(jPlayer.playerUUID, jPlayer);
        if (jPlayer.getName() == null)
            return;

        // On data load check for name duplication
        if (!Jobs.fullyLoaded) {
            // Checking for existing record by same name
            JobsPlayer oldRecord = playersNameCache.get(jPlayer.getName().toLowerCase());
            if (oldRecord != null && !jPlayer.getUniqueId().equals(oldRecord.getUniqueId())) {
                CMIMessages.consoleMessage("&cDuplicate in database for (&f" + jPlayer.getName() + "&c) -> (&f" + jPlayer.getUniqueId() + "&c) <-> (&f" + oldRecord.getUniqueId() + "&c)");
                // Using newest record
                if (jPlayer.getSeen() > oldRecord.getSeen())
                    playersNameCache.put(jPlayer.getName().toLowerCase(), jPlayer);
                return;
            }
            playersNameCache.putIfAbsent(jPlayer.getName().toLowerCase(), jPlayer);
        } else {
            // Add player to cache independent if there was other record by this name, we should use latest record
            playersNameCache.put(jPlayer.getName().toLowerCase(), jPlayer);
        }

    }

    public void addPlayer(JobsPlayer jPlayer) {
        playersUUID.putIfAbsent(jPlayer.getUniqueId(), jPlayer);
    }

    /**
     * Removes the given player from the memory.
     *
     * @param player {@link Player}
     */
    public void removePlayer(Player player) {
        if (player == null)
            return;

        playersUUID.remove(player.getUniqueId());
    }

    public ConcurrentMap<UUID, JobsPlayer> getPlayersCache() {
        return playersUUIDCache;
    }

    public Map<UUID, PlayerInfo> getPlayersInfoUUIDMap() {
        return playerUUIDMap;
    }

    /**
     * Returns the player identifier by its name. This will returns
     * -1 if the player is not cached.
     *
     * @param name the player name
     * @return the identifier
     */
    public int getPlayerId(String name) {
        PlayerInfo info = getPlayerInfo(name);
        return info == null ? -1 : info.getID();
    }

    /**
     * Returns the player identifier by its uuid. This will returns
     * -1 if the player is not cached.
     *
     * @param uuid player {@link UUID}
     * @return the identifier
     */
    public int getPlayerId(UUID uuid) {
        PlayerInfo info = playerUUIDMap.get(uuid);
        return info == null ? -1 : info.getID();
    }

    /**
     * Returns the {@link PlayerInfo} for the given name. This will returns
     * null if the player is not cached.
     *
     * @param name the player name
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(String name) {
        JobsPlayer jPlayer = playersNameCache.get(name.toLowerCase());
        if (jPlayer == null)
            return null;
        return playerUUIDMap.get(jPlayer.getUniqueId());
    }

    /**
     * Returns the {@link PlayerInfo} for the given identifier. This will returns
     * null if the player is not cached.
     *
     * @param id the player id
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(int id) {
        return playerIdMap.get(id);
    }

    /**
     * Returns the {@link PlayerInfo} for the given uuid. This will returns
     * null if the player is not cached.
     *
     * @param uuid player {@link UUID}
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(UUID uuid) {
        return playerUUIDMap.get(uuid);
    }

    /**
     * Handles join of new player synchronously. This can be called
     * within an asynchronous operation in order to load the player
     * from database if it is not cached into memory.
     *
     * @param player {@link Player}
     */
    public void playerJoin(Player player) {
        JobsPlayer jPlayer = playersUUIDCache.get(player.getUniqueId());

        if (jPlayer == null || Jobs.getGCManager().MultiServerCompatability()) {
            CompletableFuture<JobsPlayer> future = CompletableFuture.supplyAsync(() -> {
                JobsPlayer jobsPlayer = playersUUIDCache.get(player.getUniqueId());
                jobsPlayer = jobsPlayer == null ? new JobsPlayer(player) : jobsPlayer;

                return loadPlayer(jobsPlayer).join();
            });

            future.thenAccept(this::finalizeJoinPlayer);
        } else {
            finalizeJoinPlayer(jPlayer);
        }
    }

    private static CompletableFuture<JobsPlayer> loadPlayer(JobsPlayer old) {
        return CompletableFuture.supplyAsync(() -> {
            JobsPlayer jPlayer = Jobs.getJobsDAO().loadFromDao(old);

            if (Jobs.getGCManager().MultiServerCompatability()) {
                jPlayer.setArchivedJobs(Jobs.getJobsDAO().getArchivedJobs(jPlayer));
                jPlayer.setPaymentLimit(Jobs.getJobsDAO().getPlayersLimits(jPlayer));
                jPlayer.setPoints(Jobs.getJobsDAO().getPlayerPoints(jPlayer));
            }

            // Lets load quest progression
            PlayerInfo info = Jobs.getJobsDAO().loadPlayerData(jPlayer.getUniqueId());
            if (info != null) {
                jPlayer.setDoneQuests(info.getQuestsDone());
                jPlayer.setQuestProgressionFromString(info.getQuestProgression());

                ToggleBarHandling.recordPlayerOptionsFromInt(jPlayer.getUniqueId(), info.getMessageOptions());
            }

            Jobs.getJobsDAO().loadLog(jPlayer);

            return jPlayer;
        });
    }

    private void finalizeJoinPlayer(JobsPlayer jPlayer) {

        addPlayer(jPlayer);
        autoJoinJobs(jPlayer.getPlayer());
        jPlayer.onConnect();
        jPlayer.reloadHonorific();
        Jobs.getPermissionHandler().recalculatePermissions(jPlayer);

        addPlayerToCache(jPlayer);
    }

    /**
     * Handles player quit
     *
     * @param player {@link Player}
     */
    public void playerQuit(Player player) {
        JobsPlayer jPlayer = getJobsPlayer(player);
        if (jPlayer == null)
            return;

        jPlayer.onDisconnect();
        if (Jobs.getGCManager().saveOnDisconnect() || Jobs.getGCManager().MultiServerCompatability()) {
            jPlayer.setSaved(false);
            jPlayer.save(true);
        }
    }

    /**
     * Removes all jobs player miscellaneous settings like boss bar.
     */
    public void removePlayerAdditions() {
        for (JobsPlayer jPlayer : playersUUID.values()) {
            jPlayer.clearBossMaps();
            jPlayer.getUpdateBossBarFor().clear();
        }
    }

    /**
     * Save all the information of all of the players
     */
    public void saveAll() {
        /*
         * Saving is a three step process to minimize synchronization locks when called asynchronously.
         *
         * 1) Safely copy list for saving.
         * 2) Perform save on all players on copied list.
         * 3) Garbage collect the real list to remove any offline players with saved data
         */
        for (JobsPlayer jPlayer : new ArrayList<>(playersUUID.values()))
            jPlayer.save();

        playersUUID.values().removeIf(jPlayer -> jPlayer.isSaved() && !jPlayer.isOnline());

        if (!Jobs.getGCManager().useNewBlockProtection)
            Jobs.getBpManager().saveCache();
    }

    /**
     * Converts the cache of all the players into a new one.
     *
     * @param resetID true to not insert into database and reset the players id
     */
    public void convertChacheOfPlayers(boolean resetID) {
        int y = 0, i = 0, total = playersUUIDCache.size();

        for (JobsPlayer jPlayer : playersUUIDCache.values()) {
            if (resetID)
                jPlayer.setUserId(-1);

            JobsDAO dao = Jobs.getJobsDAO();
            dao.updateSeen(jPlayer);

            if (!resetID && jPlayer.getUserId() == -1)
                continue;

            for (JobProgression oneJ : jPlayer.getJobProgression())
                dao.insertJob(jPlayer, oneJ);

            dao.saveLog(jPlayer);
            dao.savePoints(jPlayer);
            dao.recordPlayersLimits(jPlayer);

            i++;

            if (y++ >= 1000) {
                CMIMessages.consoleMessage("&e[Jobs] Saved " + i + "/" + total + " players data");
                y = 0;
            }
        }
    }

    /**
     * Gets the player job info for specific player if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     *
     * @param player {@link Player}
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(Player player) {
        return getJobsPlayer(player.getUniqueId());
    }

    /**
     * Gets the player job info for specific player uuid if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     *
     * @param uuid the player uuid
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(UUID uuid) {
        JobsPlayer jPlayer = playersUUID.get(uuid);
        return jPlayer != null ? jPlayer : playersUUIDCache.get(uuid);
    }

    /**
     * Get the player job info for specific player name if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     *
     * @param playerName name - the player name who's job you're getting
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(String playerName) {
        return playersNameCache.get(playerName.toLowerCase());
    }

    /**
     * Gets the player job offline data for specific {@link PlayerInfo}
     *
     * @param info {@link PlayerInfo}
     * @param jobs the list of jobs data from database
     * @param points {@link PlayerPoints}
     * @param logs the map of logs
     * @param archivedJobs {@link ArchivedJobs}
     * @param limits {@link PaymentData}
     * @return {@link JobsPlayer}
     */
    public JobsPlayer getJobsPlayerOffline(PlayerInfo info, List<JobsDAOData> jobs, PlayerPoints points,
        Map<String, Log> logs, ArchivedJobs archivedJobs, PaymentData limits) {
        if (info == null)
            return null;

        JobsPlayer jPlayer = new JobsPlayer(info.getName());
        jPlayer.setPlayerUUID(info.getUuid());
        jPlayer.setUserId(info.getID());
        jPlayer.setDoneQuests(info.getQuestsDone());
        jPlayer.setQuestProgressionFromString(info.getQuestProgression());

        ToggleBarHandling.recordPlayerOptionsFromInt(jPlayer.getUniqueId(), info.getMessageOptions());

        jPlayer.setSeen(info.getSeen());

        if (jobs != null) {
            for (JobsDAOData jobdata : jobs) {
                Job job = Jobs.getJob(jobdata.getJobName());
                if (job != null) {
                    // Fixing issue with doubled jobs. Picking bigger job by level or exp
                    JobProgression oldProg = jPlayer.getJobProgression(job);
                    if (oldProg != null && (oldProg.getLevel() > jobdata.getLevel() || oldProg.getLevel() == jobdata.getLevel() && oldProg.getExperience() > jobdata.getExperience())) {
                        Jobs.getDBManager().getDB().removeSpecificJob(jPlayer.getUserId(), job.getName(), job.getJobFullName(), jobdata.getLevel(), jobdata.getExperience());
                        CMIMessages.consoleMessage("Cleaned up duplicated jobs record for " + jPlayer.getName() + " Job:" + jobdata.getJobName() + " Level:" + jobdata.getLevel());
                        continue;
                    }

                    jPlayer.progression.add(new JobProgression(job, jPlayer, jobdata.getLevel(), jobdata.getExperience()));
                }
            }
            jPlayer.reloadMaxExperience();
            jPlayer.reloadLimits();
        }

        if (points != null)
            jPlayer.setPoints(points);

        if (logs != null)
            jPlayer.setLog(logs);

        if (limits != null)
            jPlayer.setPaymentLimit(limits);

        if (archivedJobs != null) {
            ArchivedJobs aj = new ArchivedJobs();

            for (JobProgression one : archivedJobs.getArchivedJobs()) {
                JobProgression jp = new JobProgression(one.getJob(), jPlayer, one.getLevel(), one.getExperience());
                jp.reloadMaxExperience();

                if (one.getLeftOn() != null && one.getLeftOn() != 0L)
                    jp.setLeftOn(one.getLeftOn());

                aj.addArchivedJob(jp);
            }

            jPlayer.setArchivedJobs(aj);
        }

        return jPlayer;
    }

    private static void performCommandsOnJoin(JobsPlayer jPlayer, Job job) {
        String pName = jPlayer.getName();

        for (String one : job.getCmdOnJoin()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), one.replace("[name]", pName).replace("[jobname]", job.getName()));
        }
    }

    /**
     * Causes player to join to the given job.
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     */
    public void joinJob(JobsPlayer jPlayer, Job job) {
        if (jPlayer == null)
            return;

        // JobsJoin event
        JobsJoinEvent jobsJoinEvent = new JobsJoinEvent(jPlayer, job);
        plugin.getServer().getPluginManager().callEvent(jobsJoinEvent);
        // If event is canceled, dont do anything
        if (jobsJoinEvent.isCancelled())
            return;

        // let the user join the job
        if (!jPlayer.joinJob(job))
            return;

        Jobs.getJobsDAO().joinJob(jPlayer, jPlayer.getJobProgression(job));
        jPlayer.setLeftTime(job);

        performCommandsOnJoin(jPlayer, job);

        Jobs.takeSlot(job);
        Jobs.getSignUtil().updateAllSign(job);

        job.modifyTotalPlayerWorking(1);

        jPlayer.maxJobsEquation = CMINumber.clamp(getMaxJobs(jPlayer), 0, 9999);

        // Removing from cached item boost for recalculation
        cache.remove(jPlayer.getUniqueId());
    }

    private static void performCommandsOnLeave(JobsPlayer jPlayer, Job job) {
        String pName = jPlayer.getName();
        for (String one : job.getCmdOnLeave()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), one.replace("[name]", pName).replace("[jobname]", job.getName()));
        }
    }

    /**
     * Causes player to leave the given job.
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     */
    public boolean leaveJob(JobsPlayer jPlayer, Job job) {
        if (jPlayer == null || !jPlayer.isInJob(job))
            return false;

        JobsLeaveEvent jobsLeaveEvent = new JobsLeaveEvent(jPlayer, job);
        plugin.getServer().getPluginManager().callEvent(jobsLeaveEvent);
        // If event is canceled, don't do anything
        if (jobsLeaveEvent.isCancelled())
            return false;

        Jobs.getJobsDAO().recordToArchive(jPlayer, job);

        // let the user leave the job
        if (!jPlayer.leaveJob(job))
            return false;

        if (!Jobs.getJobsDAO().quitJob(jPlayer, job))
            return false;

        performCommandsOnLeave(jPlayer, job);
        Jobs.leaveSlot(job);

        jPlayer.getLeftTimes().remove(jPlayer.getUniqueId());

        Jobs.getSignUtil().updateAllSign(job);

        job.modifyTotalPlayerWorking(-1);

        // Removing from cached item boost for recalculation
        cache.remove(jPlayer.getUniqueId());

        return true;
    }

    /**
     * Causes player to leave all their jobs
     *
     * @param jPlayer {@link JobsPlayer}
     */
    public void leaveAllJobs(JobsPlayer jPlayer) {
        for (JobProgression job : new ArrayList<>(jPlayer.getJobProgression()))
            leaveJob(jPlayer, job.getJob());

        jPlayer.leaveAllJobs();
    }

    /**
     * Transfers player job to a new one
     *
     * @param jPlayer {@link JobsPlayer}
     * @param oldjob - the old job
     * @param newjob - the new job
     */
    public boolean transferJob(JobsPlayer jPlayer, Job oldjob, Job newjob) {
        if (!jPlayer.transferJob(oldjob, newjob) || !Jobs.getJobsDAO().quitJob(jPlayer, oldjob))
            return false;

        oldjob.modifyTotalPlayerWorking(-1);
        Jobs.getJobsDAO().joinJob(jPlayer, jPlayer.getJobProgression(newjob));
        newjob.modifyTotalPlayerWorking(1);
        jPlayer.save();
        return true;
    }

    /**
     * Promotes player in their job
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param levels - number of levels to promote
     */
    public void promoteJob(JobsPlayer jPlayer, Job job, int levels) {
        promoteJob(jPlayer, job, levels, false);
    }

    /**
     * Promotes player in their job
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param levels - number of levels to promote
     */
    public void promoteJob(JobsPlayer jPlayer, Job job, int levels, boolean performCommands) {

        if (performCommands) {
            JobProgression prog = jPlayer.getJobProgression(job);
            performCommandOnLevelUp(jPlayer, prog, prog.getLevel(), prog.getLevel() + levels);
        }

        jPlayer.promoteJob(job, levels);

        jPlayer.save();

        Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Demote player in their job
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param levels - number of levels to demote
     */
    public void demoteJob(JobsPlayer jPlayer, Job job, int levels) {
        jPlayer.demoteJob(job, levels);
        jPlayer.save();

        Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Adds experience to the player
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param experience - experience gained
     */
    public void addExperience(JobsPlayer jPlayer, Job job, double experience) {
        if (experience > Double.MAX_VALUE)
            return;

        JobProgression prog = jPlayer.getJobProgression(job);
        if (prog == null)
            return;

        int oldLevel = prog.getLevel();

        if (prog.addExperience(experience)) {
            performLevelUp(jPlayer, job, oldLevel);
            Jobs.getSignUtil().updateAllSign(job);
        }

        jPlayer.save();
    }

    /**
     * Removes experience from the player
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param experience - experience gained
     */
    public void removeExperience(JobsPlayer jPlayer, Job job, double experience) {
        if (experience > Double.MAX_VALUE)
            return;

        JobProgression prog = jPlayer.getJobProgression(job);
        if (prog == null)
            return;

        prog.addExperience(-experience);

        jPlayer.save();
        Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Broadcasts level up about a player
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param oldLevel
     */
    public void performLevelUp(JobsPlayer jPlayer, Job job, int oldLevel) {
        JobProgression prog = jPlayer.getJobProgression(job);
        if (prog == null)
            return;

        Player player = jPlayer.getPlayer();

        // when the player loses income
        if (prog.getLevel() < oldLevel) {
            String message = Jobs.getLanguage().getMessage("message.leveldown.message");

            message = Language.updateJob(message, job);

            message = message.replace("%playername%", jPlayer.getName());
            message = message.replace("%playerdisplayname%", jPlayer.getDisplayName());
            message = message.replace("%joblevel%", prog.getLevelFormatted());
            message = message.replace("%lostLevel%", Integer.toString(oldLevel));

            if (player != null && (Jobs.getGCManager().LevelChangeActionBar || Jobs.getGCManager().LevelChangeChat)) {
                for (String line : message.split("\n")) {
                    if (Jobs.getGCManager().LevelChangeActionBar)
                        CMIActionBar.send(player, line);

                    if (Jobs.getGCManager().LevelChangeChat)
                        player.sendMessage(line);
                }
            }

            jPlayer.reloadHonorific();
            Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
            performCommandOnLevelUp(jPlayer, prog, oldLevel, prog.getLevel());
            Jobs.getSignUtil().updateAllSign(job);
            return;
        }

        // LevelUp event
        JobsLevelUpEvent levelUpEvent = new JobsLevelUpEvent(
            jPlayer,
            job,
            prog.getLevel(),
            Jobs.getTitleManager().getTitle(oldLevel, prog.getJob().getName()),
            Jobs.getTitleManager().getTitle(prog.getLevel(), prog.getJob().getName()),
            Jobs.getGCManager().SoundLevelupSound,
            Jobs.getGCManager().SoundLevelupVolume,
            Jobs.getGCManager().SoundLevelupPitch,
            Jobs.getGCManager().SoundTitleChangeSound,
            Jobs.getGCManager().SoundTitleChangeVolume,
            Jobs.getGCManager().SoundTitleChangePitch);

        plugin.getServer().getPluginManager().callEvent(levelUpEvent);

        // If event is cancelled, don't do anything
        if (levelUpEvent.isCancelled())
            return;

        if (player != null && Jobs.getGCManager().SoundLevelupUse) {
            try {
                player.getWorld().playSound(player.getLocation(), levelUpEvent.getSound(),
                    levelUpEvent.getSoundVolume(), levelUpEvent.getSoundPitch());
            } catch (Exception e) { // If it fails, we can ignore it
            }
        }

        if (Jobs.getGCManager().FireworkLevelupUse && player != null) {
            CMIScheduler.runTaskLater(plugin, () -> {
                if (!player.isOnline())
                    return;

                Firework f = player.getWorld().spawn(player.getLocation(), Firework.class);
                FireworkMeta fm = f.getFireworkMeta();

                if (Jobs.getGCManager().UseRandom) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int rt = r.nextInt(4) + 1;
                    Type type = Type.BALL;

                    switch (rt) {
                    case 2:
                        type = Type.BALL_LARGE;
                        break;
                    case 3:
                        type = Type.BURST;
                        break;
                    case 4:
                        type = Type.CREEPER;
                        break;
                    case 5:
                        type = Type.STAR;
                        break;
                    default:
                        break;
                    }

                    Color c1 = Util.getColor(r.nextInt(17) + 1);
                    Color c2 = Util.getColor(r.nextInt(17) + 1);

                    FireworkEffect effect = FireworkEffect.builder().flicker(r.nextBoolean()).withColor(c1)
                        .withFade(c2).with(type).trail(r.nextBoolean()).build();
                    fm.addEffect(effect);

                    fm.setPower(r.nextInt(2) + 1);
                } else {
                    fm.addEffect(Jobs.getGCManager().getFireworkEffect());
                    fm.setPower(Jobs.getGCManager().FireworkPower);
                }

                f.setFireworkMeta(fm);
            }, Jobs.getGCManager().ShootTime);
        }

        String message = Jobs.getLanguage().getMessage("message.levelup." + (Jobs.getGCManager().isBroadcastingLevelups()
            ? "broadcast" : "nobroadcast"));

        message = Language.updateJob(message, job);

        if (levelUpEvent.getOldTitle() != null)
            message = message.replace("%titlename%", levelUpEvent.getOldTitle()
                .getChatColor().toString() + levelUpEvent.getOldTitle().getName());

        message = message.replace("%playername%", jPlayer.getName());
        message = message.replace("%playerdisplayname%", jPlayer.getDisplayName());
        message = message.replace("%joblevel%", prog.getLevelFormatted());

        if (Jobs.getGCManager().isBroadcastingLevelups() || Jobs.getGCManager().LevelChangeActionBar || Jobs.getGCManager().LevelChangeChat) {
            for (String line : message.split("\n")) {
                if (Jobs.getGCManager().isBroadcastingLevelups()) {
                    if (Jobs.getGCManager().BroadcastingLevelUpLevels.contains(oldLevel + 1)
                        || Jobs.getGCManager().BroadcastingLevelUpLevels.contains(0))
                        plugin.getComplement().broadcastMessage(line);
                } else if (player != null) {
                    if (Jobs.getGCManager().LevelChangeActionBar)
                        CMIActionBar.send(player, line);

                    if (Jobs.getGCManager().LevelChangeChat)
                        player.sendMessage(line);
                }
            }
        }

        if (levelUpEvent.getNewTitle() != null && !levelUpEvent.getNewTitle().equals(levelUpEvent.getOldTitle())) {
            if (player != null && Jobs.getGCManager().SoundTitleChangeUse) {
                try {
                    player.getWorld().playSound(player.getLocation(), levelUpEvent.getTitleChangeSound(), levelUpEvent.getTitleChangeVolume(),
                        levelUpEvent.getTitleChangePitch());
                } catch (Exception e) { // If it fails, we can ignore it
                }
            }

            // user would skill up
            message = Jobs.getLanguage().getMessage("message.skillup." + (Jobs.getGCManager().isBroadcastingSkillups()
                ? "broadcast" : "nobroadcast"));

            message = message.replace("%playername%", jPlayer.getName());
            message = message.replace("%playerdisplayname%", jPlayer.getDisplayName());
            message = message.replace("%titlename%", levelUpEvent.getNewTitle()
                .getChatColor().toString() + levelUpEvent.getNewTitle().getName());

            message = Language.updateJob(message, job);

            if (Jobs.getGCManager().isBroadcastingSkillups() || Jobs.getGCManager().TitleChangeActionBar || Jobs.getGCManager().TitleChangeChat) {
                for (String line : message.split("\n")) {
                    if (Jobs.getGCManager().isBroadcastingSkillups()) {
                        plugin.getComplement().broadcastMessage(line);
                    } else if (player != null) {
                        if (Jobs.getGCManager().TitleChangeActionBar)
                            CMIActionBar.send(player, line);

                        if (Jobs.getGCManager().TitleChangeChat)
                            player.sendMessage(line);
                    }
                }
            }
        }

        jPlayer.reloadHonorific();
        Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
        performCommandOnLevelUp(jPlayer, prog, oldLevel, prog.getLevel());
        Jobs.getSignUtil().updateAllSign(job);

        if (player != null && !job.getMaxLevelCommands().isEmpty() && prog.getLevel() == jPlayer.getMaxJobLevelAllowed(prog.getJob())) {
            for (String cmd : job.getMaxLevelCommands()) {
                if (cmd.isEmpty()) {
                    continue;
                }

                String[] split = cmd.split(":", 2);
                if (split.length == 0) {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                    continue;
                }

                String command = "";
                if (split.length > 1) {
                    command = split[1];
                    command = command.replace("[playerName]", player.getName());
                    command = command.replace("[job]", job.getName());

                    if (split[0].equalsIgnoreCase("player:")) {
                        player.performCommand(command);
                    } else {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
                    }
                }
            }
        }
    }

    /**
     * Performs command on level up
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param oldLevel
     */
    public void performCommandOnLevelUp(JobsPlayer jPlayer, JobProgression prog, int oldLevel) {
        int newLevel = oldLevel + 1;
        List<String> commands = getCommandsOnLevelUp(jPlayer, prog, newLevel);
        commands.stream().forEach(cmd -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
    }

    private static List<String> getCommandsOnLevelUp(JobsPlayer jPlayer, JobProgression prog, int newLevel) {
        List<String> commands = new ArrayList<String>();
        for (JobCommands command : prog.getJob().getCommands()) {
            if ((command.getLevelFrom() == 0 && command.getLevelUntil() == 0) || (newLevel >= command.getLevelFrom() && newLevel <= command.getLevelUntil())) {
                for (String commandString : new ArrayList<>(command.getCommands())) {
                    commandString = commandString.replace("[player]", jPlayer.getName())
                        .replace("[playerName]", jPlayer.getName())
                        .replace("[oldlevel]", Integer.toString(newLevel - 1))
                        .replace("[newlevel]", Integer.toString(newLevel));

                    commandString = Language.updateJob(commandString, prog.getJob());

                    commands.add(commandString);
                }
            }
        }
        return commands;
    }

    /**
     * Performs command for each level
     *
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param oldLevel
     */
    public void performCommandOnLevelUp(JobsPlayer jPlayer, JobProgression prog, int oldLevel, int untilLevel) {
        if (oldLevel > untilLevel)
            return;
        for (int newLevel = oldLevel + 1; newLevel <= untilLevel; newLevel++) {
            List<String> commands = getCommandsOnLevelUp(jPlayer, prog, newLevel);
            commands.stream().forEach(cmd -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
        }
    }

    /**
     * Checks whenever the given jobs player is under the max allowed jobs.
     *
     * @param player {@link JobsPlayer}
     * @param currentCount the current jobs size
     * @return true if the player is under the given jobs size
     */
    public boolean getJobsLimit(JobsPlayer jPlayer, short currentCount) {
        int max = getMaxJobs(jPlayer);
        return max == -1 ? true : max > currentCount;
    }

    /**
     * Gets the maximum jobs from player.
     *
     * @param jPlayer {@link JobsPlayer}
     * @return the maximum allowed jobs
     */
    public int getMaxJobs(JobsPlayer jPlayer) {
        if (jPlayer == null) {
            return Jobs.getGCManager().getMaxJobs();
        }

        int max = (int) Jobs.getPermissionManager().getMaxPermission(jPlayer, "jobs.max", false);
        return max == 0 ? Jobs.getGCManager().getMaxJobs() : max;
    }

    public BoostMultiplier getBoost(JobsPlayer player, Job job) {
        return getBoost(player, job, false);
    }

    public BoostMultiplier getBoost(JobsPlayer player, Job job, boolean force) {
        BoostMultiplier b = new BoostMultiplier();
        for (CurrencyType one : CurrencyType.values()) {
            b.add(one, getBoost(player, job, one, force));
        }
        return b;
    }

    public double getBoost(JobsPlayer player, Job job, CurrencyType type) {
        return getBoost(player, job, type, false);
    }

    public double getBoost(JobsPlayer player, Job job, CurrencyType type, boolean force) {
        return player.getBoost(job.getName(), type, force);
    }

    /**
     * Perform reload for all jobs players.
     */
    public void reload() {
        for (JobsPlayer jPlayer : playersUUID.values()) {
            for (JobProgression progression : jPlayer.getJobProgression()) {
                Job job = Jobs.getJob(progression.getJob().getName());
                if (job != null)
                    progression.setJob(job);
            }
            if (jPlayer.isOnline()) {
                jPlayer.reloadHonorific();
                jPlayer.reloadLimits();
                Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
            }
        }
    }

    private final Map<UUID, Map<Job, BoostMultiplier>> cache = new HashMap<>();

    public void resetItemBonusCache(UUID uuid) {
        cache.remove(uuid);
    }

    public BoostMultiplier getItemBoostNBT(Player player, Job prog) {
        return cache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(prog, k -> getInventoryBoost(player, prog));
    }

    public BoostMultiplier getInventoryBoost(Player player, Job job) {
        BoostMultiplier data = new BoostMultiplier();

        if (player == null || job == null)
            return data;

        List<JobItems> jitems = new ArrayList<>();

        // Check mainhand slot
        if (Jobs.getGCManager().boostedItemsInMainHand) {
            ItemStack iih = CMIItemStack.getItemInMainHand(player);
            if (iih != null && Jobs.getGCManager().boostedItemsSlotSpecific && (!CMIMaterial.isArmor(iih.getType()) || CMIMaterial.isShield(iih.getType())) || !Jobs
                .getGCManager().boostedItemsSlotSpecific) {
                jitems.add(getJobsItemByNbt(CMIItemStack.getItemInMainHand(player)));
            }
        }

        // Check offhand slot
        if (Version.isCurrentEqualOrHigher(Version.v1_9_R1) && Jobs.getGCManager().boostedItemsInOffHand) {
            ItemStack iih = CMIItemStack.getItemInOffHand(player);
            if (iih != null && Jobs.getGCManager().boostedItemsSlotSpecific && (!CMIMaterial.isArmor(iih.getType()) || CMIMaterial.isShield(iih.getType())) || !Jobs
                .getGCManager().boostedItemsSlotSpecific) {
                jitems.add(getJobsItemByNbt(player.getInventory().getItemInOffHand()));
            }
        }

        // Check armor slots
        if (Jobs.getGCManager().boostedArmorItems) {

            for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {

                ItemStack item = player.getInventory().getArmorContents()[i];
                if (item == null || item.getType().equals(Material.AIR))
                    continue;

                if (Jobs.getGCManager().boostedItemsSlotSpecific) {
                    //Boots
                    if (i == 0 && (!CMIMaterial.isArmor(item.getType()) || CMIMaterial.isArmor(item.getType()) && !CMIMaterial.isBoots(item.getType()))) {
                        continue;
                    }

                    //Leggings
                    if (i == 1 && (!CMIMaterial.isArmor(item.getType()) || CMIMaterial.isArmor(item.getType()) && !CMIMaterial.isLeggings(item.getType()))) {
                        continue;
                    }

                    //Chestplate
                    if (i == 2 && (!CMIMaterial.isArmor(item.getType()) || CMIMaterial.isArmor(item.getType()) && !CMIMaterial.isChestplate(item.getType()))) {
                        continue;
                    }

                    // Helmet slot
                    if (i == 3 && !CMIMaterial.isArmor(item.getType()) && (CMIMaterial.isWeapon(item.getType()) || CMIMaterial.isTool(item.getType())) || i == 3 && CMIMaterial.isArmor(item.getType())
                        && !CMIMaterial.isHelmet(item.getType())) {
                        continue;
                    }
                }

                jitems.add(getJobsItemByNbt(item));
            }
        }

        JobProgression progress = getJobsPlayer(player).getJobProgression(job);

        for (JobItems jitem : jitems) {
            if (jitem == null)
                continue;
            if (!jitem.getJobs().contains(job))
                continue;

            data.add(jitem.getBoost(progress));
        }

        return data;
    }

    @Deprecated
    public boolean containsItemBoostByNBT(ItemStack item) {
        return ItemBoostManager.containsItemBoostByNBT(item);
    }

    @Deprecated
    public JobItems getJobsItemByNbt(ItemStack item) {
        return ItemBoostManager.getJobsItemByNbt(item);
    }

    public enum BoostOf {
        McMMO, PetPay, NearSpawner, Permission, Global, Dynamic, Item, Area
    }

    public Boost getFinalBonus(JobsPlayer player, Job job, boolean force, boolean getall) {
        return getFinalBonus(player, job, null, null, force, getall);
    }

    public Boost getFinalBonus(JobsPlayer player, Job job, boolean force) {
        return getFinalBonus(player, job, null, null, force, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job job) {
        return getFinalBonus(player, job, null, null, false, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job job, Entity ent, LivingEntity victim) {
        return getFinalBonus(player, job, ent, victim, false, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job job, Entity ent, LivingEntity victim, boolean force, boolean getall) {
        Boost boost = new Boost();

        if (player == null || !player.isOnline() || job == null)
            return boost;

        Player pl = player.getPlayer();

        if (JobsHook.mcMMO.isEnabled()) {
            boost.add(BoostOf.McMMO, new BoostMultiplier().add(JobsHook.getMcMMOManager().getMultiplier(pl)));
        }

        double petPay = 0D;

        if (ent instanceof Tameable) {
            Tameable t = (Tameable) ent;
            if (t.isTamed() && t.getOwner() instanceof Player) {
                petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", false, false);
                if (petPay != 0D)
                    boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));
            }
        }

        if (ent != null && JobsHook.MyPet.isEnabled() && JobsHook.getMyPetManager().isMyPet(ent, pl)) {
            if (petPay == 0D)
                petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", false, false);
            if (petPay != 0D)
                boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));
        }

        if (victim != null && JobsMobSpawner.isSpawnerEntity(victim)) {
            double amount = Jobs.getPermissionManager().getMaxPermission(player, "jobs.nearspawner", false, false);
            if (amount != 0D)
                boost.add(BoostOf.NearSpawner, new BoostMultiplier().add(amount));
        }

        if (getall) {
            if (petPay == 0D)
                petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", force, false);

            if (petPay != 0D)
                boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));

            double amount = Jobs.getPermissionManager().getMaxPermission(player, "jobs.nearspawner", force);
            if (amount != 0D)
                boost.add(BoostOf.NearSpawner, new BoostMultiplier().add(amount));
        }

        boost.add(BoostOf.Permission, getBoost(player, job, force));
        boost.add(BoostOf.Global, job.getBoost());

        if (Jobs.getGCManager().useDynamicPayment)
            boost.add(BoostOf.Dynamic, new BoostMultiplier().add(job.getBonus()));

        if (pl != null) {
            boost.add(BoostOf.Item, getItemBoostNBT(pl, job));
        }

        if (!Jobs.getRestrictedAreaManager().getRestrictedAreas().isEmpty())
            boost.add(BoostOf.Area, Jobs.getRestrictedAreaManager().getRestrictedMultipliers(player.getJobProgression(job), pl));

        return boost;
    }

    public void autoJoinJobs(final Player player) {
        if (!Jobs.getGCManager().AutoJobJoinUse || player == null || player.isOp())
            return;

        CMIScheduler.runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;

            JobsPlayer jPlayer = getJobsPlayer(player);
            if (jPlayer == null || player.hasPermission("jobs.*"))
                return;

            int playerMaxJobs = getMaxJobs(jPlayer);
            int playerCurrentJobs = jPlayer.getJobProgression().size();

            if (playerMaxJobs == 0 || playerMaxJobs != -1 && playerCurrentJobs >= playerMaxJobs)
                return;

            for (Job one : Jobs.getJobs()) {
                if (playerMaxJobs != -1 && jPlayer.getJobProgression().size() >= playerMaxJobs)
                    return;

                if (one.getMaxSlots() != null && Jobs.getUsedSlots(one) >= one.getMaxSlots())
                    continue;

                if (!jPlayer.isInJob(one) && player.hasPermission("jobs.autojoin." + one.getName().toLowerCase()))
                    joinJob(jPlayer, one);
            }

        }, Jobs.getGCManager().AutoJobJoinDelay * 20L);
    }
}
