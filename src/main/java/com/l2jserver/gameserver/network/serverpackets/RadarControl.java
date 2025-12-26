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
package com.l2jserver.gameserver.network.serverpackets;

/**
 * Radar Control server packet implementation.
 * @author JIV
 * @author MELERIX
 * @author UnAfraid
 * @author Nos
 * @author Zoey76
 */
public class RadarControl extends L2GameServerPacket {
	private final RadarAction action;
	private final RadarType type;
	private final int x;
	private final int y;
	private final int z;
	
	public RadarControl(RadarAction action, RadarType type, int x, int y, int z) {
		this.action = action;
		this.type = type;
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	protected final void writeImpl() {
		writeC(0xF1);
		writeD(action.ordinal());
		writeD(type.ordinal() + 1);
		writeD(x);
		writeD(y);
		writeD(z);
	}
	
	public static enum RadarAction {
		ADD,
		DELETE,
		DELETE_ALL
	}
	
	public static enum RadarType {
		FLAG_1,
		FLAG_2
	}
}
