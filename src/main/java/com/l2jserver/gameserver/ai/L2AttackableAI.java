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
package com.l2jserver.gameserver.ai;

import static com.l2jserver.gameserver.GameTimeController.TICKS_PER_SECOND;
import static com.l2jserver.gameserver.ai.CtrlIntention.AI_INTENTION_ACTIVE;
import static com.l2jserver.gameserver.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static com.l2jserver.gameserver.ai.CtrlIntention.AI_INTENTION_IDLE;
import static com.l2jserver.gameserver.ai.CtrlIntention.AI_INTENTION_MOVE_TO;
import static com.l2jserver.gameserver.config.Configuration.customs;
import static com.l2jserver.gameserver.config.Configuration.npc;
import static com.l2jserver.gameserver.model.effects.L2EffectType.HP;
import static com.l2jserver.gameserver.model.effects.L2EffectType.RESURRECTION;
import static com.l2jserver.gameserver.model.skills.targets.AffectScope.DEAD_PLEDGE;
import static com.l2jserver.gameserver.model.skills.targets.AffectScope.PARTY;
import static com.l2jserver.gameserver.model.skills.targets.AffectScope.PLEDGE;
import static com.l2jserver.gameserver.model.skills.targets.TargetType.SELF;

import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.GameTimeController;
import com.l2jserver.gameserver.GeoData;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.data.sql.impl.TerritoryTable;
import com.l2jserver.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.TeleportWhereType;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2FestivalMonsterInstance;
import com.l2jserver.gameserver.model.actor.instance.L2FriendlyMobInstance;
import com.l2jserver.gameserver.model.actor.instance.L2GrandBossInstance;
import com.l2jserver.gameserver.model.actor.instance.L2GuardInstance;
import com.l2jserver.gameserver.model.actor.instance.L2MonsterInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2RaidBossInstance;
import com.l2jserver.gameserver.model.actor.instance.L2RiftInvaderInstance;
import com.l2jserver.gameserver.model.actor.instance.L2StaticObjectInstance;
import com.l2jserver.gameserver.model.events.EventDispatcher;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.OnAttackableFactionCall;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.OnAttackableHate;
import com.l2jserver.gameserver.model.events.returns.TerminateReturn;
import com.l2jserver.gameserver.model.skills.AbnormalVisualEffect;
import com.l2jserver.gameserver.model.skills.Skill;
import com.l2jserver.gameserver.model.zone.ZoneId;
import com.l2jserver.gameserver.util.Util;

/**
 * Attackable creatures AI.
 * @author Zoey76
 */
public class L2AttackableAI extends L2CharacterAI {
	
	private static final Logger LOG = LoggerFactory.getLogger(L2AttackableAI.class);
	
	private static final int FEAR_TICKS = 5;
	
	private static final int RANDOM_WALK_RATE = 30;
	
	private static final int MAX_ATTACK_TIMEOUT = 120 * TICKS_PER_SECOND;
	
	/** The L2Attackable AI task executed every 1s (call onEvtThink method). */
	private Future<?> _aiTask;
	/** The delay after which the attacked is stopped. */
	private int _attackTimeout;
	/** The L2Attackable aggro counter. */
	private int _globalAggro;
	/** The flag used to indicate that a thinking action is in progress, to prevent recursive thinking. */
	private boolean _thinking;
	private int _chaosTime = 0;
	private int _lastBuffTick;
	// Fear parameters
	private int _fearTime;
	private Future<?> _fearTask = null;
	
	/**
	 * Constructor of L2AttackableAI.
	 * @param creature the creature
	 */
	public L2AttackableAI(L2Attackable creature) {
		super(creature);
		_attackTimeout = Integer.MAX_VALUE;
		_globalAggro = -10; // 10 seconds timeout of ATTACK after respawn
	}
	
