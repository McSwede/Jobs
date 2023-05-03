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

package com.gamingmesh.jobs.tasks;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.economy.BufferedEconomy;

import net.Zrips.CMILib.Messages.CMIMessages;

public class BufferedPaymentThread extends Thread {
    private volatile boolean running = true;
    private int sleep;

    public BufferedPaymentThread(int duration) {
	super("Jobs-BufferedPaymentThread");
	// We need this to be atleast 1 or more seconds
	duration = duration < 1 ? 1 : duration;
	this.sleep = duration * 1000;	
    }

    @Override
    public void run() {

	CMIMessages.consoleMessage("&eStarted buffered payment thread.");

	while (running) {
	    try {
		sleep(sleep);
	    } catch (InterruptedException e) {
		this.running = false;
		continue;
	    }
	    try {
		BufferedEconomy economy = Jobs.getEconomy();
		if (economy != null)
		    economy.payAll();
	    } catch (Throwable t) {
		t.printStackTrace();
		CMIMessages.consoleMessage("&c[Jobs] Exception in BufferedPaymentThread, stopping economy payments!");
		running = false;
	    }
	}
	CMIMessages.consoleMessage("&eBuffered payment thread shutdown.");
    }

    public void shutdown() {
	this.running = false;
	interrupt();
    }
}
