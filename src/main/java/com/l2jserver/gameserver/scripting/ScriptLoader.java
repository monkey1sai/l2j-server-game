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
package com.l2jserver.gameserver.scripting;

/**
 * A datapack script loader that will get executed by the core at runtime.
 * @author Kita (Noé Caratini)
 */
public interface ScriptLoader {
	
	// Marker interfaces for default loader types
	interface AIScriptLoader extends ScriptLoader {}
	interface EffectScriptLoader extends ScriptLoader {}
	interface HandlerScriptLoader extends ScriptLoader {}
	interface InstanceScriptLoader extends ScriptLoader {}
	interface QuestScriptLoader extends ScriptLoader {}
	interface GraciaScriptLoader extends ScriptLoader {}
	interface HellboundScriptLoader extends ScriptLoader {}
	
	void load();
}
