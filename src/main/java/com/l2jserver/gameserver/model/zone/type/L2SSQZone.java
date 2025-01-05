/*

 * Copyright © 2004-2023 L2J Server

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

package com.l2jserver.gameserver.model.zone.type;

import com.l2jserver.gameserver.model.actor.L2Character;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

import com.l2jserver.gameserver.model.zone.L2ZoneType;

import com.l2jserver.gameserver.model.zone.ZoneId;

/**
 * SSQ Zones
 * @author Charus
 * @version 2.6.3.0
 */
public class L2SSQZone extends L2ZoneType {
	
	private int _expPenaltyPercent = 0;
	private boolean _allowItemDrop = true;
	
	public L2SSQZone(int id) {
		super(id);
	}
	
	@Override
	public void setParameter(String name, String value) {
		switch (name) {
			case "expPenaltyPercent" -> _expPenaltyPercent = Integer.parseInt(value);
			case "allowItemDrop" -> _allowItemDrop = Boolean.parseBoolean(value);
			default -> super.setParameter(name, value);
		}
	}
	
	@Override
	protected void onEnter(L2Character character) {
		if (character.isPlayer()) {
			L2PcInstance player = character.getActingPlayer();
			
			player.setInsideZone(ZoneId.SSQZONE, true);
			player.setExpPenaltyPercent(_expPenaltyPercent);
			player.setAllowItemDrop(_allowItemDrop);
		}
	}
	
	@Override
	protected void onExit(L2Character character) {
		if (character.isPlayer()) {
			L2PcInstance player = character.getActingPlayer();
			
			player.setInsideZone(ZoneId.SSQZONE, false);
			player.setExpPenaltyPercent(0);
			player.setAllowItemDrop(true);
		}
	}
}