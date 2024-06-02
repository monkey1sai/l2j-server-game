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
package com.l2jserver.gameserver.handler;

import java.util.HashMap;
import java.util.Map;

import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * Bypass Handler interface.
 * @author nBd, Zoey76
 */
public interface IBypassHandler {
	/**
	 * This is the worker method that is called when someone uses an bypass command.
	 * @param command
	 * @param activeChar
	 * @param bypassOrigin
	 * @return success
	 */
	boolean useBypass(String command, L2PcInstance player, L2Character bypassOrigin);
	
	/**
	 * This method is called at initialization to register all bypasses automatically.
	 * @return all known bypasses
	 */
	String[] getBypassList();
	
	/**
	 * Parses a string representing a command with optional parameters into a {@link Command} object.
	 * 
	 * <p>The input string can contain a command followed by parameters in the format:
	 * <code>command_name?key1=value1&key2=value2</code>. 
	 * Parameters are parsed into a {@link Map}, with keys and values trimmed of whitespace.
	 * 
	 * <p>Examples of valid inputs include:
	 * <ul>
	 *     <li><code>menu_select?ask=-3&reply=0</code></li>
	 *     <li><code>create_pledge?pledge_name= $pledge_name</code></li>
	 *     <li><code>dismiss_pledge</code></li>
	 *     <li><code>create_subpledge?pledge_name= $pledge_name & master_name= $master_name & pledge_type= 100</code></li>
	 * </ul>
	 * 
	 * @param input the string representing the command and optional parameters
	 * @return a {@link Command} object containing the parsed command name and parameters
	 */
	default Command parseCommand(String input) {
		final var parts = input.split("\\?", 2);
		final var parameters = new HashMap<String, String>();
		if (parts.length > 1) {
			final var params = parts[1].split("&");
			for (var pair : params) {
				final var param = pair.split("=", 2);
				if (param.length == 2) {
					parameters.put(param[0].trim(), param[1].trim());
				}
			}
		}
		return new Command(parts[0], parameters);
	}
	
	static record Command(String command, Map<String, String> parameters) {
		public String getString(String key) {
			return parameters.get(key);
		}
		
		public int getInt(String key) {
			return Integer.valueOf(parameters.get(key));
		}
	}
}