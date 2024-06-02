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
package com.l2jserver.gameserver.cache;

import static com.l2jserver.gameserver.config.Configuration.general;
import static com.l2jserver.gameserver.config.Configuration.server;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.util.file.filter.HTMLFilter;

/**
 * HTML Cache.
 * @author Layane
 * @author Zoey76
 */
public class HtmCache {
	
	private static final Logger LOG = LoggerFactory.getLogger(HtmCache.class);
	
	private static final HTMLFilter HTML_FILTER = new HTMLFilter();
	
	private static final Map<String, String> HTML_CACHE = general().lazyCache() ? new ConcurrentHashMap<>() : new HashMap<>();
	
	private int _loadedFiles;
	
	private long _bytesBuffLen;
	
	protected HtmCache() {
		reload();
	}
	
	public void reload() {
		reload(server().getDatapackRoot());
	}
	
	public void reload(File f) {
		if (!general().lazyCache()) {
			LOG.info("Html cache start...");
			parseDir(f);
			LOG.info("{} megabytes on {} files loaded.", String.format("%.3f", getMemoryUsage()), getLoadedFiles());
		} else {
			HTML_CACHE.clear();
			_loadedFiles = 0;
			_bytesBuffLen = 0;
			LOG.info("Running lazy cache.");
		}
	}
	
	public void reloadPath(File f) {
		parseDir(f);
		LOG.info("Reloaded specified path.");
	}
	
	public double getMemoryUsage() {
		return ((float) _bytesBuffLen / 1048576);
	}
	
	public int getLoadedFiles() {
		return _loadedFiles;
	}
	
	private void parseDir(File dir) {
		final var files = dir.listFiles();
		if (files != null) {
			for (var file : files) {
				if (!file.isDirectory()) {
					loadFile(file);
				} else {
					parseDir(file);
				}
			}
		}
	}
	
	public String loadFile(File file) {
		if (!HTML_FILTER.accept(file)) {
			return null;
		}
		
		String content = null;
		try (var fis = new FileInputStream(file);
			var bis = new BufferedInputStream(fis)) {
			final int bytes = bis.available();
			byte[] raw = new byte[bytes];
			
			bis.read(raw);
			content = new String(raw, UTF_8);
			content = content.replaceAll("(?s)<!--.*?-->", ""); // Remove html comments
			
			String oldContent = HTML_CACHE.put(file.getCanonicalPath(), content);
			if (oldContent == null) {
				_bytesBuffLen += bytes;
				_loadedFiles++;
			} else {
				_bytesBuffLen = (_bytesBuffLen - oldContent.length()) + bytes;
			}
		} catch (Exception ex) {
			LOG.warn("Problem with htm file {}!", file, ex);
		}
		return content;
	}
	
	public String getHtm(String prefix, String path) {
		final var newPath = Objects.requireNonNullElse(prefix, "") + path;
		var content = HTML_CACHE.get(newPath);
		if (general().lazyCache() && (content == null)) {
			content = tryLoadContent(newPath);
			
			// If multilanguage content is not present, try default location.
			if ((prefix != null) && (content == null)) {
				content = tryLoadContent(path);
			}
		}
		return content;
	}
	
	private String tryLoadContent(String path) {
		var content = loadFile(server().getDatapackRoot().toPath().resolve(path).toFile());
		if (content == null) {
			content = loadFile(server().getDatapackRoot().toPath().resolve("data/html").resolve(path).toFile());
			if (content == null) {
				content = loadFile(server().getScriptRoot().toPath().resolve(path).toFile());
			}
		}
		return content;
	}
	
	public boolean contains(String path) {
		return HTML_CACHE.containsKey(path);
	}
	
	/**
	 * @param path The path to the HTM
	 * @return {@code true} if the path targets a HTM or HTML file, {@code false} otherwise.
	 */
	public boolean isLoadable(String path) {
		return HTML_FILTER.accept(server().getDatapackRoot().toPath().resolve(path).toFile());
	}
	
	public static HtmCache getInstance() {
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder {
		protected static final HtmCache INSTANCE = new HtmCache();
	}
}
