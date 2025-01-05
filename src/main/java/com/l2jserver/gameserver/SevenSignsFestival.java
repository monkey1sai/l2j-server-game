/*
 * Copyright © 2004-2023 L2J Server
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
package com.l2jserver.gameserver;

import static com.l2jserver.gameserver.config.Configuration.clan;
import static com.l2jserver.gameserver.network.SystemMessageId.CLAN_MEMBER_C1_WAS_IN_HIGHEST_RANKED_PARTY_IN_FESTIVAL_OF_DARKNESS_AND_GAINED_S2_REPUTATION;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.gameserver.data.sql.impl.ClanTable;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.util.Util;

/**
 * Seven Signs Festival of Darkness Engine.<br>
 * TODO:<br>
 * <ul>
 * <li>Archer mobs should target healer characters over other party members.</li>
 * <li>Added 29 Sep: Players that leave a party during the Seven Signs Festival will now take damage and cannot be healed.</li>
 * </ul>
 * @author Tempy
 */
public class SevenSignsFestival {
	protected static final Logger LOG = LoggerFactory.getLogger(SevenSignsFestival.class);
	
	private static final String GET_CLAN_NAME = "SELECT clan_name FROM clan_data WHERE clan_id = (SELECT clanid FROM characters WHERE char_name = ?)";
	
	/**
	 * These length settings are important! :)<br>
	 * All times are relative to the ELAPSED time (in ms) since a festival begins.<br>
	 * Festival manager start is the time after the server starts to begin the first festival cycle.<br>
	 * The cycle length should ideally be at least 2x longer than the festival length.<br>
	 * This allows ample time for players to sign-up to participate in the festival.<br>
	 * The intermission is the time between the festival participants being moved to the "arenas" and the spawning of the first set of mobs.<br>
	 * The monster swarm time is the time before the monsters swarm to the center of the arena, after they are spawned.<br>
	 * The chest spawn time is for when the bonus festival chests spawn, usually towards the end of the festival.
	 */
	// Key Constants \\
	
	public static final int FESTIVAL_COUNT = 5; // do not change without correcting db and SevenSigns itself !
	
	private static final int FESTIVAL_LEVEL_MAX_31 = 0;
	private static final int FESTIVAL_LEVEL_MAX_42 = 1;
	private static final int FESTIVAL_LEVEL_MAX_53 = 2;
	private static final int FESTIVAL_LEVEL_MAX_64 = 3;
	private static final int FESTIVAL_LEVEL_MAX_NONE = 4;
	
	// 500 maximum possible score
	public static final int[] FESTIVAL_LEVEL_SCORES = { 60, 70, 100, 120, 150 };
	
	public static final int FESTIVAL_OFFERING_ID = 5901;
	
	private int _signsCycle = SevenSigns.getInstance().getCurrentCycle();
	
	private List<Integer> _accumulatedBonuses = new ArrayList<>(); // The total bonus available (in Ancient Adena)
	
	private final Map<Integer, Long> _dawnFestivalScores = new HashMap<>();
	private final Map<Integer, Long> _duskFestivalScores = new HashMap<>();
	
	private Map<Integer, L2Party> _festivalRooms = new HashMap<>();
	
	/**
	 * _festivalData is essentially an instance of the seven_signs_festival table and should be treated as such. Data is initially accessed by the related Seven Signs cycle, with _signsCycle representing data for the current round of Festivals. The actual table data is stored as a series of StatsSet
	 * constructs. These are accessed by the use of an offset based on the number of festivals, thus: offset = FESTIVAL_COUNT + festivalId (Data for Dawn is always accessed by offset > FESTIVAL_COUNT)
	 */
	private final Map<Integer, Map<Integer, StatsSet>> _festivalData = new HashMap<>();
	
	protected SevenSignsFestival() {
		restoreFestivalData();
		
		if (SevenSigns.getInstance().isSealValidationPeriod()) {
			LOG.info("Initialization bypassed due to Seal Validation in effect.");
			return;
		}
		LOG.info("The next Festival of Darkness game begins at {}", getStartDate(getStartTime()));
	}
	
