/*
 * Copyright © 2004-2024 L2J Server
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
package com.l2jserver.gameserver.model.actor.instance;

import com.l2jserver.gameserver.enums.InstanceType;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.templates.L2NpcTemplate;

/**
 * This class manages all attackable festival NPCs, spawned during the Festival of Darkness.
 * @author Tempy
 */
public class L2FestivalMonsterInstance extends L2MonsterInstance {
	
	public L2FestivalMonsterInstance(L2NpcTemplate template) {
		super(template);
		setInstanceType(InstanceType.L2FestivalMonsterInstance);
	}
	
	/**
	 * Return True if the attacker is not another L2FestivalMonsterInstance.
	 */
	@Override
	public boolean isAutoAttackable(L2Character attacker) {
		return !(attacker instanceof L2FestivalMonsterInstance);
	}
	
	/**
	 * All mobs in the festival are aggressive, and have high aggro range.
	 */
	@Override
	public boolean isAggressive() {
		return true;
	}
	
	/**
	 * All mobs in the festival really don't need random animation.
	 */
	@Override
	public boolean hasRandomAnimation() {
		return false;
	}
}