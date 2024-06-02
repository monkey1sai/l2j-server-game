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
package com.l2jserver.gameserver.data.sql.impl;

import static com.l2jserver.gameserver.config.Configuration.character;
import static com.l2jserver.gameserver.config.Configuration.clan;
import static com.l2jserver.gameserver.config.Configuration.general;
import static com.l2jserver.gameserver.model.L2Clan.SUBUNIT_ACADEMY;
import static com.l2jserver.gameserver.model.L2Clan.SUBUNIT_KNIGHT1;
import static com.l2jserver.gameserver.model.L2Clan.SUBUNIT_ROYAL1;
import static com.l2jserver.gameserver.model.zone.ZoneId.SIEGE;
import static com.l2jserver.gameserver.network.SystemMessageId.ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME;
import static com.l2jserver.gameserver.network.SystemMessageId.CANNOT_DISPERSE_THE_CLANS_IN_ALLY;
import static com.l2jserver.gameserver.network.SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_SIEGE;
import static com.l2jserver.gameserver.network.SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_WAR;
import static com.l2jserver.gameserver.network.SystemMessageId.CANNOT_DISSOLVE_WHILE_OWNING_CLAN_HALL_OR_CASTLE;
import static com.l2jserver.gameserver.network.SystemMessageId.CAPTAIN_OF_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED;
import static com.l2jserver.gameserver.network.SystemMessageId.CAPTAIN_OF_ROYAL_GUARD_CANNOT_BE_APPOINTED;
import static com.l2jserver.gameserver.network.SystemMessageId.CLAN_CREATED;
import static com.l2jserver.gameserver.network.SystemMessageId.CLAN_NAME_INCORRECT;
import static com.l2jserver.gameserver.network.SystemMessageId.CLAN_NAME_TOO_LONG;
import static com.l2jserver.gameserver.network.SystemMessageId.DISSOLUTION_IN_PROGRESS;
import static com.l2jserver.gameserver.network.SystemMessageId.S1_ALREADY_EXISTS;
import static com.l2jserver.gameserver.network.SystemMessageId.THE_KNIGHTS_OF_S1_HAVE_BEEN_CREATED;
import static com.l2jserver.gameserver.network.SystemMessageId.THE_ROYAL_GUARD_OF_S1_HAVE_BEEN_CREATED;
import static com.l2jserver.gameserver.network.SystemMessageId.THE_S1S_CLAN_ACADEMY_HAS_BEEN_CREATED;
import static com.l2jserver.gameserver.network.SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT;
import static com.l2jserver.gameserver.network.SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN_ACADEMY;
import static com.l2jserver.gameserver.network.SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_MILITARY_UNIT;
import static java.util.concurrent.TimeUnit.DAYS;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.idfactory.IdFactory;
import com.l2jserver.gameserver.instancemanager.AuctionManager;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.ClanHallSiegeManager;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.FortSiegeManager;
import com.l2jserver.gameserver.instancemanager.SiegeManager;
import com.l2jserver.gameserver.model.ClanPrivilege;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2ClanMember;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.EventDispatcher;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerClanCreate;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerClanDestroy;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerCreateSubPledge;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerClanDismiss;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerRevivePledge;
import com.l2jserver.gameserver.model.events.impl.clan.ClanWarFinish;
import com.l2jserver.gameserver.model.events.impl.clan.ClanWarStart;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ExBrExtraUserInfo;
import com.l2jserver.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import com.l2jserver.gameserver.network.serverpackets.PledgeShowMemberListAll;
import com.l2jserver.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.network.serverpackets.UserInfo;
import com.l2jserver.gameserver.util.EnumIntBitmask;

/**
 * Clan Table.
 */
public class ClanTable {
	
	private static final Logger LOG = LoggerFactory.getLogger(ClanTable.class);
	
	private final Map<Integer, L2Clan> _clans = new ConcurrentHashMap<>();
	