	/**
	 * <B><U> Actor is a L2GuardInstance</U> :</B>
	 * <ul>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The L2PcInstance target has karma (=PK)</li>
	 * <li>The L2MonsterInstance target is aggressive</li>
	 * </ul>
	 * <B><U> Actor is a L2SiegeGuardInstance</U> :</B>
	 * <ul>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>A siege is in progress</li>
	 * <li>The L2PcInstance target isn't a Defender</li>
	 * </ul>
	 * <B><U> Actor is a L2FriendlyMobInstance</U> :</B>
	 * <ul>
	 * <li>The target isn't a Folk, a Door or another L2Npc</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The L2PcInstance target has karma (=PK)</li>
	 * </ul>
	 * <B><U> Actor is a L2MonsterInstance</U> :</B>
	 * <ul>
	 * <li>The target isn't a Folk, a Door or another L2Npc</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The actor is Aggressive</li>
	 * </ul>
	 * @param target The targeted L2Object
	 * @return True if the target is autoattackable (depends on the actor type).
	 */
	private boolean autoAttackCondition(L2Character target) {
		if (target == null) {
			return false;
		}
		
		// Check if the target isn't invulnerable
		if (target.isInvul()) {
			// However EffectInvincible requires to check GMs specially
			if (target.isPlayer() && target.isGM()) {
				return false;
			}
			if (target.isSummon() && ((L2Summon) target).getOwner().isGM()) {
				return false;
			}
		}
		
		// Check if the target isn't a Folk or a Door
		if (target.isDoor()) {
			return false;
		}
		
		// Check if the target isn't dead, is in the Aggro range and is at the same height
		final var actor = getActor();
		if (target.isAlikeDead() || ((target.isPlayable()) && !actor.isInsideRadius(target, actor.getAggroRange(), true, false))) {
			return false;
		}
		
		// Check if the target is a L2Playable
		if (target.isPlayable()) {
			// Check if the AI isn't a Raid Boss, can See Silent Moving players and the target isn't in silent move mode
			if (!(actor.isRaid()) && !(actor.canSeeThroughSilentMove()) && ((L2Playable) target).isSilentMovingAffected()) {
				return false;
			}
		}
		
		// Gets the player if there is any.
		final var player = target.getActingPlayer();
		if (player != null) {
			// Don't take the aggro if the GM has the access level below or equal to GM_DONT_TAKE_AGGRO
			if (player.isGM() && !player.getAccessLevel().canTakeAggro()) {
				return false;
			}
			
			// check if the target is within the grace period for JUST getting up from fake death
			if (player.isRecentFakeDeath()) {
				return false;
			}
			
			if (player.isInParty() && player.getParty().isInDimensionalRift()) {
				var riftType = player.getParty().getDimensionalRift().getType();
				var riftRoom = player.getParty().getDimensionalRift().getCurrentRoom();
				
				if ((actor instanceof L2RiftInvaderInstance) && !DimensionalRiftManager.getInstance().getRoom(riftType, riftRoom).checkIfInZone(actor.getX(), actor.getY(), actor.getZ())) {
					return false;
				}
			}
		}
		
		// Check if the actor is a L2GuardInstance
		if (actor instanceof L2GuardInstance) {
			// Check if the L2PcInstance target has karma (=PK)
			if ((player != null) && (player.getKarma() > 0)) {
				return GeoData.getInstance().canSeeTarget(actor, player); // Los Check
			}
			// Check if the L2MonsterInstance target is aggressive
			if ((target instanceof L2MonsterInstance monster) && npc().guardAttackAggroMob()) {
				return (monster.isAggressive() && GeoData.getInstance().canSeeTarget(actor, target));
			}
			
			return false;
		}
		
		if (actor instanceof L2FriendlyMobInstance) {
			// Check if the target isn't another L2Npc
			if (target instanceof L2Npc) {
				return false;
			}
			
			// Check if the L2PcInstance target has karma (=PK)
			if ((target instanceof L2PcInstance targetPlayer) && (targetPlayer.getKarma() > 0)) {
				return GeoData.getInstance().canSeeTarget(actor, target); // Los Check
			}
			return false;
		}
		
		if (target instanceof L2Attackable attackable) {
			if (!target.isAutoAttackable(actor)) {
				return false;
			}
			
			if (actor.isChaos() && actor.isInsideRadius(target, actor.getAggroRange(), false, false)) {
				if (attackable.isInMyClan(actor)) {
					return false;
				}
				// Los Check
				return GeoData.getInstance().canSeeTarget(actor, target);
			}
		}
		
		if (target instanceof L2Npc) {
			return false;
		}
		
		// depending on config, do not allow mobs to attack _new_ players in peace zones,
		// unless they are already following those players from outside the peace zone.
		if (!npc().mobAggroInPeaceZone() && target.isInsideZone(ZoneId.PEACE)) {
			return false;
		}
		
		if (actor.isChampion() && customs().championPassive()) {
			return false;
		}
		
		// Check if the actor is Aggressive
		return (actor.isAggressive() && GeoData.getInstance().canSeeTarget(actor, target));
	}
	
	public void startAITask() {
		// If not idle - create an AI task (schedule onEvtThink repeatedly)
		if ((_aiTask == null) && (_actor.isInActiveRegion())) {
			_aiTask = ThreadPoolManager.getInstance().scheduleAiAtFixedRate(this::onEvtThink, 1000, 1000);
		}
	}
	
	@Override
	public void stopAITask() {
		if (_aiTask != null) {
			_aiTask.cancel(false);
			_aiTask = null;
		}
		super.stopAITask();
	}
	
	/**
	 * Set the Intention of this L2CharacterAI and create an AI Task executed every 1s (call onEvtThink method) for this L2Attackable.<br>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If actor _knowPlayer isn't EMPTY, AI_INTENTION_IDLE will be change in AI_INTENTION_ACTIVE</B></FONT>
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention
	 * @param arg1 The second parameter of the Intention
	 */
	@Override
	synchronized void changeIntention(CtrlIntention intention, Object arg0, Object arg1) {
		if ((intention == AI_INTENTION_IDLE) || (intention == AI_INTENTION_ACTIVE)) {
			// Check if actor is not dead
			final var actor = getActor();
			if (!actor.isAlikeDead()) {
				// If its _knownPlayer isn't empty set the Intention to AI_INTENTION_ACTIVE
				if (!actor.getKnownList().getKnownPlayers().isEmpty()) {
					intention = AI_INTENTION_ACTIVE;
				} else {
					if (actor.getSpawn() != null) {
						final var loc = actor.getSpawn().getLocation(actor);
						final int range = npc().getMaxDriftRange();
						
						if (!actor.isInsideRadius(loc, range + range, true, false)) {
							intention = AI_INTENTION_ACTIVE;
						}
					}
				}
			}
			
			if (intention == AI_INTENTION_IDLE) {
				// Set the Intention of this L2AttackableAI to AI_INTENTION_IDLE
				super.changeIntention(AI_INTENTION_IDLE, null, null);
				
				// Stop AI task and detach AI from NPC
				stopAITask();
				
				// Cancel the AI
				_actor.detachAI();
				return;
			}
		}
		
		// Set the Intention of this L2AttackableAI to intention
		super.changeIntention(intention, arg0, arg1);
		
		// If not idle - create an AI task (schedule onEvtThink repeatedly)
		startAITask();
	}
	
	/**
	 * Manage the Attack Intention : Stop current Attack (if necessary), Calculate attack timeout, Start a new Attack and Launch Think Event.
	 * @param target The L2Character to attack
	 */
	@Override
	protected void onIntentionAttack(L2Character target) {
		// Calculate the attack timeout
		final var currentTick = GameTimeController.getInstance().getGameTicks();
		_attackTimeout = MAX_ATTACK_TIMEOUT + currentTick;
		
		// self and buffs
		if ((_lastBuffTick + (3 * TICKS_PER_SECOND)) < currentTick) {
			// TODO(Zoey76): Cast self buffs.
			LOG.warn("{} should have cast a random buff upon itself!", this);
			_lastBuffTick = currentTick;
		}
		
		// Manage the Attack Intention : Stop current Attack (if necessary), Start a new Attack and Launch Think Event
		super.onIntentionAttack(target);
	}
	
