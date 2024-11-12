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

import static com.l2jserver.gameserver.config.Configuration.server;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.config.ServerConfiguration;

/**
 * Script engine manager.
 * @author KenM, Zoey76, Kita
 */
public final class ScriptEngineManager {
	private static final Logger LOG = LoggerFactory.getLogger(ScriptEngineManager.class);
	
	private final ServerConfiguration serverConfig;
	
	private final Map<String, Set<Class<?>>> scriptLoaders;
	
	private ClassLoader classLoader;
	
	private ScriptEngineManager(ServerConfiguration serverConfig) {
		this.serverConfig = serverConfig;
		this.scriptLoaders = new HashMap<>();
		
		try {
			final var scriptRootUrl = Path.of(serverConfig.getScriptRoot().getPath()).toUri().toURL();
			classLoader = new URLClassLoader("Scripts", new URL[] { scriptRootUrl }, this.getClass().getClassLoader());
			
			discoverLoaders();
		} catch (Exception e) {
			LOG.error("Failed to initialise classloader", e);
		}
	}
	
	private void discoverLoaders() {
		final var scriptRootPath = Path.of(serverConfig.getScriptRoot().getPath());
		LOG.debug("Discovering script loaders from {}", scriptRootPath);
		
		try (var scripts = Files.walk(scriptRootPath)) {
			// Find all the class files in the script directory
			scripts.filter(Files::isRegularFile)
				.filter(path -> path.toString().endsWith(".class"))
				.map(scriptRootPath::relativize)
				.map(Path::toString)
				// Turn file paths to fully qualified class names
				.map(pathStr -> pathStr.replace(File.separatorChar, '.'))
				.map(pathStr -> pathStr.replace(".class", ""))
				.peek(fqcn -> LOG.trace("Found class: {}", fqcn))
				// Load classes
				.map(fqcn -> {
					try {
						return classLoader.loadClass(fqcn);
					} catch (ClassNotFoundException e) {
						LOG.error("Failed to load class: {}", fqcn, e);
						return null;
					}
				})
				.filter(Objects::nonNull)
				// Find implementations of script loader
				.filter(ScriptLoader.class::isAssignableFrom)
				.forEach(clazz -> {
					// Sort them by marker interface in the script loaders map
					Arrays.stream(clazz.getInterfaces())
						.filter(ScriptLoader.class::isAssignableFrom)
						.findFirst()
						.ifPresent(infClass -> {
							LOG.debug("Registered script loader {} with type {}", clazz.getSimpleName(), infClass.getSimpleName());
							scriptLoaders.putIfAbsent(infClass.getSimpleName(), new HashSet<>());
							scriptLoaders.get(infClass.getSimpleName()).add(clazz);
						});
				});
			
			LOG.info("Loaded {} script loaders", scriptLoaders.values().stream().mapToLong(Set::size).sum());
		} catch (IOException e) {
			LOG.error("Failed to discover scripts from {}", scriptRootPath, e);
		}
	}
	
	public void loadType(Class<? extends ScriptLoader> type) {
		scriptLoaders.get(type.getSimpleName())
			.forEach(ScriptEngineManager::runLoader);
	}
	
	/**
	 * Executes a script class contained in the script root directory, by loading it through the ScriptEngine classloader and executing its {@code main} method.
	 * @param fqcn fully qualified class name of the script (e.g. {@code com.l2jserver.datapack.MyScript})
	 */
	public void executeScript(String fqcn) {
		try {
			final var clazz = classLoader.loadClass(fqcn);
			runLoader(clazz);
		} catch (ClassNotFoundException e) {
			LOG.error("Could not load {}", fqcn, e);
		}
	}
	
	/**
	 * Reads a script list file and attempts to load and execute all scripts listed in that file
	 * @param scriptListFile the script list file to load
	 * @see ScriptEngineManager#executeScript(String)
	 */
	public void executeScriptList(File scriptListFile) {
		if (!scriptListFile.isFile()) {
			throw new IllegalArgumentException("Argument must be an file containing a list of scripts to be loaded");
		}
		
		try (var lines = Files.lines(scriptListFile.toPath())) {
			lines.map(String::trim)
				.filter(line -> !line.isEmpty())
				.filter(line -> !line.startsWith("#"))
				.forEach(this::executeScript);
		} catch (Exception e) {
			LOG.error("Error loading {}", scriptListFile.getName(), e);
		}
	}
	
	private static void runLoader(Class<?> clazz) {
		if (clazz == null) {
			LOG.error("Attempting to load null class");
			return;
		}
		
		if (!ScriptLoader.class.isAssignableFrom(clazz)) {
			LOG.error("{} is not a valid loader", clazz.getSimpleName());
			return;
		}
		
		try {
			final var loader = (ScriptLoader) clazz.getDeclaredConstructor().newInstance();
			loader.load();
		} catch (Exception e) {
			LOG.error("Error loading script {}", clazz.getSimpleName(), e);
		}
	}
	
	public static ScriptEngineManager getInstance() {
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder {
		protected static final ScriptEngineManager INSTANCE = new ScriptEngineManager(server());
	}
}
