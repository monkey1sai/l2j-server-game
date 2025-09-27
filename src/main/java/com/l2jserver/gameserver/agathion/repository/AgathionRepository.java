/*
 * Copyright © 2004-2025 L2J Server
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
package com.l2jserver.gameserver.agathion.repository;

import static com.l2jserver.gameserver.config.Configuration.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.l2jserver.gameserver.agathion.Agathion;

/**
 * Agathion repository.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class AgathionRepository {
	private static final Logger LOG = LoggerFactory.getLogger(AgathionRepository.class);
	private static final Gson GSON = new Gson();
	private static final Type TYPE_LIST_AGATHION = new TypeToken<List<Agathion>>() {
	}.getType();
	
	private final Map<Integer, Agathion> agathions = new HashMap<>();
	private final Map<Integer, Integer> itemIdToNpcId = new HashMap<>();
	
	private AgathionRepository() {
		load();
	}
	
	private void load() {
		agathions.clear();
		itemIdToNpcId.clear();
		final var file = new File(server().getDatapackRoot(), "data/stats/agathions.json");
		try (var reader = new JsonReader(new FileReader(file))) {
			final List<Agathion> loaded = GSON.fromJson(reader, TYPE_LIST_AGATHION);
			if (loaded != null) {
				for (var a : loaded) {
					agathions.put(a.npcId(), a);
					itemIdToNpcId.put(a.itemId(), a.npcId());
				}
			}
		} catch (FileNotFoundException ex) {
			LOG.warn("data/stats/agathions.json not found!", ex);
		} catch (IOException ex) {
			LOG.warn("Failed to load agathions.json: ", ex);
		}
	}
	
	public Agathion getByNpcId(int npcId) {
		return agathions.get(npcId);
	}
	
	public Agathion getByItemId(int itemId) {
		final var npcId = itemIdToNpcId.get(itemId);
		return (npcId != null) ? agathions.get(npcId) : null;
	}
	
	public static AgathionRepository getInstance() {
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder {
		static final AgathionRepository INSTANCE = new AgathionRepository();
	}
}