	private long getStartTime() {
		final Calendar cal = Calendar.getInstance();
		int i0 = cal.get(Calendar.MINUTE);
		
		int i1;
		if (i0 > 39) {
			i1 = 60 - i0;
		} else {
			if (i0 > 19) {
				i1 = 40 - i0;
			} else {
				i1 = 20 - i0;
			}
		}
		
		cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) + (i1));
		cal.set(Calendar.SECOND, 0);
		
		return cal.getTimeInMillis() - System.currentTimeMillis();
	}
	
	private String getStartDate(long millis) {
		SimpleDateFormat obj = new SimpleDateFormat("HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(millis + System.currentTimeMillis());
		return obj.format(cal.getTime());
	}
	
	/**
	 * Restores saved festival data, basic settings from the properties file and past high score data from the database.
	 */
	protected void restoreFestivalData() {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var s = con.createStatement();
			var rs = s.executeQuery("SELECT festivalId, cabal, cycle, date, score, members, members_reward_flags FROM seven_signs_festival")) {
			while (rs.next()) {
				int ssqCycle = rs.getInt("cycle");
				int festivalId = rs.getInt("festivalId");
				String cabal = rs.getString("cabal");
				
				StatsSet festivalDat = new StatsSet();
				festivalDat.set("festivalId", festivalId);
				festivalDat.set("cabal", cabal);
				festivalDat.set("cycle", ssqCycle);
				festivalDat.set("date", rs.getString("date"));
				festivalDat.set("score", rs.getInt("score"));
				festivalDat.set("members", rs.getString("members"));
				festivalDat.set("members_reward_flags", rs.getString("members_reward_flags"));
				
				if (cabal.equals("dawn")) {
					festivalId += FESTIVAL_COUNT;
				}
				
				final Map<Integer, StatsSet> tempData = _festivalData.getOrDefault(ssqCycle, new HashMap<>());
				tempData.put(festivalId, festivalDat);
				_festivalData.put(ssqCycle, tempData);
			}
		} catch (SQLException e) {
			LOG.error("Failed to load configuration!", e);
		}
		
		StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		
		for (int i = 0; i < (FESTIVAL_COUNT - 1); i++) {
			query.append("accumulated_bonus");
			query.append(i);
			query.append(", ");
		}
		
		query.append("accumulated_bonus");
		query.append(FESTIVAL_COUNT - 1);
		query.append(' ');
		query.append("FROM seven_signs_status WHERE id=0");
		
		try (var con = ConnectionFactory.getInstance().getConnection();
			var s = con.createStatement();
			var rs = s.executeQuery(query.toString())) {
			while (rs.next()) {
				for (int i = 0; i < FESTIVAL_COUNT; i++) {
					_accumulatedBonuses.add(i, rs.getInt("accumulated_bonus" + i));
				}
			}
		} catch (SQLException e) {
			LOG.error("Failed to load configuration!", e);
		}
	}
	
	/**
	 * Stores current festival data, basic settings to the properties file and past high score data to the database. If updateSettings = true, then all Seven Signs data is updated in the database.
	 * @param updateSettings
	 */
	public void saveFestivalData(boolean updateSettings) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var psInsert = con.prepareStatement("REPLACE INTO seven_signs_festival (festivalId, cabal, cycle, date, score, members, members_reward_flags) VALUES (?,?,?,?,?,?,?)")) {
			for (Map<Integer, StatsSet> currCycleData : _festivalData.values()) {
				for (StatsSet festivalDat : currCycleData.values()) {
					psInsert.setInt(1, festivalDat.getInt("festivalId"));
					psInsert.setString(2, festivalDat.getString("cabal"));
					psInsert.setInt(3, festivalDat.getInt("cycle"));
					psInsert.setLong(4, Long.parseLong(festivalDat.getString("date")));
					psInsert.setInt(5, festivalDat.getInt("score"));
					psInsert.setString(6, festivalDat.getString("members"));
					psInsert.setString(7, festivalDat.getString("members_reward_flags"));
					psInsert.addBatch();
				}
			}
			psInsert.executeBatch();
		} catch (SQLException e) {
			LOG.error("Failed to save configuration!", e);
		}
		
		// Updates Seven Signs DB data also, so call only if really necessary.
		if (updateSettings) {
			SevenSigns.getInstance().saveSevenSignsStatus();
		}
	}
	
	/**
	 * If a clan member is a member of the highest-ranked party in the Festival of Darkness, 100 points are added per member
	 */
	protected void rewardHighestRanked() {
		String[] partyMembers;
		StatsSet overallData = getOverallHighestScoreData(FESTIVAL_LEVEL_MAX_31);
		if (overallData != null) {
			partyMembers = overallData.getString("members").split(",");
			for (String partyMemberName : partyMembers) {
				addReputationPointsForPartyMemberClan(partyMemberName);
			}
		}
		
		overallData = getOverallHighestScoreData(FESTIVAL_LEVEL_MAX_42);
		if (overallData != null) {
			partyMembers = overallData.getString("members").split(",");
			for (String partyMemberName : partyMembers) {
				addReputationPointsForPartyMemberClan(partyMemberName);
			}
		}
		
		overallData = getOverallHighestScoreData(FESTIVAL_LEVEL_MAX_53);
		if (overallData != null) {
			partyMembers = overallData.getString("members").split(",");
			for (String partyMemberName : partyMembers) {
				addReputationPointsForPartyMemberClan(partyMemberName);
			}
		}
		
		overallData = getOverallHighestScoreData(FESTIVAL_LEVEL_MAX_64);
		if (overallData != null) {
			partyMembers = overallData.getString("members").split(",");
			for (String partyMemberName : partyMembers) {
				addReputationPointsForPartyMemberClan(partyMemberName);
			}
		}
		
		overallData = getOverallHighestScoreData(FESTIVAL_LEVEL_MAX_NONE);
		if (overallData != null) {
			partyMembers = overallData.getString("members").split(",");
			for (String partyMemberName : partyMembers) {
				addReputationPointsForPartyMemberClan(partyMemberName);
			}
		}
	}
	
	private void addReputationPointsForPartyMemberClan(String partyMemberName) {
		L2PcInstance player = L2World.getInstance().getPlayer(partyMemberName);
		if (player != null) {
			if (player.getClan() != null) {
				player.getClan().addReputationScore(clan().getFestivalOfDarknessWin(), true);
				SystemMessage sm = SystemMessage.getSystemMessage(CLAN_MEMBER_C1_WAS_IN_HIGHEST_RANKED_PARTY_IN_FESTIVAL_OF_DARKNESS_AND_GAINED_S2_REPUTATION);
				sm.addString(partyMemberName);
				sm.addInt(clan().getFestivalOfDarknessWin());
				player.getClan().broadcastToOnlineMembers(sm);
			}
		} else {
			try (var con = ConnectionFactory.getInstance().getConnection();
				var ps = con.prepareStatement(GET_CLAN_NAME)) {
				ps.setString(1, partyMemberName);
				try (var rs = ps.executeQuery()) {
					if (rs.next()) {
						String clanName = rs.getString("clan_name");
						if (clanName != null) {
							L2Clan clan = ClanTable.getInstance().getClanByName(clanName);
							if (clan != null) {
								clan.addReputationScore(clan().getFestivalOfDarknessWin(), true);
								SystemMessage sm = SystemMessage.getSystemMessage(CLAN_MEMBER_C1_WAS_IN_HIGHEST_RANKED_PARTY_IN_FESTIVAL_OF_DARKNESS_AND_GAINED_S2_REPUTATION);
								sm.addString(partyMemberName);
								sm.addInt(clan().getFestivalOfDarknessWin());
								clan.broadcastToOnlineMembers(sm);
							}
						}
					}
				}
			} catch (Exception e) {
				LOG.warn("Could not get clan name of {}!", partyMemberName, e);
			}
		}
	}
	
	/**
	 * Used to reset all festival data at the beginning of a new quest event period.
	 * @param updateSettings
	 */
	protected void resetFestivalData(boolean updateSettings) {
		_signsCycle = SevenSigns.getInstance().getCurrentCycle();
		
		// Set all accumulated bonuses back to 0.
		for (int i = 0; i < FESTIVAL_COUNT; i++) {
			_accumulatedBonuses.set(i, 0);
		}
		
		_festivalRooms.clear();
		_dawnFestivalScores.clear();
		_duskFestivalScores.clear();
		
		// Set up a new data set for the current cycle of festivals
		Map<Integer, StatsSet> newData = new HashMap<>();
		
		for (int i = 0; i < (FESTIVAL_COUNT * 2); i++) {
			int festivalId = i;
			
			if (i >= FESTIVAL_COUNT) {
				festivalId -= FESTIVAL_COUNT;
			}
			
			// Create a new StatsSet with "default" data for Dusk
			StatsSet tempStats = new StatsSet();
			tempStats.set("festivalId", festivalId);
			tempStats.set("cycle", _signsCycle);
			tempStats.set("date", "0");
			tempStats.set("score", 0);
			tempStats.set("members", "");
			tempStats.set("members_reward_flags", "");
			
			if (i >= FESTIVAL_COUNT) {
				tempStats.set("cabal", SevenSigns.getCabalShortName(SevenSigns.CABAL_DAWN));
			} else {
				tempStats.set("cabal", SevenSigns.getCabalShortName(SevenSigns.CABAL_DUSK));
			}
			
			newData.put(i, tempStats);
		}
		
		// Add the newly created cycle data to the existing festival data, and
		// subsequently save it to the database.
		_festivalData.put(_signsCycle, newData);
		
		saveFestivalData(updateSettings);
		
		LOG.info("Reinitialized engine for next competition period.");
	}
	
	public boolean isWinnerOfTimeAttackEvent(L2PcInstance player, int roomId) {
		final StatsSet data = getHighestScoreData(SevenSigns.getInstance().getPlayerCabal(player.getObjectId()), roomId);
		String[] members = data.getString("members").split(",");
		
		for (String name : members) {
			if (name.equals(player.getName())) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean getTimeAttackRewardFlag(L2PcInstance player, int roomId) {
		final StatsSet data = getHighestScoreData(SevenSigns.getInstance().getPlayerCabal(player.getObjectId()), roomId);
		String[] members = data.getString("members_reward_flags").split(",");
		
		for (String name : members) {
			if (name.equals(player.getName())) {
				return true;
			}
		}
		
		return false;
	}
	
	public void giveTimeAttackReward(L2PcInstance player, int roomId, int amount) {
		StatsSet data = getHighestScoreData(SevenSigns.getInstance().getPlayerCabal(player.getObjectId()), roomId);
		final String[] members = data.getString("members_reward_flags").split(",");
		int count = data.getString("members_reward_flags").isEmpty() ? 0 : members.length;
		List<String> list = new ArrayList<>(count);
		
		if (count > 0) {
			for (String member : members) {
				list.add(member);
			}
		}
		list.add(player.getName());
		
		data.set("members_reward_flags", Util.implodeString(list, ","));
		
		player.addAncientAdena("SevenSigns", amount, player, true);
		
		saveFestivalData(true);
	}
	
	public void addPartyToEventRoom(int type, int room, L2Party party) {
		_festivalRooms.put(type == SevenSigns.CABAL_DUSK ? 10 + room : 20 + room, party);
	}
	
	public L2Party getPartyFromEventRoom(int type, int room) {
		room += (type == SevenSigns.CABAL_DUSK) ? 10 : 20;
		
		return _festivalRooms.get(room);
	}
	
	public boolean checkEventRoom(int type, int room) {
		room += (type == SevenSigns.CABAL_DUSK) ? 10 : 20;
		if (_festivalRooms.get(room) != null) {
			return false;
		}
		
		return true;
	}
	
	public void festivalEnd(int type, int room) {
		_festivalRooms.remove(type == SevenSigns.CABAL_DUSK ? 10 + room : 20 + room);
	}
	
	public final int getHighestScore(int oracle, int festivalId) {
		return getHighestScoreData(oracle, festivalId).getInt("score");
	}
	
	/**
	 * Returns a stats set containing the highest score <b>this cycle</b> for the the specified cabal and associated festival ID.
	 * @param oracle
	 * @param festivalId
	 * @return StatsSet festivalDat
	 */
	public final StatsSet getHighestScoreData(int oracle, int festivalId) {
		int offsetId = festivalId;
		
		if (oracle == SevenSigns.CABAL_DAWN) {
			offsetId += 5;
		}
		
		// Attempt to retrieve existing score data (if found), otherwise create a
		// new blank data set and display a console warning.
		StatsSet currData;
		
		try {
			currData = _festivalData.get(_signsCycle).get(offsetId);
		} catch (Exception e) {
			currData = new StatsSet();
			currData.set("score", 0);
			currData.set("members", "");
		}
		
		return currData;
	}
	
	/**
	 * Returns a stats set containing the highest ever recorded score data for the specified festival.
	 * @param festivalId
	 * @return StatsSet result
	 */
	public final StatsSet getOverallHighestScoreData(int festivalId) {
		StatsSet result = null;
		int highestScore = 0;
		
		for (Map<Integer, StatsSet> currCycleData : _festivalData.values()) {
			for (StatsSet currFestData : currCycleData.values()) {
				int currFestID = currFestData.getInt("festivalId");
				int festivalScore = currFestData.getInt("score");
				
				if (currFestID != festivalId) {
					continue;
				}
				
				if (festivalScore > highestScore) {
					highestScore = festivalScore;
					result = currFestData;
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Set the final score details for the last participants of the specified festival data. Returns <b>true</b> if the score is higher than that previously recorded <b>this cycle</b>.
	 * @param player
	 * @param oracle
	 * @param festivalId
	 * @param offeringScore
	 * @return boolean isHighestScore
	 */
	public boolean addTimeAttackRecord(int oracle, int festivalId, L2Party party, long offeringScore) {
		List<String> partyMembers;
		
		int currDawnHighScore = getHighestScore(SevenSigns.CABAL_DAWN, festivalId);
		int currDuskHighScore = getHighestScore(SevenSigns.CABAL_DUSK, festivalId);
		
		int thisCabalHighScore;
		int otherCabalHighScore;
		if (oracle == SevenSigns.CABAL_DAWN) {
			thisCabalHighScore = currDawnHighScore;
			otherCabalHighScore = currDuskHighScore;
			
			_dawnFestivalScores.put(festivalId, offeringScore);
		} else {
			thisCabalHighScore = currDuskHighScore;
			otherCabalHighScore = currDawnHighScore;
			
			_duskFestivalScores.put(festivalId, offeringScore);
		}
		
		StatsSet currFestData = getHighestScoreData(oracle, festivalId);
		// Check if this is the highest score for this level range so far for the player's cabal.
		if (offeringScore > thisCabalHighScore) {
			partyMembers = new ArrayList<>(party.getMemberCount());
			
			// Record a string list of the party members involved.
			for (L2PcInstance pc : party.getMembers()) {
				partyMembers.add(pc.getName());
			}
			
			// Update the highest scores and party list.
			currFestData.set("date", String.valueOf(System.currentTimeMillis()));
			currFestData.set("score", offeringScore);
			currFestData.set("members", Util.implodeString(partyMembers, ","));
			
			// Only add the score to the cabal's overall if it's higher than the other cabal's score.
			if (offeringScore > otherCabalHighScore) {
				// Give this cabal the festival points, while deducting them from the other.
				SevenSigns.getInstance().calcFestivalScore();
			}
			
			saveFestivalData(true);
			
			return true;
		}
		
		return false;
	}
	
	public final int getAccumulatedBonus(int festivalId) {
		return _accumulatedBonuses.get(festivalId);
	}
	
	public void addTimeAttackFee(int festivalId, int amount) {
		int newTotalBonus = _accumulatedBonuses.get(festivalId) + amount;
		_accumulatedBonuses.set(festivalId, newTotalBonus);
	}
	
	public static SevenSignsFestival getInstance() {
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder {
		protected static final SevenSignsFestival _instance = new SevenSignsFestival();
	}
}