	protected ClanTable() {
		int clanCount = 0;
		try (var con = ConnectionFactory.getInstance().getConnection();
			var s = con.createStatement();
			var rs = s.executeQuery("SELECT clan_id FROM clan_data")) {
			while (rs.next()) {
				int clanId = rs.getInt("clan_id");
				_clans.put(clanId, new L2Clan(clanId));
				final var clan = getClan(clanId);
				if (clan.getDissolvingExpiryTime() != 0) {
					scheduleRemoveClan(clan.getId());
				}
				clanCount++;
			}
		} catch (Exception ex) {
			LOG.error("There has been an error restoring clans from database!", ex);
		}
		LOG.info("Restored {} clans from the database.", clanCount);
		allianceCheck();
		restorewars();
	}
	
	/**
	 * Gets the clans.
	 * @return the clans
	 */
	public Collection<L2Clan> getClans() {
		return _clans.values();
	}
	
	/**
	 * Gets the clan count.
	 * @return the clan count
	 */
	public int getClanCount() {
		return _clans.size();
	}
	
	public L2Clan getClan(int clanId) {
		return _clans.get(clanId);
	}
	
	public L2Clan getClanByName(String clanName) {
		return getClans().stream().filter(c -> c.getName().equalsIgnoreCase(clanName)).findFirst().orElse(null);
	}
	
	/**
	 * Creates a new clan and store clan info to database
	 * @param player
	 * @param clanName
	 * @return NULL if clan with same name already exists
	 */
	public L2Clan createClan(L2PcInstance player, String clanName) {
		if (null == player) {
			return null;
		}
		
		if (general().debug()) {
			LOG.info("{} requested a clan creation.", player);
		}
		
		if (player.getLevel() < clan().getLeaderMinimumLevel()) {
			player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN);
			return null;
		}
		
		if (player.getClanId() != 0) {
			player.sendPacket(SystemMessageId.FAILED_TO_CREATE_CLAN);
			return null;
		}
		
		if (System.currentTimeMillis() < player.getClanCreateExpiryTime()) {
			player.sendPacket(SystemMessageId.YOU_MUST_WAIT_XX_DAYS_BEFORE_CREATING_A_NEW_CLAN);
			return null;
		}
		
		if (!clan().getClanNameTemplate().matcher(clanName).matches()) {
			player.sendPacket(SystemMessageId.CLAN_NAME_INCORRECT);
			return null;
		}
		
		if (clanName.length() > clan().getClanNameMaximumLength()) {
			player.sendPacket(SystemMessageId.CLAN_NAME_TOO_LONG);
			return null;
		}
		