	@Override
	protected void onEvtAfraid(L2Character effector, boolean start) {
		if ((_fearTime > 0) && (_fearTask == null)) {
			_fearTask = ThreadPoolManager.getInstance().scheduleAiAtFixedRate(new FearTask(this, effector, start), 0, FEAR_TICKS, TimeUnit.SECONDS);
			_actor.startAbnormalVisualEffect(true, AbnormalVisualEffect.TURN_FLEE);
			return;
		}
		
		super.onEvtAfraid(effector, start);
		
		if ((_actor.isDead() || (_fearTime <= 0)) && (_fearTask != null)) {
			_fearTask.cancel(true);
			_fearTask = null;
			_actor.stopAbnormalVisualEffect(true, AbnormalVisualEffect.TURN_FLEE);
			setIntention(AI_INTENTION_IDLE);
		}
	}
	
	protected void thinkCast() {
		if (checkTargetLost(getCastTarget())) {
			setCastTarget(null);
			return;
		}
		if (maybeMoveToPawn(getCastTarget(), _actor.getMagicalAttackRange(_skill))) {
			return;
		}
		clientStopMoving(null);
		setIntention(AI_INTENTION_ACTIVE);
		_actor.doCast(_skill);
	}
	
	/**
	 * Manage AI standard thinks of a L2Attackable (called by onEvtThink). <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Update every 1s the _globalAggro counter to come close to 0</li>
	 * <li>If the actor is Aggressive and can attack, add all autoAttackable L2Character in its Aggro Range to its _aggroList, chose a target and order to attack it</li>
	 * <li>If the actor is a L2GuardInstance that can't attack, order to it to return to its home location</li>
	 * <li>If the actor is a L2MonsterInstance that can't attack, order to it to random walk (1/100)</li>
	 * </ul>
	 */
	protected void thinkActive() {
		final var actor = getActor();
		
		// Update every 1s the _globalAggro counter to come close to 0
		if (_globalAggro != 0) {
			if (_globalAggro < 0) {
				_globalAggro++;
			} else {
				_globalAggro--;
			}
		}
		
		// Add all autoAttackable L2Character in L2Attackable Aggro Range to its _aggroList with 0 damage and 1 hate
		// A L2Attackable isn't aggressive during 10s after its spawn because _globalAggro is set to -10
		if (_globalAggro >= 0) {
			// Get all visible objects inside its Aggro Range
			for (var obj : actor.getKnownList().getKnownObjects().values()) {
				if (!(obj instanceof L2Character target) || (obj instanceof L2StaticObjectInstance)) {
					continue;
				}
				
				// Check to see if this is a festival mob spawn.
				// If it is, then check to see if the aggro trigger is a festival participant...if so, move to attack it.
				if ((actor instanceof L2FestivalMonsterInstance) && (obj instanceof L2PcInstance targetPlayer)) {
					if (!(targetPlayer.isFestivalParticipant())) {
						continue;
					}
				}
				
				// For each L2Character check if the target is autoattackable, check aggression
				if (autoAttackCondition(target)) {
					if (target.isPlayable()) {
						final var term = EventDispatcher.getInstance().notifyEvent(new OnAttackableHate(getActor(), target.getActingPlayer(), target.isSummon()), getActor(), TerminateReturn.class);
						if ((term != null) && term.terminate()) {
							continue;
						}
					}
					
					// Get the hate level of the L2Attackable against this L2Character target contained in _aggroList
					var hating = actor.getHating(target);
					
					// Add the attacker to the L2Attackable _aggroList with 0 damage and 1 hate
					if (hating == 0) {
						actor.addDamageHate(target, 0, 0);
					}
				}
			}
			
			// Chose a target from its aggroList
			final var hated = actor.isConfused() ? getAttackTarget() : actor.getMostHated();
			// Order to the L2Attackable to attack the target
			if ((hated != null) && !actor.isCoreAIDisabled()) {
				// Get the hate level of the L2Attackable against this L2Character target contained in _aggroList
				final var aggro = actor.getHating(hated);
				if ((aggro + _globalAggro) > 0) {
					// Set the L2Character movement type to run and send Server->Client packet ChangeMoveType to all others L2PcInstance
					if (!actor.isRunning()) {
						actor.setRunning();
					}
					
					// Set the AI Intention to AI_INTENTION_ATTACK
					setIntention(AI_INTENTION_ATTACK, hated);
				}
				return;
			}
		}
		
		// Chance to forget attackers after some time
		if ((actor.getCurrentHp() == actor.getMaxHp()) && (actor.getCurrentMp() == actor.getMaxMp()) && !actor.getAttackByList().isEmpty() && (Rnd.nextInt(500) == 0)) {
			actor.clearAggroList();
			actor.getAttackByList().clear();
			if (actor instanceof L2MonsterInstance monster) {
				if (monster.hasMinions()) {
					monster.getMinionList().deleteReusedMinions();
				}
			}
		}
		
		// Check if the mob should not return to spawn point
		if (!actor.canReturnToSpawnPoint()) {
			return;
		}
		
		// Check if the actor is a L2GuardInstance
		if ((actor instanceof L2GuardInstance) && !actor.isWalker()) {
			// Order to the L2GuardInstance to return to its home location because there's no target to attack
			actor.returnHome();
		}
		
		// If this is a festival monster, then it remains in the same location.
		if (actor instanceof L2FestivalMonsterInstance) {
			return;
		}
		
		// Minions following leader
		final var leader = actor.getLeader();
		if ((leader != null) && !leader.isAlikeDead()) {
			final int offset;
			final var minRadius = 30;
			
			if (actor.isRaidMinion()) {
				offset = 500; // for Raids - need correction
			} else {
				offset = 200; // for normal minions - need correction :)
			}
			
			if (leader.isRunning()) {
				actor.setRunning();
			} else {
				actor.setWalking();
			}
			
			if (actor.calculateDistance(leader, false, true) > (offset * offset)) {
				int x1, y1, z1;
				x1 = Rnd.get(minRadius * 2, offset * 2); // x
				y1 = Rnd.get(x1, offset * 2); // distance
				y1 = (int) Math.sqrt((y1 * y1) - (x1 * x1)); // y
				if (x1 > (offset + minRadius)) {
					x1 = (leader.getX() + x1) - offset;
				} else {
					x1 = (leader.getX() - x1) + minRadius;
				}
				if (y1 > (offset + minRadius)) {
					y1 = (leader.getY() + y1) - offset;
				} else {
					y1 = (leader.getY() - y1) + minRadius;
				}
				
				z1 = leader.getZ();
				// Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet CharMoveToLocation (broadcast)
				moveTo(x1, y1, z1);
			} else if (Rnd.nextInt(RANDOM_WALK_RATE) == 0) {
				// TODO(Zoey76): Cast random buff.
				LOG.warn("{} should have cast a random buff upon itself!", this);
			}
		}
		// Order to the L2MonsterInstance to random walk (1/100)
		else if ((actor.getSpawn() != null) && (Rnd.nextInt(RANDOM_WALK_RATE) == 0) && !actor.isNoRndWalk()) {
			var x1 = 0;
			var y1 = 0;
			var z1 = 0;
			final int range = npc().getMaxDriftRange();
			
			if (actor.isWalker()) {
				return;
			}
			
			// TODO(Zoey76): Cast random buff.
			LOG.warn("{} should have cast a random buff upon itself!", this);
			
			// If me with random coord in territory - old method (for backward compatibility)
			if ((actor.getSpawn().getX() == 0) && (actor.getSpawn().getY() == 0) && (actor.getSpawn().getSpawnTerritory() == null)) {
				// Calculate a destination point in the spawn area
				final var location = TerritoryTable.getInstance().getRandomPoint(actor.getSpawn().getLocationId());
				if (location != null) {
					x1 = location.getX();
					y1 = location.getY();
					z1 = location.getZ();
				}
				
				// Calculate the distance between the current position of the L2Character and the target (x,y)
				var distance2 = actor.calculateDistance(x1, y1, 0, false, true);
				
				if (distance2 > ((range + range) * (range + range))) {
					actor.setisReturningToSpawnPoint(true);
					var delay = (float) Math.sqrt(distance2) / range;
					x1 = actor.getX() + (int) ((x1 - actor.getX()) / delay);
					y1 = actor.getY() + (int) ((y1 - actor.getY()) / delay);
				}
				
				// If me with random fixed coord, don't move (unless needs to return to spawnpoint)
				if (!actor.isReturningToSpawnPoint() && (TerritoryTable.getInstance().getProcMax(actor.getSpawn().getLocationId()) > 0)) {
					return;
				}
			} else {
				x1 = actor.getSpawn().getX(actor);
				y1 = actor.getSpawn().getY(actor);
				z1 = actor.getSpawn().getZ(actor);
				
				if (!actor.isInsideRadius(x1, y1, 0, range, false, false)) {
					actor.setisReturningToSpawnPoint(true);
				} else {
					var deltaX = Rnd.nextInt(range * 2); // x
					var deltaY = Rnd.get(deltaX, range * 2); // distance
					deltaY = (int) Math.sqrt((deltaY * deltaY) - (deltaX * deltaX)); // y
					x1 = (deltaX + x1) - range;
					y1 = (deltaY + y1) - range;
					z1 = actor.getZ();
				}
			}
			
			// Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet CharMoveToLocation (broadcast)
			final var moveLoc = GeoData.getInstance().moveCheck(actor.getX(), actor.getY(), actor.getZ(), x1, y1, z1, actor.getInstanceId());
			moveTo(moveLoc.getX(), moveLoc.getY(), moveLoc.getZ());
		}
	}
	
