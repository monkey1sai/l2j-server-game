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
package com.l2jserver.gameserver.model.quest;

import static com.l2jserver.gameserver.config.Configuration.general;
import static com.l2jserver.gameserver.model.events.EventType.ATTACKABLE_AGGRO_RANGE_ENTER;
import static com.l2jserver.gameserver.model.events.EventType.ATTACKABLE_ATTACK;
import static com.l2jserver.gameserver.model.events.EventType.ATTACKABLE_KILL;
import static com.l2jserver.gameserver.model.events.EventType.CREATURE_ZONE_ENTER;
import static com.l2jserver.gameserver.model.events.EventType.CREATURE_ZONE_EXIT;
import static com.l2jserver.gameserver.model.events.EventType.FACTION_CALL;
import static com.l2jserver.gameserver.model.events.EventType.ITEM_BYPASS;
import static com.l2jserver.gameserver.model.events.EventType.ITEM_TALK;
import static com.l2jserver.gameserver.model.events.EventType.NPC_CREATURE_SEE;
import static com.l2jserver.gameserver.model.events.EventType.NPC_MANOR_BYPASS;
import static com.l2jserver.gameserver.model.events.EventType.NPC_MOVE_FINISHED;
import static com.l2jserver.gameserver.model.events.EventType.NPC_MOVE_NODE_ARRIVED;
import static com.l2jserver.gameserver.model.events.EventType.NPC_MOVE_ROUTE_FINISHED;
import static com.l2jserver.gameserver.model.events.EventType.NPC_SKILL_FINISHED;
import static com.l2jserver.gameserver.model.events.EventType.NPC_SKILL_SEE;
import static com.l2jserver.gameserver.model.events.EventType.NPC_SPAWN;
import static com.l2jserver.gameserver.model.events.EventType.NPC_TELEPORT;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_LEARN_SKILL_REQUESTED;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_LOGIN;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_MENU_SELECTED;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_QUEST_ACCEPTED;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_SKILL_LEARNED;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_TELEPORT_REQUEST;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_TUTORIAL;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_TUTORIAL_CLIENT_EVENT;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_TUTORIAL_CMD;
import static com.l2jserver.gameserver.model.events.EventType.PLAYER_TUTORIAL_QUESTION_MARK;
import static com.l2jserver.gameserver.model.events.EventType.TRAP_ACTION;
import static com.l2jserver.gameserver.model.events.ListenerRegisterType.GLOBAL;
import static com.l2jserver.gameserver.model.events.ListenerRegisterType.ITEM;
import static com.l2jserver.gameserver.model.events.ListenerRegisterType.NPC;
import static com.l2jserver.gameserver.model.events.ListenerRegisterType.ZONE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.commons.util.Rnd;
import com.l2jserver.commons.util.Util;
import com.l2jserver.gameserver.cache.HtmCache;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;
import com.l2jserver.gameserver.enums.audio.IAudio;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.ClassId;
import com.l2jserver.gameserver.model.events.AbstractScript;
import com.l2jserver.gameserver.model.events.impl.character.CreatureZoneEnter;
import com.l2jserver.gameserver.model.events.impl.character.CreatureZoneExit;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcCreatureSee;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcEventReceived;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcManorBypass;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcMoveFinished;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcMoveNodeArrived;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcMoveRouteFinished;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcSkillFinished;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcSkillSee;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcSpawn;
import com.l2jserver.gameserver.model.events.impl.character.npc.NpcTeleport;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.AttackableAggroRangeEnter;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.AttackableAttack;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.AttackableKill;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.FactionCall;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLearnSkillRequested;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLogin;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerMenuSelected;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerOneSkillSelected;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerQuestAccepted;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerSkillLearned;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerTeleportRequest;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerTutorial;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerTutorialClientEvent;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerTutorialCmd;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerTutorialQuestionMark;
import com.l2jserver.gameserver.model.events.impl.character.trap.OnTrapAction;
import com.l2jserver.gameserver.model.events.impl.item.ItemBypass;
import com.l2jserver.gameserver.model.events.impl.item.ItemTalk;
import com.l2jserver.gameserver.model.events.listeners.AbstractEventListener;
import com.l2jserver.gameserver.model.events.returns.TerminateReturn;
import com.l2jserver.gameserver.model.interfaces.IIdentifiable;
import com.l2jserver.gameserver.model.items.L2Item;
import com.l2jserver.gameserver.model.olympiad.CompetitionType;
import com.l2jserver.gameserver.model.olympiad.Participant;
import com.l2jserver.gameserver.model.skills.Skill;
import com.l2jserver.gameserver.model.zone.L2ZoneType;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.network.serverpackets.NpcQuestHtmlMessage;
import com.l2jserver.gameserver.network.serverpackets.TutorialShowHtml;
import com.l2jserver.gameserver.scripting.ScriptManager;

/**
 * Quest main class.
 * @author Luis Arias
 * @author Zoey76
 */
public class Quest extends AbstractScript implements IIdentifiable {
	
	private static final Logger LOG = LoggerFactory.getLogger(Quest.class);
	
	/** Map containing lists of timers from the name of the timer. */
	private volatile Map<String, List<QuestTimer>> _questTimers = null;
	
	private final ReentrantReadWriteLock _rwLock = new ReentrantReadWriteLock();
	
	private final WriteLock _writeLock = _rwLock.writeLock();
	
	private final ReadLock _readLock = _rwLock.readLock();
	
	/** Map containing all the start conditions. */
	private volatile Map<Predicate<L2PcInstance>, String> _startCondition = null;
	
	private final int _questId;
	private final String _name;
	private final byte _initialState = State.CREATED;
	protected boolean _onEnterWorld = false;
	private final String customName;
	
	private int[] _questItemIds = null;
	
	private static final String DEFAULT_NO_QUEST_MSG = "<html><body>You are either not on a quest that involves this NPC, or you don't meet this NPC's minimum quest requirements.</body></html>";
	private static final String DEFAULT_ALREADY_COMPLETED_MSG = "<html><body>This quest has already been completed.</body></html>";
	
	private static final String QUEST_DELETE_FROM_CHAR_QUERY = "DELETE FROM character_quests WHERE charId=? AND name=?";
	private static final String QUEST_DELETE_FROM_CHAR_QUERY_NON_REPEATABLE_QUERY = "DELETE FROM character_quests WHERE charId=? AND name=? AND var!=?";
	
	private static final int RESET_HOUR = 6;
	private static final int RESET_MINUTES = 30;
	
	/**
	 * Script constructor.
	 */
	public Quest() {
		this(-1);
	}
	
	/**
	 * Quest constructor.
	 * @param id the ID of the quest
	 */
	public Quest(int id) {
		this(id, null);
	}
	
	/**
	 * Custom quest constructor.
	 * @param id the ID of the quest
	 */
	public Quest(int id, String customName) {
		_questId = id;
		_name = getClass().getSimpleName();
		this.customName = customName;
		if (id > 0) {
			QuestManager.getInstance().addQuest(this);
		} else {
			QuestManager.getInstance().addScript(this);
		}
		
		loadGlobalData();
	}
	
	/**
	 * Gets the reset hour for a daily quest.
	 * @return the reset hour
	 */
	public int getResetHour() {
		return RESET_HOUR;
	}
	
	/**
	 * Gets the reset minutes for a daily quest.
	 * @return the reset minutes
	 */
	public int getResetMinutes() {
		return RESET_MINUTES;
	}
	
	/**
	 * This method is, by default, called by the constructor of all scripts.<br>
	 * Children of this class can implement this function in order to define what variables to load and what structures to save them in.<br>
	 * By default, nothing is loaded.
	 */
	protected void loadGlobalData() {
		
	}
	
	/**
	 * The function saveGlobalData is, by default, called at shutdown, for all quests, by the QuestManager.<br>
	 * Children of this class can implement this function in order to convert their structures<br>
	 * into <var, value> tuples and make calls to save them to the database, if needed.<br>
	 * By default, nothing is saved.
	 */
	public void saveGlobalData() {
		
	}
	
	/**
	 * Gets the quest ID.
	 * @return the quest ID
	 */
	@Override
	public int getId() {
		return _questId;
	}
	
	/**
	 * Add a new quest state of this quest to the database.
	 * @param player the owner of the newly created quest state
	 * @return the newly created {@link QuestState} object
	 */
	public QuestState newQuestState(L2PcInstance player) {
		return new QuestState(this, player, _initialState);
	}
	
	/**
	 * Get the specified player's {@link QuestState} object for this quest.<br>
	 * If the player does not have it and initIfNode is {@code true},<br>
	 * create a new QuestState object and return it, otherwise return {@code null}.
	 * @param player the player whose QuestState to get
	 * @param initIfNone if true and the player does not have a QuestState for this quest,<br>
	 *            create a new QuestState
	 * @return the QuestState object for this quest or null if it doesn't exist
	 */
	public QuestState getQuestState(L2PcInstance player, boolean initIfNone) {
		final QuestState qs = player.getQuestState(_name);
		if ((qs != null) || !initIfNone) {
			return qs;
		}
		return newQuestState(player);
	}
	
	/**
	 * @return the initial state of the quest
	 */
	public byte getInitialState() {
		return _initialState;
	}
	
	@Override
	public String getName() {
		return _name;
	}
	
