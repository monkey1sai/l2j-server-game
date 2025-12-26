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
package com.l2jserver.gameserver.model;

import static com.l2jserver.gameserver.network.serverpackets.RadarControl.RadarAction.ADD;
import static com.l2jserver.gameserver.network.serverpackets.RadarControl.RadarAction.DELETE;
import static com.l2jserver.gameserver.network.serverpackets.RadarControl.RadarAction.DELETE_ALL;
import static com.l2jserver.gameserver.network.serverpackets.RadarControl.RadarType.FLAG_1;
import static com.l2jserver.gameserver.network.serverpackets.RadarControl.RadarType.FLAG_2;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.RadarControl;

/**
 * Radar.
 * @author dalrond
 * @author Zoey76
 */
public final class L2Radar {
	private final L2PcInstance _player;
	private final Set<RadarMarker> _markers = ConcurrentHashMap.newKeySet();
	
	public L2Radar(L2PcInstance player) {
		_player = player;
	}
	
	public void showRadar(int x, int y, int z, int type) {
		_markers.add(new RadarMarker(x, y, z));
		_player.sendPacket(new RadarControl(DELETE_ALL, FLAG_2, x, y, z));
		_player.sendPacket(new RadarControl(ADD, FLAG_1, x, y, z));
	}
	
	public void deleteRadar(int x, int y, int z, int type) {
		_markers.remove(new RadarMarker(x, y, z));
		_player.sendPacket(new RadarControl(DELETE, FLAG_1, x, y, z));
	}
	
	public void deleteAllRadar(int type) {
		for (var r : _markers) {
			_player.sendPacket(new RadarControl(DELETE_ALL, FLAG_2, r._x, r._y, r._z));
		}
		_markers.clear();
	}
	
	public void loadMarkers() {
		_player.sendPacket(new RadarControl(DELETE_ALL, FLAG_2, _player.getX(), _player.getY(), _player.getZ()));
		for (var r : _markers) {
			_player.sendPacket(new RadarControl(ADD, FLAG_1, r._x, r._y, r._z));
		}
	}
	
	static class RadarMarker {
		int _type, _x, _y, _z;
		
		RadarMarker(int type, int x, int y, int z) {
			_type = type;
			_x = x;
			_y = y;
			_z = z;
		}
		
		RadarMarker(int x, int y, int z) {
			_type = 1;
			_x = x;
			_y = y;
			_z = z;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = (prime * result) + _type;
			result = (prime * result) + _x;
			result = (prime * result) + _y;
			result = (prime * result) + _z;
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof RadarMarker other)) {
				return false;
			}
			return (_type == other._type) && (_x == other._x) && (_y == other._y) && (_z == other._z);
		}
	}
}
