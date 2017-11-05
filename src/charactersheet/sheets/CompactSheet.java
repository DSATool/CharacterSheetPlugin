/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package charactersheet.sheets;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import boxtable.cell.Cell;
import boxtable.cell.TableCell;
import boxtable.cell.TextCell;
import boxtable.common.HAlign;
import boxtable.common.Text;
import boxtable.event.EventType;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.resources.Settings;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class CompactSheet extends Sheet {
	private static final float fontSize = 10;

	private final IntegerProperty additionalArmorRows = new SimpleIntegerProperty(0);
	private final IntegerProperty additionalCloseCombatWeaponRows = new SimpleIntegerProperty(0);
	private final IntegerProperty additionalRangedWeaponRows = new SimpleIntegerProperty(0);
	private final IntegerProperty additionalTalentRows = new SimpleIntegerProperty(0);
	private final BooleanProperty showSpells = new SimpleBooleanProperty(true);
	private final IntegerProperty additionalSpellRows = new SimpleIntegerProperty(0);
	private final BooleanProperty groupBasis = new SimpleBooleanProperty(true);
	private final BooleanProperty markBasis = new SimpleBooleanProperty(false);

	private final BooleanProperty showTotalArmor = new SimpleBooleanProperty(
			!"Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart")));
	private final BooleanProperty showZoneArmor = new SimpleBooleanProperty(
			"Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart")));

	public CompactSheet() {
		super(842, false);
	}

	private void addAttributesTable(final PDDocument document) throws IOException {
		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		final JSONObject actualAttributes = hero.getObj("Eigenschaften");

		final int numAttributes = attributes.size() + 3;

		final Table table = new Table().setBorder(0, 0, 0, 0);

		for (int i = 0; i < numAttributes; ++i) {
			table.addColumn(new Column(291.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		for (final String attribute : attributes.keySet()) {
			table.addCells(attribute, actualAttributes.getObj(attribute).getIntOrDefault("Wert", 0));
		}

		table.addCells("SO", HeroUtil.getCurrentValue(hero.getObj("Basiswerte").getObj("Sozialstatus"), false));

		table.addCells("GS", HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Geschwindigkeit"), actualAttributes,
				hero.getObj("Basiswerte").getObj("Geschwindigkeit"), false));

		final int woundThreshold = Math.round(HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Wundschwelle"), actualAttributes,
				hero.getObj("Basiswerte").getObj("Wundschwelle"), false));
		table.addCells("WS", woundThreshold);

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addBiographyTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(453, 453, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(65, 65, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(65, 65, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));

		final JSONObject bio = hero.getObj("Biografie");

		table.addRow("Name: " + bio.getStringOrDefault("Vorname", "") + " " + bio.getStringOrDefault("Nachname", ""),
				new TextCell("Spieler: " + hero.getStringOrDefault("Spieler", "")).setColSpan(2));
		table.addRow("Rasse: " + SheetUtil.getRaceString(bio), new TextCell("AP: " + bio.getIntOrDefault("Abenteuerpunkte", 0)).setColSpan(2));
		table.addRow("Kultur: " + SheetUtil.getCultureString(bio),
				new TextCell("Geschlecht: " + ("weiblich".equals(bio.getString("Geschlecht")) ? "♀" : "♂")).setColSpan(2));
		table.addRow("Profession: " + SheetUtil.getProfessionString(hero, bio), "Größe: " + bio.getIntOrDefault("Größe", 0),
				"Gewicht: " + bio.getIntOrDefault("Gewicht", 0));

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addCloseCombatTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(92, 92, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(26, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Nahkampfwaffen", 1);
		final Cell tpTitle = SheetUtil.createTitleCell("TP", 1);
		final Cell atTitle = SheetUtil.createTitleCell("AT", 1);
		final Cell paTitle = SheetUtil.createTitleCell("PA", 1);
		final Cell tpkkTitle = ((TextCell) SheetUtil.createTitleCell("TP", 1)).addText("/").addText("KK").setEquallySpaced(true);
		final Cell wmTitle = SheetUtil.createTitleCell("WM", 1);
		final Cell iniTitle = SheetUtil.createTitleCell("INI", 1);
		final Cell dkTitle = SheetUtil.createTitleCell("DK", 1);
		final Cell bfTitle = SheetUtil.createTitleCell("BF", 1);
		table.addRow(nameTitle, tpTitle, atTitle, paTitle, tpkkTitle, wmTitle, iniTitle, dkTitle, bfTitle);

		final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");

		for (int i = 0; i < items.size(); ++i) {
			JSONObject item = items.getObj(i);
			final JSONObject baseWeapon = item;
			final JSONArray categories = item.getArrOrDefault("Kategorien", null);
			if (categories != null && categories.contains("Nahkampfwaffe")) {
				if (item.containsKey("Nahkampfwaffe")) {
					item = item.getObj("Nahkampfwaffe");
				}
				final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

				final String type = item.getStringOrDefault("Waffentyp:Primär",
						item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen")).getString(0));
				final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));
				final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArr("Waffenmeister"), type,
						item.getStringOrDefault("Typ", baseWeapon.getString("Typ")));

				final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

				final String at = Integer.toString(HeroUtil.getAT(hero, item, type, true, false, false));
				final Integer PA = HeroUtil.getPA(hero, item, type, false, false);
				final String pa = PA != null ? Integer.toString(PA) : "—";

				final JSONObject TPKKValues = item.getObjOrDefault("Trefferpunkte/Körperkraft", baseWeapon.getObj("Trefferpunkte/Körperkraft"));
				final Cell tpkk = new TextCell(Integer.toString(TPKKValues.getInt("Schwellenwert")
						+ (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schwellenwert", 0) : 0))).addText("/")
								.addText(Integer.toString(TPKKValues.getInt("Schadensschritte") + (weaponMastery != null
										? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schadensschritte", 0) : 0)))
								.setEquallySpaced(true);

				final Cell wm = new TextCell(Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0)
						+ (weaponMastery != null ? weaponMastery.getObj("Waffenmodifikatoren").getIntOrDefault("Attackemodifikator", 0) : 0))).addText("/")
								.addText(Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0)
										+ (weaponMastery != null ? weaponMastery.getObj("Waffenmodifikatoren").getIntOrDefault("Parademodifikator", 0) : 0)))
								.setEquallySpaced(true);

				final String ini = Util
						.getSignedIntegerString(item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0)
								+ (weaponMastery != null ? weaponMastery.getIntOrDefault("Initiative:Modifikator", 0) : 0)));

				final String distance = String.join("", item.getArrOrDefault("Distanzklassen", baseWeapon.getArr("Distanzklassen")).getStrings());

				final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
				String bf = null;
				if (BF != null) {
					bf = BF.toString();
				} else {
					bf = "—";
				}

				table.addRow(name, tp, at, pa, tpkk, wm, ini, distance, bf);
			}
		}

		for (int i = 0; i < additionalCloseCombatWeaponRows.get(); ++i) {
			table.addRow(" ", " ", " ", " ", "/", "/");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 321, 6, bottom.bottom - 5, 10, 10);
		}
	}

	private void addDerivedValuesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		for (int i = 0; i < 4; ++i) {
			table.addColumn(new Column(583f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		for (int i = 0; i < 5; ++i) {
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		final JSONObject attributes = hero.getObj("Eigenschaften");

		final JSONObject derivedValues = ResourceManager.getResource("data/Basiswerte");
		String[] derivedNames = new String[] { "Attacke-Basis", "Parade-Basis", "Fernkampf-Basis", "Initiative-Basis" };
		String[] derivedLabels = new String[] { "AT-Basis", "PA-Basis", "FK-Basis", "INI-Basis" };
		for (int i = 0; i < 4; ++i) {
			final String derivedName = derivedNames[i];
			final JSONObject derivedValue = derivedValues.getObj(derivedName);
			table.addCells(derivedLabels[i], HeroUtil.deriveValue(derivedValue, attributes, hero.getObj("Basiswerte").getObj(derivedName), false));
		}

		derivedNames = new String[] { "Lebensenergie", "Ausdauer", "Magieresistenz", "Astralenergie", "Karmaenergie" };
		derivedLabels = new String[] { "LeP", "AuP", "MR", "AsP", "KaP" };
		for (int i = 0; i < 5; ++i) {
			final String derivedName = derivedNames[i];
			final JSONObject derivedValue = derivedValues.getObj(derivedName);
			final JSONObject actualValue = hero.getObj("Basiswerte").getObjOrDefault(derivedName, null);
			String value;
			if (actualValue != null) {
				value = Integer.toString(HeroUtil.deriveValue(derivedValue, attributes, actualValue, false));
			} else {
				value = "—";
			}
			table.addCells(derivedLabels[i], value);
		}

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addInfightTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(34, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(43, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));

		SheetUtil.addTitle(table, "Waffenloser Kampf");

		table.addRow("Talent", "TP", "AT", "PA");

		final int TPKKModifier = (HeroUtil.getCurrentValue(hero.getObj("Eigenschaften").getObj("KK"), false) - 10) / 3;

		final String tp = "1W" + (TPKKModifier == 0 ? "" : Util.getSignedIntegerString(TPKKModifier)) + "(A)";
		final String at1 = Integer.toString(HeroUtil.getAT(hero, null, "Raufen", true, false, false));
		final String pa1 = Integer.toString(HeroUtil.getPA(hero, null, "Raufen", false, false));

		table.addRow("Raufen", tp, at1, pa1);

		final String at2 = Integer.toString(HeroUtil.getAT(hero, null, "Ringen", true, false, false));
		final String pa2 = Integer.toString(HeroUtil.getPA(hero, null, "Ringen", false, false));

		table.addRow("Ringen", tp, at2, pa2);

		bottom.bottom = table.render(document, 117, 332, bottom.bottom - 5, 10, 10);
	}

	private void addProsAndConsTable(final PDDocument document) throws IOException {
		final Table table = new Table();
		table.addColumn(new Column(583, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Vor- und Nachteile");

		for (final String pOrC : new String[] { "Vorteile", "Nachteile" }) {
			final StringBuilder prosAndCons = new StringBuilder();
			final JSONObject prosOrCons = ResourceManager.getResource("data/" + pOrC);
			final JSONObject actual = hero.getObj(pOrC);

			final Map<String, JSONObject> actualProsOrCons = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
			for (final String proOrConName : actual.keySet()) {
				actualProsOrCons.put(proOrConName, prosOrCons.getObj(proOrConName));
			}

			for (final String proOrConName : actualProsOrCons.keySet()) {
				final JSONObject proOrCon = actualProsOrCons.get(proOrConName);
				if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
					final JSONArray proOrConList = actual.getArr(proOrConName);
					for (int i = 0; i < proOrConList.size(); ++i) {
						final JSONObject actualProOrCon = proOrConList.getObj(i);
						prosAndCons.append(DSAUtil.printProOrCon(actualProOrCon, proOrConName, proOrCon, true));
						prosAndCons.append("; ");
					}
				} else {
					prosAndCons.append(DSAUtil.printProOrCon(actual.getObj(proOrConName), proOrConName, proOrCon, true));
					prosAndCons.append("; ");
				}
			}
			if (prosAndCons.length() > 2) {
				prosAndCons.delete(prosAndCons.length() - 2, prosAndCons.length());
			}
			table.addRow(new TextCell(prosAndCons.toString()).setDrawRows(true));
		}

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addRangedCombatTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(92, 92, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(12.3f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(12.3f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(12.3f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(12.3f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(12.3f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(10.9f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(10.9f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(10.9f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(10.9f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(10.9f, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Fernkampfwaffen", 1);
		final Cell tpTitle = SheetUtil.createTitleCell("TP", 1);
		final Cell atTitle = SheetUtil.createTitleCell("AT", 1);
		final Cell loadTitle = SheetUtil.createTitleCell("Lad.", 1);
		final Cell distanceTitle = SheetUtil.createTitleCell("Entfernung", 5);
		final Cell tpdistanceTitle = SheetUtil.createTitleCell("TP+", 5);
		final Cell numTitle = SheetUtil.createTitleCell("Anz", 1);
		table.addRow(nameTitle, tpTitle, atTitle, loadTitle, distanceTitle, tpdistanceTitle, numTitle);

		final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
		for (int i = 0; i < items.size(); ++i) {
			JSONObject item = items.getObj(i);
			final JSONObject baseWeapon = item;
			final JSONArray categories = item.getArrOrDefault("Kategorien", null);
			if (categories != null && categories.contains("Fernkampfwaffe")) {
				if (item.containsKey("Fernkampfwaffe")) {
					item = item.getObj("Fernkampfwaffe");
				}

				final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

				final String type = item.getStringOrDefault("Waffentyp:Primär",
						item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen")).getString(0));

				final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

				final String at = Integer.toString(HeroUtil.getAT(hero, item, type, false, false, false));

				final String load = Integer.toString(HeroUtil.getLoadTime(hero, item, type));

				int j = 0;

				final TextCell[] distances = new TextCell[5];
				for (final String distance : new String[] { "Sehr Nah", "Nah", "Mittel", "Weit", "Extrem Weit" }) {
					final int dist = HeroUtil.getDistance(hero, item, type, distance);
					distances[j] = new TextCell(dist != Integer.MIN_VALUE ? Integer.toString(dist) : "—");
					++j;
				}

				j = 0;
				final TextCell[] tpdistance = new TextCell[5];

				final JSONObject distanceTPs = item.getObjOrDefault("Trefferpunkte/Entfernung", baseWeapon.getObj("Trefferpunkte/Entfernung"));
				for (final String distance : new String[] { "Sehr Nah", "Nah", "Mittel", "Weit", "Extrem Weit" }) {
					final int dist = distanceTPs.getIntOrDefault(distance, Integer.MIN_VALUE);
					tpdistance[j] = new TextCell(dist != Integer.MIN_VALUE ? Util.getSignedIntegerString(distanceTPs.getInt(distance)) : "—");
					++j;
				}

				String num = "1";
				final String ammunitionType = item.getStringOrDefault("Geschoss:Typ", baseWeapon.getString("Geschoss:Typ"));
				if ("Pfeile".equals(ammunitionType) || "Bolzen".equals(ammunitionType)) {
					final JSONObject ammunitionTypes = ResourceManager.getResource("data/Geschosstypen");
					final JSONObject ammunition = item.getObjOrDefault("Munition", baseWeapon.getObj("Munition"));
					int amount = 0;
					for (final String typeName : ammunitionTypes.keySet()) {
						amount += ammunition.getObj(typeName).getIntOrDefault("Aktuell", 0);
					}
					num = Integer.toString(amount);
				} else {
					final JSONObject amount = item.getObjOrDefault("Anzahl", baseWeapon.getObjOrDefault("Anzahl", null));
					if (amount != null) {
						num = Integer.toString(amount.getIntOrDefault("Gesamt", 1));
					}
				}

				table.addRow(name, tp, at, load, distances[0], distances[1], distances[2], distances[3], distances[4], tpdistance[0], tpdistance[1],
						tpdistance[2],
						tpdistance[3], tpdistance[4], num);
			}
		}

		for (int i = 0; i < additionalRangedWeaponRows.get(); ++i) {
			table.addRow("");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 321, 6, bottom.bottom - 5, 10, 10);
		}
	}

	private void addSpecialSkillsTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(583, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Sonderfertigkeiten");

		final StringBuilder skillsString = new StringBuilder();

		final JSONObject regularSkills = ResourceManager.getResource("data/Sonderfertigkeiten");
		final JSONObject rituals = ResourceManager.getResource("data/Rituale");
		final JSONObject shamanRituals = ResourceManager.getResource("data/Schamanenrituale");
		final JSONObject liturgies = ResourceManager.getResource("data/Liturgien");
		final JSONObject actualSkills = hero.getObj("Sonderfertigkeiten");

		final JSONObject[] skillGroups = new JSONObject[regularSkills.size() + rituals.size() + 2];
		int i = 0;
		for (final String skillGroupName : regularSkills.keySet()) {
			skillGroups[i] = regularSkills.getObj(skillGroupName);
			++i;
		}
		for (final String skillGroupName : rituals.keySet()) {
			skillGroups[i] = rituals.getObj(skillGroupName);
			++i;
		}
		skillGroups[i] = shamanRituals;
		++i;
		skillGroups[i] = liturgies;

		final Map<String, JSONObject> skills = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
		for (final String skillName : actualSkills.keySet()) {
			for (final JSONObject skillGroup : skillGroups) {
				if (skillGroup.containsKey(skillName)) {
					skills.put(skillName, skillGroup.getObj(skillName));
					break;
				}
			}
		}

		for (final String skillName : skills.keySet()) {
			final JSONObject skill = skills.get(skillName);

			if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
				final JSONArray skillList = actualSkills.getArr(skillName);
				for (int j = 0; j < skillList.size(); ++j) {
					final JSONObject actualSkill = skillList.getObj(j);
					skillsString.append(DSAUtil.printProOrCon(actualSkill, skillName, skill, false));
					skillsString.append("; ");
				}
			} else {
				skillsString.append(skillName);
				skillsString.append("; ");
			}
		}

		if (skillsString.length() > 2) {
			skillsString.delete(skillsString.length() - 2, skillsString.length());
		}
		table.addRow(new TextCell(skillsString.toString()).setDrawRows(true));

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addSpellTable(final PDDocument document) throws IOException {
		final Table spellTable = new Table().setFiller(SheetUtil.stripe());
		spellTable.addColumn(new Column(289, FontManager.serif, fontSize, HAlign.LEFT));
		spellTable.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		spellTable.addColumn(new Column(289, FontManager.serif, fontSize, HAlign.CENTER));

		SheetUtil.addTitle(spellTable, "Zauber");

		Table table = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);
		table.addColumn(new Column(162, 162, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(19, 19, FontManager.serif, 4, 8, HAlign.CENTER));
		table.addColumn(new Column(57, 57, FontManager.serif, 4, 8, HAlign.CENTER));
		table.addColumn(new Column(28, 28, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(23, FontManager.serif, 8, HAlign.CENTER));

		final JSONObject spells = ResourceManager.getResource("data/Zauber");
		final JSONObject actualSpells = hero.getObj("Zauber");

		int rows = additionalSpellRows.get() + 1;

		for (final String spellName : actualSpells.keySet()) {
			final JSONObject spell = HeroUtil.findTalent(spellName)._1;
			final JSONObject actualSpell = actualSpells.getObj(spellName);
			for (final String repName : actualSpell.keySet()) {
				if (spell.containsKey("Auswahl") || spell.containsKey("Freitext")) {
					rows += actualSpell.getArr(repName).size();
				} else {
					++rows;
				}
			}
		}

		if (rows == 1) return;

		int index = 0;

		final Cell nameTitle = new TextCell("Zauber", FontManager.serifBold, 0, 8);
		final Cell repTitle = new TextCell("Rep.", FontManager.serifBold, 0, 8);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, 0, 8);
		final Cell traitTitle = new TextCell("Merk.", FontManager.serifBold, 0, 8).setHAlign(HAlign.CENTER);
		final Cell valueTitle = new TextCell("ZfW", FontManager.serifBold, 0, 8);

		table.addRow(nameTitle, repTitle, challengeTitle, traitTitle, valueTitle);

		final Map<Tuple<String, String>, JSONValue> actual = new TreeMap<>((s1, s2) -> {
			final int name = SheetUtil.comparator.compare(s1._1, s2._1);
			if (name == 0) return SheetUtil.comparator.compare(s1._2, s2._2);
			return name;
		});
		for (final String spellName : actualSpells.keySet()) {
			final JSONObject spell = spells.getObj(spellName);
			final JSONObject actualSpell = actualSpells.getObj(spellName);

			for (final String repName : actualSpell.keySet()) {
				if (spell.containsKey("Auswahl") || spell.containsKey("Freitext")) {
					actual.put(new Tuple<>(spellName, repName), actualSpell.getArr(repName));
				} else {
					actual.put(new Tuple<>(spellName, repName), actualSpells.getObj(spellName));
				}
			}
		}

		for (final Tuple<String, String> spellNameRep : actual.keySet()) {
			final JSONObject spell = spells.getObj(spellNameRep._1);
			final JSONObject rep = spell.getObj(spellNameRep._2);

			final List<JSONObject> actualTalents = new LinkedList<>();
			if (spell.containsKey("Auswahl") || spell.containsKey("Freitext")) {
				final JSONArray choiceTalent = (JSONArray) actual.get(spellNameRep);
				if (choiceTalent != null) {
					for (int i = 0; i < choiceTalent.size(); ++i) {
						actualTalents.add(choiceTalent.getObj(i));
					}
				}
			} else {
				actualTalents.add(((JSONObject) actual.get(spellNameRep)).getObj(spellNameRep._2));
			}

			for (final JSONObject actualTalent : actualTalents) {
				if (index >= rows / 2) {
					spellTable.addCells(new TableCell(table), " ");
					table = table.duplicate();
					table.addRow(nameTitle, repTitle, challengeTitle, traitTitle, valueTitle);
					index = 0;
				}

				String name = spellNameRep._1;
				if (spell.containsKey("Auswahl")) {
					name = name + ": " + actualTalent.getStringOrDefault("Auswahl", "");
				} else if (spell.containsKey("Freitext")) {
					name = name + ": " + actualTalent.getStringOrDefault("Freitext", "");
				}

				final String challenge = DSAUtil.getChallengeString(rep.getArrOrDefault("Probe", spell.getArr("Probe")));

				final TextCell traitString = new TextCell();
				final JSONObject traits = ResourceManager.getResource("data/Merkmale");
				final JSONArray actualTraits = rep.getArrOrDefault("Merkmale", spell.getArrOrDefault("Merkmale", null));
				if (actualTraits != null) {
					for (final String traitName : traits.keySet()) {
						for (int i = 0; i < actualTraits.size(); ++i) {
							if (traitName.equals(actualTraits.getString(i))) {
								final Text current = new Text(traits.getObj(traitName).getStringOrDefault("Abkürzung", "X"));
								traitString.addText(current);
							}
						}
					}
				}

				String zfw = "—";
				if (actualTalent.getBoolOrDefault("aktiviert", true)) {
					zfw = actualTalent.getIntOrDefault("ZfW", 0).toString();
				}

				table.addCells(name, spellNameRep._2, challenge, traitString, zfw);

				++index;
			}
		}

		for (int i = 0; i < additionalSpellRows.get() || index < rows / 2; ++i) {
			if (index >= rows / 2) {
				spellTable.addCells(new TableCell(table), " ");
				table = table.duplicate();
				table.addRow(nameTitle, repTitle, challengeTitle, traitTitle, valueTitle);
				index = 0;
			}
			table.addRow("", "", "", "", " ");
			++index;
		}

		spellTable.addCells(new TableCell(table));

		bottom.bottom = spellTable.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addTalentsTable(final PDDocument document) throws IOException {
		final Table talentsTable = new Table().setFiller(SheetUtil.stripe());
		talentsTable.addColumn(new Column(191, FontManager.serif, fontSize, HAlign.LEFT));
		talentsTable.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		talentsTable.addColumn(new Column(191, FontManager.serif, fontSize, HAlign.CENTER));
		talentsTable.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		talentsTable.addColumn(new Column(191, FontManager.serif, fontSize, HAlign.CENTER));

		SheetUtil.addTitle(talentsTable, "Talente");

		Table table = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);
		table.addColumn(new Column(100, 100, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(43, FontManager.serif, 8, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, 8, HAlign.CENTER));
		table.addColumn(new Column(23, FontManager.serif, 8, HAlign.CENTER));

		final JSONObject talents = ResourceManager.getResource("data/Talente");
		final JSONObject actualTalentGroups = hero.getObj("Talente");

		int rows = additionalTalentRows.get() + 2;

		for (final String talentGroupName : actualTalentGroups.keySet()) {
			++rows;
			final JSONObject actualTalentGroup = actualTalentGroups.getObj(talentGroupName);
			for (final String talentName : actualTalentGroup.keySet()) {
				final JSONObject talent = HeroUtil.findTalent(talentName)._1;
				if (talent != null && (talent.containsKey("Auswahl") || talent.containsKey("Freitext"))) {
					rows += actualTalentGroup.getArr(talentName).size();
				} else {
					++rows;
				}
			}
		}

		int index = 0;

		final int ATBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Attacke-Basis"), hero.getObj("Eigenschaften"),
				hero.getObj("Basiswerte").getObj("Attacke-Basis"), true);
		final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero.getObj("Eigenschaften"),
				hero.getObj("Basiswerte").getObj("Parade-Basis"), true);
		final int FKBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Fernkampf-Basis"), hero.getObj("Eigenschaften"),
				hero.getObj("Basiswerte").getObj("Fernkampf-Basis"), false);

		for (final String talentGroupName : talents.keySet()) {
			final JSONObject talentGroup = talents.getObj(talentGroupName);
			final JSONObject actualTalentGroup = actualTalentGroups.getObjOrDefault(talentGroupName, null);

			if (actualTalentGroup == null || actualTalentGroup.size() == 0) {
				continue;
			}

			if (index >= rows / 3) {
				talentsTable.addCells(new TableCell(table), " ");
				table = table.duplicate();
				index = 0;
			}

			final Cell nameTitle = new TextCell(talentGroupName, FontManager.serifBold, 0, 8);
			final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, 0, 8);

			switch (talentGroupName) {
			case "Nahkampftalente":
				table.addRow(nameTitle, new TextCell("AT", FontManager.serifBold, 0, 8).addText("/").addText("PA").setEquallySpaced(true),
						new TextCell("BE", FontManager.serifBold, 0, 8), tawTitle);
				break;
			case "Fernkampftalente":
				table.addRow(nameTitle, new TextCell("FK", FontManager.serifBold, 0, 8), new TextCell("BE", FontManager.serifBold, 0, 8),
						tawTitle);
				break;
			case "Körperliche Talente":
				table.addRow(nameTitle, new TextCell("Probe", FontManager.serifBold, 0, 8), new TextCell("BE", FontManager.serifBold, 0, 8),
						tawTitle);
				break;
			case "Sprachen und Schriften":
				table.addRow(nameTitle, new TextCell("Kpl.", FontManager.serifBold, 0, 8), new TextCell("S", FontManager.serifBold, 0, 8), tawTitle);
				break;
			default:
				table.addRow(nameTitle, new TextCell("Probe", FontManager.serifBold, 0, 8), " ", tawTitle);
				break;
			}

			if (index != 0) {
				table.getRows().get(table.getNumRows() - 1).addEventHandler(EventType.AFTER_ROW, event -> {
					try {
						final PDPageContentStream stream = event.getStream();
						stream.setLineWidth(1);
						stream.moveTo(event.getLeft(), event.getTop());
						stream.lineTo(event.getLeft() + event.getWidth(), event.getTop());
						stream.stroke();
					} catch (final IOException e) {
						ErrorLogger.logError(e);
					}
				});
			}

			++index;

			final Map<String, JSONValue> actual = new TreeMap<>((s1, s2) -> {
				final boolean firstIsBasis = talentGroup.getObj(s1).getBoolOrDefault("Basis", false);
				if (groupBasis.get() && firstIsBasis != talentGroup.getObj(s2).getBoolOrDefault("Basis", false)) return firstIsBasis ? -1 : 1;
				final boolean firstIsWriting = talentGroup.getObj(s1).getBoolOrDefault("Schrift", false);
				if (firstIsWriting != talentGroup.getObj(s2).getBoolOrDefault("Schrift", false)) return firstIsWriting ? 1 : -1;
				return SheetUtil.comparator.compare(s1, s2);
			});
			for (final String talentName : actualTalentGroup.keySet()) {
				final JSONObject talent = talentGroup.getObj(talentName);
				if (talent.containsKey("Auswahl") || talent.containsKey("Freitext")) {
					actual.put(talentName, actualTalentGroup.getArr(talentName));
				} else {
					actual.put(talentName, actualTalentGroup.getObj(talentName));
				}
			}

			boolean basicTalent = false;

			for (final String talentName : actual.keySet()) {
				final JSONObject talent = talentGroup.getObj(talentName);

				final List<JSONObject> actualTalents = new LinkedList<>();
				if (talent.containsKey("Auswahl") || talent.containsKey("Freitext")) {
					final JSONArray choiceTalent = (JSONArray) actual.get(talentName);
					if (choiceTalent != null) {
						for (int i = 0; i < choiceTalent.size(); ++i) {
							actualTalents.add(choiceTalent.getObj(i));
						}
					}
				} else {
					actualTalents.add((JSONObject) actual.get(talentName));
				}

				for (final JSONObject actualTalent : actualTalents) {
					if (index >= rows / 3) {
						talentsTable.addCells(new TableCell(table), " ");
						table = table.duplicate();
						index = 0;
					}

					Cell special;
					Cell language = null;
					switch (talentGroupName) {
					case "Nahkampftalente":
						final boolean ATOnly = talent.getBoolOrDefault("NurAT", false);

						final int at = ATBase + actualTalent.getIntOrDefault("AT", 0);
						final String atString = (at < 10 ? "  " : "") + Integer.toString(at);

						String paString;
						if (ATOnly) {
							paString = "—";
						} else {
							final int pa = PABase + actualTalent.getIntOrDefault("PA", 0);
							paString = (pa < 10 ? "  " : "") + Integer.toString(pa);
						}
						special = new TextCell(atString).addText("/").addText(paString).setEquallySpaced(true);
						break;
					case "Fernkampftalente":
						special = new TextCell(Integer.toString(FKBase + actualTalent.getIntOrDefault("AT", 0)));
						break;
					case "Sprachen und Schriften":
						special = new TextCell(talent.getInt("Komplexität").toString());
						if (actualTalent.getBoolOrDefault("Muttersprache", false)) {
							language = new TextCell("MS");
						} else if (actualTalent.getBoolOrDefault("Zweitsprache", false)) {
							language = new TextCell("ZS");
						} else if (actualTalent.getBoolOrDefault("Lehrsprache", false)) {
							language = new TextCell("LS");
						} else {
							language = new TextCell(" ");
						}
						break;
					default:
						if (talent.containsKey("Probe")) {
							final JSONArray challenge = talent.getArr("Probe");
							special = new TextCell(challenge.getString(0)).addText("/").addText(challenge.getString(1)).addText("/")
									.addText(challenge.getString(2))
									.setEquallySpaced(true).setPadding(0, 1, 1, 0);
						} else {
							special = new TextCell("—");
						}
						break;
					}

					String be = " ";
					switch (talentGroupName) {
					case "Nahkampftalente":
					case "Fernkampftalente":
					case "Körperliche Talente":
						be = DSAUtil.getBEString(talent);
						break;
					}

					String taw = "—";
					if (actualTalent.getBoolOrDefault("aktiviert", true) || talent.getBoolOrDefault("Basis", false)) {
						taw = actualTalent.getIntOrDefault("TaW", 0).toString();
					}

					String name = talentName;
					if (talent.containsKey("Auswahl")) {
						name = name + ": " + actualTalent.getStringOrDefault("Auswahl", "");
					} else if (talent.containsKey("Freitext")) {
						name = name + ": " + actualTalent.getStringOrDefault("Freitext", "");
					}

					final TextCell nameCell = new TextCell(name,
							markBasis.get() && talent.getBoolOrDefault("Basis", false) ? FontManager.serifItalic : FontManager.serif, 8, 8);
					table.addCells(nameCell, special);
					if ("Sprachen und Schriften".equals(talentGroupName)) {
						table.addCells(language);
					} else {
						table.addCells(be);
					}
					table.addCells(taw);

					if (index != 0 && groupBasis.get() && basicTalent && !talent.getBoolOrDefault("Basis", false)) {
						table.getRows().get(table.getNumRows() - 1).addEventHandler(EventType.AFTER_ROW, event -> {
							try {
								final PDPageContentStream stream = event.getStream();
								stream.setLineWidth(1);
								stream.moveTo(event.getLeft(), event.getTop());
								stream.lineTo(event.getLeft() + event.getWidth(), event.getTop());
								stream.stroke();
							} catch (final IOException e) {
								ErrorLogger.logError(e);
							}
						});
					}
					basicTalent = talent.getBoolOrDefault("Basis", false);

					++index;
				}
			}
		}

		for (int i = 0; i < additionalTalentRows.get() || index < rows / 3; ++i) {
			if (index >= rows / 3) {
				talentsTable.addCells(new TableCell(table), " ");
				table = table.duplicate();
				index = 0;
			}
			table.addRow(" ");
			++index;
		}

		talentsTable.addCells(new TableCell(table));

		bottom.bottom = talentsTable.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addTotalArmorTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(91, 91, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(22, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(22, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Rüstung", 1);
		final Cell beTitle = SheetUtil.createTitleCell("BE", 1);
		final Cell rsTitle = SheetUtil.createTitleCell("RS", 1);
		table.addRow(nameTitle, beTitle, rsTitle);

		double RS = 0;

		final String armorSetting = Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart");
		final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");

		for (int i = 0; i < items.size(); ++i) {
			JSONObject item = items.getObj(i);
			final JSONArray categories = item.getArrOrDefault("Kategorien", null);
			if (categories != null && categories.contains("Rüstung")) {
				final JSONObject baseArmor = item;
				if (item.containsKey("Rüstung")) {
					item = item.getObj("Rüstung");
				}

				final String name = item.getStringOrDefault("Name", baseArmor.getStringOrDefault("Name", ""));

				String beString;
				if ("Gesamtrüstung".equals(armorSetting)) {
					beString = item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).toString();
				} else {
					beString = SheetUtil.threeDecimalPlaces.format(item.getDoubleOrDefault("Behinderung", baseArmor.getDoubleOrDefault("Behinderung",
							item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).doubleValue())));
				}

				String rsString;
				if ("Gesamtrüstung".equals(armorSetting) || !(item.containsKey("Rüstungsschutz") || baseArmor.containsKey("Rüstungsschutz"))) {
					final int pieceRS = item.getIntOrDefault("Gesamtrüstungsschutz", baseArmor.getIntOrDefault("Gesamtrüstungsschutz", 0));
					RS += pieceRS;
					rsString = Integer.toString(pieceRS);
				} else {
					final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObj("Rüstungsschutz"));
					final int[] zoneValues = new int[8];
					int j = 0;
					for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
						zoneValues[j] = zones.getInt(zone);
						++j;
					}
					final double pieceRS = (zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4 + zoneValues[4] + zoneValues[5]
							+ zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0;
					RS += pieceRS;
					rsString = SheetUtil.threeDecimalPlaces.format(pieceRS);
				}
				table.addRow(name, beString, rsString);
			}
		}

		final JSONObject pros = hero.getObj("Vorteile");
		if (pros.containsKey("Natürlicher Rüstungsschutz")) {
			RS += pros.getObj("Natürlicher Rüstungsschutz").getIntOrDefault("Stufe", 0);
		}

		for (int i = 0; i < additionalArmorRows.get(); ++i) {
			table.addRow("");
		}

		table.addRow("Gesamt:", SheetUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero)), SheetUtil.threeDecimalPlaces.format(RS));

		bottom.bottom = table.render(document, 135, 454, bottom.bottom - 5, 10, 10);
	}

	private void addZoneArmorTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addColumn(new Column(91, 91, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(22, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Rüstung", 1);
		final Cell beTitle = SheetUtil.createTitleCell("BE", 1);
		final Cell kTitle = SheetUtil.createTitleCell("K", 1);
		final Cell brTitle = SheetUtil.createTitleCell("Br", 1);
		final Cell rTitle = SheetUtil.createTitleCell("R", 1);
		final Cell baTitle = SheetUtil.createTitleCell("Ba", 1);
		final Cell laTitle = SheetUtil.createTitleCell("LA", 1);
		final Cell raTitle = SheetUtil.createTitleCell("RA", 1);
		final Cell lbTitle = SheetUtil.createTitleCell("LB", 1);
		final Cell rbTitle = SheetUtil.createTitleCell("RB", 1);
		table.addRow(nameTitle, beTitle, kTitle, brTitle, rTitle, baTitle, laTitle, raTitle, lbTitle, rbTitle);

		final String armorSetting = Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart");
		final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
		for (int i = 0; i < items.size(); ++i) {
			JSONObject item = items.getObj(i);
			final JSONArray categories = item.getArrOrDefault("Kategorien", null);
			if (categories != null && categories.contains("Rüstung")) {
				final JSONObject baseArmor = item;
				if (item.containsKey("Rüstung")) {
					item = item.getObj("Rüstung");
				}

				final String name = item.getStringOrDefault("Name", baseArmor.getStringOrDefault("Name", ""));

				String be;
				if ("Gesamtrüstung".equals(armorSetting)) {
					be = item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).toString();
				} else {
					be = SheetUtil.threeDecimalPlaces.format(item.getDoubleOrDefault("Behinderung", baseArmor.getDoubleOrDefault("Behinderung",
							item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).doubleValue())));

				}

				final String[] rs = new String[8];
				if ("Gesamtrüstung".equals(armorSetting) || !(item.containsKey("Rüstungsschutz") || baseArmor.containsKey("Rüstungsschutz"))) {
					final int RS = item.getIntOrDefault("Gesamtrüstungsschutz", baseArmor.getIntOrDefault("Gesamtrüstungsschutz", 0));
					for (int j = 0; j < 8; ++j) {
						rs[i] = Integer.toString(RS);
					}
				} else {
					final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObj("Rüstungsschutz"));
					final int[] zoneValues = new int[8];
					int j = 0;
					for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
						zoneValues[j] = zones.getInt(zone);
						if ("Zonenrüstung".equals(armorSetting)) {
							rs[j] = Integer.toString(zoneValues[j]);
						}
						++j;
					}
					if ("Zonengesamtrüstung".equals(armorSetting)) {
						final String zoneRS = SheetUtil.threeDecimalPlaces.format((zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4
								+ zoneValues[4] + zoneValues[5] + zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0);
						for (int k = 0; k < 8; ++k) {
							rs[k] = zoneRS;
						}
					}
				}
				table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7]);
			}
		}

		for (int i = 0; i < additionalArmorRows.get(); ++i) {
			table.addRow("");
		}

		int i = 0;
		final int[] rs = new int[8];
		for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
			rs[i] = HeroUtil.getZoneRS(hero, zone);
			++i;
		}
		table.addRow("Gesamt:", SheetUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero)), rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7]);

		bottom.bottom = table.render(document, 257, 332, bottom.bottom - 5, 10, 10);
	}

	@Override
	public boolean check() {
		return hero != null;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (hero != null) {
			startCreate(document);

			addBiographyTable(document);
			addAttributesTable(document);
			addDerivedValuesTable(document);
			addProsAndConsTable(document);
			addSpecialSkillsTable(document);
			final float smallTop = bottom.bottom;
			addCloseCombatTable(document);
			addRangedCombatTable(document);
			final float smallBottom = bottom.bottom;
			bottom.bottom = smallTop;
			addInfightTable(document);
			final float secondBottom = bottom.bottom;
			bottom.bottom = smallTop;
			if (showTotalArmor.get()) {
				addTotalArmorTable(document);
			}
			bottom.bottom = Math.min(secondBottom, bottom.bottom);
			if (showZoneArmor.get()) {
				addZoneArmorTable(document);
			}
			bottom.bottom = Math.min(bottom.bottom, smallBottom);
			addTalentsTable(document);
			if (showSpells.get()) {
				addSpellTable(document);
			}

			endCreate(document);
		}
	}

	@Override
	public void load() {
		super.load();
		settings.addBooleanChoice("Gesamtrüstung", showTotalArmor);
		settings.addBooleanChoice("Zonenrüstung", showZoneArmor);
		settings.addIntegerChoice("Zusätzliche Zeilen für Nahkampfwaffen", additionalCloseCombatWeaponRows, 0, 30);
		settings.addIntegerChoice("Zusätzliche Zeilen für Fernkampfwaffen", additionalRangedWeaponRows, 0, 30);
		settings.addIntegerChoice("Zusätzliche Zeilen für Rüstung", additionalArmorRows, 0, 30);
		settings.addIntegerChoice("Zusätzliche Zeilen für Talente", additionalTalentRows, 0, 60);
		settings.addBooleanChoice("Zauber", showSpells);
		settings.addIntegerChoice("Zusätzliche Zeilen für Zauber", additionalSpellRows, 0, 60);
		settings.addBooleanChoice("Basistalente gruppieren", groupBasis);
		settings.addBooleanChoice("Basistalente markieren", markBasis);
	}

	@Override
	public String toString() {
		return "Kompaktbogen";
	}

}