	/**
	 * Manage AI attack thinks of a L2Attackable (called by onEvtThink).<br>
	 * <b><u>Actions</u>:</b>
	 * <ul>
	 * <li>Update the attack timeout if actor is running</li>
	 * <li>If target is dead or timeout is expired, stop this attack and set the Intention to AI_INTENTION_ACTIVE</li>
	 * <li>Call all L2Object of its Faction inside the Faction Range</li>
	 * <li>Chose a target and order to attack it with magic skill or physical attack</li>
	 * </ul>
	 */
	protected void thinkAttack() {
		final var actor = getActor();
		if (actor.isCastingNow() || actor.isAttackingNow()) {
			return;
		}
		
		if (actor.isCoreAIDisabled()) {
			return;
		}
		
		if (actor.isOutOfControl()) {
			return;
		}
		
		final var mostHated = actor.getMostHated();
		if (mostHated == null) {
			setIntention(AI_INTENTION_ACTIVE);
			return;
		}
		
		setAttackTarget(mostHated);
		actor.setTarget(mostHated);
		
		// Immobilize condition
		if (actor.isMovementDisabled()) {
			movementDisable();
			return;
		}
		
		// Check if target is dead or if timeout is expired to stop this attack
		final var originalAttackTarget = getAttackTarget();
		if ((originalAttackTarget == null) || originalAttackTarget.isAlikeDead() || (_attackTimeout < GameTimeController.getInstance().getGameTicks())) {
			// Stop hating this target after the attack timeout or if target is dead
			actor.stopHating(originalAttackTarget);
			
			// Set the AI Intention to AI_INTENTION_ACTIVE
			setIntention(AI_INTENTION_ACTIVE);
			
			actor.setWalking();
			return;
		}
		
		if (actor.isSevenNpc()) {
			final var target = originalAttackTarget.getActingPlayer();
			if (SevenSigns.getInstance().isSealValidationPeriod() || SevenSigns.getInstance().isCompResultsPeriod()) {
				if (!target.isGM() && target.isIn7sDungeon() && (SevenSigns.getInstance().getPlayerCabal(target.getObjectId()) != SevenSigns.getInstance().getCabalHighestScore())) {
					target.teleToLocation(TeleportWhereType.TOWN);
					target.setIsIn7sDungeon(false);
					target.sendMessage("You have been teleported to the nearest town due to the beginning of the Seal Validation period.");
				}
			} else {
				if (!target.isGM() && target.isIn7sDungeon() && (SevenSigns.getInstance().getPlayerCabal(target.getObjectId()) == SevenSigns.CABAL_NULL)) {
					target.teleToLocation(TeleportWhereType.TOWN);
					target.setIsIn7sDungeon(false);
					target.sendMessage("You have been teleported to the nearest town because you have not signed for any cabal.");
				}
			}
		}
		
		final var collision = actor.getTemplate().getCollisionRadius();
		
		// Handle all L2Object of its Faction inside the Faction Range
		
		final var clans = getActor().getTemplate().getClans();
		if ((clans != null) && !clans.isEmpty()) {
			final var factionRange = actor.getTemplate().getClanHelpRange() + collision;
			// Go through all L2Object that belong to its faction
			try {
				for (var obj : actor.getKnownList().getKnownCharactersInRadius(factionRange)) {
					if (obj instanceof L2Npc called) {
						if (!getActor().getTemplate().isClan(called.getTemplate().getClans())) {
							continue;
						}
						
						// Check if the L2Object is inside the Faction Range of the actor
						if (called.hasAI()) {
							if ((Math.abs(originalAttackTarget.getZ() - called.getZ()) < 600) && actor.getAttackByList().contains(originalAttackTarget) && ((called.getAI()._intention == AI_INTENTION_IDLE) || (called.getAI()._intention == AI_INTENTION_ACTIVE))
								&& (called.getInstanceId() == actor.getInstanceId())) {
								if (originalAttackTarget.isPlayable()) {
									if (originalAttackTarget.isInParty() && originalAttackTarget.getParty().isInDimensionalRift()) {
										var riftType = originalAttackTarget.getParty().getDimensionalRift().getType();
										var riftRoom = originalAttackTarget.getParty().getDimensionalRift().getCurrentRoom();
										
										if ((actor instanceof L2RiftInvaderInstance) && !DimensionalRiftManager.getInstance().getRoom(riftType, riftRoom).checkIfInZone(actor.getX(), actor.getY(), actor.getZ())) {
											continue;
										}
									}
									
									// By default, when a faction member calls for help, attack the caller's attacker.
									// Notify the AI with EVT_AGGRESSION
									called.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, originalAttackTarget, 1);
									EventDispatcher.getInstance().notifyEventAsync(new OnAttackableFactionCall(called, getActor(), originalAttackTarget.getActingPlayer(), originalAttackTarget.isSummon()), called);
								} else if ((called instanceof L2Attackable attackable) && (getAttackTarget() != null) && (called.getAI()._intention != AI_INTENTION_ATTACK)) {
									attackable.addDamageHate(getAttackTarget(), 0, actor.getHating(getAttackTarget()));
									called.getAI().setIntention(AI_INTENTION_ATTACK, getAttackTarget());
								}
							}
						}
					}
				}
			} catch (Exception ex) {
				LOG.warn("There has been a problem trying to think the attack!", ex);
			}
		}
		
