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
package com.l2jserver.gameserver.dao.factory;

import static com.l2jserver.gameserver.config.Configuration.database;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.dao.ClanDAO;
import com.l2jserver.gameserver.dao.DAO;
import com.l2jserver.gameserver.dao.ForumDAO;
import com.l2jserver.gameserver.dao.FriendDAO;
import com.l2jserver.gameserver.dao.HennaDAO;
import com.l2jserver.gameserver.dao.ItemDAO;
import com.l2jserver.gameserver.dao.ItemReuseDAO;
import com.l2jserver.gameserver.dao.PetDAO;
import com.l2jserver.gameserver.dao.PetSkillSaveDAO;
import com.l2jserver.gameserver.dao.PlayerDAO;
import com.l2jserver.gameserver.dao.PlayerSkillSaveDAO;
import com.l2jserver.gameserver.dao.PostDAO;
import com.l2jserver.gameserver.dao.PremiumItemDAO;
import com.l2jserver.gameserver.dao.RecipeBookDAO;
import com.l2jserver.gameserver.dao.RecipeShopListDAO;
import com.l2jserver.gameserver.dao.RecommendationBonusDAO;
import com.l2jserver.gameserver.dao.ServitorSkillSaveDAO;
import com.l2jserver.gameserver.dao.ShortcutDAO;
import com.l2jserver.gameserver.dao.SkillDAO;
import com.l2jserver.gameserver.dao.SubclassDAO;
import com.l2jserver.gameserver.dao.TeleportBookmarkDAO;
import com.l2jserver.gameserver.dao.TopicDAO;
import com.l2jserver.gameserver.dao.impl.mysql.ClanDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.ForumDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.FriendDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.HennaDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.ItemDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.ItemReuseDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PetDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PetSkillSaveDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PlayerDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PlayerSkillSaveDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PostDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.PremiumItemDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.RecipeBookDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.RecipeShopListDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.RecommendationBonusDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.ServitorSkillSaveDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.ShortcutDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.SkillDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.SubclassDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.TeleportBookmarkDAOMySQLImpl;
import com.l2jserver.gameserver.dao.impl.mysql.TopicDAOMySQLImpl;

/**
 * DAO Factory static registry.
 * @author Zoey76
 * @version 2.6.3.0
 */
public final class DAOFactory {
	
	private static final Logger LOG = LoggerFactory.getLogger(DAOFactory.class);
	
	private static final Map<Class<?>, DAO> DAOS = new ConcurrentHashMap<>();
	
	private DAOFactory() {
		final var type = database().getDatabaseType();
		switch (type) {
			case MYSQL: {
				initMySQL();
				break;
			}
			default: {
				throw new UnsupportedOperationException("Database type " + type + " is not supported yet.");
			}
		}
		LOG.info("Initialized DAO factory for database type: {}", type);
	}
	
	private void initMySQL() {
		registerDAO(FriendDAO.class, new FriendDAOMySQLImpl());
		registerDAO(HennaDAO.class, new HennaDAOMySQLImpl());
		registerDAO(ItemDAO.class, new ItemDAOMySQLImpl());
		registerDAO(ItemReuseDAO.class, new ItemReuseDAOMySQLImpl());
		registerDAO(PetDAO.class, new PetDAOMySQLImpl());
		registerDAO(PetSkillSaveDAO.class, new PetSkillSaveDAOMySQLImpl());
		registerDAO(PlayerDAO.class, new PlayerDAOMySQLImpl());
		registerDAO(PlayerSkillSaveDAO.class, new PlayerSkillSaveDAOMySQLImpl());
		registerDAO(PremiumItemDAO.class, new PremiumItemDAOMySQLImpl());
		registerDAO(RecipeBookDAO.class, new RecipeBookDAOMySQLImpl());
		registerDAO(RecipeShopListDAO.class, new RecipeShopListDAOMySQLImpl());
		registerDAO(RecommendationBonusDAO.class, new RecommendationBonusDAOMySQLImpl());
		registerDAO(ServitorSkillSaveDAO.class, new ServitorSkillSaveDAOMySQLImpl());
		registerDAO(ShortcutDAO.class, new ShortcutDAOMySQLImpl());
		registerDAO(SkillDAO.class, new SkillDAOMySQLImpl());
		registerDAO(SubclassDAO.class, new SubclassDAOMySQLImpl());
		registerDAO(TeleportBookmarkDAO.class, new TeleportBookmarkDAOMySQLImpl());
		registerDAO(ClanDAO.class, new ClanDAOMySQLImpl());
		registerDAO(ForumDAO.class, new ForumDAOMySQLImpl());
		registerDAO(TopicDAO.class, new TopicDAOMySQLImpl());
		registerDAO(PostDAO.class, new PostDAOMySQLImpl());
	}
	
