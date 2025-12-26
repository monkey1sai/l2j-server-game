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
package com.l2jserver.gameserver.config;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.Config.LoadType.MERGE;

import java.util.List;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Reloadable;

/**
 * Castle Configuration.
 * @author Zoey76
 * @version 2.6.1.0
 */
@Sources({
	"file:${L2J_HOME}/custom/game/config/castle.properties",
	"file:./config/castle.properties",
	"classpath:config/castle.properties"
})
@LoadPolicy(MERGE)
@HotReload(value = 1, unit = HOURS, type = ASYNC)
public interface CastleConfiguration extends Reloadable {
	
	@Key("SiegeHourList")
	List<Integer> getSiegeHourList();
	
	@Key("FunctionFeeDay7")
	long getFunctionFeeDay7();
	
	@Key("TeleportFunctionFeeLvl11")
	int getTeleportFunctionFeeLvl11();
	
	@Key("TeleportFunctionFeeLvl12")
	int getTeleportFunctionFeeLvl12();
	
	@Key("SupportFeeLvl15")
	int getSupportFeeLvl15();
	
	@Key("SupportFeeLvl18")
	int getSupportFeeLvl18();
	
	@Key("MpRegenerationFeeLvl18")
	int getMpRegenerationFeeLvl18();
	
	@Key("MpRegenerationFeeLvl20")
	int getMpRegenerationFeeLvl20();
	
	@Key("HpRegenerationFeeLvl25")
	int getHpRegenerationFeeLvl25();
	
	@Key("HpRegenerationFeeLvl30")
	int getHpRegenerationFeeLvl30();
	
	@Key("ExpRegenerationFeeLvl19")
	int getExpRegenerationFeeLvl19();
	
	@Key("ExpRegenerationFeeLvl20")
	int getExpRegenerationFeeLvl20();
	
	@Key("OuterDoorUpgradePriceLvl2")
	int getOuterDoorUpgradePriceLvl2();
	
	@Key("OuterDoorUpgradePriceLvl3")
	int getOuterDoorUpgradePriceLvl3();
	
	@Key("OuterDoorUpgradePriceLvl5")
	int getOuterDoorUpgradePriceLvl5();
	
	@Key("InnerDoorUpgradePriceLvl2")
	int getInnerDoorUpgradePriceLvl2();
	
	@Key("InnerDoorUpgradePriceLvl3")
	int getInnerDoorUpgradePriceLvl3();
	
	@Key("InnerDoorUpgradePriceLvl5")
	int getInnerDoorUpgradePriceLvl5();
	
	@Key("WallUpgradePriceLvl2")
	int getWallUpgradePriceLvl2();
	
	@Key("WallUpgradePriceLvl3")
	int getWallUpgradePriceLvl3();
	
	@Key("WallUpgradePriceLvl5")
	int getWallUpgradePriceLvl5();
	
	@Key("TrapUpgradePriceLvl1")
	int getTrapUpgradePriceLvl1();
	
	@Key("TrapUpgradePriceLvl2")
	int getTrapUpgradePriceLvl2();
	
	@Key("TrapUpgradePriceLvl3")
	int getTrapUpgradePriceLvl3();
	
	@Key("TrapUpgradePriceLvl4")
	int getTrapUpgradePriceLvl4();
	
	@Key("AllowRideWyvernAlways")
	boolean allowRideWyvernAlways();
	
	@Key("AllowRideWyvernDuringSiege")
	boolean allowRideWyvernDuringSiege();
	
	@Key("MpBuffFree")
	boolean mpBuffFree();
}