		// ------------------------------------------------------
		// In case many mobs are trying to hit from same place, move a bit, circling around the target
		// Note from Gnacik:
		// On l2js because of that sometimes mobs don't attack player only running
		// around player without any sense, so decrease chance for now
		final var combinedCollision = collision + mostHated.getTemplate().getCollisionRadius();
		if (!actor.isMovementDisabled() && (Rnd.nextInt(100) <= 3)) {
			for (var nearby : actor.getKnownList().getKnownObjects().values()) {
				if ((nearby instanceof L2Attackable) && actor.isInsideRadius(nearby, collision, false, false) && (nearby != mostHated)) {
					var newX = combinedCollision + Rnd.get(40);
					if (Rnd.nextBoolean()) {
						newX = mostHated.getX() + newX;
					} else {
						newX = mostHated.getX() - newX;
					}
					var newY = combinedCollision + Rnd.get(40);
					if (Rnd.nextBoolean()) {
						newY = mostHated.getY() + newY;
					} else {
						newY = mostHated.getY() - newY;
					}
					
					if (!actor.isInsideRadius(newX, newY, 0, collision, false, false)) {
						var newZ = actor.getZ() + 30;
						if (GeoData.getInstance().canMove(actor.getX(), actor.getY(), actor.getZ(), newX, newY, newZ, actor.getInstanceId())) {
							moveTo(newX, newY, newZ);
						}
					}
					return;
				}
			}
		}
		
		// Calculate Archer movement
		if (!actor.isMovementDisabled() && (actor.getTemplate().getBaseAttackRange() >= 700)) {
			if (Rnd.get(100) <= 15) {
				var distance2 = actor.calculateDistance(mostHated, false, true);
				if (Math.sqrt(distance2) <= (60 + combinedCollision)) {
					var posX = actor.getX();
					var posY = actor.getY();
					var posZ = actor.getZ() + 30;
					
					if (originalAttackTarget.getX() < posX) {
						posX = posX + 300;
					} else {
						posX = posX - 300;
					}
					
					if (originalAttackTarget.getY() < posY) {
						posY = posY + 300;
					} else {
						posY = posY - 300;
					}
					
					if (GeoData.getInstance().canMove(actor.getX(), actor.getY(), actor.getZ(), posX, posY, posZ, actor.getInstanceId())) {
						setIntention(AI_INTENTION_MOVE_TO, new Location(posX, posY, posZ, 0));
					}
					return;
				}
			}
		}
		
