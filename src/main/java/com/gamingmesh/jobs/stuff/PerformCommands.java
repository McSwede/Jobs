package com.gamingmesh.jobs.stuff;

import org.bukkit.Bukkit;

import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobsPlayer;

public final class PerformCommands {

    public static void performCommandsOnLeave(JobsPlayer jPlayer, Job job) {
	String pName = jPlayer.getName();

	for (String one : job.getCmdOnLeave()) {
	    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), one.replace("[name]", pName).replace("[jobname]", job.getName()));
	}
    }

    public static void performCommandsOnJoin(JobsPlayer jPlayer, Job job) {
	String pName = jPlayer.getName();

	for (String one : job.getCmdOnJoin()) {
	    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), one.replace("[name]", pName).replace("[jobname]", job.getName()));
	}
    }
}