	/**
	 * Add a timer to the quest (if it doesn't exist already) and start it.
	 * @param name the name of the timer (also passed back as "event" in {@link #onEvent(String, L2Npc, L2PcInstance)})
	 * @param time time in ms for when to fire the timer
	 * @param npc the npc associated with this timer (can be null)
	 * @param player the player associated with this timer (can be null)
	 * @see #startQuestTimer(String, long, L2Npc, L2PcInstance, boolean)
	 */
	public void startQuestTimer(String name, long time, L2Npc npc, L2PcInstance player) {
		startQuestTimer(name, time, npc, player, false);
	}
	
	/**
	 * Gets the quest timers.
	 * @return the quest timers
	 */
	public final Map<String, List<QuestTimer>> getQuestTimers() {
		if (_questTimers == null) {
			synchronized (this) {
				if (_questTimers == null) {
					_questTimers = new ConcurrentHashMap<>(1);
				}
			}
		}
		return _questTimers;
	}
	
	/**
	 * Add a timer to the quest (if it doesn't exist already) and start it.
	 * @param name the name of the timer (also passed back as "event" in {@link #onEvent(String, L2Npc, L2PcInstance)})
	 * @param time time in ms for when to fire the timer
	 * @param npc the npc associated with this timer (can be null)
	 * @param player the player associated with this timer (can be null)
	 * @param repeating indicates whether the timer is repeatable or one-time.<br>
	 *            If {@code true}, the task is repeated every {@code time} milliseconds until explicitly stopped.
	 */
	public void startQuestTimer(String name, long time, L2Npc npc, L2PcInstance player, boolean repeating) {
		final List<QuestTimer> timers = getQuestTimers().computeIfAbsent(name, k -> new ArrayList<>(1));
		// if there exists a timer with this name, allow the timer only if the [npc, player] set is unique
		// nulls act as wildcards
		if (getQuestTimer(name, npc, player) == null) {
			_writeLock.lock();
			try {
				timers.add(new QuestTimer(this, name, time, npc, player, repeating));
			} finally {
				_writeLock.unlock();
			}
		}
	}
	
	/**
	 * Get a quest timer that matches the provided name and parameters.
	 * @param name the name of the quest timer to get
	 * @param npc the NPC associated with the quest timer to get
	 * @param player the player associated with the quest timer to get
	 * @return the quest timer that matches the specified parameters or {@code null} if nothing was found
	 */
	public QuestTimer getQuestTimer(String name, L2Npc npc, L2PcInstance player) {
		if (_questTimers == null) {
			return null;
		}
		
		final List<QuestTimer> timers = getQuestTimers().get(name);
		if (timers != null) {
			_readLock.lock();
			try {
				for (QuestTimer timer : timers) {
					if (timer != null) {
						if (timer.isMatch(this, name, npc, player)) {
							return timer;
						}
					}
				}
			} finally {
				_readLock.unlock();
			}
		}
		return null;
	}
	
	/**
	 * Cancel all quest timers with the specified name.
	 * @param name the name of the quest timers to cancel
	 */
	public void cancelQuestTimers(String name) {
		if (_questTimers == null) {
			return;
		}
		
		final List<QuestTimer> timers = getQuestTimers().get(name);
		if (timers != null) {
			_writeLock.lock();
			try {
				for (QuestTimer timer : timers) {
					if (timer != null) {
						timer.cancel();
					}
				}
				timers.clear();
			} finally {
				_writeLock.unlock();
			}
		}
	}
	
	/**
	 * Cancel the quest timer that matches the specified name and parameters.
	 * @param name the name of the quest timer to cancel
	 * @param npc the NPC associated with the quest timer to cancel
	 * @param player the player associated with the quest timer to cancel
	 */
	public void cancelQuestTimer(String name, L2Npc npc, L2PcInstance player) {
		final QuestTimer timer = getQuestTimer(name, npc, player);
		if (timer != null) {
			timer.cancelAndRemove();
		}
	}
	
	/**
	 * Remove a quest timer from the list of all timers.<br>
	 * Note: does not stop the timer itself!
	 * @param timer the {@link QuestState} object to remove
	 */
	public void removeQuestTimer(QuestTimer timer) {
		if ((timer != null) && (_questTimers != null)) {
			final List<QuestTimer> timers = getQuestTimers().get(timer.getName());
			if (timers != null) {
				_writeLock.lock();
				try {
					timers.remove(timer);
				} finally {
					_writeLock.unlock();
				}
			}
		}
	}
	
	// These are methods to call within the core to call the quest events.
	
	/**
	 * Notify Event.
	 * @param event the event
	 * @param npc the NPC
	 * @param player the player
	 */
	public final void notifyEvent(String event, L2Npc npc, L2PcInstance player) {
		try {
			final var result = onEvent(event, npc, player);
			showResult(player, result, npc);
		} catch (Exception ex) {
			showError(player, ex);
		}
	}
	
	/**
	 * Notify Talk event.
	 * @param npc the npc
	 * @param player the player
	 */
	public final void notifyTalk(L2Npc npc, L2PcInstance player) {
		try {
			String result;
			final var startConditionHtml = getStartConditionHtml(player);
			if (!player.hasQuestState(_name) && (startConditionHtml != null)) {
				result = startConditionHtml;
			} else {
				result = onTalk(npc, player);
			}
			player.setLastQuestNpcObject(npc.getObjectId());
			showResult(player, result, npc);
		} catch (Exception ex) {
			showError(player, ex);
		}
	}
	
	/**
	 * Notify First Talk event.<br>
	 * Overrides the default NPC dialogs when a quest defines this for the given NPC.<br>
	 * Note: If the default html for this npc needs to be shown, onFirstTalk should call npc.showChatWindow(player) and then return null.
	 * @param npc the NPC whose dialogs to override
	 * @param player the player talking to the NPC
	 */
	public final void notifyFirstTalk(L2Npc npc, L2PcInstance player) {
		try {
			final var result = onFirstTalk(npc, player);
			showResult(player, result, npc);
		} catch (Exception ex) {
			showError(player, ex);
		}
	}
	
	// These are methods that java calls to invoke scripts.
	