		// BOSS/Raid Minion Target Reconsider
		if (actor.isRaid() || actor.isRaidMinion()) {
			_chaosTime++;
			if (actor instanceof L2RaidBossInstance) {
				if (!((L2MonsterInstance) actor).hasMinions()) {
					if (_chaosTime > npc().getRaidChaosTime()) {
						if (Rnd.get(100) <= (100 - ((actor.getCurrentHp() * 100) / actor.getMaxHp()))) {
							aggroReconsider();
							_chaosTime = 0;
							return;
						}
					}
				} else {
					if (_chaosTime > npc().getRaidChaosTime()) {
						if (Rnd.get(100) <= (100 - ((actor.getCurrentHp() * 200) / actor.getMaxHp()))) {
							aggroReconsider();
							_chaosTime = 0;
							return;
						}
					}
				}
			} else if (actor instanceof L2GrandBossInstance) {
				if (_chaosTime > npc().getGrandChaosTime()) {
					var chaosRate = 100 - ((actor.getCurrentHp() * 300) / actor.getMaxHp());
					if (((chaosRate <= 10) && (Rnd.get(100) <= 10)) || ((chaosRate > 10) && (Rnd.get(100) <= chaosRate))) {
						aggroReconsider();
						_chaosTime = 0;
						return;
					}
				}
			} else {
				if (_chaosTime > npc().getMinionChaosTime()) {
					if (Rnd.get(100) <= (100 - ((actor.getCurrentHp() * 200) / actor.getMaxHp()))) {
						aggroReconsider();
						_chaosTime = 0;
						return;
					}
				}
			}
		}
		
		final var dist = actor.calculateDistance(mostHated, false, false);
		final var dist2 = (int) dist - collision;
		var range = actor.getPhysicalAttackRange() + combinedCollision;
		if (actor.getTemplate().getBaseAttackRange() >= 700) {
			range = actor.getTemplate().getBaseAttackRange(); // Base Bow Range NPC
		}
		
		if (mostHated.isMoving()) {
			range = range + 50;
			if (actor.isMoving()) {
				range = range + 50;
			}
		}
		
		// Core Skill Handling
		if (handleSkill(actor)) {
			return;
		}
		
		// Starts melee attack
		if ((dist2 > range) || !GeoData.getInstance().canSeeTarget(actor, mostHated)) {
			if (actor.isMovementDisabled()) {
				targetReconsider();
			} else {
				final var target = getAttackTarget();
				if (target != null) {
					if (target.isMoving()) {
						range -= 100;
					}
					moveToPawn(target, Math.max(range, 5));
				}
			}
			return;
		}
		