	public FriendDAO getFriendDAO() {
		return getDAO(FriendDAO.class);
	}
	
	public HennaDAO getHennaDAO() {
		return getDAO(HennaDAO.class);
	}
	
	public ItemDAO getItemDAO() {
		return getDAO(ItemDAO.class);
	}
	
	public ItemReuseDAO getItemReuseDAO() {
		return getDAO(ItemReuseDAO.class);
	}
	
	public PetDAO getPetDAO() {
		return getDAO(PetDAO.class);
	}
	
	public PetSkillSaveDAO getPetSkillSaveDAO() {
		return getDAO(PetSkillSaveDAO.class);
	}
	
	public PlayerDAO getPlayerDAO() {
		return getDAO(PlayerDAO.class);
	}
	
	public PlayerSkillSaveDAO getPlayerSkillSaveDAO() {
		return getDAO(PlayerSkillSaveDAO.class);
	}
	
	public PremiumItemDAO getPremiumItemDAO() {
		return getDAO(PremiumItemDAO.class);
	}
	
	public RecipeBookDAO getRecipeBookDAO() {
		return getDAO(RecipeBookDAO.class);
	}
	
	public RecipeShopListDAO getRecipeShopListDAO() {
		return getDAO(RecipeShopListDAO.class);
	}
	
	public RecommendationBonusDAO getRecommendationBonusDAO() {
		return getDAO(RecommendationBonusDAO.class);
	}
	
	public ServitorSkillSaveDAO getServitorSkillSaveDAO() {
		return getDAO(ServitorSkillSaveDAO.class);
	}
	
	public ShortcutDAO getShortcutDAO() {
		return getDAO(ShortcutDAO.class);
	}
	
	public SkillDAO getSkillDAO() {
		return getDAO(SkillDAO.class);
	}
	
	public SubclassDAO getSubclassDAO() {
		return getDAO(SubclassDAO.class);
	}
	
	public TeleportBookmarkDAO getTeleportBookmarkDAO() {
		return getDAO(TeleportBookmarkDAO.class);
	}
	
	public ClanDAO getClanDAO() {
		return getDAO(ClanDAO.class);
	}
	
	public ForumDAO getForumDAO() {
		return getDAO(ForumDAO.class);
	}
	
	public TopicDAO getTopicDAO() {
		return getDAO(TopicDAO.class);
	}
	
	public PostDAO getPostDAO() {
		return getDAO(PostDAO.class);
	}
	
	private <T extends DAO> void registerDAO(Class<T> daoInterface, T daoImplementation) {
		DAOS.put(daoInterface, daoImplementation);
	}
	
	@SuppressWarnings("unchecked")
	private <T extends DAO> T getDAO(Class<T> daoInterface) {
		final T dao = (T) DAOS.get(daoInterface);
		if (dao == null) {
			throw new IllegalStateException("No DAO implementation found for interface: " + daoInterface.getName());
		}
		return dao;
	}
	
	public static DAOFactory getInstance() {
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder {
		static final DAOFactory INSTANCE = new DAOFactory();
	}
}
