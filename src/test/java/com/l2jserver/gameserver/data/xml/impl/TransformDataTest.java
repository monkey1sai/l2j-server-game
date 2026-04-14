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
package com.l2jserver.gameserver.data.xml.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.l2jserver.gameserver.config.Configuration;
import com.l2jserver.gameserver.config.ServerConfiguration;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.stats.Stats;

/**
 * TransformData tests.
 * @author Zoey76
 */
@ExtendWith(MockitoExtension.class)
class TransformDataTest {
	
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private L2PcInstance player;
	
	@Mock
	private ServerConfiguration serverConfiguration;
	
	@Test
	void testParseMultipleLevelsCorrectly() throws Exception {
		try (var configurationMock = org.mockito.Mockito.mockStatic(Configuration.class)) {
			configurationMock.when(Configuration::server).thenReturn(serverConfiguration);
			when(serverConfiguration.getDatapackRoot()).thenReturn(new File("."));
			
			final String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<list>
					<transform id="1" type="COMBAT" can_swim="0" normal_attackable="1">
						<Male>
							<common>
								<base range="20" attackSpeed="300" attackType="SWORD" critRate="5" mAtk="5" pAtk="5" randomDamage="10" />
								<stats str="40" int="21" con="43" dex="30" wit="11" men="25" />
								<defense chest="31" legs="18" head="12" feet="7" gloves="8" underwear="3" cloak="1" />
								<magicDefense rear="9" lear="9" rfinger="5" lfinger="5" neck="13" />
								<collision radius="12" height="14.5" />
								<moving walk="30" run="125" waterWalk="50" waterRun="50" flyWalk="0" flyRun="0" unknownWalk="0" unknownRun="0" />
							</common>
							<actions>1 2 3</actions>
							<levels>
								<level val="1" levelMod="0.9" hp="100.0" mp="50.0" cp="80.0" hpRegen="2.0" mpRegen="1.0" cpRegen="1.5" />
								<level val="2" levelMod="0.95" hp="200.0" mp="100.0" cp="160.0" hpRegen="4.0" mpRegen="2.0" cpRegen="3.0" />
							</levels>
						</Male>
					</transform>
				</list>
				""";
			
			final var dbf = DocumentBuilderFactory.newInstance();
			final var db = dbf.newDocumentBuilder();
			final var doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			
			// TransformData is final, but we can call the protected constructor as we are in the same package.
			final var transformData = new TransformData();
			transformData.parseDocument(doc);
			
			final var transform = transformData.getTransform(1);
			assertThat(transform).isNotNull();
			
			// Test Level 1
			when(player.getLevel()).thenReturn(1);
			when(player.getAppearance().getSex()).thenReturn(false); // Male
			
			assertThat(transform.getStat(player, Stats.MAX_HP)).isEqualTo(100.0);
			assertThat(transform.getStat(player, Stats.MAX_MP)).isEqualTo(50.0);
			assertThat(transform.getStat(player, Stats.MAX_CP)).isEqualTo(80.0);
			assertThat(transform.getLevelMod(player)).isEqualTo(0.9);
			
			// Test Level 2
			when(player.getLevel()).thenReturn(2);
			
			assertThat(transform.getStat(player, Stats.MAX_HP)).isEqualTo(200.0);
			assertThat(transform.getStat(player, Stats.MAX_MP)).isEqualTo(100.0);
			assertThat(transform.getStat(player, Stats.MAX_CP)).isEqualTo(160.0);
			assertThat(transform.getLevelMod(player)).isEqualTo(0.95);
		}
	}
}