		clientStopMoving(null);
		_actor.doAttack(getAttackTarget());
	}
	
	private boolean handleSkill(L2Attackable actor) {
		final var collision = actor.getTemplate().getCollisionRadius();
		final var skills = new ArrayList<>(actor.getTemplate().getSkills().values());
		final var skill = skills.get(Rnd.get(skills.size()));
		
		if (!checkSkillCastConditions(actor, skill)) {
			return false;
		}
		
		// Suicide
		if (skill.isSuicideAttack()) {
			if (actor.getCurrentHp() / actor.getMaxHp() < 0.30) {
				final var mostHated = actor.getMostHated();
				if (Util.checkIfInRange(skill.getAffectRange(), getActor(), mostHated, false) && actor.hasSkillChance()) {
					// TODO(Zoey76): Cast suicide skill.
					LOG.warn("{} should have cast a suicide skill!", this);
					return true;
				}
			}
		}
		
		// Healing
		if (skill.hasEffectType(HP)) {
			// Self-healing
			if (skill.getTargetType() == SELF) {
				// TODO: Implement self-healing.
				LOG.warn("{} should have cast a healing skill upon itself!", this);
				return true;
			}
			
			// Master healing
			if (actor.isMinion()) {
				final var leader = actor.getLeader();
				if ((leader != null) && !leader.isDead() && (Rnd.get(100) > ((leader.getCurrentHp() / leader.getMaxHp()) * 100))) {
					if (!Util.checkIfInRange((skill.getCastRange() + collision + leader.getTemplate().getCollisionRadius()), actor, leader, false) && (skill.getAffectScope() != PARTY) && !actor.isMovementDisabled()) {
						moveToPawn(leader, skill.getCastRange() + collision + leader.getTemplate().getCollisionRadius());
						return false;
					}
					
					if (!GeoData.getInstance().canSeeTarget(actor, leader)) {
						return false;
					}
					
					clientStopMoving(null);
					final var target = actor.getTarget();
					actor.setTarget(leader);
					actor.doCast(skill);
					actor.setTarget(target);
					LOG.debug("{} used heal skill {} on leader {}", this, skill, leader);
					return true;
				}
			}
			
			// Party healing
			if (skill.getAffectScope() == PARTY) {
				clientStopMoving(null);
				actor.doCast(skill);
				return true;
			}
			
			// Clan healing
			if (skill.getAffectScope() == PLEDGE) {
				for (var obj : actor.getKnownList().getKnownCharactersInRadius(skill.getCastRange() + collision)) {
					if (!(obj instanceof L2Attackable attackable) || obj.isDead()) {
						continue;
					}
					
					if (!attackable.isInMyClan(actor)) {
						continue;
					}
					
					final var percentage = (attackable.getCurrentHp() / attackable.getMaxHp()) * 100;
					if (Rnd.get(100) < ((100 - percentage) / 10)) {
						if (GeoData.getInstance().canSeeTarget(actor, attackable)) {
							clientStopMoving(null);
							final var target = actor.getTarget();
							actor.setTarget(obj);
							actor.doCast(skill);
							actor.setTarget(target);
							LOG.debug("{} used heal skill {} on {}", this, skill, obj);
							return true;
						}
					}
				}
			}
		}
		
		// Resurrection
		if (skill.hasEffectType(RESURRECTION)) {
			if (actor.isMinion()) {
				// TODO: Resurrect leader.
				LOG.warn("{} should have cast a resurrection skill upon its leader!", this);
				return true;
			}
			
			if (skill.getAffectScope() == DEAD_PLEDGE) {
				// TODO: Resurrect clan member.
				LOG.warn("{} should have cast a resurrection skill upon a clan member!", this);
				return true;
			}
			
			if (skill.getAffectScope() == PARTY) {
				// TODO: Resurrect party member.
				LOG.warn("{} should have cast a resurrection skill upon a party member!", this);
				return true;
			}
		}
		
		// Target selection
		final var oldTarget = getTarget();
		var target = getAttackTarget();
		if (target == null) {
			target = actor.getMostHated();
		}
		setAttackTarget(target);
		
		// Ranged Skills
		if (skill.getCastRange() > 0) {
			if (!Util.checkIfInRange(skill.getCastRange(), getActor(), target, false)) {
				return false;
			}
			
			if (!actor.hasSkillChance()) {
				return false;
			}
			
			if (!GeoData.getInstance().canSeeTarget(actor, target)) {
				return false;
			}
			
			if (skill.isDebuff() && target.isAffectedBySkill(skill.getId())) {
				return false;
			}
			
			castSkill(skill, target, oldTarget);
			LOG.debug("{} used short range skill {} on {}", this, skill, actor.getTarget());
			return true;
		}
		
		// TODO(Zoey76): Handle non-ranged skills.
		LOG.warn("{} should have cast a non-ranged skill!", this);
		
		return false;
	}
	
	/**
	 * Cast a skill by this actor to a given target.
	 * @param skill the skill to cast
	 * @param target the target of the skill cast
	 * @param oldTarget the previous target to be restored
	 */
	private void castSkill(Skill skill, L2Object target, L2Object oldTarget) {
		clientStopMoving(null);
		_actor.setTarget(target);
		_actor.doCast(skill);
		_actor.setTarget(oldTarget);
	}
	
	private void movementDisable() {
		final var target = getAttackTarget();
		if (target == null) {
			return;
		}
		
		final var actor = getActor();
		if (actor.getTarget() == null) {
			actor.setTarget(target);
		}
		
		final var dist = actor.calculateDistance(target, false, false);
		final var range = actor.getPhysicalAttackRange() + actor.getTemplate().getCollisionRadius() + target.getTemplate().getCollisionRadius();
		
		// TODO(Zoey76): Try to cast a skill.
		LOG.warn("{} should have tried to cast a skill while movement is disabled!", this);
		
		// If cannot cast, try to attack.
		if ((dist <= range) && GeoData.getInstance().canSeeTarget(actor, target)) {
			_actor.doAttack(target);
			return;
		}
		
		// If cannot cast nor attack, find a new target.
		targetReconsider();
	}
	
	/**
	 * @param caster the caster
	 * @param skill the skill to check.
	 * @return {@code true} if the skill is available for casting {@code false} otherwise.
	 */
	private static boolean checkSkillCastConditions(L2Attackable caster, Skill skill) {
		if (skill == null) {
			return false;
		}
		
		if (caster.isCastingNow() && !skill.isSimultaneousCast()) {
			return false;
		}
		
		// Not enough MP.
		if (skill.getMpConsume2() >= caster.getCurrentMp()) {
			return false;
		}
		
		// Character is in "skill disabled" mode.
		if (caster.isSkillDisabled(skill)) {
			return false;
		}
		
		// If is a static skill and magic skill and character is muted or is a physical skill muted and character is physically muted.
		return skill.isStatic() || ((!skill.isMagic() || !caster.isMuted()) && !caster.isPhysicalMuted());
	}
	
	private void targetReconsider() {
		final var actor = getActor();
		final var mostHated = actor.getMostHated();
		if (actor.getHateList() != null) {
			for (var obj : actor.getHateList()) {
				if ((obj == null) || !GeoData.getInstance().canSeeTarget(actor, obj) || obj.isDead() || (obj != mostHated) || (obj == actor)) {
					continue;
				}
				try {
					double dist = actor.calculateDistance(obj, false, false);
					double dist2 = dist - actor.getTemplate().getCollisionRadius();
					int range = actor.getPhysicalAttackRange() + actor.getTemplate().getCollisionRadius() + obj.getTemplate().getCollisionRadius();
					if (obj.isMoving()) {
						dist2 = dist2 - 70;
					}
					
					if (dist2 <= range) {
						actor.addDamageHate(obj, 0, actor.getHating(mostHated));
						actor.setTarget(obj);
						setAttackTarget(obj);
						return;
					}
				} catch (NullPointerException e) {
					continue;
				}
			}
		}
		if (!(actor instanceof L2GuardInstance)) {
			for (var target : actor.getKnownList().getKnownObjects().values()) {
				if (!(target instanceof L2Character obj)) {
					continue;
				}
				
				if (!GeoData.getInstance().canSeeTarget(actor, obj) || obj.isDead() || (obj != mostHated) || (obj == actor) || (obj == getAttackTarget())) {
					continue;
				}
				
				if (obj instanceof L2PcInstance) {
					actor.addDamageHate(obj, 0, actor.getHating(mostHated));
					actor.setTarget(obj);
					setAttackTarget(obj);
				} else if (obj instanceof L2Attackable attackable) {
					if (actor.isChaos()) {
						if (attackable.isInMyClan(actor)) {
							continue;
						}
						
						actor.addDamageHate(obj, 0, actor.getHating(mostHated));
						actor.setTarget(obj);
						setAttackTarget(obj);
					}
				} else if (obj instanceof L2Summon) {
					actor.addDamageHate(obj, 0, actor.getHating(mostHated));
					actor.setTarget(obj);
					setAttackTarget(obj);
				}
			}
		}
	}
	
	private void aggroReconsider() {
		final var actor = getActor();
		final var mostHated = actor.getMostHated();
		if (actor.getHateList() != null) {
			var rand = Rnd.get(actor.getHateList().size());
			var count = 0;
			for (var creature : actor.getHateList()) {
				if (count < rand) {
					count++;
					continue;
				}
				
				if ((creature == null) || !GeoData.getInstance().canSeeTarget(actor, creature) || creature.isDead() || (creature == getAttackTarget()) || (creature == actor)) {
					continue;
				}
				
				try {
					actor.setTarget(getAttackTarget());
				} catch (NullPointerException e) {
					continue;
				}
				if (mostHated != null) {
					actor.addDamageHate(creature, 0, actor.getHating(mostHated));
				} else {
					actor.addDamageHate(creature, 0, 2000);
				}
				actor.setTarget(creature);
				setAttackTarget(creature);
				return;
			}
		}
		
		if (!(actor instanceof L2GuardInstance)) {
			for (var target : actor.getKnownList().getKnownObjects().values()) {
				if (!(target instanceof L2Character obj)) {
					continue;
				}
				
				if (!GeoData.getInstance().canSeeTarget(actor, obj) || obj.isDead() || (obj != mostHated) || (obj == actor)) {
					continue;
				}
				
				if (obj instanceof L2PcInstance) {
					actor.addDamageHate(obj, 0, actor.getHating(mostHated));
					actor.setTarget(obj);
					setAttackTarget(obj);
				} else if (obj instanceof L2Attackable attackable) {
					if (actor.isChaos()) {
						if (attackable.isInMyClan(actor)) {
							continue;
						}
						
						actor.addDamageHate(obj, 0, actor.getHating(mostHated));
						actor.setTarget(obj);
						setAttackTarget(obj);
					}
				} else if (obj instanceof L2Summon) {
					actor.addDamageHate(obj, 0, actor.getHating(mostHated));
					actor.setTarget(obj);
					setAttackTarget(obj);
				}
			}
		}
	}
	
	/**
	 * Manage AI thinking actions of a L2Attackable.
	 */
	@Override
	protected void onEvtThink() {
		// Check if the actor can't use skills and if a thinking action isn't already in progress
		if (_thinking || getActor().isAllSkillsDisabled()) {
			return;
		}
		
		// Start thinking action
		_thinking = true;
		
		try {
			// Manage AI thinks of a L2Attackable
			switch (getIntention()) {
				case AI_INTENTION_ACTIVE -> thinkActive();
				case AI_INTENTION_ATTACK -> thinkAttack();
				case AI_INTENTION_CAST -> thinkCast();
			}
		} catch (Exception ex) {
			LOG.warn("{} - onEvtThink() for {} failed!", this, getIntention(), ex);
		} finally {
			// Stop thinking action
			_thinking = false;
		}
	}
	
	/**
	 * Launch actions corresponding to the Event Attacked.<br>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Init the attack : Calculate the attack timeout, Set the _globalAggro to 0, Add the attacker to the actor _aggroList</li>
	 * <li>Set the L2Character movement type to run and send Server->Client packet ChangeMoveType to all others L2PcInstance</li>
	 * <li>Set the Intention to AI_INTENTION_ATTACK</li>
	 * </ul>
	 * @param attacker The L2Character that attacks the actor
	 */
	@Override
	protected void onEvtAttacked(L2Character attacker) {
		final var actor = getActor();
		if (attacker == actor) {
			return;
		}
		
		// Calculate the attack timeout
		_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeController.getInstance().getGameTicks();
		
		// Set the _globalAggro to 0 to permit attack even just after spawn
		if (_globalAggro < 0) {
			_globalAggro = 0;
		}
		
		// Add the attacker to the _aggroList of the actor
		actor.addDamageHate(attacker, 0, 1);
		
		// Set the L2Character movement type to run and send Server->Client packet ChangeMoveType to all others L2PcInstance
		if (!actor.isRunning()) {
			actor.setRunning();
		}
		
		// Set the Intention to AI_INTENTION_ATTACK
		if (getIntention() != AI_INTENTION_ATTACK) {
			setIntention(AI_INTENTION_ATTACK, attacker);
		} else if (actor.getMostHated() != getAttackTarget()) {
			setIntention(AI_INTENTION_ATTACK, attacker);
		}
		
		if (actor instanceof L2MonsterInstance master) {
			if (master.hasMinions()) {
				master.getMinionList().onAssist(actor, attacker);
			}
			
			master = master.getLeader();
			if ((master != null) && master.hasMinions()) {
				master.getMinionList().onAssist(actor, attacker);
			}
		}
		
		super.onEvtAttacked(attacker);
	}
	
	/**
	 * Launch actions corresponding to the Event Aggression.<br>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Add the target to the actor _aggroList or update hate if already present</li>
	 * <li>Set the actor Intention to AI_INTENTION_ATTACK (if actor is L2GuardInstance check if it isn't too far from its home location)</li>
	 * </ul>
	 * @param aggro The value of hate to add to the actor against the target
	 */
	@Override
	protected void onEvtAggression(L2Character target, long aggro) {
		final var actor = getActor();
		if (actor.isDead()) {
			return;
		}
		
		if (target != null) {
			// Add the target to the actor _aggroList or update hate if already present
			actor.addDamageHate(target, 0, aggro);
			
			// Set the actor AI Intention to AI_INTENTION_ATTACK
			if (getIntention() != AI_INTENTION_ATTACK) {
				// Set the L2Character movement type to run and send Server->Client packet ChangeMoveType to all others L2PcInstance
				if (!actor.isRunning()) {
					actor.setRunning();
				}
				
				setIntention(AI_INTENTION_ATTACK, target);
			}
			
			if (actor instanceof L2MonsterInstance master) {
				if (master.hasMinions()) {
					master.getMinionList().onAssist(actor, target);
				}
				
				master = master.getLeader();
				if ((master != null) && master.hasMinions()) {
					master.getMinionList().onAssist(actor, target);
				}
			}
		}
	}
	
	@Override
	protected void onIntentionActive() {
		// Cancel attack timeout
		_attackTimeout = Integer.MAX_VALUE;
		super.onIntentionActive();
	}
	
	public void setGlobalAggro(int value) {
		_globalAggro = value;
	}
	
	@Override
	public L2Attackable getActor() {
		return (L2Attackable) _actor;
	}
	
	public int getFearTime() {
		return _fearTime;
	}
	
	public void setFearTime(int fearTime) {
		_fearTime = fearTime;
	}
}
