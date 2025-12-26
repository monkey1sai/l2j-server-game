/*
 * Copyright © 2004-2026 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.taskmanager.tasks;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.taskmanager.Task;
import com.l2jserver.gameserver.taskmanager.TaskManager;
import com.l2jserver.gameserver.taskmanager.TaskTypes;
import com.l2jserver.gameserver.taskmanager.TaskManager.ExecutedTask;

public class TaskCastleTaxUpdate extends Task {
	private static final Logger LOG = LoggerFactory.getLogger(TaskCastleTaxUpdate.class);
	
	private static final String NAME = "castle_tax_update";
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public void onTimeElapsed(ExecutedTask task) {
		final var castles = CastleManager.getInstance().getCastles();
		for (var castle : castles) {
			if (castle.getNextTaxPercent() != castle.getTaxPercent()) {
				castle.setTaxPercent(castle.getNextTaxPercent());
				
				LOG.info("Scheduled tax update for castleId: {}, new tax at {}%", castle.getResidenceId(), castle.getNextTaxPercent());
			}
		}
		
		resetNextTax();
	}
	
	private void resetNextTax() {
		final var cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 24);
		cal.set(Calendar.MINUTE, 00);
		cal.set(Calendar.SECOND, 00);
		final var time = cal.getTimeInMillis();
		
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("UPDATE castle SET next_tax_time = ? WHERE id > 0")) {
			ps.setLong(1, time);
			ps.execute();
		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
		}
	}
	
	@Override
	public void initializate() {
		var reset = false;
		
		final var castles = CastleManager.getInstance().getCastles();
		for (var castle : castles) {
			if (castle.getNextTaxTime() < System.currentTimeMillis()) {
				reset = true;
				if (castle.getNextTaxPercent() != castle.getTaxPercent()) {
					castle.setTaxPercent(castle.getNextTaxPercent());
					
					LOG.info("Force tax update after server offline for castleId: {}, new tax at {}%", castle.getResidenceId(), castle.getNextTaxPercent());
				}
			}
		}
		
		if (reset) {
			resetNextTax();
		}
		
		TaskManager.addUniqueTask(NAME, TaskTypes.TYPE_GLOBAL_TASK, "1", "00:00:00", "");
	}
}