	/**
	 * On Attack event triggered when a player attacks an NPC.
	 * @param npc the NPC
	 * @param attacker the attacker player
	 * @param damage the inflicted to the NPC
	 * @param isSummon {@code true} if the attacker is a summon
	 */
	public void onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isSummon) {
		
	}
	
	/**
	 * On Attack event triggered when a player attacks an NPC.
	 * @param npc the NPC
	 * @param attacker the attacker player
	 * @param damage the inflicted to the NPC
	 * @param isSummon {@code true} if the attacker is a summon
	 * @param skill the skill used to attack, if any
	 */
	public void onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isSummon, Skill skill) {
		onAttack(npc, attacker, damage, isSummon);
	}
	
	/**
	 * This function is called whenever an <b>exact instance</b> of a character who was previously registered for this event dies.<br>
	 * The registration for {@link #onDeath(L2Character, L2Character, QuestState)} events <b>is not</b> done via the quest itself, but it is instead handled by the QuestState of a particular player.
	 * @param killer this parameter contains a reference to the exact instance of the NPC that <b>killed</b> the character.
	 * @param victim this parameter contains a reference to the exact instance of the character that got killed.
	 * @param qs this parameter contains a reference to the QuestState of whomever was interested (waiting) for this kill.
	 */
	public void onDeath(L2Character killer, L2Character victim, QuestState qs) {
		onEvent("", ((killer instanceof L2Npc) ? ((L2Npc) killer) : null), qs.getPlayer());
	}
	
	/**
	 * This function is called whenever a player clicks on a link in a quest dialog and whenever a timer fires.
	 * @param event this parameter contains a string identifier for the event.<br>
	 *            Generally, this string is passed directly via the link.<br>
	 *            For example:<br>
	 *            <code>
	 *            &lt;a action="bypass -h Quest 626_ADarkTwilight 31517-01.htm"&gt;hello&lt;/a&gt;
	 *            </code><br>
	 *            The above link sets the event variable to "31517-01.htm" for the quest 626_ADarkTwilight.<br>
	 *            In the case of timers, this will be the name of the timer.<br>
	 *            This parameter serves as a sort of identifier.
	 * @param npc this parameter contains a reference to the instance of NPC associated with this event.<br>
	 *            This may be the NPC registered in a timer, or the NPC with whom a player is speaking, etc.<br>
	 *            This parameter may be {@code null} in certain circumstances.
	 * @param player this parameter contains a reference to the player participating in this function.<br>
	 *            It may be the player speaking to the NPC, or the player who caused a timer to start (and owns that timer).<br>
	 *            This parameter may be {@code null} in certain circumstances.
	 * @return the text returned by the event (maybe {@code null}, a filename or just text)
	 */
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		return null;
	}
	
	/**
	 * On Kill event triggered when a player kills a NPC.
	 * @param npc the NPC
	 * @param player the player
	 * @param isSummon if {@code true} the killer is the summon
	 */
	public void onKill(L2Npc npc, L2PcInstance player, boolean isSummon) {
		
	}
	
	/**
	 * This function is called whenever a player clicks to the "Quest" link of an NPC that is registered for the quest.
	 * @param npc this parameter contains a reference to the exact instance of the NPC that the player is talking with.
	 * @param talker this parameter contains a reference to the exact instance of the player who is talking to the NPC.
	 * @return the text returned by the event (maybe {@code null}, a filename or just text)
	 */
	public String onTalk(L2Npc npc, L2PcInstance talker) {
		return null;
	}
	
	/**
	 * This function is called whenever a player talks to an NPC that is registered for the quest.<br>
	 * That is, it is triggered from the very first click on the NPC, not via another dialog.<br>
	 * <b>Note 1:</b><br>
	 * Each NPC can be registered to at most one quest for triggering this function.<br>
	 * In other words, the same one NPC cannot respond to an "onFirstTalk" request from two different quests.<br>
	 * Attempting to register an NPC in two different quests for this function will result in one of the two registration being ignored.<br>
	 * <b>Note 2:</b><br>
	 * Since a Quest link isn't clicked in order to reach this, a quest state can be invalid within this function.<br>
	 * The coder of the script may need to create a new quest state (if necessary).<br>
	 * <b>Note 3:</b><br>
	 * The returned value of onFirstTalk replaces the default HTML that would have otherwise been loaded from a sub-folder of DatapackRoot/game/data/html/.<br>
	 * If you wish to show the default HTML, within onFirstTalk do npc.showChatWindow(player) and then return ""<br>
	 * @param npc this parameter contains a reference to the exact instance of the NPC that the player is talking with.
	 * @param player this parameter contains a reference to the exact instance of the player who is talking to the NPC.
	 * @return the text returned by the event (maybe {@code null}, a filename or just text)
	 */
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return null;
	}
	
	/**
	 * On Item Talk event triggered when an item bypass is called.
	 * @param event the event
	 */
	public void onItemTalk(ItemTalk event) {
		
	}
	
	/**
	 * On Item event.
	 * @param event the event
	 */
	public void onItemEvent(ItemBypass event) {
		
	}
	
	/**
	 * On Menu Selected event.
	 * @param event the event
	 */
	public void onMenuSelected(PlayerMenuSelected event) {
		
	}
	
	/**
	 * On Manor Menu Selected event.
	 * @param event the event
	 */
	public void onManorMenuSelected(NpcManorBypass event) {
		
	}
	
	/**
	 * On Quest Accepted event.
	 * @param event the event
	 */
	public void onQuestAccepted(PlayerQuestAccepted event) {
		
	}
	
	/**
	 * On Learn Skill Requested event.
	 * @param event the event
	 */
	public void onLearnSkillRequested(PlayerLearnSkillRequested event) {
		
	}
	
	/**
	 * On One Skill Selected event.
	 * @param event the event
	 */
	public void onOneSkillSelected(PlayerOneSkillSelected event) {
		
	}
	
	/**
	 * On Skill Learned event.
	 * @param event the event
	 */
	public void onSkillLearned(PlayerSkillLearned event) {
		
	}
	
	/**
	 * On Teleport Request event
	 */
	public void onTeleportRequest(PlayerTeleportRequest event) {
		
	}
	
	/**
	 * This function is called whenever a player uses a quest item that has a quest events list.<br>
	 * TODO: complete this documentation and unhardcode it to work with all item uses not with those listed.
	 * @param item the quest item that the player used
	 * @param player the player who used the item
	 */
	public void onItemUse(L2Item item, L2PcInstance player) {
		
	}
	
	/**
	 * This function is called whenever a player casts a skill near a registered NPC (1000 distance).<br>
	 * <b>Note:</b><br>
	 * If a skill does damage, both onSkillSee(..) and onAttack(..) will be triggered for the damaged NPC!<br>
	 * However, only onSkillSee(..) will be triggered if the skill does no damage,<br>
	 * or if it damages an NPC who has no onAttack(..) registration while near another NPC who has an onSkillSee registration.<br>
	 * TODO: confirm if the distance is 1000 and unhardcode.
	 * @param npc the NPC that saw the skill
	 * @param caster the player who cast the skill
	 * @param skill the actual skill that was used
	 * @param targets an array of all objects (can be any type of object, including mobs and players) that were affected by the skill
	 * @param isSummon if {@code true}, the skill was actually cast by the player's summon, not the player himself
	 */
	public void onSkillSee(L2Npc npc, L2PcInstance caster, Skill skill, List<L2Object> targets, boolean isSummon) {
		
	}
	
	/**
	 * On Spell Finished event triggered when an NPC finishes casting a skill.
	 * @param event the event
	 */
	public void onSpellFinished(NpcSkillFinished event) {
		
	}
	
	/**
	 * On Trap Action event triggered when a trap action is triggered.
	 * @param event the event
	 */
	public void onTrapAction(OnTrapAction event) {
		
	}
	
	/**
	 * This function is called whenever an NPC spawns or re-spawns and passes a reference to the newly (re)spawned NPC.<br>
	 * It is useful for initializations, starting quest timers, displaying chat (NpcSay), and more.
	 * @param npc the npc
	 */
	public void onSpawn(L2Npc npc) {
		
	}
	
	/**
	 * This function is called whenever an NPC is teleport.
	 * @param npc the npc
	 */
	protected void onTeleport(L2Npc npc) {
		
	}
	
	/**
	 * On Faction Call event triggered when an NPC ask for help.
	 * @param event the event
	 */
	public void onFactionCall(FactionCall event) {
		
	}
	
	/**
	 * On Aggro Range Enter event triggered when a player enters an NPC aggression range.
	 * @param event the event
	 */
	public void onAggroRangeEnter(AttackableAggroRangeEnter event) {
		
	}
	
	/**
	 * On See Creature event is triggered when an NPC sees a creature.
	 * @param npc the NPC who sees the creature
	 * @param creature the creature seen by the NPC
	 */
	public void onSeeCreature(L2Npc npc, L2Character creature) {
		
	}
	
	/**
	 * On Enter World event is triggered when a player enters the game.
	 * @param player the player who is entering the world
	 */
	public void onEnterWorld(L2PcInstance player) {
		
	}
	
	/**
	 * On Tutorial Event is triggered by a tutorial event and the associated command.
	 * @param player the player who triggered the tutorial event
	 * @param command the command associated with the tutorial event
	 */
	public void onTutorialEvent(L2PcInstance player, String command) {
		
	}
	
	/**
	 * On Tutorial Client Event is triggered by a tutorial event and the associated client event.
	 * @param player the player who triggered the tutorial client event
	 * @param event the client event associated with the tutorial
	 */
	public void onTutorialClientEvent(L2PcInstance player, int event) {
		
	}
	
	/**
	 * On Tutorial Question Mark event is triggered when a tutorial question mark appears for the player.
	 * @param player the player who sees the tutorial question mark
	 * @param number the identifier of the tutorial question mark
	 */
	public void onTutorialQuestionMark(L2PcInstance player, int number) {
		
	}
	
	/**
	 * On Tutorial Command event is triggered when a tutorial command is entered by the player.
	 * @param player the player who entered the tutorial command
	 * @param command the command entered by the player
	 */
	public void onTutorialCmd(L2PcInstance player, String command) {
		
	}
	
	/**
	 * On Enter Zone event is triggered when a character enters a registered zone.
	 * @param creature the character entering the zone
	 * @param zoneType the zone that the character is entering
	 */
	public void onEnterZone(L2Character creature, L2ZoneType zoneType) {
		
	}
	
	/**
	 * On Exit Zone event is triggered when a character exits a registered zone.
	 * @param creature the character exiting the zone
	 * @param zoneType the zone that the character is exiting
	 */
	public void onExitZone(L2Character creature, L2ZoneType zoneType) {
		
	}
	
	/**
	 * On Event Received event is triggered when an event is received by the NPC.
	 * @param event the event received
	 */
	public void onEventReceived(NpcEventReceived event) {
		
	}
	
	/**
	 * On Olympiad Match Finish event is triggered when a player wins an Olympiad Game.
	 * @param winner the player who won the match
	 * @param loser the player who lost the match
	 * @param type the competition type
	 */
	public void onOlympiadMatchFinish(Participant winner, Participant loser, CompetitionType type) {
		
	}
	
	/**
	 * On Olympiad Lose event is triggered when a player loses an Olympiad Game.
	 * @param loser the player who lost the match
	 * @param type the competition type
	 */
	public void onOlympiadLose(L2PcInstance loser, CompetitionType type) {
		
	}
	
	/**
	 * On Move Finished event is triggered when an NPC finishes moving.
	 * @param npc the NPC who finished moving
	 */
	public void onMoveFinished(L2Npc npc) {
		
	}
	
	/**
	 * On Node Arrived event is triggered when a walker NPC arrives at a walking node.
	 * @param npc the NPC who arrived at the node
	 */
	public void onNodeArrived(L2Npc npc) {
		
	}
	
	/**
	 * On Route Finished event is triggered when a walker NPC finishes its route.
	 * @param npc the NPC who finished its route
	 */
	public void onRouteFinished(L2Npc npc) {
		
	}
	
	/**
	 * On NPC Hate event is triggered when an NPC determines if it can hate a playable.
	 * @param mob the NPC who may hate the playable
	 * @param player the player being considered for hate
	 * @param isSummon whether the hate is on the summon
	 * @return {@code true} if the NPC can hate the playable, {@code false} otherwise
	 */
	public boolean onNpcHate(L2Attackable mob, L2PcInstance player, boolean isSummon) {
		return true;
	}
	
	/**
	 * On Summon Spawn event is triggered when a summon spawns.
	 * @param summon the summon that spawned
	 */
	public void onSummonSpawn(L2Summon summon) {
		
	}
	
	/**
	 * On Summon Talk event is triggered when a summon is being talked.
	 * @param summon the summon that is talking
	 */
	public void onSummonTalk(L2Summon summon) {
		
	}
	
	/**
	 * On Can See Me event is triggered when an NPC checks if it can see a player.
	 * @param npc the NPC performing the check
	 * @param player the player being checked
	 * @return {@code true} if the NPC can see the player, {@code false} otherwise
	 */
	public boolean onCanSeeMe(L2Npc npc, L2PcInstance player) {
		return false;
	}
	
	/**
	 * Show an error message to the specified player.
	 * @param player the player to whom to send the error (must be a GM)
	 * @param t the {@link Throwable} to get the message/stacktrace from
	 * @return {@code false}
	 */
	public boolean showError(L2PcInstance player, Throwable t) {
		LOG.warn("There has been an error on the script!", t);
		if (t.getMessage() == null) {
			LOG.warn(t.getMessage()); // TODO(Zoey76): Test and eventually remove it if duplicated.
		}
		if ((player != null) && player.getAccessLevel().isGm()) {
			final var result = "<html><body><title>Script error</title>" + Util.getStackTrace(t) + "</body></html>";
			return showResult(player, result, null);
		}
		return false;
	}
	
	/**
	 * Show a message to the specified player.<br>
	 * <u><i>Concept:</i></u><br>
	 * Three cases are managed according to the value of the {@code res} parameter:<br>
	 * <ul>
	 * <li><u>{@code res} ends with ".htm" or ".html":</u> the contents of the specified HTML file are shown in a dialog window</li>
	 * <li><u>{@code res} starts with "&lt;html&gt;":</u> the contents of the parameter are shown in a dialog window</li>
	 * <li><u>all other cases :</u> the text contained in the parameter is shown in chat</li>
	 * </ul>
	 * @param player the player to whom to show the result
	 * @param npc npc to show the result for
	 * @param res the message to show to the player
	 * @return {@code false} if the message was sent, {@code true} otherwise
	 */
	protected boolean showResult(L2PcInstance player, String res, L2Npc npc) {
		if ((res == null) || res.isEmpty() || (player == null)) {
			return true;
		}
		
		if (res.endsWith(".htm") || res.endsWith(".html")) {
			showHtmlFile(player, res, npc);
		} else if (res.startsWith("<html")) {
			final NpcHtmlMessage npcReply = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0, res);
			npcReply.replace("%playername%", player.getName());
			player.sendPacket(npcReply);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		} else {
			player.sendMessage(res);
		}
		return false;
	}
	
	/**
	 * Loads all quest states and variables for the specified player.
	 * @param player the player who is entering the world
	 */
	public static void playerEnter(L2PcInstance player) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var invalidQuestData = con.prepareStatement("DELETE FROM character_quests WHERE charId = ? AND name = ?");
			var invalidQuestDataVar = con.prepareStatement("DELETE FROM character_quests WHERE charId = ? AND name = ? AND var = ?");
			var ps1 = con.prepareStatement("SELECT name, value FROM character_quests WHERE charId = ? AND var = ?")) {
			// Get list of quests owned by the player from database
			
			ps1.setInt(1, player.getObjectId());
			ps1.setString(2, "<state>");
			try (var rs = ps1.executeQuery()) {
				while (rs.next()) {
					// Get the ID of the quest and its state
					final var questId = rs.getString("name");
					final var statename = rs.getString("value");
					
					// Search quest associated with the ID
					final var q = QuestManager.getInstance().getQuest(questId);
					if (q == null) {
						LOG.warn("Unknown quest {} for player {}!", questId, player);
						if (general().autoDeleteInvalidQuestData()) {
							invalidQuestData.setInt(1, player.getObjectId());
							invalidQuestData.setString(2, questId);
							invalidQuestData.executeUpdate();
						}
						continue;
					}
					
					// Create a new QuestState for the player that will be added to the player's list of quests
					new QuestState(q, player, State.getStateId(statename));
				}
			}
			
			// Get list of quests owned by the player from the DB in order to add variables used in the quest.
			try (var ps2 = con.prepareStatement("SELECT name, var, value FROM character_quests WHERE charId = ? AND var <> ?")) {
				ps2.setInt(1, player.getObjectId());
				ps2.setString(2, "<state>");
				try (var rs = ps2.executeQuery()) {
					while (rs.next()) {
						final var questId = rs.getString("name");
						final var var = rs.getString("var");
						final var value = rs.getString("value");
						// Get the QuestState saved in the loop before
						final var q = QuestManager.getInstance().getQuest(questId);
						if (q == null) {
							continue;
						}
						
						final var qs = q.getQuestState(player, true);
						if (qs == null) {
							LOG.warn("Lost variable {} in quest {} for player {}!", var, questId, player.getName());
							if (general().autoDeleteInvalidQuestData()) {
								invalidQuestDataVar.setInt(1, player.getObjectId());
								invalidQuestDataVar.setString(2, questId);
								invalidQuestDataVar.setString(3, var);
								invalidQuestDataVar.executeUpdate();
							}
							continue;
						}
						// Add parameter to the quest
						qs.setInternal(var, value);
					}
				}
			}
		} catch (Exception ex) {
			LOG.warn("Could not insert char quest!", ex);
		}
		
		// events
		for (String name : QuestManager.getInstance().getScripts().keySet()) {
			player.processQuestEvent(name, "enter");
		}
	}
	
	/**
	 * Insert (or update) in the database variables that need to stay persistent for this quest after a reboot.<br>
	 * This function is for storage of values that do not relate to a specific player but are global for all characters.<br>
	 * For example, if we need to disable a quest-gatekeeper until a certain time (as is done with some grand-boss gatekeepers), we can save that time in the DB.
	 * @param var the name of the variable to save
	 * @param value the value of the variable
	 */
	public final void saveGlobalQuestVar(String var, String value) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("REPLACE INTO quest_global_data (quest_name,var,value) VALUES (?,?,?)")) {
			ps.setString(1, getName());
			ps.setString(2, var);
			ps.setString(3, value);
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not insert global quest variable!", ex);
		}
	}
	
	/**
	 * Read from the database a previously saved variable for this quest.<br>
	 * Due to performance considerations, this function should best be used only when the quest is first loaded.<br>
	 * Subclasses of this class can define structures into which these loaded values can be saved.<br>
	 * However, on-demand usage of this function throughout the script is not prohibited, only not recommended.<br>
	 * Values read from this function were entered by calls to "saveGlobalQuestVar".
	 * @param var the name of the variable to load
	 * @return the current value of the specified variable, or an empty string if the variable does not exist
	 */
	public final String loadGlobalQuestVar(String var) {
		String result = "";
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("SELECT value FROM quest_global_data WHERE quest_name = ? AND var = ?")) {
			ps.setString(1, getName());
			ps.setString(2, var);
			try (var rs = ps.executeQuery()) {
				if (rs.next()) {
					result = rs.getString(1);
				}
			}
		} catch (Exception ex) {
			LOG.warn("Could not load global quest variable!", ex);
		}
		return result;
	}
	
	/**
	 * Permanently delete from the database a global quest variable that was previously saved for this quest.
	 * @param var the name of the variable to delete
	 */
	public final void deleteGlobalQuestVar(String var) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("DELETE FROM quest_global_data WHERE quest_name = ? AND var = ?")) {
			ps.setString(1, getName());
			ps.setString(2, var);
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not delete global quest variable!", ex);
		}
	}
	
	/**
	 * Permanently delete from the database all global quest variables that were previously saved for this quest.
	 */
	public final void deleteAllGlobalQuestVars() {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("DELETE FROM quest_global_data WHERE quest_name = ?")) {
			ps.setString(1, getName());
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not delete global quest variables!", ex);
		}
	}
	
	/**
	 * Insert in the database the quest for the player.
	 * @param qs the {@link QuestState} object whose variable to insert
	 * @param var the name of the variable
	 * @param value the value of the variable
	 */
	public static void createQuestVarInDb(QuestState qs, String var, String value) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("INSERT INTO character_quests (charId,name,var,value) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE value=?")) {
			ps.setInt(1, qs.getPlayer().getObjectId());
			ps.setString(2, qs.getQuestName());
			ps.setString(3, var);
			ps.setString(4, value);
			ps.setString(5, value);
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not insert char quest!", ex);
		}
	}
	
	/**
	 * Update the value of the variable "var" for the specified quest in database
	 * @param qs the {@link QuestState} object whose variable to update
	 * @param var the name of the variable
	 * @param value the value of the variable
	 */
	public static void updateQuestVarInDb(QuestState qs, String var, String value) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("UPDATE character_quests SET value=? WHERE charId=? AND name=? AND var = ?")) {
			ps.setString(1, value);
			ps.setInt(2, qs.getPlayer().getObjectId());
			ps.setString(3, qs.getQuestName());
			ps.setString(4, var);
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not update char quest!", ex);
		}
	}
	
	/**
	 * Delete a variable of player's quest from the database.
	 * @param qs the {@link QuestState} object whose variable to delete
	 * @param var the name of the variable to delete
	 */
	public static void deleteQuestVarInDb(QuestState qs, String var) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("DELETE FROM character_quests WHERE charId=? AND name=? AND var=?")) {
			ps.setInt(1, qs.getPlayer().getObjectId());
			ps.setString(2, qs.getQuestName());
			ps.setString(3, var);
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Could not delete char quest!", ex);
		}
	}
	
	/**
	 * Delete from the database all variables and states of the specified quest state.
	 * @param qs the {@link QuestState} object whose variables to delete
	 * @param repeatable if {@code false}, the state variable will be preserved, otherwise it will be deleted as well
	 */
	public static void deleteQuestInDb(QuestState qs, boolean repeatable) {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement(repeatable ? QUEST_DELETE_FROM_CHAR_QUERY : QUEST_DELETE_FROM_CHAR_QUERY_NON_REPEATABLE_QUERY)) {
			ps.setInt(1, qs.getPlayer().getObjectId());
			ps.setString(2, qs.getQuestName());
			if (!repeatable) {
				ps.setString(3, "<state>");
			}
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.warn("Unable to delete char quest!", ex);
		}
	}
	
	/**
	 * Create a database record for the specified quest state.
	 * @param qs the {@link QuestState} object whose data to write in the database
	 */
	public static void createQuestInDb(QuestState qs) {
		createQuestVarInDb(qs, "<state>", State.getStateName(qs.getState()));
	}
	
	/**
	 * Update a quest state record of the specified quest state in database.
	 * @param qs the {@link QuestState} object whose data to update in the database
	 */
	public static void updateQuestInDb(QuestState qs) {
		updateQuestVarInDb(qs, "<state>", State.getStateName(qs.getState()));
	}
	
	/**
	 * @param player the player whose language settings to use in finding the html of the right language
	 * @return the default html for when no quest is available: "You are either not on a quest that involves this NPC.."
	 */
	public static String getNoQuestMsg(L2PcInstance player) {
		final String result = HtmCache.getInstance().getHtm(player.getHtmlPrefix(), "data/html/noquest.htm");
		if ((result != null) && (result.length() > 0)) {
			return result;
		}
		return DEFAULT_NO_QUEST_MSG;
	}
	
	/**
	 * @param player the player whose language settings to use in finding the html of the right language
	 * @return the default html for when no quest is already completed: "This quest has already been completed."
	 */
	public static String getAlreadyCompletedMsg(L2PcInstance player) {
		final String result = HtmCache.getInstance().getHtm(player.getHtmlPrefix(), "data/html/alreadycompleted.htm");
		if ((result != null) && !result.isEmpty()) {
			return result;
		}
		return DEFAULT_ALREADY_COMPLETED_MSG;
	}
	
	/**
	 * Binds the NPCs to the start NPC event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindStartNpc(int... npcIds) {
		setNpcQuestStartId(npcIds);
	}
	
	/**
	 * Binds the NPCs to the start NPC event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindStartNpc(Collection<Integer> npcIds) {
		setNpcQuestStartId(npcIds);
	}
	
	/**
	 * Binds the NPCs to the first talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindFirstTalk(int... npcIds) {
		setNpcFirstTalkId(event -> notifyFirstTalk(event.npc(), event.player()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the first talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindFirstTalk(Collection<Integer> npcIds) {
		setNpcFirstTalkId(event -> notifyFirstTalk(event.npc(), event.player()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Menu Selected event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindMenuSelected(int... npcIds) {
		registerConsumer((PlayerMenuSelected event) -> onMenuSelected(event), PLAYER_MENU_SELECTED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Manor Menu Selected event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindManorMenuSelected(int... npcIds) {
		registerConsumer((NpcManorBypass event) -> onManorMenuSelected(event), NPC_MANOR_BYPASS, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Quest Accepted event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindQuestAccepted(int... npcIds) {
		registerConsumer((PlayerQuestAccepted event) -> onQuestAccepted(event), PLAYER_QUEST_ACCEPTED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Learn Skill Requested event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindLearnSkillRequested(int... npcIds) {
		registerConsumer((PlayerLearnSkillRequested event) -> onLearnSkillRequested(event), PLAYER_LEARN_SKILL_REQUESTED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Skill Learned event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSkillLearned(int... npcIds) {
		registerConsumer((PlayerSkillLearned event) -> onSkillLearned(event), PLAYER_SKILL_LEARNED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Teleport Request event.
	 */
	public void bindTeleportRequest(int... npcIds) {
		registerConsumer((PlayerTeleportRequest event) -> onTeleportRequest(event), PLAYER_TELEPORT_REQUEST, NPC, npcIds);
	}
	
	/**
	 * Binds the Item to the notify when player speaks with it.
	 * @param itemIds the IDs of the Item
	 */
	public void bindItemBypass(int... itemIds) {
		registerConsumer((ItemBypass event) -> onItemEvent(event), ITEM_BYPASS, ITEM, itemIds);
	}
	
	/**
	 * Binds the Item to the item bypass event.
	 * @param itemIds the IDs of the Item
	 */
	public void bindItemBypass(Collection<Integer> itemIds) {
		registerConsumer((ItemBypass event) -> onItemEvent(event), ITEM_BYPASS, ITEM, itemIds);
	}
	
	/**
	 * Binds the Item to the item talk event.
	 * @param itemIds the IDs of the Item
	 */
	public void bindItemTalk(int... itemIds) {
		registerConsumer((ItemTalk event) -> onItemTalk(event), ITEM_TALK, ITEM, itemIds);
	}
	
	/**
	 * Binds the Item to the item talk event.
	 * @param itemIds the IDs of the Item
	 */
	public void addItemTalk(Collection<Integer> itemIds) {
		registerConsumer((ItemTalk event) -> onItemTalk(event), ITEM_TALK, ITEM, itemIds);
	}
	
	/**
	 * Binds the NPCs to the Attack event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindAttack(int... npcIds) {
		registerConsumer((AttackableAttack event) -> onAttack(event.target(), event.attacker(), event.damage(), event.isSummon(), event.skill()), ATTACKABLE_ATTACK, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Attack event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindAttack(Collection<Integer> npcIds) {
		registerConsumer((AttackableAttack event) -> onAttack(event.target(), event.attacker(), event.damage(), event.isSummon(), event.skill()), ATTACKABLE_ATTACK, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Kill event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindKill(int... npcIds) {
		registerConsumer((AttackableKill event) -> onKill(event.target(), event.attacker(), event.isSummon()), ATTACKABLE_KILL, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Kill event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindKill(Collection<Integer> npcIds) {
		registerConsumer((AttackableKill event) -> onKill(event.target(), event.attacker(), event.isSummon()), ATTACKABLE_KILL, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTalk(int... npcIds) {
		setNpcTalkId(npcIds);
	}
	
	/**
	 * Binds the NPCs to the Talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTalk(Collection<Integer> npcIds) {
		setNpcTalkId(npcIds);
	}
	
	/**
	 * Binds the NPCs to the Teleport event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTeleport(int... npcIds) {
		registerConsumer((NpcTeleport event) -> onTeleport(event.npc()), NPC_TELEPORT, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Teleport event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTeleport(Collection<Integer> npcIds) {
		registerConsumer((NpcTeleport event) -> onTeleport(event.npc()), NPC_TELEPORT, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Spawn event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSpawn(int... npcIds) {
		registerConsumer((NpcSpawn event) -> onSpawn(event.npc()), NPC_SPAWN, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Spawn event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSpawn(Collection<Integer> npcIds) {
		registerConsumer((NpcSpawn event) -> onSpawn(event.npc()), NPC_SPAWN, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Skill See event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSkillSee(int... npcIds) {
		registerConsumer((NpcSkillSee event) -> onSkillSee(event.npc(), event.caster(), event.skill(), event.targets(), event.isSummon()), NPC_SKILL_SEE, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Skill See event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSkillSee(Collection<Integer> npcIds) {
		registerConsumer((NpcSkillSee event) -> onSkillSee(event.npc(), event.caster(), event.skill(), event.targets(), event.isSummon()), NPC_SKILL_SEE, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Spell Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSpellFinished(int... npcIds) {
		registerConsumer((NpcSkillFinished event) -> onSpellFinished(event), NPC_SKILL_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Spell Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSpellFinished(Collection<Integer> npcIds) {
		registerConsumer((NpcSkillFinished event) -> onSpellFinished(event), NPC_SKILL_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Trap Action event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTrapAction(int... npcIds) {
		registerConsumer((OnTrapAction event) -> onTrapAction(event), TRAP_ACTION, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Trap Action event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindTrapAction(Collection<Integer> npcIds) {
		registerConsumer((OnTrapAction event) -> onTrapAction(event), TRAP_ACTION, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Faction Call event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindFactionCall(int... npcIds) {
		registerConsumer((FactionCall event) -> onFactionCall(event), FACTION_CALL, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Faction Call event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindFactionCall(Collection<Integer> npcIds) {
		registerConsumer((FactionCall event) -> onFactionCall(event), FACTION_CALL, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Aggro Range Enter event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindAggroRangeEnter(int... npcIds) {
		registerConsumer((AttackableAggroRangeEnter event) -> onAggroRangeEnter(event), ATTACKABLE_AGGRO_RANGE_ENTER, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Aggro Range Enter event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindAggroRangeEnter(Collection<Integer> npcIds) {
		registerConsumer((AttackableAggroRangeEnter event) -> onAggroRangeEnter(event), ATTACKABLE_AGGRO_RANGE_ENTER, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the See Creature event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSeeCreature(int... npcIds) {
		registerConsumer((NpcCreatureSee event) -> onSeeCreature(event.npc(), event.creature()), NPC_CREATURE_SEE, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the See Creature event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSeeCreature(Collection<Integer> npcIds) {
		registerConsumer((NpcCreatureSee event) -> onSeeCreature(event.npc(), event.creature()), NPC_CREATURE_SEE, NPC, npcIds);
	}
	
	/**
	 * Binds the Zones to the Enter Zone event.
	 * @param zoneIds the IDs of the zones
	 */
	public void bindEnterZone(int... zoneIds) {
		registerConsumer((CreatureZoneEnter event) -> onEnterZone(event.creature(), event.zone()), CREATURE_ZONE_ENTER, ZONE, zoneIds);
	}
	
	/**
	 * Binds the Zones to the Enter Zone event.
	 * @param zoneIds the IDs of the zones
	 */
	public void bindEnterZone(Collection<Integer> zoneIds) {
		registerConsumer((CreatureZoneEnter event) -> onEnterZone(event.creature(), event.zone()), CREATURE_ZONE_ENTER, ZONE, zoneIds);
	}
	
	/**
	 * Binds the Zones to the Exit Zone event.
	 * @param zoneIds the IDs of the zones
	 */
	public void bindExitZone(int... zoneIds) {
		registerConsumer((CreatureZoneExit event) -> onExitZone(event.creature(), event.zone()), CREATURE_ZONE_EXIT, ZONE, zoneIds);
	}
	
	/**
	 * Binds the Zones to the Exit Zone event.
	 * @param zoneIds the IDs of the zones
	 */
	public void bindExitZone(Collection<Integer> zoneIds) {
		registerConsumer((CreatureZoneExit event) -> onExitZone(event.creature(), event.zone()), CREATURE_ZONE_EXIT, ZONE, zoneIds);
	}
	
	/**
	 * Binds the NPCs to the Event Received event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindEventReceived(int... npcIds) {
		setNpcEventReceivedId(event -> onEventReceived(event), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Event Received event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindEventReceived(Collection<Integer> npcIds) {
		setNpcEventReceivedId(event -> onEventReceived(event), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Move Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindMoveFinished(int... npcIds) {
		registerConsumer((NpcMoveFinished event) -> onMoveFinished(event.npc()), NPC_MOVE_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Move Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindMoveFinished(Collection<Integer> npcIds) {
		registerConsumer((NpcMoveFinished event) -> onMoveFinished(event.npc()), NPC_MOVE_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Node Arrived event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindNodeArrived(int... npcIds) {
		registerConsumer((NpcMoveNodeArrived event) -> onNodeArrived(event.npc()), NPC_MOVE_NODE_ARRIVED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Node Arrived event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindNodeArrived(Collection<Integer> npcIds) {
		registerConsumer((NpcMoveNodeArrived event) -> onNodeArrived(event.npc()), NPC_MOVE_NODE_ARRIVED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Route Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindRouteFinished(int... npcIds) {
		registerConsumer((NpcMoveRouteFinished event) -> onRouteFinished(event.npc()), NPC_MOVE_ROUTE_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the Route Finished event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindRouteFinished(Collection<Integer> npcIds) {
		registerConsumer((NpcMoveRouteFinished event) -> onRouteFinished(event.npc()), NPC_MOVE_ROUTE_FINISHED, NPC, npcIds);
	}
	
	/**
	 * Binds the NPCs to the NPC Hate event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindNpcHate(int... npcIds) {
		addNpcHateId(event -> new TerminateReturn(!onNpcHate(event.npc(), event.player(), event.isSummon()), false, false), npcIds);
	}
	
	/**
	 * Binds the NPCs to the NPC Hate event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindNpcHate(Collection<Integer> npcIds) {
		addNpcHateId(event -> new TerminateReturn(!onNpcHate(event.npc(), event.player(), event.isSummon()), false, false), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Summon Spawn event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSummonSpawn(int... npcIds) {
		setPlayerSummonSpawnId(event -> onSummonSpawn(event.summon()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Summon Spawn event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSummonSpawn(Collection<Integer> npcIds) {
		setPlayerSummonSpawnId(event -> onSummonSpawn(event.summon()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Summon Talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSummonTalk(int... npcIds) {
		setPlayerSummonTalkId(event -> onSummonTalk(event.summon()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Summon Talk event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindSummonTalk(Collection<Integer> npcIds) {
		setPlayerSummonTalkId(event -> onSummonTalk(event.summon()), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Can See Me event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindCanSeeMe(int... npcIds) {
		addNpcHateId(event -> new TerminateReturn(!onCanSeeMe(event.npc(), event.player()), false, false), npcIds);
	}
	
	/**
	 * Binds the NPCs to the Can See Me event.
	 * @param npcIds the IDs of the NPCs
	 */
	public void bindCanSeeMe(Collection<Integer> npcIds) {
		addNpcHateId(event -> new TerminateReturn(!onCanSeeMe(event.npc(), event.player()), false, false), npcIds);
	}
	
	/**
	 * Binds the Quest to the Olympiad Match Finish event.
	 */
	public void bindOlympiadMatchFinish() {
		setOlympiadMatchResult(event -> onOlympiadMatchFinish(event.winner(), event.loser(), event.competitionType()));
	}
	
	public void setOnEnterWorld(boolean state) {
		if (state) {
			registerConsumer((PlayerLogin event) -> onEnterWorld(event.player()), PLAYER_LOGIN, GLOBAL);
		} else {
			getListeners().stream().filter(listener -> listener.getType() == PLAYER_LOGIN).forEach(AbstractEventListener::unregisterMe);
		}
	}
	
	/**
	 * Binds the Quest to the Tutorial event.
	 */
	public void bindTutorial() {
		registerConsumer((PlayerTutorial event) -> onTutorialEvent(event.player(), event.command()), PLAYER_TUTORIAL, GLOBAL);
	}
	
	/**
	 * Binds the Quest to the Tutorial Client event.
	 */
	public void bindTutorialClient() {
		registerConsumer((PlayerTutorialClientEvent event) -> onTutorialClientEvent(event.player(), event.event()), PLAYER_TUTORIAL_CLIENT_EVENT, GLOBAL);
	}
	
	/**
	 * Binds the Quest to the Tutorial Question Mark event.
	 */
	public void bindTutorialQuestionMark() {
		registerConsumer((PlayerTutorialQuestionMark event) -> onTutorialQuestionMark(event.player(), event.number()), PLAYER_TUTORIAL_QUESTION_MARK, GLOBAL);
	}
	
	/**
	 * Binds the Quest to the Tutorial Cmd event.
	 */
	public void bindTutorialCmd() {
		registerConsumer((PlayerTutorialCmd event) -> onTutorialCmd(event.player(), event.command()), PLAYER_TUTORIAL_CMD, GLOBAL);
	}
	
	/**
	 * Use this method to get a random party member from a player's party.<br>
	 * Useful when distributing rewards after killing an NPC.
	 * @param player this parameter represents the player whom the party will taken.
	 * @return {@code null} if {@code player} is {@code null}, {@code player} itself if the player does not have a party, and a random party member in all other cases
	 */
	public L2PcInstance getRandomPartyMember(L2PcInstance player) {
		if (player == null) {
			return null;
		}
		final L2Party party = player.getParty();
		if ((party == null) || (party.getMembers().isEmpty())) {
			return player;
		}
		return party.getMembers().get(Rnd.get(party.getMembers().size()));
	}
	
	/**
	 * Get a random party member with required cond value.
	 * @param player the instance of a player whose party is to be searched
	 * @param cond the value of the "cond" variable that must be matched
	 * @return a random party member that matches the specified condition, or {@code null} if no match was found
	 */
	public L2PcInstance getRandomPartyMember(L2PcInstance player, int cond) {
		return getRandomPartyMember(player, "cond", String.valueOf(cond));
	}
	
	/**
	 * Auxiliary function for party quests.<br>
	 * Note: This function is only here because of how commonly it may be used by quest developers.<br>
	 * For any variations on this function, the quest script can always handle things on its own.
	 * @param player the instance of a player whose party is to be searched
	 * @param var the quest variable to look for in party members. If {@code null}, it simply unconditionally returns a random party member
	 * @param value the value of the specified quest variable the random party member must have
	 * @return a random party member that matches the specified conditions or {@code null} if no match was found.<br>
	 *         If the {@code var} parameter is {@code null}, a random party member is selected without any conditions.<br>
	 *         The party member must be within a range of 1500 ingame units of the target of the reference player, or, if no target exists, within the same range of the player itself
	 */
	public L2PcInstance getRandomPartyMember(L2PcInstance player, String var, String value) {
		// if no valid player instance is passed, there is nothing to check...
		if (player == null) {
			return null;
		}
		
		// for null var condition, return any random party member.
		if (var == null) {
			return getRandomPartyMember(player);
		}
		
		// normal cases...if the player is not in a party, check the player's state
		QuestState temp;
		L2Party party = player.getParty();
		// if this player is not in a party, just check if this player instance matches the conditions itself
		if ((party == null) || (party.getMembers().isEmpty())) {
			temp = player.getQuestState(getName());
			if ((temp != null) && temp.isSet(var) && temp.get(var).equalsIgnoreCase(value)) {
				return player; // match
			}
			return null; // no match
		}
		
		// if the player is in a party, gather a list of all matching party members (possibly including this player)
		List<L2PcInstance> candidates = new ArrayList<>();
		// get the target for enforcing distance limitations.
		L2Object target = player.getTarget();
		if (target == null) {
			target = player;
		}
		
		for (L2PcInstance partyMember : party.getMembers()) {
			if (partyMember == null) {
				continue;
			}
			temp = partyMember.getQuestState(getName());
			if ((temp != null) && (temp.get(var) != null) && (temp.get(var)).equalsIgnoreCase(value) && partyMember.isInsideRadius(target, 1500, true, false)) {
				candidates.add(partyMember);
			}
		}
		// if there was no match, return null...
		if (candidates.isEmpty()) {
			return null;
		}
		// if a match was found from the party, return one of them at random.
		return candidates.get(Rnd.get(candidates.size()));
	}
	
	/**
	 * Auxiliary function for party quests.<br>
	 * Note: This function is only here because of how commonly it may be used by quest developers.<br>
	 * For any variations on this function, the quest script can always handle things on its own.
	 * @param player the player whose random party member is to be selected
	 * @param state the quest state required of the random party member
	 * @return {@code null} if nothing was selected or a random party member that has the specified quest state
	 */
	public L2PcInstance getRandomPartyMemberState(L2PcInstance player, int state) {
		// if no valid player instance is passed, there is nothing to check...
		if (player == null) {
			return null;
		}
		
		// normal cases...if the player is not in a party check the player's state
		QuestState temp;
		L2Party party = player.getParty();
		// if this player is not in a party, just check if this player instance matches the conditions itself
		if ((party == null) || (party.getMembers().isEmpty())) {
			temp = player.getQuestState(getName());
			if ((temp != null) && (temp.getState() == state)) {
				return player; // match
			}
			
			return null; // no match
		}
		
		// if the player is in a party, gather a list of all matching party members (possibly
		// including this player)
		List<L2PcInstance> candidates = new ArrayList<>();
		
		// get the target for enforcing distance limitations.
		L2Object target = player.getTarget();
		if (target == null) {
			target = player;
		}
		
		for (L2PcInstance partyMember : party.getMembers()) {
			if (partyMember == null) {
				continue;
			}
			temp = partyMember.getQuestState(getName());
			if ((temp != null) && (temp.getState() == state) && partyMember.isInsideRadius(target, 1500, true, false)) {
				candidates.add(partyMember);
			}
		}
		// if there was no match, return null...
		if (candidates.isEmpty()) {
			return null;
		}
		
		// if a match was found from the party, return one of them at random.
		return candidates.get(Rnd.get(candidates.size()));
	}
	
	/**
	 * Get a random party member from the specified player's party.<br>
	 * If the player is not in a party, only the player himself is checked.<br>
	 * The lucky member is chosen by standard loot roll rules -<br>
	 * each member rolls a random number, the one with the highest roll wins.
	 * @param player the player whose party to check
	 * @param npc the NPC used for distance and other checks (if {@link #checkPartyMember(L2PcInstance, L2Npc)} is overridden)
	 * @return the random party member or {@code null}
	 */
	public L2PcInstance getRandomPartyMember(L2PcInstance player, L2Npc npc) {
		if ((player == null) || !checkDistanceToTarget(player, npc)) {
			return null;
		}
		final L2Party party = player.getParty();
		L2PcInstance luckyPlayer = null;
		if (party == null) {
			if (checkPartyMember(player, npc)) {
				luckyPlayer = player;
			}
		} else {
			int highestRoll = 0;
			
			for (L2PcInstance member : party.getMembers()) {
				final int rnd = getRandom(1000);
				
				if ((rnd > highestRoll) && checkPartyMember(member, npc)) {
					highestRoll = rnd;
					luckyPlayer = member;
				}
			}
		}
		if ((luckyPlayer != null) && checkDistanceToTarget(luckyPlayer, npc)) {
			return luckyPlayer;
		}
		return null;
	}
	
	/**
	 * This method is called for every party member in {@link #getRandomPartyMember(L2PcInstance, L2Npc)}.<br>
	 * It is intended to be overridden by the specific quest implementations.
	 * @param player the player to check
	 * @param npc the NPC that was passed to {@link #getRandomPartyMember(L2PcInstance, L2Npc)}
	 * @return {@code true} if this party member passes the check, {@code false} otherwise
	 */
	public boolean checkPartyMember(L2PcInstance player, L2Npc npc) {
		return true;
	}
	
	/**
	 * Get a random party member from the player's party who has this quest at the specified quest progress.<br>
	 * If the player is not in a party, only the player himself is checked.
	 * @param player the player whose random party member state to get
	 * @param condition the quest progress step the random member should be at (-1 = check only if quest is started)
	 * @param playerChance how many times more chance does the player get compared to other party members (3 - 3x more chance).<br>
	 *            On retail servers, the killer usually gets 2-3x more chance than other party members
	 * @param target the NPC to use for the distance check (can be null)
	 * @return the {@link QuestState} object of the random party member or {@code null} if none matched the condition
	 */
	public QuestState getRandomPartyMemberState(L2PcInstance player, int condition, int playerChance, L2Npc target) {
		if ((player == null) || (playerChance < 1)) {
			return null;
		}
		
		QuestState qs = player.getQuestState(getName());
		if (!player.isInParty()) {
			if (!checkPartyMemberConditions(qs, condition, target)) {
				return null;
			}
			if (!checkDistanceToTarget(player, target)) {
				return null;
			}
			return qs;
		}
		
		final List<QuestState> candidates = new ArrayList<>();
		if (checkPartyMemberConditions(qs, condition, target)) {
			for (int i = 0; i < playerChance; i++) {
				candidates.add(qs);
			}
		}
		
		for (L2PcInstance member : player.getParty().getMembers()) {
			if (member == player) {
				continue;
			}
			
			qs = member.getQuestState(getName());
			if (checkPartyMemberConditions(qs, condition, target)) {
				candidates.add(qs);
			}
		}
		
		if (candidates.isEmpty()) {
			return null;
		}
		
		qs = candidates.get(getRandom(candidates.size()));
		if (!checkDistanceToTarget(qs.getPlayer(), target)) {
			return null;
		}
		return qs;
	}
	
	private boolean checkPartyMemberConditions(QuestState qs, int condition, L2Npc npc) {
		return ((qs != null) && ((condition == -1) ? qs.isStarted() : qs.isCond(condition)) && checkPartyMember(qs, npc));
	}
	
	private static boolean checkDistanceToTarget(L2PcInstance player, L2Npc target) {
		return ((target == null) || com.l2jserver.gameserver.util.Util.checkIfInRange(1500, player, target, true));
	}
	
	/**
	 * This method is called for every party member in {@link #getRandomPartyMemberState(L2PcInstance, int, int, L2Npc)} if/after all the standard checks are passed.<br>
	 * It is intended to be overridden by the specific quest implementations.<br>
	 * It can be used in cases when there are more checks performed than simply a quest condition check,<br>
	 * for example, if an item is required in the player's inventory.
	 * @param qs the {@link QuestState} object of the party member
	 * @param npc the NPC that was passed as the last parameter to {@link #getRandomPartyMemberState(L2PcInstance, int, int, L2Npc)}
	 * @return {@code true} if this party member passes the check, {@code false} otherwise
	 */
	public boolean checkPartyMember(QuestState qs, L2Npc npc) {
		return true;
	}
	
	/**
	 * Displays an HTML page to the specified player.<br>
	 * This method fetches the HTML content associated with the given file name and sends it as an {@link NpcHtmlMessage} to the player.
	 * @param player the player to whom the HTML content will be displayed
	 * @param fileName the name of the HTML file to be shown
	 */
	public void showPage(L2PcInstance player, String fileName) {
		showFHTML(player, fileName, Map.of());
	}
	
	public void showFHTML(L2PcInstance player, String fileName, Map<String, Object> mappings) {
		var content = getHtm(player.getHtmlPrefix(), fileName);
		if (content == null) {
			LOG.warn("Player {} requested non-existent file {}!", player, fileName);
			return;
		}
		
		for (var mapping : mappings.entrySet()) {
			content = content.replace(mapping.getKey(), mapping.getValue().toString());
		}
		
		final var npc = player.getLastFolkNPC();
		player.sendPacket(new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0, content));
	}
	
	/**
	 * Displays a quest-specific HTML file to the specified player.<br>
	 * The method fetches the HTML content, ensures it exists, and sends it as an {@link NpcQuestHtmlMessage} to the player.
	 * @param player the player to whom the HTML content will be displayed
	 * @param fileName the name of the HTML file to be shown
	 * @param questId the ID of the quest associated with the HTML content
	 */
	public void showQuestPage(L2PcInstance player, String fileName, int questId) {
		showQuestFHTML(player, fileName, questId, Map.of());
	}
	
	/**
	 * Displays a quest-specific HTML file to the specified player with dynamic mappings applied.<br>
	 * This method fetches the HTML content, replaces placeholders with the provided mappings, and sends the processed content as an {@link NpcQuestHtmlMessage} to the player.
	 * @param player the player to whom the HTML content will be displayed
	 * @param fileName the name of the HTML file to be shown
	 * @param questId the ID of the quest associated with the HTML content
	 * @param mappings a map of placeholders and their corresponding values to replace in the HTML content
	 */
	public void showQuestFHTML(L2PcInstance player, String fileName, int questId, Map<String, Object> mappings) {
		var content = getHtm(player.getHtmlPrefix(), fileName);
		if (content == null) {
			LOG.warn("Player {} requested non-existent file {}!", player, fileName);
			return;
		}
		
		for (var mapping : mappings.entrySet()) {
			content = content.replace(mapping.getKey(), mapping.getValue().toString());
		}
		
		final var npc = player.getLastFolkNPC();
		player.sendPacket(new NpcQuestHtmlMessage(npc != null ? npc.getObjectId() : 0, questId, content));
	}
	
	/**
	 * Displays a tutorial-specific HTML file to the specified player.<br>
	 * This method retrieves the HTML content based on the provided file name and the player's language preference.<br>
	 * If the file exists, the content is sent to the player as a {@link TutorialShowHtml} packet.
	 * @param player the player to whom the tutorial HTML content will be displayed
	 * @param fileName the name of the tutorial HTML file to be shown
	 */
	public void showTutorialHTML(L2PcInstance player, String fileName) {
		final var content = getHtm(player.getHtmlPrefix(), fileName);
		if (content != null) {
			player.sendPacket(new TutorialShowHtml(content));
		}
	}
	
	/**
	 * Sends an HTML file to the specified player, optionally associating it with an NPC.<br>
	 * If an NPC is provided, its object ID is included in the content.<br>
	 * If the file is related to a quest, it will be displayed as a quest window.
	 * @param player the player to send the HTML file to
	 * @param filename the name of the HTML file to show
	 * @param npc the NPC that is showing the HTML file, or {@code null} if none
	 * @return the contents of the HTML file that was sent to the player
	 */
	@Deprecated
	private String showHtmlFile(L2PcInstance player, String filename, L2Npc npc) {
		boolean questWindow = !filename.endsWith(".html");
		int questId = getId();
		
		// Retrieve the HTML content linked to the quest or NPC
		String content = getHtm(player.getHtmlPrefix(), filename);
		
		// Send content to the player if it's not empty
		if (content != null) {
			var npcObjId = 0;
			if (npc != null) {
				content = content.replace("%objectId%", String.valueOf(npc.getObjectId()));
				npcObjId = npc.getObjectId();
			}
			
			content = content.replace("%playername%", player.getName());
			
			if (questWindow && (questId > 0) && (questId < 20000) && (questId != 999)) {
				player.sendPacket(new NpcQuestHtmlMessage(npcObjId, questId, content));
			} else {
				player.sendPacket(new NpcHtmlMessage(npcObjId, content));
			}
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		
		return content;
	}
	
	/**
	 * @param prefix player's language prefix
	 * @param fileName the html file to get
	 * @return the HTML file contents
	 */
	public String getHtm(String prefix, String fileName) {
		final var path = fileName.startsWith("data/") ? fileName : getClass().getPackageName().replace('.', '/') + "/" + fileName;
		return HtmCache.getInstance().getHtm(prefix, path);
	}
	
	public int[] getRegisteredItemIds() {
		return _questItemIds;
	}
	
	/**
	 * Registers all items that have to be destroyed in case player abort the quest or finish it.
	 */
	public void registerQuestItems(int... items) {
		if (_questItemIds != null) {
			_questItemIds = IntStream.concat(Arrays.stream(_questItemIds), IntStream.of(items)).toArray();
		} else {
			_questItemIds = items;
		}
	}
	
	public void registerQuestItems(Set<Integer> itemIds) {
		registerQuestItems(itemIds.stream().mapToInt(i -> i).toArray());
	}
	
	/**
	 * Remove all quest items associated with this quest from the specified player's inventory.
	 * @param player the player whose quest items to remove
	 */
	public void removeRegisteredQuestItems(L2PcInstance player) {
		takeItems(player, -1, _questItemIds);
	}
	
	@Override
	public void setActive(boolean status) {
		// TODO: Implement.
	}
	
	@Override
	public boolean reload() {
		unload();
		
		// TODO(Zoey76): Implement.
		return false;
	}
	
	@Override
	public boolean unload() {
		return unload(true);
	}
	
	public boolean unload(boolean removeFromList) {
		saveGlobalData();
		// cancel all pending timers before reloading.
		// if timers ought to be restarted, the quest can take care of it
		// with its code (example: save global data indicating what timer must be restarted).
		if (_questTimers != null) {
			for (List<QuestTimer> timers : getQuestTimers().values()) {
				_readLock.lock();
				try {
					for (QuestTimer timer : timers) {
						timer.cancel();
					}
				} finally {
					_readLock.unlock();
				}
				timers.clear();
			}
			getQuestTimers().clear();
		}
		
		if (removeFromList) {
			return QuestManager.getInstance().removeScript(this) && super.unload();
		}
		return super.unload();
	}
	
	@Override
	public ScriptManager<?> getManager() {
		return QuestManager.getInstance();
	}
	
	/**
	 * Checks if this is a custom quest.
	 * @return {@code true} if the quest is a custom quest, {@code false} otherwise
	 */
	public boolean isCustom() {
		return customName != null;
	}
	
	/**
	 * Gets the quest's custom name.
	 * @return the custom name
	 */
	public String getCustomName() {
		return customName;
	}
	
	/**
	 * Gets the start conditions.
	 * @return the start conditions
	 */
	private Map<Predicate<L2PcInstance>, String> getStartConditions() {
		if (_startCondition == null) {
			synchronized (this) {
				if (_startCondition == null) {
					_startCondition = new LinkedHashMap<>(1);
				}
			}
		}
		return _startCondition;
	}
	
	/**
	 * Verifies if the player meets all the start conditions.
	 * @param player the player
	 * @return {@code true} if all conditions are met
	 */
	public boolean canStartQuest(L2PcInstance player) {
		if (_startCondition == null) {
			return true;
		}
		
		for (Predicate<L2PcInstance> cond : _startCondition.keySet()) {
			if (!cond.test(player)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Gets the HTML for the first starting condition not met.
	 * @param player the player
	 * @return the HTML
	 */
	public String getStartConditionHtml(L2PcInstance player) {
		if (_startCondition == null) {
			return null;
		}
		
		for (Entry<Predicate<L2PcInstance>, String> startRequirement : _startCondition.entrySet()) {
			if (!startRequirement.getKey().test(player)) {
				return startRequirement.getValue();
			}
		}
		return null;
	}
	
	/**
	 * Adds a predicate to the start conditions.
	 * @param questStartRequirement the predicate condition
	 * @param html the HTML to display if that condition is not met
	 */
	public void addCondStart(Predicate<L2PcInstance> questStartRequirement, String html) {
		getStartConditions().put(questStartRequirement, html);
	}
	
	/**
	 * Adds a minimum/maximum level start condition to the quest.
	 * @param minLevel the minimum player's level to start the quest
	 * @param maxLevel the maximum player's level to start the quest
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondLevel(int minLevel, int maxLevel, String html) {
		getStartConditions().put(p -> (p.getLevel() >= minLevel) && (p.getLevel() <= maxLevel), html);
	}
	
	/**
	 * Adds a minimum level start condition to the quest.
	 * @param minLevel the minimum player's level to start the quest
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondMinLevel(int minLevel, String html) {
		getStartConditions().put(p -> p.getLevel() >= minLevel, html);
	}
	
	/**
	 * Adds a minimum/maximum level start condition to the quest.
	 * @param maxLevel the maximum player's level to start the quest
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondMaxLevel(int maxLevel, String html) {
		getStartConditions().put(p -> p.getLevel() <= maxLevel, html);
	}
	
	/**
	 * Adds a race start condition to the quest.
	 * @param race the race
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondRace(Race race, String html) {
		getStartConditions().put(p -> p.getRace() == race, html);
	}
	
	/**
	 * Adds a not-race start condition to the quest.
	 * @param race the race
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondNotRace(Race race, String html) {
		getStartConditions().put(p -> p.getRace() != race, html);
	}
	
	/**
	 * Adds a quest completed start condition to the quest.
	 * @param name the quest name
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondCompletedQuest(String name, String html) {
		getStartConditions().put(p -> p.hasQuestState(name) && p.getQuestState(name).isCompleted(), html);
	}
	
	/**
	 * Adds a class ID start condition to the quest.
	 * @param classId the class ID
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondClassId(ClassId classId, String html) {
		getStartConditions().put(p -> p.getClassId() == classId, html);
	}
	
	/**
	 * Adds a not-class ID start condition to the quest.
	 * @param classId the class ID
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondNotClassId(ClassId classId, String html) {
		getStartConditions().put(p -> p.getClassId() != classId, html);
	}
	
	/**
	 * Adds a subclass active start condition to the quest.
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondIsSubClassActive(String html) {
		getStartConditions().put(L2PcInstance::isSubClassActive, html);
	}
	
	/**
	 * Adds a not-subclass active start condition to the quest.
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondIsNotSubClassActive(String html) {
		getStartConditions().put(p -> !p.isSubClassActive(), html);
	}
	
	/**
	 * Adds a category start condition to the quest.
	 * @param categoryType the category type
	 * @param html the HTML to display if the condition is not met
	 */
	public void addCondInCategory(CategoryType categoryType, String html) {
		getStartConditions().put(p -> p.isInCategory(categoryType), html);
	}
	
	public boolean haveMemo(L2PcInstance talker, int questId) {
		Quest quest = QuestManager.getInstance().getQuest(questId);
		return (quest != null) && talker.hasQuestState(quest.getName());
	}
	
	/**
	 * @param talker Player
	 * @param questId Quest Id
	 * @param flag 0 = false / 1 = true
	 */
	public void setOneTimeQuestFlag(L2PcInstance talker, int questId, int flag) {
		Quest quest = QuestManager.getInstance().getQuest(questId);
		if (quest != null) {
			quest.getQuestState(talker, true).setState(flag == 1 ? State.COMPLETED : State.STARTED);
		}
	}
	
	public int getOneTimeQuestFlag(L2PcInstance talker, int questId) {
		Quest quest = QuestManager.getInstance().getQuest(questId);
		if ((quest != null) && quest.getQuestState(talker, true).isCompleted()) {
			return 1;
		}
		return 0;
	}
	
	public static void playSound(L2PcInstance player, IAudio sound) {
		player.sendPacket(sound.getPacket());
	}
	
	public boolean isVisibleInQuestWindow() {
		return true;
	}
	
	@Override
	public String toString() {
		return _name + " (" + _questId + ")";
	}
}
