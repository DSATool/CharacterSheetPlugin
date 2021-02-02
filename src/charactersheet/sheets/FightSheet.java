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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import boxtable.cell.Cell;
import boxtable.cell.TextCell;
import boxtable.common.HAlign;
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
import dsatool.util.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class FightSheet extends Sheet {
	private static final float fontSize = 11;
	private final IntegerProperty additionalArmorRows = new SimpleIntegerProperty(5);
	private final IntegerProperty additionalCloseCombatWeaponRows = new SimpleIntegerProperty(5);
	private final IntegerProperty additionalDefensiveWeaponRows = new SimpleIntegerProperty(5);
	private final IntegerProperty additionalRangedWeaponRows = new SimpleIntegerProperty(5);
	private final BooleanProperty showCloseCombatWeapons = new SimpleBooleanProperty(true);
	private final BooleanProperty showDefensiveWeapons = new SimpleBooleanProperty(true);
	private final BooleanProperty showEnergies = new SimpleBooleanProperty(true);
	private final BooleanProperty showEvasion = new SimpleBooleanProperty(true);
	private final BooleanProperty showInfight = new SimpleBooleanProperty(true);
	private final BooleanProperty showRangedCombatWeapons = new SimpleBooleanProperty(true);
	private final BooleanProperty showAmmunition = new SimpleBooleanProperty(true);
	private final BooleanProperty showZoneArmor = new SimpleBooleanProperty(
			"Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart")));
	private final BooleanProperty showZoneTable = new SimpleBooleanProperty(
			!"Gesamtrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart")));
	private final StringProperty zoneImage = new SimpleStringProperty();

	public FightSheet() {
		super(771);
	}

	private void addAmmunitionTable(final PDDocument document) throws IOException {
		int numCols = additionalRangedWeaponRows.get();

		final List<JSONObject> ammunition = new ArrayList<>();
		if (hero != null) {
			final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Fernkampfwaffe")) {
					final JSONObject baseWeapon = item;
					if (item.containsKey("Fernkampfwaffe")) {
						item = item.getObj("Fernkampfwaffe");
					}
					final String ammunitionType = item.getStringOrDefault("Geschoss:Typ", baseWeapon.getString("Geschoss:Typ"));
					if ("Pfeile".equals(ammunitionType) || "Bolzen".equals(ammunitionType)) {
						ammunition.add(item.getObjOrDefault("Munition", baseWeapon.getObj("Munition")));
						++numCols;
					}
				}
			}
		}

		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		for (int i = 0; i < numCols; ++i) {
			table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		}
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		table.addCells(SheetUtil.createTitleCell("Geschosstyp", 1));
		table.addCells(SheetUtil.createTitleCell("Preis", 1));
		for (int i = 1; i <= numCols; ++i) {
			table.addCells(SheetUtil.createTitleCell("#" + i, 1));
		}
		table.addCells(SheetUtil.createTitleCell("Besonderes", 1));

		final JSONObject ammunitionTypes = ResourceManager.getResource("data/Geschosstypen");
		for (final String name : ammunitionTypes.keySet()) {
			final JSONObject type = ammunitionTypes.getObj(name);
			table.addCells(name, "x" + DSAUtil.threeDecimalPlaces.format(type.getDoubleOrDefault("Preis:Faktor", 1.0)));
			for (int i = 0; i < numCols; ++i) {
				if (fillAll && i < ammunition.size()) {
					table.addCells(ammunition.get(i).getObj(name).getIntOrDefault("Aktuell", 0));
				} else {
					table.addCells(" ");
				}
			}
			table.addCells(type.getStringOrDefault("Beschreibung:Kurz", ""));
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
		}
	}

	private void addCloseCombatTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, 53, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(26, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Nahkampfwaffen", 1);
		final Cell typeTitle = SheetUtil.createTitleCell("Typ", 1);
		final Cell ebeTitle = SheetUtil.createTitleCell("eBE", 1);
		final Cell tpTitle = SheetUtil.createTitleCell("TP", 1);
		final Cell atTitle = SheetUtil.createTitleCell("AT", 1);
		final Cell paTitle = SheetUtil.createTitleCell("PA", 1);
		final Cell tpkkTitle = ((TextCell) SheetUtil.createTitleCell("TP", 1)).addText("/").addText("KK").setEquallySpaced(true);
		final Cell wmTitle = SheetUtil.createTitleCell("WM", 1);
		final Cell iniTitle = SheetUtil.createTitleCell("INI", 1);
		final Cell dkTitle = SheetUtil.createTitleCell("DK", 1);
		final Cell bfTitle = SheetUtil.createTitleCell("BF", 1);
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 1);
		table.addRow(nameTitle, typeTitle, ebeTitle, tpTitle, atTitle, paTitle, tpkkTitle, wmTitle, iniTitle, dkTitle, bfTitle, notesTitle);

		if (hero != null) {
			final JSONObject closeCombatTalents = ResourceManager.getResource("data/Talente").getObj("Nahkampftalente");
			final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Nahkampfwaffe")) {
					final JSONObject baseWeapon = item;
					if (item.containsKey("Nahkampfwaffe")) {
						item = item.getObj("Nahkampfwaffe");
					}
					if (fill) {
						final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

						final JSONArray types = item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen"));
						final String type = item.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : "");
						final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));
						final String ebe = Integer.toString(closeCombatTalents.getObjOrDefault(type, new JSONObject(null)).getIntOrDefault("BEAdditiv", 0));

						final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArrOrDefault("Waffenmeister", null),
								type, item.getStringOrDefault("Typ", baseWeapon.getString("Typ")));

						final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

						final Integer atValue = HeroUtil.getAT(hero, item, type, true, false, false);
						final String at = fillAll && atValue != null ? Integer.toString(atValue) : " ";

						final Integer paValue = HeroUtil.getPA(hero, item, type, false, false);
						final String pa = fillAll ? paValue != null ? Integer.toString(paValue) : "—" : " ";

						final JSONObject TPKKValues = item.getObjOrDefault("Trefferpunkte/Körperkraft",
								baseWeapon.getObjOrDefault("Trefferpunkte/Körperkraft", null));
						final int threshold = TPKKValues != null ? TPKKValues.getIntOrDefault("Schwellenwert", Integer.MIN_VALUE) : Integer.MIN_VALUE;
						final int step = TPKKValues != null ? TPKKValues.getIntOrDefault("Schadensschritte", Integer.MIN_VALUE) : Integer.MIN_VALUE;
						final String tpkkThreshold = threshold == Integer.MIN_VALUE ? "—" : Integer.toString(threshold
								+ (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schwellenwert", 0) : 0));
						final String tpkkStep = step == Integer.MIN_VALUE ? "—" : Integer.toString(
								step + (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schadensschritte", 0) : 0));
						final TextCell tpkk = new TextCell(tpkkThreshold).addText("/").addText(tpkkStep).setEquallySpaced(true);

						final String atMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0)
								+ (weaponMastery != null ? weaponMastery.getObj("Waffenmodifikatoren").getIntOrDefault("Attackemodifikator", 0) : 0));
						final String paMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0)
								+ (weaponMastery != null ? weaponMastery.getObj("Waffenmodifikatoren").getIntOrDefault("Parademodifikator", 0) : 0));
						final TextCell wm = new TextCell(atMod).addText("/").addText(paMod).setEquallySpaced(true);

						final String ini = Util.getSignedIntegerString(
								item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0))
										+ (weaponMastery != null ? weaponMastery.getIntOrDefault("Initiative:Modifikator", 0) : 0));

						final String distance = String.join("", item.getArrOrDefault("Distanzklassen", baseWeapon.getArr("Distanzklassen")).getStrings());

						final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
						String bf = null;
						if (BF != null) {
							bf = BF.toString();
						} else {
							bf = "—";
						}

						String notes = item.getStringOrDefault("Anmerkungen", baseWeapon.getStringOrDefault("Anmerkungen", ""));
						boolean first = notes.isEmpty();
						if (baseWeapon.containsKey("Bannschwert") && baseWeapon.getObj("Bannschwert").getObj("Rituale").containsKey("Bannschwert")) {
							if (first) {
								first = false;
							} else {
								notes += ", ";
							}
							notes += "Bannschwert";
							if (baseWeapon.getObj("Bannschwert").getObj("Rituale").containsKey("Apport")) {
								notes += ", Apport";
							}
						}
						if (weaponMastery != null) {
							final JSONObject easierManeuvers = weaponMastery.getObjOrDefault("Manöver:Erleichterung", null);
							final JSONArray additionalManeuvers = weaponMastery.getArrOrDefault("Manöver:Zusätzlich", null);
							final JSONObject pros = weaponMastery.getObjOrDefault("Vorteile", null);
							if (easierManeuvers != null) {
								for (final String maneuver : easierManeuvers.keySet()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += maneuver + "-" + easierManeuvers.getInt(maneuver);
								}
							}
							if (additionalManeuvers != null) {
								for (final String maneuver : additionalManeuvers.getStrings()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += maneuver;
								}
							}
							if (pros != null) {
								for (final String pro : pros.keySet()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += pro;
								}
							}
						}

						table.addRow(name, type, ebe, tp, at, pa, tpkk, wm, ini, distance, bf, notes);
					} else {
						table.addRow(" ", " ", " ", " ", " ", " ", "/", "/");
					}
				}
			}
		}
		for (int i = 0; i < additionalCloseCombatWeaponRows.get(); ++i) {
			table.addRow(" ", " ", " ", " ", " ", " ", "/", "/");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
		}
	}

	private void addDefensiveWeaponsTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Schilde/Parierwaffen", 1);
		final Cell typeTitle = SheetUtil.createTitleCell("Typ", 1);
		final Cell paTitle = SheetUtil.createTitleCell("PA", 1);
		final Cell wmTitle = SheetUtil.createTitleCell("WM", 1);
		final Cell iniTitle = SheetUtil.createTitleCell("INI", 1);
		final Cell bfTitle = SheetUtil.createTitleCell("BF", 1);
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 1);
		table.addRow(nameTitle, typeTitle, paTitle, wmTitle, iniTitle, bfTitle, notesTitle);

		if (hero != null) {
			final JSONObject skills = hero.getObj("Sonderfertigkeiten");
			final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero,
					hero.getObj("Basiswerte").getObj("Parade-Basis"), false);
			final int shieldMod = skills.containsKey("Waffenmeister (Schild)") ? 7
					: skills.containsKey("Schildkampf II") ? 5 : skills.containsKey("Schildkampf I") ? 3 : skills.containsKey("Linkhand") ? 1 : 0;
			final int defensiveMod = skills.containsKey("Parierwaffen II") ? 2
					: skills.containsKey("Parierwaffen I") ? -1 : skills.containsKey("Linkhand") ? -4 : Integer.MIN_VALUE;
			final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONObject baseWeapon = item;
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Schild")) {
					if (fill) {
						if (item.containsKey("Schild")) {
							item = item.getObj("Schild");
						}

						final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

						final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));
						final String pa = fillAll ? Integer.toString(PABase + shieldMod + weaponModifier.getIntOrDefault("Parademodifikator", 0)) : " ";

						final String atMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0));
						final String paMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0));
						final TextCell wm = new TextCell(atMod).addText("/").addText(paMod).setEquallySpaced(true);

						final String ini = Util.getSignedIntegerString(
								item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0)));

						final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
						String bf = null;
						if (BF != null) {
							bf = BF.toString();
						} else {
							bf = "—";
						}

						final String notes = item.getStringOrDefault("Anmerkungen", baseWeapon.getStringOrDefault("Anmerkungen", " "));

						table.addRow(name, "S", pa, wm, ini, bf, notes);
						item = baseWeapon;
					} else {
						table.addRow(" ", " ", " ", "/");
					}
				}
				if (categories != null && categories.contains("Parierwaffe")) {
					if (fill) {
						if (item.containsKey("Parierwaffe")) {
							item = item.getObj("Parierwaffe");
						}
						final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

						final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));

						final String pa = defensiveMod != Integer.MIN_VALUE
								? Util.getSignedIntegerString(defensiveMod + weaponModifier.getIntOrDefault("Parademodifikator", 0)) : "—";

						final String atMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0));
						final String paMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0));
						final TextCell wm = new TextCell(atMod).addText("/").addText(paMod).setEquallySpaced(true);

						final String ini = Util.getSignedIntegerString(
								item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0)));

						final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
						String bf = null;
						if (BF != null) {
							bf = BF.toString();
						} else {
							bf = "—";
						}

						final String notes = item.getStringOrDefault("Anmerkungen", baseWeapon.getStringOrDefault("Anmerkungen", " "));

						table.addRow(name, "P", pa, wm, ini, bf, notes);
					} else {
						table.addRow(" ", " ", " ", "/");
					}
				}
			}
		}
		for (int i = 0; i < additionalDefensiveWeaponRows.get(); ++i) {
			table.addRow(" ", " ", " ", "/");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 397, 12, bottom.bottom, 72, 10) - 5;
		}
	}

	private void addEnergiesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(61, FontManager.serif, 10, HAlign.LEFT));
		table.addColumn(new Column(23, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(23, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(23, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(23, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("LeP / AuP", 1);
		final Cell maxTitle = SheetUtil.createTitleCell("max.", 1);
		final Cell halfTitle = SheetUtil.createTitleCell("1/2", 1);
		final Cell thirdTitle = SheetUtil.createTitleCell("1/3", 1);
		final Cell quarterTitle = SheetUtil.createTitleCell("1/4", 1);
		final Cell effectsTitle = SheetUtil.createTitleCell("Auswirkungen", 1);
		table.addRow(nameTitle, maxTitle, halfTitle, thirdTitle, quarterTitle, effectsTitle);

		final JSONObject lowEnergy = ResourceManager.getResource("data/Niedrige_Energie");
		final StringBuilder effectsLeP = new StringBuilder();
		boolean first = true;
		int length = 0;
		for (final String level : lowEnergy.getObj("Lebensenergie").keySet()) {
			if (first) {
				first = false;
			} else {
				if (length > 80) {
					effectsLeP.append('\n');
					length = 0;
				} else {
					effectsLeP.append(" | ");
				}
			}
			effectsLeP.append(level);
			effectsLeP.append(": ");
			final String text = lowEnergy.getObj("Lebensenergie").getObj(level).getStringOrDefault("Beschreibung:Kurz", "");
			effectsLeP.append(text);
			length += text.length() + 5;
		}
		final StringBuilder effectsAuP = new StringBuilder();
		first = true;
		length = 0;
		for (final String level : lowEnergy.getObj("Ausdauer").keySet()) {
			if (first) {
				first = false;
			} else {
				if (length > 80) {
					effectsAuP.append('\n');
					length = 0;
				} else {
					effectsAuP.append(" | ");
				}
			}
			effectsAuP.append(level);
			effectsAuP.append(": ");
			final String text = lowEnergy.getObj("Ausdauer").getObj(level).getStringOrDefault("Beschreibung:Kurz", "");
			effectsAuP.append(text);
			length += text.length() + 5;
		}

		if (hero != null && fill) {
			final int lep = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Lebensenergie"), hero,
					hero.getObj("Basiswerte").getObj("Lebensenergie"), false);
			final String maxLeP = Integer.toString(lep);
			final String halfLeP = Integer.toString((int) Math.round(lep / 2.0));
			final String thirdLeP = Integer.toString((int) Math.round(lep / 3.0));
			final String quarterLeP = Integer.toString((int) Math.round(lep / 4.0));

			final int aup = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Ausdauer"), hero,
					hero.getObj("Basiswerte").getObj("Ausdauer"), false);
			final String maxAuP = Integer.toString(aup);
			final String halfAuP = Integer.toString((int) Math.round(aup / 2.0));
			final String thirdAuP = Integer.toString((int) Math.round(aup / 3.0));
			final String quarterAuP = Integer.toString((int) Math.round(aup / 4.0));

			table.addRow("Lebenspunkte", maxLeP, halfLeP, thirdLeP, quarterLeP, effectsLeP);
			table.addRow("Ausdauer", maxAuP, halfAuP, thirdAuP, quarterAuP, effectsAuP);
		} else {
			table.addRow("Lebenspunkte", " ", " ", " ", " ", effectsAuP);
			table.addRow("Ausdauer", " ", " ", " ", " ", effectsAuP);
		}

		bottom.bottom = table.render(document, 397, 12, bottom.bottom, 72, 10) - 5;
	}

	private void addEvasionTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(41, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(92, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(144, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(70, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell paTitle = SheetUtil.createTitleCell("PA-Basis", 1);
		final Cell beTitle = SheetUtil.createTitleCell("-BE", 1);
		final Cell evadingTitle = SheetUtil.createTitleCell("Ausweichen I/II/III", 1);
		final Cell acrobaticsTitle = SheetUtil.createTitleCell("Akrobatik 12/15/18/21/24", 1);
		final Cell agileTitle = SheetUtil.createTitleCell("Flink/Behäbig", 1);
		final Cell resultsTitle = SheetUtil.createTitleCell("=", 1);
		table.addRow(paTitle, beTitle, evadingTitle, acrobaticsTitle, agileTitle, resultsTitle);

		JSONObject specialSkills = null;
		int acrobaticsValue = 0;
		JSONObject pros = null;
		JSONObject cons = null;

		if (hero != null && fill) {
			int result = 0;

			final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero,
					hero.getObj("Basiswerte").getObj("Parade-Basis"), true);
			result = PABase;
			final String pa = Integer.toString(PABase);

			final int BE = HeroUtil.getBE(hero);
			result -= BE;
			final String be = Integer.toString(-BE);

			final TextCell evading = new TextCell("     +3").addText("/").addText("     +3").addText("/").addText("     +3").setEquallySpaced(true);
			final TextCell acrobatics = new TextCell("     +1").addText("/").addText("     +1").addText("/").addText("     +1").addText("/").addText("     +1")
					.addText("/").addText("     +1").setEquallySpaced(true);
			final TextCell speed = new TextCell("     +1").addText("/").addText("     -1").setEquallySpaced(true);

			specialSkills = hero.getObj("Sonderfertigkeiten");
			if (specialSkills.containsKey("Ausweichen I")) {
				result += 3;
			}
			if (specialSkills.containsKey("Ausweichen II")) {
				result += 3;
			}
			if (specialSkills.containsKey("Ausweichen III")) {
				result += 3;
			}

			final JSONObject acrobaticsTalent = hero.getObj("Talente").getObj("Körperliche Talente").getObjOrDefault("Akrobatik", null);
			if (acrobaticsTalent != null) {
				acrobaticsValue = acrobaticsTalent.getIntOrDefault("TaW", 0);
			}
			if (acrobaticsValue > 11) {
				result += (acrobaticsValue - 9) / 3;
			}

			pros = hero.getObj("Vorteile");
			if (pros.containsKey("Flink")) {
				result += 1;
			}

			cons = hero.getObj("Nachteile");
			if (cons.containsKey("Behäbig")) {
				result -= 1;
			}

			final String results = Integer.toString(result);

			table.addRow(pa, be, evading, acrobatics, speed, results);
		} else {
			table.addRow(" ");
		}

		bottom.bottom = table.render(document, 397, 12, bottom.bottom, 72, 10) - 5;

		final PDPageContentStream stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), AppendMode.APPEND, true);

		if (hero != null && fill) {
			if (specialSkills.containsKey("Ausweichen I")) {
				SheetUtil.checkChoiceBox(stream, 80, bottom.bottom + 15);
			}
			if (specialSkills.containsKey("Ausweichen II")) {
				SheetUtil.checkChoiceBox(stream, 110, bottom.bottom + 15);
			}
			if (specialSkills.containsKey("Ausweichen III")) {
				SheetUtil.checkChoiceBox(stream, 141, bottom.bottom + 15);
			}

			if (acrobaticsValue > 11) {
				SheetUtil.checkChoiceBox(stream, 171, bottom.bottom + 15);
				if (acrobaticsValue >= 15) {
					SheetUtil.checkChoiceBox(stream, 200, bottom.bottom + 15);
				}
				if (acrobaticsValue >= 18) {
					SheetUtil.checkChoiceBox(stream, 229, bottom.bottom + 15);
				}
				if (acrobaticsValue >= 21) {
					SheetUtil.checkChoiceBox(stream, 258, bottom.bottom + 15);
				}
				if (acrobaticsValue >= 24) {
					SheetUtil.checkChoiceBox(stream, 287, bottom.bottom + 15);
				}
			}

			if (pros.containsKey("Flink")) {
				SheetUtil.checkChoiceBox(stream, 318, bottom.bottom + 15);
			}
			if (cons.containsKey("Behäbig")) {
				SheetUtil.checkChoiceBox(stream, 353, bottom.bottom + 15);
			}
		}

		SheetUtil.drawChoiceBox(stream, 80, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 110, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 141, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 171, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 200, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 229, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 258, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 287, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 318, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 353, bottom.bottom + 15);

		stream.close();
	}

	private void addInfightTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Waffenloser Kampf", 1);
		final Cell tpTitle = SheetUtil.createTitleCell("TP", 1);
		final Cell atTitle = SheetUtil.createTitleCell("AT", 1);
		final Cell paTitle = SheetUtil.createTitleCell("PA", 1);
		final Cell tpkkTitle = ((TextCell) SheetUtil.createTitleCell("TP", 1)).addText("/").addText("KK").setEquallySpaced(true);
		final Cell iniTitle = SheetUtil.createTitleCell("INI", 1);
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 1);
		table.addRow(nameTitle, tpTitle, atTitle, paTitle, tpkkTitle, iniTitle, notesTitle);

		int TPKKModifier = 0;

		final TextCell tpkk = new TextCell("10").addText("/").addText("3").setEquallySpaced(true);

		if (hero != null && fill) {
			TPKKModifier = (HeroUtil.getCurrentValue(hero.getObj("Eigenschaften").getObj("KK"), false) - 10) / 3;
			final String tp = "1W" + (TPKKModifier == 0 ? "" : Util.getSignedIntegerString(TPKKModifier)) + "(A)";
			final String at1 = fillAll ? Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Raufen", true, false, false)) : " ";
			final String pa1 = fillAll ? Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Raufen", false, false)) : " ";
			table.addRow("Raufen", tp, at1, pa1, tpkk, "±0");
			final String at2 = fillAll ? Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Ringen", true, false, false)) : " ";
			final String pa2 = fillAll ? Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Ringen", false, false)) : " ";
			table.addRow("Ringen", tp, at2, pa2, tpkk, "±0");
		} else {
			table.addRow("Raufen", " ", " ", " ", tpkk, "±0");
			table.addRow("Ringen", " ", " ", " ", tpkk, "±0");
		}

		bottom.bottom = table.render(document, 397, 12, bottom.bottom, 72, 10) - 5;
	}

	private void addRangedCombatTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(53, 53, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
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
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Fernkampfwaffen", 1);
		final Cell typeTitle = SheetUtil.createTitleCell("Typ", 1);
		final Cell ebeTitle = SheetUtil.createTitleCell("eBE", 1);
		final Cell tpTitle = SheetUtil.createTitleCell("TP", 1);
		final Cell atTitle = SheetUtil.createTitleCell("AT", 1);
		final Cell loadTitle = SheetUtil.createTitleCell("Lad.", 1);
		final Cell distanceTitle = SheetUtil.createTitleCell("Entfernung", 5);
		final Cell tpdistanceTitle = SheetUtil.createTitleCell("TP+", 5);
		final Cell numTitle = SheetUtil.createTitleCell("#", 1);
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 1);
		table.addRow(nameTitle, typeTitle, ebeTitle, tpTitle, atTitle, loadTitle, distanceTitle, tpdistanceTitle, numTitle, notesTitle);

		if (hero != null) {
			int numAmmunition = 1;
			final JSONObject rangedCombatTalents = ResourceManager.getResource("data/Talente").getObj("Fernkampftalente");
			final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Fernkampfwaffe")) {
					final JSONObject baseWeapon = item;
					if (item.containsKey("Fernkampfwaffe")) {
						item = item.getObj("Fernkampfwaffe");
					}
					if (fill) {
						final String name = item.getStringOrDefault("Name", baseWeapon.getStringOrDefault("Name", ""));

						final JSONArray types = item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen"));
						final String type = item.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : "");
						final String ebe = Integer.toString(rangedCombatTalents.getObjOrDefault(type, new JSONObject(null)).getIntOrDefault("BEAdditiv", 0));

						final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

						final Integer atValue = HeroUtil.getAT(hero, item, type, false, false, false);
						final String at = fillAll && atValue != null ? Integer.toString(atValue) : " ";

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

						String num = fillAll ? "1" : " ";
						final String ammunitionType = item.getStringOrDefault("Geschoss:Typ", baseWeapon.getString("Geschoss:Typ"));
						if ("Pfeile".equals(ammunitionType) || "Bolzen".equals(ammunitionType)) {
							if (showAmmunition.get()) {
								num = "#" + numAmmunition;
								++numAmmunition;
							} else if (fillAll) {
								final JSONObject ammunitionTypes = ResourceManager.getResource("data/Geschosstypen");
								final JSONObject ammunition = item.getObjOrDefault("Munition", baseWeapon.getObj("Munition"));
								int amount = 0;
								for (final String typeName : ammunitionTypes.keySet()) {
									amount += ammunition.getObj(typeName).getIntOrDefault("Aktuell", 0);
								}
								num = Integer.toString(amount);
							}
						} else if (fillAll) {
							final JSONObject amount = item.getObjOrDefault("Anzahl", baseWeapon.getObjOrDefault("Anzahl", null));
							if (amount != null) {
								num = Integer.toString(amount.getIntOrDefault("Gesamt", 1));
							}
						}

						String notes = item.getStringOrDefault("Anmerkungen", baseWeapon.getStringOrDefault("Anmerkungen", " "));

						final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArrOrDefault("Waffenmeister", null),
								type, item.getStringOrDefault("Typ", baseWeapon.getString("Typ")));
						if (weaponMastery != null) {
							boolean first = notes.isEmpty();
							final JSONObject easierManeuvers = weaponMastery.getObjOrDefault("Manöver:Erleichterung", null);
							final JSONArray additionalManeuvers = weaponMastery.getArrOrDefault("Manöver:Zusätzlich", null);
							final JSONObject pros = weaponMastery.getObjOrDefault("Vorteile", null);
							if (easierManeuvers != null) {
								for (final String maneuver : easierManeuvers.keySet()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += maneuver + "-" + easierManeuvers.getInt(maneuver);
								}
							}
							if (additionalManeuvers != null) {
								for (final String maneuver : additionalManeuvers.getStrings()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += maneuver;
								}
							}
							if (pros != null) {
								for (final String pro : pros.keySet()) {
									if (first) {
										first = false;
									} else {
										notes += ", ";
									}
									notes += pro;
								}
							}
						}

						table.addRow(name, type, ebe, tp, at, load, distances[0], distances[1], distances[2], distances[3], distances[4], tpdistance[0],
								tpdistance[1], tpdistance[2], tpdistance[3], tpdistance[4], num, notes);
					} else {
						table.addRow("");
					}
				}
			}
		}
		for (int i = 0; i < additionalRangedWeaponRows.get(); ++i) {
			table.addRow("");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
		}
	}

	private void addZoneArmorTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(96, 96, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(18, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(60, FontManager.serif, fontSize, HAlign.RIGHT));
		table.addColumn(new Column(0, FontManager.serif, fontSize, HAlign.CENTER));

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
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 3);
		table.addRow(nameTitle, beTitle, kTitle, brTitle, rTitle, baTitle, laTitle, raTitle, lbTitle, rbTitle, notesTitle);

		if (hero != null) {
			final String armorSetting = Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart");
			final JSONArray items = hero.getObj("Besitz").getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Rüstung")) {
					if (fill) {
						final JSONObject baseArmor = item;
						if (item.containsKey("Rüstung")) {
							item = item.getObj("Rüstung");
						}

						final String name = item.getStringOrDefault("Name", baseArmor.getStringOrDefault("Name", ""));

						String be;
						if ("Gesamtrüstung".equals(armorSetting)) {
							be = item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).toString();
						} else {
							be = DSAUtil.threeDecimalPlaces.format(item.getDoubleOrDefault("Behinderung", baseArmor.getDoubleOrDefault("Behinderung",
									item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).doubleValue())));
						}

						final String[] rs = new String[8];
						final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObjOrDefault("Rüstungsschutz", null));
						if ("Gesamtrüstung".equals(armorSetting)
								|| zones == null && !(item.containsKey("Rüstungsschutz") || baseArmor.containsKey("Rüstungsschutz"))) {
							final int RS = item.getIntOrDefault("Gesamtrüstungsschutz", baseArmor.getIntOrDefault("Gesamtrüstungsschutz", 0));
							for (int j = 0; j < 8; ++j) {
								rs[j] = Integer.toString(RS);
							}
						} else if (zones == null) {
							final String pieceRS = DSAUtil.threeDecimalPlaces
									.format(item.getDoubleOrDefault("Rüstungsschutz", baseArmor.getDoubleOrDefault("Rüstungsschutz", 0.0)));
							for (int k = 0; k < 8; ++k) {
								rs[k] = pieceRS;
							}
						} else {
							final int[] zoneValues = new int[8];
							int j = 0;
							for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein",
									"Rechtes Bein" }) {
								zoneValues[j] = zones.getInt(zone);
								rs[j] = Integer.toString(zoneValues[j]);
								++j;
							}
							if ("Zonengesamtrüstung".equals(armorSetting)) {
								for (int k = 0; k < 8; ++k) {
									rs[k] = DSAUtil.threeDecimalPlaces.format((zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4
											+ zoneValues[4] + zoneValues[5] + zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0);
								}
							}
						}

						final Cell notes = new TextCell(item.getStringOrDefault("Anmerkungen", baseArmor.getStringOrDefault("Anmerkungen", " "))).setColSpan(3);

						table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7], notes);
					} else {
						table.addRow(" ", " ", " ", " ", " ", " ", " ", " ", " ", " ", new TextCell(" ").setColSpan(3));
					}
				}
			}
		}
		for (int i = 0; i < additionalArmorRows.get(); ++i) {
			table.addRow(" ", " ", " ", " ", " ", " ", " ", " ", " ", " ", new TextCell(" ").setColSpan(3));
		}

		final Cell sum = new TextCell("Summe:").setHAlign(HAlign.RIGHT);

		final String beSum = hero != null && fill ? DSAUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero)) : " ";

		final Cell rg = new TextCell("Rüstungsgewöhnung:      I      II      III").setHAlign(HAlign.LEFT).setColSpan(9);

		final String beResult = hero != null && fill ? Integer.toString(HeroUtil.getBE(hero)) : " ";

		table.addRow(sum, beSum, rg, "Ergebnis:", beResult);

		bottom.bottom = table.render(document, 397, 12, bottom.bottom, 72, 10) - 5;

		final PDPageContentStream stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), AppendMode.APPEND, true);

		SheetUtil.drawChoiceBox(stream, 238, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 258, bottom.bottom + 15);
		SheetUtil.drawChoiceBox(stream, 281, bottom.bottom + 15);

		if (hero != null && fill) {
			final JSONObject skills = hero.getObj("Sonderfertigkeiten");
			if (skills.containsKey("Rüstungsgewöhnung I")) {
				SheetUtil.checkChoiceBox(stream, 238, bottom.bottom + 15);
			}
			if (skills.containsKey("Rüstungsgewöhnung II")) {
				SheetUtil.checkChoiceBox(stream, 258, bottom.bottom + 15);
			}
			if (skills.containsKey("Rüstungsgewöhnung III")) {
				SheetUtil.checkChoiceBox(stream, 281, bottom.bottom + 15);
			}
		}

		stream.close();
	}

	private void addZoneImage(final PDDocument document, final float shortTablesTop) {
		if ("Keines".equals(zoneImage.get())) return;
		final float shortTablesBottom = bottom.bottom;
		try {
			final BufferedImage image = ImageIO.read(new File(Util.getAppDir() + "/resources/images/zones", zoneImage.get()).toURI().toURL());
			float width = image.getWidth();
			float height = image.getHeight();
			final float ratio = width / height;
			final float maxWidth = 173;
			final float maxHeight = shortTablesTop - shortTablesBottom - 5;
			if (width > maxWidth) {
				width = maxWidth;
				height = 1 / ratio * maxWidth;
			}
			if (height > maxHeight) {
				width = ratio * maxHeight;
				height = maxHeight;
			}
			final PDImageXObject imageObject = JPEGFactory.createFromImage(document, image);
			final PDPageContentStream stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), AppendMode.APPEND, true);
			stream.drawImage(imageObject, 496 - width / 2, shortTablesTop - height, width, height);
			stream.close();
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}
	}

	private void addZonesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(61, FontManager.serif, 9, HAlign.LEFT));
		table.addColumn(new Column(33, FontManager.serif, 9, HAlign.CENTER));
		table.addColumn(new Column(28, FontManager.serif, 9, HAlign.CENTER));
		table.addColumn(new Column(28, FontManager.serif, 9, HAlign.CENTER));
		table.addColumn(new Column(53, FontManager.serif, 9, HAlign.CENTER));
		table.addColumn(new Column(53, FontManager.serif, 9, HAlign.CENTER));
		table.addColumn(new Column(156, FontManager.serif, 9, HAlign.LEFT));
		table.addColumn(new Column(0, FontManager.serif, 9, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Trefferzonen", 1);
		final Cell difficultyTitle = SheetUtil.createTitleCell("Ansage", 1);
		final Cell paTitle = SheetUtil.createTitleCell("PA", 1);
		final Cell rangedTitle = SheetUtil.createTitleCell("FK", 1);
		final Cell chanceTitle = SheetUtil.createTitleCell("Zufall(W20)", 1);
		final Cell healingTitle = SheetUtil.createTitleCell("Heilung", 1);
		final Cell firstTitle = SheetUtil.createTitleCell("Erste/zweite/dritte Wunde", 1);
		final Cell thirdTitle = SheetUtil.createTitleCell("Dritte Wunde zusätzlich", 1);
		table.addRow(nameTitle, difficultyTitle, paTitle, rangedTitle, chanceTitle, healingTitle, firstTitle, thirdTitle);

		final JSONObject zones = ResourceManager.getResource("data/Wunden").getObj("Zonenwunden");

		for (final String zoneName : zones.keySet()) {
			final JSONObject zone = zones.getObj(zoneName);
			final Integer difficulty = zone.getIntOrDefault("Ansage", null);
			final Integer pa = zone.getIntOrDefault("Parade", null);
			final Integer ranged = zone.getIntOrDefault("Fernkampf", null);
			final JSONObject chance = zone.getObjOrDefault("Zufall", null);
			final JSONArray healing = zone.getArrOrDefault("Heilung", null);
			final JSONObject first = zone.getObjOrDefault("Allgemein", null);
			final JSONObject third = zone.getObjOrDefault("Dritte Wunde", null);
			table.addRow(zoneName, difficulty != null ? Util.getSignedIntegerString(difficulty) : "—", pa != null ? Util.getSignedIntegerString(pa) : "—",
					ranged != null ? Util.getSignedIntegerString(ranged) : "—", chance != null ? chance.getStringOrDefault("Text", "—") : "—",
					healing != null ? new TextCell(Util.getSignedIntegerString(healing.getInt(0))).addText("/")
							.addText(Util.getSignedIntegerString(healing.getInt(1))).addText("/").addText(Util.getSignedIntegerString(healing.getInt(2)))
							.setEquallySpaced(true) : "—",
					first != null ? first.getStringOrDefault("Text", " ") : " ", third != null ? third.getStringOrDefault("Text", " ") : " ");
		}

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Kampfbrief", true, false, true, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		if (showCloseCombatWeapons.get()) {
			try {
				addCloseCombatTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showRangedCombatWeapons.get()) {
			try {
				addRangedCombatTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showAmmunition.get()) {
			try {
				addAmmunitionTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		final float shortTablesTop = bottom.bottom;

		if (showInfight.get()) {
			try {
				addInfightTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showDefensiveWeapons.get()) {
			try {
				addDefensiveWeaponsTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showZoneArmor.get()) {
			try {
				addZoneArmorTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showEvasion.get()) {
			try {
				addEvasionTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showEnergies.get()) {
			try {
				addEnergiesTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showZoneArmor.get()) {
			try {
				addZoneImage(document, shortTablesTop);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showZoneTable.get()) {
			try {
				addZonesTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		endCreate(document);
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = new JSONObject(parent);
		settings.put("Als eigenständigen Bogen drucken", separatePage.get());
		settings.put("Leerseite einfügen", emptyPage.get());
		settings.put("Nahkampfwaffen", showCloseCombatWeapons.get());
		settings.put("Zusätzliche Zeilen für Nahkampfwaffen", additionalCloseCombatWeaponRows.get());
		settings.put("Fernkampfwaffen", showRangedCombatWeapons.get());
		settings.put("Zusätzliche Zeilen für Fernkampfwaffen", additionalRangedWeaponRows.get());
		settings.put("Geschosstypen", showAmmunition.get());
		settings.put("Waffenloser Kampf", showInfight.get());
		settings.put("Schilde/Parierwaffen", showDefensiveWeapons.get());
		settings.put("Zusätzliche Zeilen für Schilde/Parierwaffen", additionalDefensiveWeaponRows.get());
		settings.put("Zonenrüstung", showZoneArmor.get());
		settings.put("Zusätzliche Zeilen für Rüstung", additionalArmorRows.get());
		settings.put("Bild für Zonenrüstung", zoneImage.get());
		settings.put("Ausweichen", showEvasion.get());
		settings.put("Lebensenergie/Ausdauer", showEnergies.get());
		settings.put("Trefferzonen", showZoneTable.get());
		return settings;
	}

	private List<String> getZoneImages() {
		final List<String> zoneImages = new ArrayList<>();
		final File[] files = new File(Util.getAppDir() + "/resources/images/zones/").listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isFile()) {
					zoneImages.add(file.getName());
				}
			}
		}
		zoneImages.add(0, "Keines");
		return zoneImages;
	}

	@Override
	public void load() {
		super.load();
		settingsPage.addBooleanChoice("Nahkampfwaffen", showCloseCombatWeapons);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Nahkampfwaffen", additionalCloseCombatWeaponRows, 0, 30);
		settingsPage.addBooleanChoice("Fernkampfwaffen", showRangedCombatWeapons);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Fernkampfwaffen", additionalRangedWeaponRows, 0, 30);
		settingsPage.addBooleanChoice("Geschosstypen", showAmmunition);
		settingsPage.addBooleanChoice("Waffenloser Kampf", showInfight);
		settingsPage.addBooleanChoice("Schilde/Parierwaffen", showDefensiveWeapons);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Schilde/Parierwaffen", additionalDefensiveWeaponRows, 0, 30);
		settingsPage.addBooleanChoice("Zonenrüstung", showZoneArmor);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Rüstung", additionalArmorRows, 0, 30);
		final List<String> zoneImages = getZoneImages();
		if (zoneImages.contains("Amazone.jpg")) {
			zoneImage.set("Amazone.jpg");
		} else if (zoneImages.size() > 1) {
			zoneImage.set(zoneImages.get(1));
		} else {
			zoneImage.set("Keines");
		}
		settingsPage.addStringChoice("Bild für Zonenrüstung", zoneImage, zoneImages);
		settingsPage.addBooleanChoice("Ausweichen", showEvasion);
		settingsPage.addBooleanChoice("Lebensenergie/Ausdauer", showEnergies);
		settingsPage.addBooleanChoice("Trefferzonen", showZoneTable);
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		showCloseCombatWeapons.set(settings.getBoolOrDefault("Nahkampfwaffen", true));
		additionalCloseCombatWeaponRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Nahkampfwaffen", 5));
		showRangedCombatWeapons.set(settings.getBoolOrDefault("Fernkampfwaffen", true));
		additionalRangedWeaponRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Fernkampfwaffen", 5));
		showAmmunition.set(settings.getBoolOrDefault("Geschosstypen", true));
		showInfight.set(settings.getBoolOrDefault("Waffenloser Kampf", true));
		showDefensiveWeapons.set(settings.getBoolOrDefault("Schilde/Parierwaffen", true));
		additionalDefensiveWeaponRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Schilde/Parierwaffen", 5));
		showZoneArmor.set(settings.getBoolOrDefault("Zonenrüstung", true));
		additionalArmorRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Rüstung", 5));
		zoneImage.set(settings.getStringOrDefault("Bild für Zonenrüstung", "Keines"));
		if (!new File(Util.getAppDir() + "/resources/images/zones/" + zoneImage.get()).exists()) {
			final List<String> zoneImages = getZoneImages();
			if (zoneImages.contains("Amazone.jpg")) {
				zoneImage.set("Amazone.jpg");
			} else if (zoneImages.size() > 1) {
				zoneImage.set(zoneImages.get(1));
			} else {
				zoneImage.set("Keines");
			}
		}
		showEvasion.set(settings.getBoolOrDefault("Ausweichen", true));
		showEnergies.set(settings.getBoolOrDefault("Lebensenergie/Ausdauer", true));
		showZoneTable.set(settings.getBoolOrDefault("Trefferzonen", true));
	}

	@Override
	public String toString() {
		return "Kampfbrief";
	}
}