		if (getClanByName(clanName) != null) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
			sm.addString(clanName);
			player.sendPacket(sm);
			return null;
		}
		
		final var objectId = IdFactory.getInstance().getNextId();
		final var clan = new L2Clan(objectId, clanName);
		final var leader = new L2ClanMember(clan, player);
		clan.setLeader(leader);
		leader.setPlayerInstance(player);
		clan.store();
		player.setClan(clan);
		player.setPledgeClass(L2ClanMember.calculatePledgeClass(player));
		player.setClanPrivileges(new EnumIntBitmask<>(ClanPrivilege.class, true));
		
		_clans.put(clan.getId(), clan);
		
		// should be update packet only
		player.sendPacket(new PledgeShowInfoUpdate(clan));
		player.sendPacket(new PledgeShowMemberListAll(clan, player));
		player.sendPacket(new UserInfo(player));
		player.sendPacket(new ExBrExtraUserInfo(player));
		player.sendPacket(new PledgeShowMemberListUpdate(player));
		player.sendPacket(SystemMessageId.CLAN_CREATED);
		
		// Notify to scripts
		EventDispatcher.getInstance().notifyEventAsync(new PlayerClanCreate(player, clan), player.getLastFolkNPC());
		return clan;
	}
	
	public synchronized void destroyClan(int clanId) {
		final var clan = getClan(clanId);
		if (clan == null) {
			return;
		}
		
		clan.broadcastToOnlineMembers(SystemMessage.getSystemMessage(SystemMessageId.CLAN_HAS_DISPERSED));
		final var castleId = clan.getCastleId();
		if (castleId == 0) {
			for (var siege : SiegeManager.getInstance().getSieges()) {
				siege.removeSiegeClan(clan);
			}
		}
		
		final var fortId = clan.getFortId();
		if (fortId == 0) {
			for (var siege : FortSiegeManager.getInstance().getSieges()) {
				siege.removeAttacker(clan);
			}
		}
		
		final var hallId = clan.getHideoutId();
		if (hallId == 0) {
			for (var hall : ClanHallSiegeManager.getInstance().getConquerableHalls().values()) {
				hall.removeAttacker(clan);
			}
		}
		
		final var auction = AuctionManager.getInstance().getAuction(clan.getAuctionBidAt());
		if (auction != null) {
			auction.cancelBid(clan.getId());
		}
		
		final var leaderMember = clan.getLeader();
		if (leaderMember == null) {
			clan.getWarehouse().destroyAllItems("ClanRemove", null, null);
		} else {
			clan.getWarehouse().destroyAllItems("ClanRemove", clan.getLeader().getPlayerInstance(), null);
		}
		
		for (var member : clan.getMembers()) {
			clan.removeClanMember(member.getObjectId(), 0);
		}
		
		_clans.remove(clanId);
		IdFactory.getInstance().releaseId(clanId);
		
		try (var con = ConnectionFactory.getInstance().getConnection()) {
			try (var ps = con.prepareStatement("DELETE FROM clan_data WHERE clan_id=?")) {
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (var ps = con.prepareStatement("DELETE FROM clan_privs WHERE clan_id=?")) {
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (var ps = con.prepareStatement("DELETE FROM clan_skills WHERE clan_id=?")) {
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (var ps = con.prepareStatement("DELETE FROM clan_subpledges WHERE clan_id=?")) {
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (var ps = con.prepareStatement("DELETE FROM clan_wars WHERE clan1=? OR clan2=?")) {
				ps.setInt(1, clanId);
				ps.setInt(2, clanId);
				ps.execute();
			}
			
			try (var ps = con.prepareStatement("DELETE FROM clan_notices WHERE clan_id=?")) {
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			if (castleId != 0) {
				try (var ps = con.prepareStatement("UPDATE castle SET taxPercent = 0 WHERE id = ?")) {
					ps.setInt(1, castleId);
					ps.execute();
				}
			}
			
			if (fortId != 0) {
				final var fort = FortManager.getInstance().getFortById(fortId);
				if (fort != null) {
					final var owner = fort.getOwnerClan();
					if (clan == owner) {
						fort.removeOwner(true);
					}
				}
			}
			
			if (hallId != 0) {
				final var hall = ClanHallSiegeManager.getInstance().getSiegableHall(hallId);
				if ((hall != null) && (hall.getOwnerId() == clanId)) {
					hall.free();
				}
			}
		} catch (Exception ex) {
			LOG.error("There has been an error deleting a clan from database!", ex);
		}
		
		// Notify to scripts
		EventDispatcher.getInstance().notifyEventAsync(new PlayerClanDestroy(leaderMember, clan));
	}
	
	public boolean dismissPledge(L2PcInstance player) {
		if (!player.isClanLeader()) {
			player.sendPacket(YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		
		final var clan = player.getClan();
		if (clan.getAllyId() != 0) {
			player.sendPacket(CANNOT_DISPERSE_THE_CLANS_IN_ALLY);
			return false;
		}
		
		if (clan.isAtWar()) {
			player.sendPacket(CANNOT_DISSOLVE_WHILE_IN_WAR);
			return false;
		}
		
		if ((clan.getCastleId() != 0) || (clan.getHideoutId() != 0) || (clan.getFortId() != 0)) {
			player.sendPacket(CANNOT_DISSOLVE_WHILE_OWNING_CLAN_HALL_OR_CASTLE);
			return false;
		}
		
		for (var castle : CastleManager.getInstance().getCastles()) {
			if (SiegeManager.getInstance().checkIsRegistered(clan, castle.getResidenceId())) {
				player.sendPacket(CANNOT_DISSOLVE_WHILE_IN_SIEGE);
				return false;
			}
		}
		
		for (var fort : FortManager.getInstance().getForts()) {
			if (FortSiegeManager.getInstance().checkIsRegistered(clan, fort.getResidenceId())) {
				player.sendPacket(CANNOT_DISSOLVE_WHILE_IN_SIEGE);
				return false;
			}
		}
		
		if (player.isInsideZone(SIEGE)) {
			player.sendPacket(CANNOT_DISSOLVE_WHILE_IN_SIEGE);
			return false;
		}
		
		if (clan.getDissolvingExpiryTime() > System.currentTimeMillis()) {
			player.sendPacket(DISSOLUTION_IN_PROGRESS);
			return false;
		}
		
		clan.setDissolvingExpiryTime(System.currentTimeMillis() + DAYS.toMillis(character().getDaysToPassToDissolveAClan()));
		clan.updateClanInDB();
		
		// The clan leader should take the XP penalty of a full death.
		player.calculateDeathExpPenalty(null, false);
		ClanTable.getInstance().scheduleRemoveClan(clan.getId());
		
		// Notify
		EventDispatcher.getInstance().notifyEvent(new PlayerClanDismiss(player), player.getLastFolkNPC());
		
		return true;
	}
	
	/**
	 * Restores a clan scheduled for deletion.
	 * @param player the clan leader
	 */
	public boolean revivePledge(L2PcInstance player) {
		if (!player.isClanLeader()) {
			player.sendPacket(YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		
		final var clan = player.getClan();
		clan.setDissolvingExpiryTime(0);
		clan.updateClanInDB();
		
		EventDispatcher.getInstance().notifyEvent(new PlayerRevivePledge(player), player.getLastFolkNPC());
		
		return true;
	}
	
	public boolean createSubPledge(L2PcInstance player, String subPledgeName, String leaderName, int pledgeType) {
		// TODO(Zoey76): This check might be incomplete.
		// if (!player.isClanLeader() && (!player.hasClanPrivilege(MANAGE_GROWTH) || player.getClanId() == 0))
		if (!player.isClanLeader()) {
			player.sendPacket(YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		
		final var clan = player.getClan();
		final var minimumClanLevel = getMinimumClanLevel(pledgeType);
		
		if (clan.getLevel() < minimumClanLevel) {
			if (pledgeType == SUBUNIT_ACADEMY) {
				player.sendPacket(YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN_ACADEMY);
			} else {
				player.sendPacket(YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_MILITARY_UNIT);
			}
			return false;
		}
		
		if (!clan().getClanNameTemplate().matcher(subPledgeName).matches()) {
			player.sendPacket(CLAN_NAME_INCORRECT);
			return false;
		}
		
		if (subPledgeName.length() > clan().getClanNameMaximumLength()) {
			player.sendPacket(CLAN_NAME_TOO_LONG);
			return false;
		}
		
		for (var tempClan : ClanTable.getInstance().getClans()) {
			if (tempClan.getSubPledge(subPledgeName) != null) {
				if (pledgeType == SUBUNIT_ACADEMY) {
					final var sm = SystemMessage.getSystemMessage(S1_ALREADY_EXISTS);
					sm.addString(subPledgeName);
					player.sendPacket(sm);
				} else {
					player.sendPacket(ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME);
				}
				return false;
			}
		}
		
		if (pledgeType != SUBUNIT_ACADEMY) {
			if ((clan.getClanMember(leaderName) == null) || (clan.getClanMember(leaderName).getPledgeType() != 0)) {
				if (pledgeType >= SUBUNIT_KNIGHT1) {
					player.sendPacket(CAPTAIN_OF_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED);
				} else if (pledgeType >= SUBUNIT_ROYAL1) {
					player.sendPacket(CAPTAIN_OF_ROYAL_GUARD_CANNOT_BE_APPOINTED);
				}
				return false;
			}
		}
		
		final int leaderId = pledgeType != SUBUNIT_ACADEMY ? clan.getClanMember(leaderName).getObjectId() : 0;
		if (clan.createSubPledge(player, pledgeType, leaderId, subPledgeName) == null) {
			return false;
		}
		
		SystemMessage sm;
		if (pledgeType == SUBUNIT_ACADEMY) {
			sm = SystemMessage.getSystemMessage(THE_S1S_CLAN_ACADEMY_HAS_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else if (pledgeType >= SUBUNIT_KNIGHT1) {
			sm = SystemMessage.getSystemMessage(THE_KNIGHTS_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else if (pledgeType >= SUBUNIT_ROYAL1) {
			sm = SystemMessage.getSystemMessage(THE_ROYAL_GUARD_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else {
			sm = SystemMessage.getSystemMessage(CLAN_CREATED);
		}
		player.sendPacket(sm);
		
		if (pledgeType != SUBUNIT_ACADEMY) {
			final var leaderSubPledge = clan.getClanMember(leaderName);
			final var leaderPlayer = leaderSubPledge.getPlayerInstance();
			if (leaderPlayer != null) {
				leaderPlayer.setPledgeClass(L2ClanMember.calculatePledgeClass(leaderPlayer));
				leaderPlayer.sendPacket(new UserInfo(leaderPlayer));
				leaderPlayer.sendPacket(new ExBrExtraUserInfo(leaderPlayer));
			}
		}
		
		// Notify
		EventDispatcher.getInstance().notifyEvent(new PlayerCreateSubPledge(player, pledgeType), player.getLastFolkNPC());
		
		return true;
	}
	
	public boolean isAllyExists(String allyName) {
		for (var clan : getClans()) {
			if ((clan.getAllyName() != null) && clan.getAllyName().equalsIgnoreCase(allyName)) {
				return true;
			}
		}
		return false;
	}
	
	public void storeclanswars(int clanId1, int clanId2) {
		final var clan1 = getClan(clanId1);
		final var clan2 = getClan(clanId2);
		
		EventDispatcher.getInstance().notifyEventAsync(new ClanWarStart(clan1, clan2));
		
		clan1.setEnemyClan(clan2);
		clan2.setAttackerClan(clan1);
		clan1.broadcastClanStatus();
		clan2.broadcastClanStatus();
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("REPLACE INTO clan_wars (clan1, clan2, wantspeace1, wantspeace2) VALUES(?,?,?,?)")) {
			ps.setInt(1, clanId1);
			ps.setInt(2, clanId2);
			ps.setInt(3, 0);
			ps.setInt(4, 0);
			ps.execute();
		} catch (Exception ex) {
			LOG.error("There has been an error saving clan wars data!", ex);
		}
		
		// SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_BEGUN);
		//
		var msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_WAR_DECLARED_AGAINST_S1_IF_KILLED_LOSE_LOW_EXP);
		msg.addString(clan2.getName());
		clan1.broadcastToOnlineMembers(msg);
		// msg = SystemMessage.getSystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_BEGUN);
		// msg.addString(clan1.getName());
		// clan2.broadcastToOnlineMembers(msg);
		// clan1 declared clan war.
		msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_S1_DECLARED_WAR);
		msg.addString(clan1.getName());
		clan2.broadcastToOnlineMembers(msg);
	}
	
	public void deleteclanswars(int clanId1, int clanId2) {
		final var clan1 = getClan(clanId1);
		final var clan2 = getClan(clanId2);
		
		EventDispatcher.getInstance().notifyEventAsync(new ClanWarFinish(clan1, clan2));
		
		clan1.deleteEnemyClan(clan2);
		clan2.deleteAttackerClan(clan1);
		clan1.broadcastClanStatus();
		clan2.broadcastClanStatus();
		
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("DELETE FROM clan_wars WHERE clan1=? AND clan2=?")) {
			ps.setInt(1, clanId1);
			ps.setInt(2, clanId2);
			ps.execute();
		} catch (Exception ex) {
			LOG.error("There has been an error removing clan wars data!", ex);
		}
		
		// SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_ENDED);
		var msg = SystemMessage.getSystemMessage(SystemMessageId.WAR_AGAINST_S1_HAS_STOPPED);
		msg.addString(clan2.getName());
		clan1.broadcastToOnlineMembers(msg);
		msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_S1_HAS_DECIDED_TO_STOP);
		msg.addString(clan1.getName());
		clan2.broadcastToOnlineMembers(msg);
	}
	
	public void checkSurrender(L2Clan clan1, L2Clan clan2) {
		int count = 0;
		for (var player : clan1.getMembers()) {
			if ((player != null) && (player.getPlayerInstance().getWantsPeace() == 1)) {
				count++;
			}
		}
		if (count == (clan1.getMembers().length - 1)) {
			clan1.deleteEnemyClan(clan2);
			clan2.deleteEnemyClan(clan1);
			deleteclanswars(clan1.getId(), clan2.getId());
		}
	}
	
	private void restorewars() {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var statement = con.createStatement();
			var rs = statement.executeQuery("SELECT clan1, clan2 FROM clan_wars")) {
			while (rs.next()) {
				final var clan1 = getClan(rs.getInt("clan1"));
				final var clan2 = getClan(rs.getInt("clan2"));
				if ((clan1 != null) && (clan2 != null)) {
					clan1.setEnemyClan(rs.getInt("clan2"));
					clan2.setAttackerClan(rs.getInt("clan1"));
				} else {
					LOG.warn("While restoring clan wars on of the clans [{}, {}] is null!", clan1, clan2);
				}
			}
		} catch (Exception ex) {
			LOG.error("There has been an error restoring clan wars data!", ex);
		}
	}
	
	/**
	 * Check for nonexistent alliances
	 */
	private void allianceCheck() {
		for (var clan : _clans.values()) {
			int allyId = clan.getAllyId();
			if ((allyId != 0) && (clan.getId() != allyId)) {
				if (!_clans.containsKey(allyId)) {
					clan.setAllyId(0);
					clan.setAllyName(null);
					clan.changeAllyCrest(0, true);
					clan.updateClanInDB();
					LOG.info("Removed alliance from clan {}.", clan);
				}
			}
		}
	}
	
	public List<L2Clan> getClanAllies(int allianceId) {
		final var clanAllies = new LinkedList<L2Clan>();
		if (allianceId != 0) {
			for (var clan : _clans.values()) {
				if ((clan != null) && (clan.getAllyId() == allianceId)) {
					clanAllies.add(clan);
				}
			}
		}
		return clanAllies;
	}
	
	public void storeClanScore() {
		for (var clan : _clans.values()) {
			clan.updateClanScoreInDB();
		}
	}
	
	public static ClanTable getInstance() {
		return SingletonHolder._instance;
	}
	
	private void scheduleRemoveClan(int clanId) {
		ThreadPoolManager.getInstance().scheduleGeneral(() -> {
			if (getClan(clanId) == null) {
				return;
			}
			if (getClan(clanId).getDissolvingExpiryTime() != 0) {
				destroyClan(clanId);
			}
		}, Math.max(getClan(clanId).getDissolvingExpiryTime() - System.currentTimeMillis(), 300000));
	}
	
	private static int getMinimumClanLevel(int pledgeType) {
		return switch (pledgeType) {
			case SUBUNIT_ACADEMY -> 5;
			case SUBUNIT_ROYAL1 -> 6;
			case SUBUNIT_KNIGHT1 -> 7;
			default -> throw new RuntimeException("Unk " + pledgeType + " minimum level!");
		};
	}
	
	private static class SingletonHolder {
		protected static final ClanTable _instance = new ClanTable();
	}
}
