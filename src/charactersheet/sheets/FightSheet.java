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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.CheckModel;
import org.controlsfx.control.IndexedCheckModel;

import boxtable.cell.Cell;
import boxtable.cell.TableCell;
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
import dsatool.ui.RenameDialog;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Tuple3;
import dsatool.util.Util;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.util.StringConverter;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class FightSheet extends Sheet {

	private static final String ADDITIONAL_ROWS = "Zusätzliche Zeilen";

	private static final float fontSize = 11f;

	private Button addWeaponSet;

	private JSONArray weaponSets;

	public FightSheet() {
		super(771);
	}

	private boolean addAmmunitionTable(final PDDocument document, final TitledPane section) throws IOException {
		int numCols = settingsPage.getInt(sections.get("Fernkampfwaffen"), ADDITIONAL_ROWS).get();

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
			separatePage(document, settingsPage, section);
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
			return true;
		}

		return false;
	}

	private void addArmorValues(final Table table, final JSONObject armorSet, final int BE) {
		table.addCells(BE);
		for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
			table.addCells(HeroUtil.getZoneRS(hero, zone, armorSet));
		}
	}

	private boolean addCloseCombatTable(final PDDocument document, final TitledPane section) throws IOException {
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
						final String type = item.getStringOrDefault("Waffentyp:Primär",
								baseWeapon.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
						final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));
						final String ebe = Integer.toString(closeCombatTalents.getObjOrDefault(type, new JSONObject(null)).getIntOrDefault("BEAdditiv", 0));

						final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArrOrDefault("Waffenmeister", null),
								type, item.getStringOrDefault("Typ", baseWeapon.getString("Typ")));

						final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

						final Integer atValue = HeroUtil.getAT(hero, item, type, true, false, null, false);
						final String at = fillAll && atValue != null ? Integer.toString(atValue) : " ";

						final Integer paValue = HeroUtil.getPA(hero, item, type, false, false);
						final String pa = fillAll ? paValue != null ? Integer.toString(paValue) : "—" : " ";

						final TextCell tpkk = getTPKKCell(item, baseWeapon, weaponMastery);

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
						final String bf = BF != null ? BF.toString() : "—";

						final String notes = HeroUtil.getWeaponNotes(item, baseWeapon, type, hero);

						table.addRow(name, type, ebe, tp, at, pa, tpkk, wm, ini, distance, bf, notes);
					} else {
						table.addRow(" ", " ", " ", " ", " ", " ", "/", "/");
					}
				}
			}
		}
		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ", " ", " ", " ", " ", " ", "/", "/");
		}

		if (table.getNumRows() > 1) {
			separatePage(document, settingsPage, section);
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
			return true;
		}

		return false;
	}

	private boolean addRangedCombatTable(final PDDocument document, final TitledPane section) throws IOException {
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
						final String type = item.getStringOrDefault("Waffentyp:Primär",
								baseWeapon.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
						final String ebe = Integer.toString(rangedCombatTalents.getObjOrDefault(type, new JSONObject(null)).getIntOrDefault("BEAdditiv", 0));

						final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

						final Integer atValue = HeroUtil.getAT(hero, item, type, false, false, null, false);
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
							if (settingsPage.getBool(sections.get("Geschosstypen"), "").get()) {
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

						final String notes = HeroUtil.getWeaponNotes(item, baseWeapon, type, hero);

						table.addRow(name, type, ebe, tp, at, load, distances[0], distances[1], distances[2], distances[3], distances[4], tpdistance[0],
								tpdistance[1], tpdistance[2], tpdistance[3], tpdistance[4], num, notes);
					} else {
						table.addRow("");
					}
				}
			}
		}
		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow("");
		}

		if (table.getNumRows() > 1) {
			separatePage(document, settingsPage, section);
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
			return true;
		}

		return false;
	}

	private void addWeaponSet(final JSONObject weaponSet, final JSONObject settings, final List<String> weaponSetNames, final JSONArray armorSets) {
		final String setName = weaponSet.getString("Name");
		final String name = weaponSet.getStringOrDefault("Name", "Unbenannte Waffenkombination");

		final TitledPane section = settingsPage.addSection(name, true);
		section.setUserData(new Tuple<>(weaponSet, false));
		sections.put(name, section);
		addOwnPageOption(settingsPage, section);

		settingsPage.getBool(section, "").set(weaponSet.getBoolOrDefault("Anzeigen", true));

		section.setOnMouseClicked(e -> {
			if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
				renameWeaponSet(weaponSet, section);
			}
		});

		final ContextMenu menu = section.getContextMenu();

		final MenuItem editItem = new MenuItem("Bearbeiten");
		editItem.setOnAction(event -> renameWeaponSet(weaponSet, section));
		menu.getItems().add(0, editItem);

		final MenuItem removeItem = new MenuItem("Löschen");
		removeItem.setOnAction(e -> {
			weaponSets.remove(weaponSet);
			settingsPage.removeSection(section);
		});
		menu.getItems().add(removeItem);

		final CheckComboBox<JSONObject> mainWeapon = new CheckComboBox<>();
		final List<JSONObject> mainWeapons = new ArrayList<>();
		final List<JSONObject> selectedMainWeapons = new ArrayList<>();

		final CheckComboBox<Tuple<JSONObject, String>> secondaryWeapon = new CheckComboBox<>();
		final List<Tuple<JSONObject, String>> secondaryWeapons = new ArrayList<>();
		final List<Tuple<JSONObject, String>> selectedSecondaryWeapons = new ArrayList<>();

		mainWeapons.add(HeroUtil.brawling);
		mainWeapons.add(HeroUtil.wrestling);

		if (settings.getBoolOrDefault("Raufen", false)) {
			selectedMainWeapons.add(HeroUtil.brawling);
		}
		if (settings.getBoolOrDefault("Ringen", false)) {
			selectedMainWeapons.add(HeroUtil.wrestling);
		}

		final JSONObject noSelection = new JSONObject(null);
		noSelection.put("Name", "");
		final Tuple<JSONObject, String> noSecondarySelection = new Tuple<>(noSelection, null);
		secondaryWeapons.add(noSecondarySelection);

		if (settings.getBoolOrDefault("Keine Seitenwaffe", false)) {
			selectedSecondaryWeapons.add(noSecondarySelection);
		}

		HeroUtil.foreachInventoryItem(hero, item -> item.containsKey("Kategorien"), (item, extraInventory) -> {
			if (item.getArr("Kategorien").contains("Nahkampfwaffe")) {
				for (final String category : item.getArr("Kategorien").getStrings()) {
					if ("Nahkampfwaffe".equals(category)) {
						JSONObject actual = item;
						if (item.containsKey(category)) {
							actual = item.getObj(category);
						}
						mainWeapons.add(actual);
						final JSONArray sets = actual.getArrOrDefault("Waffenkombinationen:Hauptwaffe", null);
						if (sets != null && sets.contains(setName)) {
							selectedMainWeapons.add(actual);
						}
					}
				}
			}
			if (item.getArr("Kategorien").contains("Nahkampfwaffe") || item.getArr("Kategorien").contains("Parierwaffe")
					|| item.getArr("Kategorien").contains("Schild")) {
				for (final String category : item.getArr("Kategorien").getStrings()) {
					if (List.of("Nahkampfwaffe", "Parierwaffe", "Schild").contains(category)) {
						JSONObject actual = item;
						if (item.containsKey(category)) {
							actual = item.getObj(category);
						}
						String actualCategory = null;
						for (final String other : List.of("Nahkampfwaffe", "Parierwaffe", "Schild")) {
							if (!other.equals(category) && item.getArr("Kategorien").contains(other)) {
								actualCategory = category;
								break;
							}
						}
						secondaryWeapons.add(new Tuple<>(actual, actualCategory));
						final JSONArray sets = actual.getArrOrDefault("Waffenkombinationen:Seitenwaffe", null);
						if (sets != null && sets.contains(setName)) {
							selectedSecondaryWeapons.add(new Tuple<>(actual, actualCategory));
						}
					}
				}
			}
		});

		final CheckComboBox<JSONObject> armor = new CheckComboBox<>();
		final List<JSONObject> armorSetList = new ArrayList<>();
		final List<JSONObject> selectedArmor = new ArrayList<>();

		armorSetList.add(noSelection);
		armorSetList.add(null);
		for (int j = 0; j < armorSets.size(); ++j) {
			final JSONObject armorSet = armorSets.getObj(j);
			armorSetList.add(armorSet);
			final JSONArray sets = armorSet.getArrOrDefault("Waffenkombinationen", null);
			if (sets != null && sets.contains(setName)) {
				selectedArmor.add(armorSet);
			}
		}

		if (weaponSetNames == null) {
			selectedArmor.add(null);
		} else {
			weaponSetNames.add(name);

			if (settings.getBoolOrDefault("Keine Rüstung", false)) {
				selectedArmor.add(noSelection);
			}
			if (settings.getBoolOrDefault("Rüstung", false)) {
				selectedArmor.add(null);
			}
		}

		final ComboBox<String> additionalRows = settingsPage.addStringChoice("Zusatzzeilen", List.of("Hauptwaffe", "Seitenwaffe", "Rüstung"));
		additionalRows.getStyleClass().add("disabled-opaque");
		additionalRows.setValue(weaponSet.getStringOrDefault("Typ", "Hauptwaffe"));

		final boolean[] clearing = new boolean[] { false };
		setupWeaponSelection("Hauptwaffe", mainWeapon, mainWeapons, selectedMainWeapons, secondaryWeapon, armor, clearing, additionalRows);
		setupWeaponSelection("Seitenwaffe", secondaryWeapon, secondaryWeapons, selectedSecondaryWeapons, mainWeapon, armor, clearing,
				additionalRows);
		setupWeaponSelection("Rüstung", armor, armorSetList, selectedArmor, mainWeapon, secondaryWeapon, clearing, additionalRows);

		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);
	}

	private void addWeaponSetRow(final Table table, final String type, final JSONObject item, final JSONObject base, final JSONObject hero,
			final JSONObject mainWeapon, final JSONObject mainWeaponBase, final JSONObject secondaryWeapon, final JSONObject secondaryWeaponBase,
			final JSONObject armorSet) {

		String name = item.getStringOrDefault("Name", base != null ? base.getStringOrDefault("Name", "Unbenannt") : "Unbekannt");
		if ("".equals(name)) {
			name = "Keine";
		}
		table.addCells(name);

		final TextCell iniCell = new TextCell();
		table.addCells(iniCell);

		final int BE = HeroUtil.getBE(hero, armorSet);

		final JSONObject skills = hero.getObj("Sonderfertigkeiten");

		int ini = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Initiative-Basis"), hero,
				hero.getObj("Basiswerte").getObj("Initiative-Basis"), false) - (skills.containsKey("Rüstungsgewöhnung III") ? (BE + 1) / 2 : BE);

		ini += addWeaponSetWeaponValues(table, hero, type, false, mainWeapon, mainWeaponBase, secondaryWeapon, armorSet);
		ini += addWeaponSetWeaponValues(table, hero, type, true, secondaryWeapon, secondaryWeaponBase, mainWeapon, armorSet);

		if ("Rüstung".equals(type)) {
			addArmorValues(table, armorSet, BE);
		} else {
			final JSONArray types = item.getArrOrDefault("Waffentypen", base.getArr("Waffentypen"));
			final String weaponType = item.getStringOrDefault("Waffentyp:Primär",
					base.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
			table.addCells(HeroUtil.getWeaponNotes(item, base, weaponType, hero));
		}

		iniCell.addText(Integer.toString(ini));
	}

	private int addWeaponSetWeaponValues(final Table table, final JSONObject hero, final String type, final boolean secondary, final JSONObject weapon,
			final JSONObject baseWeapon, final JSONObject otherWeapon, final JSONObject armorSet) {
		if (weapon == null) {
			table.addCells("—", "—", "—", "— / —", "—");
			return 0;
		}

		final JSONArray types = weapon.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen"));
		final String weaponType = weapon.getStringOrDefault("Waffentyp:Primär",
				baseWeapon.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
		final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArrOrDefault("Waffenmeister", null),
				weaponType, weapon.getStringOrDefault("Typ", baseWeapon.getString("Typ")));

		final String defensiveWeaponType = getWeaponType(weapon, baseWeapon);
		if (defensiveWeaponType == null) {
			table.addCells("—", "—", "—", "— / —", "—");
		} else {
			switch (defensiveWeaponType) {
				case "Nahkampfwaffe" -> {
					table.addCells(HeroUtil.getTPString(hero, weapon, baseWeapon));
					table.addCells(HeroUtil.getAT(hero, baseWeapon, weaponType, true, secondary, otherWeapon, armorSet, false));
					table.addCells(HeroUtil.getPA(hero, baseWeapon, weaponType, secondary, otherWeapon, armorSet, false));
					table.addCells(getTPKKCell(weapon, baseWeapon, weaponMastery));
					table.addCells(String.join("", weapon.getArrOrDefault("Distanzklassen", baseWeapon.getArr("Distanzklassen")).getStrings()));
				}
				case "Schild" -> {
					final Integer shieldAT = HeroUtil.getShieldAT(hero, baseWeapon, armorSet, false);
					if (shieldAT != null && shieldAT > 0) {
						table.addCells(HeroUtil.getShieldTPString(hero, baseWeapon), shieldAT, "S");
						table.addCells(new TextCell("13").addText("/").addText("3").setEquallySpaced(true));
						table.addCells("H");
					} else {
						table.addCells("—", "—", "S", "—", "—");
					}
				}
				case "Parierwaffe" -> {
					final Integer defensiveAT = HeroUtil.getDefensiveWeaponAT(hero, baseWeapon, otherWeapon, armorSet, false);
					if (defensiveAT != null && defensiveAT > 0) {
						table.addCells(HeroUtil.getTPString(hero, weapon, baseWeapon));
						table.addCells(defensiveAT, "P");
						table.addCells(getTPKKCell(weapon, baseWeapon, weaponMastery));
						table.addCells(String.join("", weapon.getArrOrDefault("Distanzklassen", baseWeapon.getArr("Distanzklassen")).getStrings()));
					} else {
						table.addCells("—", "—", "P", "—", "—");
					}
				}
				default -> table.addCells("—", "—", "—", "— / —", "—");
			}
		}

		return weapon.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0))
				+ (weaponMastery != null ? weaponMastery.getIntOrDefault("Initiative:Modifikator", 0) : 0);
	}

	private void addZoneImage(final PDDocument document, final String imageName, final float imageTop, final float armorTableMid) {
		final float shortTablesBottom = bottom.bottom;
		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		if ("Tabelle".equals(imageName)) {
			final float left = 419;
			final float bottom = armorTableMid - 27;
			try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true)) {
				stream.setLineWidth(1);
				stream.moveTo(left + 18, bottom);
				stream.lineTo(left + 54, bottom);
				stream.lineTo(left + 54, bottom + 36);
				stream.lineTo(left, bottom + 36);
				stream.lineTo(left, bottom);
				stream.lineTo(left + 18, bottom);
				stream.lineTo(left + 18, bottom + 54);
				stream.lineTo(left + 36, bottom + 54);
				stream.lineTo(left + 36, bottom);
				stream.moveTo(left, bottom + 18);
				stream.lineTo(left + 54, bottom + 18);
				stream.stroke();
				stream.setLineWidth(0.5f);
				stream.moveTo(left, bottom + 6);
				stream.lineTo(left + 54, bottom + 6);
				stream.moveTo(left, bottom + 24);
				stream.lineTo(left + 54, bottom + 24);
				stream.moveTo(left + 18, bottom + 42);
				stream.lineTo(left + 36, bottom + 42);
				for (int i = 0; i < 3; ++i) {
					for (int j = 0; j < 9; ++j) {
						if (i != 2 || j > 2 && j < 6) {
							stream.moveTo(left + j * 6, bottom + i * 18);
							stream.lineTo(left + j * 6, bottom + 6 + i * 18);
						}
					}
				}
				stream.stroke();

				stream.beginText();
				stream.setFont(FontManager.serifBold, 10);
				stream.setNonStrokingColor(Color.LIGHT_GRAY);
				stream.newLineAtOffset(left + 3, bottom + 8.5f);
				stream.showText("LB");
				stream.newLineAtOffset(18, 0);
				stream.showText("Ba");
				stream.newLineAtOffset(17f, 0);
				stream.showText("RB");
				stream.newLineAtOffset(-35.5f, 18);
				stream.showText("LA");
				stream.newLineAtOffset(35, 0);
				stream.showText("RA");
				stream.newLineAtOffset(-14, 18);
				stream.showText("K");
				stream.newLineAtOffset(-5, -16);
				stream.showText("Br");
				stream.newLineAtOffset(8.5f, -2);
				stream.showText("/");
				stream.newLineAtOffset(1.5f, -2);
				stream.showText("R");
				stream.endText();
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		} else {
			try {
				final BufferedImage image = ImageIO.read(new File(Util.getAppDir() + "/resources/images/zones", imageName).toURI().toURL());

				final float ratio = (float) image.getWidth() / image.getHeight();
				final float maxWidth = 173;
				final float maxHeight = imageTop - shortTablesBottom - 5;

				float width = maxWidth;
				float height = 1 / ratio * maxWidth;

				if (height > maxHeight) {
					width = ratio * maxHeight;
					height = maxHeight;
				}

				float pos = armorTableMid - height / 2;
				if (armorTableMid + height / 2 > imageTop) {
					pos = imageTop - height;
				} else if (pos < shortTablesBottom + 5) {
					pos = shortTablesBottom + 5;
				}

				final PDImageXObject imageObject = JPEGFactory.createFromImage(document, image);
				try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND,
						true)) {
					stream.drawImage(imageObject, 496 - width / 2, pos, width, height);
				} catch (final Exception e) {
					ErrorLogger.logError(e);
				}
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
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

		float wideBottom = bottom.bottom;
		Tuple3<String, Float, Float> zoneImage = null;

		for (final TitledPane section : settingsPage.getSections()) {
			if (!settingsPage.getBool(section, "").get()) {
				continue;
			}

			final String categoryName = settingsPage.getString(section, null).get();

			try {
				switch (categoryName) {
					case "Nahkampfwaffen", "Fernkampfwaffen", "Geschosstypen", "Trefferzonen" -> {
						if (zoneImage != null) {
							addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
							zoneImage = null;
						}
						final boolean addedTable = switch (categoryName) {
							case "Nahkampfwaffen" -> addCloseCombatTable(document, section);
							case "Fernkampfwaffen" -> addRangedCombatTable(document, section);
							case "Geschosstypen" -> addAmmunitionTable(document, section);
							case "Trefferzonen" -> {
								separatePage(document, settingsPage, section);
								addZonesTable(document);
								yield true;
							}
							default -> false;
						};
						if (addedTable) {
							wideBottom = bottom.bottom;
						}
					}
					default -> {
						final Tuple3<Table, Runnable, Boolean> table = switch (categoryName) {
							case "Waffenloser Kampf" -> getInfightTable(document);
							case "Schilde/Parierwaffen" -> getDefensiveWeaponsTable(document, section);
							case "Ausweichen" -> getEvasionTable(document);
							case "Lebensenergie/Ausdauer" -> getEnergiesTable(document);
							default -> {
								@SuppressWarnings("unchecked")
								final Tuple<JSONObject, Boolean> data = (Tuple<JSONObject, Boolean>) section.getUserData();
								if (data == null || data._2) {
									final Tuple3<Table, Runnable, Boolean> armorTable = getZoneArmorTable(document, section, categoryName,
											data != null ? data._1 : null);
									final String imageSetting = settingsPage.getString(section, "Bild").get();
									if (armorTable != null && !"Keines".equals(imageSetting)) {
										if (zoneImage != null) {
											addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
											wideBottom = bottom.bottom;
										}
										zoneImage = new Tuple3<>(imageSetting, wideBottom, bottom.bottom - armorTable._1.getHeight(397) / 2);
									}
									yield armorTable;
								} else {
									yield getWeaponSetTable(document, section, categoryName);
								}
							}
						};
						if (table != null) {
							if (zoneImage != null) {
								if (table._1.getHeight(table._3 ? 397 : 571) > bottom.bottom - 10 || settingsPage.getBool(section, AS_SEPARATE_SHEET).get()) {
									if (List.of("Waffenloser Kampf", "Schilde/Parierwaffen", "Ausweichen", "Lebensenergie/Ausdauer").contains(categoryName)) {
										bottom.bottom = 10;
										addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
										zoneImage = null;
									} else {
										@SuppressWarnings("unchecked")
										final Tuple<JSONObject, Boolean> data = (Tuple<JSONObject, Boolean>) section.getUserData();
										if (data == null || data._2) {
											final String imageSetting = settingsPage.getString(section, "Bild").get();
											if ("Keines".equals(imageSetting)) {
												bottom.bottom = 10;
												addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
												zoneImage = null;
											} else {
												zoneImage = new Tuple3<>(imageSetting, (float) height, height - table._1.getHeight(397) / 2);
											}
										} else {
											addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
											zoneImage = null;
										}
									}
								} else if (!table._3) {
									addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
									zoneImage = null;
								}
							}
							separatePage(document, settingsPage, section);
							bottom.bottom = table._1.render(document, table._3 ? 397 : 571, 12, bottom.bottom, 72, 10) - 5;
							if (!table._3) {
								wideBottom = bottom.bottom;
							}
							table._2.run();
							if (bottom.bottom >= wideBottom) {
								wideBottom = height;
							}
						}
					}
				}

			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		try {
			if (zoneImage != null) {
				addZoneImage(document, zoneImage._1, zoneImage._2, zoneImage._3);
				zoneImage = null;
			}
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		endCreate(document);
	}

	private Tuple3<Table, Runnable, Boolean> getDefensiveWeaponsTable(final PDDocument document, final TitledPane section) {
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
						final String pa = fillAll ? Integer.toString(HeroUtil.getShieldPA(hero, baseWeapon, null, false)) : " ";

						final String atMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0));
						final String paMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0));
						final TextCell wm = new TextCell(atMod).addText("/").addText(paMod).setEquallySpaced(true);

						final String ini = Util.getSignedIntegerString(
								item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0)));

						final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
						final String bf = BF != null ? BF.toString() : "—";

						final String notes = HeroUtil.getItemNotes(item, baseWeapon);

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

						final int pa = HeroUtil.getDefensiveWeaponPA(hero, baseWeapon, null, false);
						final String paString = pa != Integer.MIN_VALUE ? Util.getSignedIntegerString(pa) : "—";

						final String atMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Attackemodifikator", 0));
						final String paMod = Util.getSignedIntegerString(weaponModifier.getIntOrDefault("Parademodifikator", 0));
						final TextCell wm = new TextCell(atMod).addText("/").addText(paMod).setEquallySpaced(true);

						final String ini = Util.getSignedIntegerString(
								item.getIntOrDefault("Initiative:Modifikator", baseWeapon.getIntOrDefault("Initiative:Modifikator", 0)));

						final Integer BF = item.getIntOrDefault("Bruchfaktor", baseWeapon.getIntOrDefault("Bruchfaktor", null));
						final String bf = BF != null ? BF.toString() : "—";

						final String notes = item.getStringOrDefault("Anmerkungen", baseWeapon.getStringOrDefault("Anmerkungen", " "));

						table.addRow(name, "P", paString, wm, ini, bf, notes);
					} else {
						table.addRow(" ", " ", " ", "/");
					}
				}
			}
		}
		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ", " ", " ", "/");
		}

		if (table.getNumRows() > 1) return new Tuple3<>(table, () -> {}, true);

		return null;
	}

	private Tuple3<Table, Runnable, Boolean> getEnergiesTable(final PDDocument document) {
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

		return new Tuple3<>(table, () -> {}, true);
	}

	private Tuple3<Table, Runnable, Boolean> getEvasionTable(final PDDocument document) {
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

		if (hero != null && fill) {
			final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero,
					hero.getObj("Basiswerte").getObj("Parade-Basis"), true);
			final String pa = Integer.toString(PABase);

			final int BE = HeroUtil.getBE(hero);
			final String be = Integer.toString(-BE);

			final TextCell evading = new TextCell("     +3").addText("/").addText("     +3").addText("/").addText("     +3").setEquallySpaced(true);
			final TextCell acrobatics = new TextCell("     +1").addText("/").addText("     +1").addText("/").addText("     +1").addText("/").addText("     +1")
					.addText("/").addText("     +1").setEquallySpaced(true);

			final int speedBonus = hero.getObj("Vorteile").containsKey("Flink") ? hero.getObj("Vorteile").getObj("Flink").getIntOrDefault("Stufe", 1) : 1;
			final TextCell speed = new TextCell("     +" + speedBonus).addText("/").addText("     -1").setEquallySpaced(true);

			String results = Integer.toString(HeroUtil.getEvasion(hero));

			if (hero.getObj("Nachteile").containsKey("Zwergenwuchs")) {
				results += "(-1)";
			}

			table.addRow(pa, be, evading, acrobatics, speed, results);
		} else {
			table.addRow(" ");
		}

		return new Tuple3<>(table, () -> {
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), AppendMode.APPEND, true)) {
				if (hero != null && fill) {
					final JSONObject specialSkills = hero.getObj("Sonderfertigkeiten");
					if (specialSkills.containsKey("Ausweichen I")) {
						SheetUtil.checkChoiceBox(stream, 80, bottom.bottom + 15);
					}
					if (specialSkills.containsKey("Ausweichen II")) {
						SheetUtil.checkChoiceBox(stream, 110, bottom.bottom + 15);
					}
					if (specialSkills.containsKey("Ausweichen III")) {
						SheetUtil.checkChoiceBox(stream, 141, bottom.bottom + 15);
					}

					final JSONObject acrobaticsTalent = hero.getObj("Talente").getObj("Körperliche Talente").getObjOrDefault("Akrobatik", null);
					if (acrobaticsTalent != null) {
						final int acrobaticsValue = acrobaticsTalent.getIntOrDefault("TaW", 0);
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
					}

					if (hero.getObj("Vorteile").containsKey("Flink")) {
						SheetUtil.checkChoiceBox(stream, 318, bottom.bottom + 15);
					}
					if (hero.getObj("Nachteile").containsKey("Behäbig")) {
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
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		},
				true);
	}

	private Tuple3<Table, Runnable, Boolean> getInfightTable(final PDDocument document) {
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
			final String at1 = fillAll ? Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Raufen", true, false, null, false)) : " ";
			final String pa1 = fillAll ? Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Raufen", false, false)) : " ";
			final String notes1 = HeroUtil.getWeaponNotes(HeroUtil.infight, HeroUtil.infight, "Raufen", hero);
			table.addRow("Raufen", tp, at1, pa1, tpkk, "±0", notes1);
			final String at2 = fillAll ? Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Ringen", true, false, null, false)) : " ";
			final String pa2 = fillAll ? Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Ringen", false, false)) : " ";
			final String notes2 = HeroUtil.getWeaponNotes(HeroUtil.infight, HeroUtil.infight, "Ringen", hero);
			table.addRow("Ringen", tp, at2, pa2, tpkk, "±0", notes2);
		} else {
			table.addRow("Raufen", " ", " ", " ", tpkk, "±0");
			table.addRow("Ringen", " ", " ", " ", tpkk, "±0");
		}

		return new Tuple3<>(table, () -> {}, true);
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);

		final JSONObject fight = hero == null ? null : hero.getObj("Kampf");
		if (fight != null) {
			fight.removeKey("Waffenkombinationen");
			HeroUtil.foreachInventoryItem(hero, item -> true, (item, extraInventory) -> {
				item.removeKey("Waffenkombinationen:Hauptwaffe");
				item.removeKey("Waffenkombinationen:Seitenwaffe");
				for (final String category : List.of("Nahkampfwaffe", "Fernkampfwaffe", "Schild", "Parierwaffe")) {
					if (item.containsKey(category)) {
						item.getObj(category).removeKey("Waffenkombinationen:Hauptwaffe");
						item.getObj(category).removeKey("Waffenkombinationen:Seitenwaffe");
					}
				}
			});
			for (final JSONObject armorSet : fight.getArr("Rüstungskombinationen").getObjs()) {
				armorSet.removeKey("Waffenkombinationen");
			}
		}
		final JSONArray weaponSets = fight == null ? null : fight.getArr("Waffenkombinationen");

		final JSONObject categories = new JSONObject(settings);
		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject category = new JSONObject(categories);
			category.put("Anzeigen", settingsPage.getBool(section, "").get());
			category.put(AS_SEPARATE_SHEET, settingsPage.getBool(section, AS_SEPARATE_SHEET).get());
			switch (name) {
				case "Geschosstypen", "Waffenloser Kampf", "Ausweichen", "Lebensenergie/Ausdauer", "Trefferzonen" -> {}
				default -> category.put(ADDITIONAL_ROWS, settingsPage.getInt(section, ADDITIONAL_ROWS).get());
			}
			if ("Rüstung".equals(name) || section.getUserData() != null && ((Tuple<JSONObject, Boolean>) section.getUserData())._2) {
				category.put("Bild", settingsPage.getString(section, "Bild").get());
			}
			if (section.getUserData() != null && !((Tuple<JSONObject, Boolean>) section.getUserData())._2) {
				final JSONObject weaponSet = ((Tuple<JSONObject, Boolean>) section.getUserData())._1;
				weaponSet.put("Name", name);
				weaponSet.put("Typ", settingsPage.getString(section, "Zusatzzeilen").get());
				weaponSets.add(weaponSet);
				for (final JSONObject weapon : ((CheckModel<JSONObject>) settingsPage.getProperty(section, "Hauptwaffe").getValue()).getCheckedItems()) {
					if (weapon == HeroUtil.brawling) {
						category.put("Raufen", true);
					} else if (weapon == HeroUtil.wrestling) {
						category.put("Ringen", true);
					} else {
						weapon.getArr("Waffenkombinationen:Hauptwaffe").add(name);
					}
				}
				for (final Tuple<JSONObject, String> weapon : ((CheckModel<Tuple<JSONObject, String>>) settingsPage.getProperty(section, "Seitenwaffe")
						.getValue()).getCheckedItems()) {
					if (weapon._1.getParent() == null) {
						category.put("Keine Seitenwaffe", true);
					} else {
						weapon._1.getArr("Waffenkombinationen:Seitenwaffe").add(name);
					}
				}
				for (final JSONObject armor : ((CheckModel<JSONObject>) settingsPage.getProperty(section, "Rüstung").getValue()).getCheckedItems()) {
					if (armor == null) {
						category.put("Rüstung", true);
					} else if (armor.getParent() == null) {
						category.put("Keine Rüstung", true);
					} else {
						armor.getArr("Waffenkombinationen").add(name);
					}
				}
			}
			categories.put(name, category);
		}
		settings.put("Kategorien", categories);

		return settings;
	}

	private TextCell getTPKKCell(final JSONObject weapon, final JSONObject baseWeapon, final JSONObject weaponMastery) {
		final JSONObject TPKKValues = weapon.getObjOrDefault("Trefferpunkte/Körperkraft",
				baseWeapon.getObjOrDefault("Trefferpunkte/Körperkraft", null));
		final int threshold = TPKKValues != null ? TPKKValues.getIntOrDefault("Schwellenwert", Integer.MIN_VALUE) : Integer.MIN_VALUE;
		final int step = TPKKValues != null ? TPKKValues.getIntOrDefault("Schadensschritte", Integer.MIN_VALUE) : Integer.MIN_VALUE;
		final String tpkkThreshold = threshold == Integer.MIN_VALUE ? "—" : Integer.toString(threshold
				+ (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schwellenwert", 0) : 0));
		final String tpkkStep = step == Integer.MIN_VALUE ? "—" : Integer.toString(
				step + (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schadensschritte", 0) : 0));
		return new TextCell(tpkkThreshold).addText("/").addText(tpkkStep).setEquallySpaced(true);
	}

	@SuppressWarnings("unchecked")
	private Tuple3<Table, Runnable, Boolean> getWeaponSetTable(final PDDocument document, final TitledPane section, final String setName) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe().invert(true));
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(274, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(297, FontManager.serif, fontSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell(setName, 2));

		final String type = settingsPage.getString(section, "Zusatzzeilen").getValue();

		final List<Table> fixedTables = new ArrayList<>(2);

		JSONObject mainWeapon = null;
		JSONObject mainBase = null;
		JSONObject item = null;
		JSONObject baseItem = null;
		JSONObject actualArmor = new JSONObject(null);

		final ObservableList<JSONObject> mainWeapons = ((CheckModel<JSONObject>) settingsPage.getProperty(section, "Hauptwaffe").getValue()).getCheckedItems();
		final ObservableList<Tuple<JSONObject, String>> secondaryWeapons = ((CheckModel<Tuple<JSONObject, String>>) settingsPage
				.getProperty(section, "Seitenwaffe").getValue()).getCheckedItems();

		for (final String current : List.of("Hauptwaffe", "Seitenwaffe")) {
			final ObservableList<?> currentItems = "Hauptwaffe".equals(current) ? mainWeapons : secondaryWeapons;
			if (!current.equals(type) && currentItems.size() > 0) {
				final Table fixedTable = new Table().setFiller(SheetUtil.stripe()).setBorder(0, 0, 0, 0);
				fixedTables.add(fixedTable);
				item = "Hauptwaffe".equals(current) ? (JSONObject) currentItems.get(0) : ((Tuple<JSONObject, String>) currentItems.get(0))._1;
				baseItem = item;
				if (item.getParent() instanceof final JSONObject parent) {
					baseItem = parent;
				}
				if ("Hauptwaffe".equals(current)) {
					mainWeapon = item;
					mainBase = baseItem;
				}
				final float width = fixedTables.size() == 1 ? 121 : 153;
				fixedTable.addColumn(new Column(width, width, FontManager.serif, 4, fontSize, HAlign.LEFT));
				final Column lastColumn = new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT);
				if (fixedTables.size() == 1) {
					lastColumn.setRightBorder(0);
				}
				fixedTable.addColumn(lastColumn);

				final Cell nameTitle = new TextCell(current, FontManager.serifBold, 0, 6f).setBackground(Color.white);
				final Cell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 0, 6f).setBackground(Color.white);
				fixedTable.addRow(nameTitle, notesTitle);

				final JSONArray types = item.getArrOrDefault("Waffentypen", baseItem.getArr("Waffentypen"));
				final String weaponType = item.getStringOrDefault("Waffentyp:Primär",
						baseItem.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
				final String name = item.getStringOrDefault("Name", baseItem.getStringOrDefault("Name", "Unbenannt"));
				final String notes = HeroUtil.getWeaponNotes(item, baseItem, weaponType, hero);
				fixedTable.addRow(name, notes);
			}
		}

		final ObservableList<JSONObject> armor = ((CheckModel<JSONObject>) settingsPage.getProperty(section, "Rüstung").getValue()).getCheckedItems();
		if (!"Rüstung".equals(type) && armor.size() > 0) {
			final Table armorTable = new Table().setFiller(SheetUtil.stripe()).setBorder(0, 0, 0, 0);
			fixedTables.add(armorTable);
			final float nameWidth = fixedTables.size() == 1 ? 130 : 153;
			armorTable.addColumn(new Column(nameWidth, nameWidth, FontManager.serif, 4, fontSize, HAlign.LEFT));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			armorTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			final Column lastColumn = new Column(16, FontManager.serif, fontSize, HAlign.CENTER);
			if (fixedTables.size() == 1) {
				lastColumn.setRightBorder(0);
			}
			armorTable.addColumn(lastColumn);
			for (final String title : List.of("Rüstung", "BE", "K", "Br", "R", "Ba", "LA", "RA", "LB", "RB")) {
				armorTable.addCells(new TextCell(title, FontManager.serifBold, 0, 6f).setBackground(Color.white));
			}
			actualArmor = armor.get(0);
			final String armorSetName = actualArmor == null ? "Rüstung" : actualArmor.getStringOrDefault("Name", "Unbenannt");
			armorTable.addCells(armorSetName);
			addArmorValues(armorTable, actualArmor, HeroUtil.getBE(hero, actualArmor));
		}

		if (fixedTables.size() == 1) {
			final Table empty = new Table();
			empty.addColumn(new Column(0, FontManager.serif, 0, HAlign.LEFT));
			empty.addRow(new TextCell("").setBackground(Color.white).setMinHeight(fixedTables.get(0).getHeight(275)));
			fixedTables.add(empty);
		}

		final Table itemsTable = new Table().setFiller(SheetUtil.stripe().invert(true));
		itemsTable.addColumn(new Column(101, 101, FontManager.serif, 4, fontSize, HAlign.LEFT));
		itemsTable.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(35, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(53, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(35, FontManager.serif, fontSize, HAlign.CENTER));
		itemsTable.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell nameTitle = new TextCell(type, FontManager.serifBold, 0, 6f);
		final Cell iniTitle = new TextCell("INI", FontManager.serifBold, 0, 6f);
		final Cell tpTitle = new TextCell("TP", FontManager.serifBold, 0, 6f);
		final Cell atTitle = new TextCell("AT", FontManager.serifBold, 0, 6f);
		final Cell paTitle = new TextCell("PA", FontManager.serifBold, 0, 6f);
		final Cell tpkkTitle = new TextCell("TP/KK", FontManager.serifBold, 0, 6f);
		final Cell dkTitle = new TextCell("DK", FontManager.serifBold, 0, 6f);
		final Cell tp2Title = new TextCell("TP 2", FontManager.serifBold, 0, 6f);
		final Cell at2Title = new TextCell("AT 2", FontManager.serifBold, 0, 6f);
		final Cell pa2Title = new TextCell("PA 2", FontManager.serifBold, 0, 6f);
		final Cell tpkk2Title = new TextCell("TP/KK 2", FontManager.serifBold, 0, 6f);
		final Cell dk2Title = new TextCell("DK 2", FontManager.serifBold, 0, 6f);

		switch (type) {
			case "Hauptwaffe" -> {
				itemsTable.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

				final Cell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 0, 6f);
				itemsTable.addRow(nameTitle, iniTitle, tpTitle, atTitle, paTitle, tpkkTitle, dkTitle, tp2Title, at2Title, pa2Title, tpkk2Title, dk2Title,
						notesTitle);

				for (final JSONObject weapon : mainWeapons) {
					JSONObject base = weapon;
					if (weapon.getParent() instanceof final JSONObject parent) {
						base = parent;
					}
					addWeaponSetRow(itemsTable, type, weapon, base, hero, weapon, base, item, baseItem, actualArmor);
				}
			}
			case "Seitenwaffe" -> {
				itemsTable.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

				final Cell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 0, 6f);
				itemsTable.addRow(nameTitle, iniTitle, tpTitle, atTitle, paTitle, tpkkTitle, dkTitle, tp2Title, at2Title, pa2Title, tpkk2Title, dk2Title,
						notesTitle);

				for (final Tuple<JSONObject, String> secondary : secondaryWeapons) {
					final JSONObject weapon = secondary._1;
					JSONObject base = weapon;
					if (weapon.getParent() instanceof final JSONObject parent) {
						base = parent;
					}
					addWeaponSetRow(itemsTable, type, weapon, base, hero, mainWeapon, mainBase, weapon, base, actualArmor);
				}
			}
			case "Rüstung" -> {
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
				itemsTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));

				final Cell beTitle = new TextCell("BE", FontManager.serifBold, 0, 6f);
				final Cell kTitle = new TextCell("K", FontManager.serifBold, 0, 6f);
				final Cell brTitle = new TextCell("Br", FontManager.serifBold, 0, 6f);
				final Cell rTitle = new TextCell("R", FontManager.serifBold, 0, 6f);
				final Cell baTitle = new TextCell("Ba", FontManager.serifBold, 0, 6f);
				final Cell laTitle = new TextCell("LA", FontManager.serifBold, 0, 6f);
				final Cell raTitle = new TextCell("RA", FontManager.serifBold, 0, 6f);
				final Cell lbTitle = new TextCell("LB", FontManager.serifBold, 0, 6f);
				final Cell rbTitle = new TextCell("RB", FontManager.serifBold, 0, 6f);
				itemsTable.addRow(nameTitle, iniTitle, tpTitle, atTitle, paTitle, tpkkTitle, dkTitle, tp2Title, at2Title, pa2Title, tpkk2Title, dk2Title,
						beTitle, kTitle, brTitle, rTitle, baTitle, laTitle, raTitle, lbTitle, rbTitle);

				for (final JSONObject armorSet : armor) {
					JSONObject actualArmorSet = armorSet;
					if (armorSet == null) {
						actualArmorSet = new JSONObject(null);
						actualArmorSet.put("Name", "Rüstung");
					}
					addWeaponSetRow(itemsTable, "Rüstung", actualArmorSet, actualArmorSet, hero, mainWeapon, mainBase, item, baseItem, armorSet);
				}
			}
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			itemsTable.addRow(" ", " ", " ", " ", " ", "/", " ", " ", " ", " ", "/");
		}

		if (!fixedTables.isEmpty()) {
			table.addRow(new TableCell(fixedTables.get(0)), new TableCell(fixedTables.get(1)));
		} else {
			table.addRow();
		}
		table.addRow(new TableCell(itemsTable).setColSpan(2));

		if (itemsTable.getNumRows() > 1) return new Tuple3<>(table, () -> {}, false);

		return null;
	}

	private String getWeaponType(final JSONObject weapon, final JSONObject baseWeapon) {
		final String weaponType = baseWeapon.keyOf(weapon);
		if (weaponType == null) {
			final JSONArray categories = weapon.getArr("Kategorien");
			if (categories.contains("Nahkampfwaffe")) return "Nahkampfwaffe";
			if (categories.contains("Schild")) return "Schild";
			if (categories.contains("Parierwaffe")) return "Parierwaffe";
			final String name = weapon.getString("Name");
			if ("Raufen".equals(name) || "Ringen".equals(name)) return "Nahkampfwaffe";
		}
		return weaponType;
	}

	private Tuple3<Table, Runnable, Boolean> getZoneArmorTable(final PDDocument document, final TitledPane section, final String title,
			final JSONObject armorSet) {
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

		final Cell nameTitle = SheetUtil.createTitleCell(title, 1);
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
			HeroUtil.foreachInventoryItem(hero, item -> item.containsKey("Kategorien") && item.getArr("Kategorien").contains("Rüstung"),
					(item, extraInventory) -> {
						final JSONArray sets = item.getArrOrDefault("Rüstungskombinationen", null);
						if (armorSet == null && (sets == null || sets.size() == 0)
								|| armorSet != null && sets != null && sets.contains(armorSet.getString("Name"))) {
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
											rs[k] = DSAUtil.threeDecimalPlaces
													.format((zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4
															+ zoneValues[4] + zoneValues[5] + zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0);
										}
									}
								}

								final Cell notes = new TextCell(HeroUtil.getItemNotes(item, baseArmor)).setColSpan(3);

								table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7], notes);
							} else {
								table.addRow(" ", " ", " ", " ", " ", " ", " ", " ", " ", " ", new TextCell(" ").setColSpan(3));
							}
						}
					});
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ", " ", " ", " ", " ", " ", " ", " ", " ", " ", new TextCell(" ").setColSpan(3));
		}

		final Cell sum = new TextCell("Summe:").setHAlign(HAlign.RIGHT);

		final String beSum = hero != null && fill ? DSAUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero, armorSet)) : " ";

		final Cell rg = new TextCell("Rüstungsgewöhnung:      I      II      III").setHAlign(HAlign.LEFT).setColSpan(9);

		final String beResult = hero != null && fill ? Integer.toString(HeroUtil.getBE(hero, armorSet)) : " ";

		table.addRow(sum, beSum, rg, "Ergebnis:", beResult);

		if (table.getNumRows() > 2) return new Tuple3<>(table, () -> {
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), AppendMode.APPEND, true)) {
				SheetUtil.drawChoiceBox(stream, 238, bottom.bottom + 15);
				SheetUtil.drawChoiceBox(stream, 258, bottom.bottom + 15);
				SheetUtil.drawChoiceBox(stream, 281, bottom.bottom + 15);

				if (hero != null && fill) {
					final JSONObject skills = hero.getObj("Sonderfertigkeiten");
					if (skills.containsKey("Rüstungsgewöhnung I") && HeroUtil.hasReducedBE(hero, armorSet)) {
						SheetUtil.checkChoiceBox(stream, 238, bottom.bottom + 15);
					}
					if (skills.containsKey("Rüstungsgewöhnung II")) {
						SheetUtil.checkChoiceBox(stream, 258, bottom.bottom + 15);
					}
					if (skills.containsKey("Rüstungsgewöhnung III")) {
						SheetUtil.checkChoiceBox(stream, 281, bottom.bottom + 15);
					}
				}
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		},
				true);

		return null;
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
		zoneImages.add(1, "Tabelle");

		return zoneImages;
	}

	@Override
	public void load() {
		super.load();

		final TitledPane closeCombatSection = settingsPage.addSection("Nahkampfwaffen", true);
		sections.put("Nahkampfwaffen", closeCombatSection);
		addOwnPageOption(settingsPage, closeCombatSection);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);

		final TitledPane rangedCombatSection = settingsPage.addSection("Fernkampfwaffen", true);
		sections.put("Fernkampfwaffen", rangedCombatSection);
		addOwnPageOption(settingsPage, rangedCombatSection);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);

		final TitledPane ammunitionSection = settingsPage.addSection("Geschosstypen", true);
		sections.put("Geschosstypen", ammunitionSection);
		addOwnPageOption(settingsPage, ammunitionSection);

		final TitledPane infightSection = settingsPage.addSection("Waffenloser Kampf", true);
		sections.put("Waffenloser Kampf", infightSection);
		addOwnPageOption(settingsPage, infightSection);

		final TitledPane defensiveWeaponsSection = settingsPage.addSection("Schilde/Parierwaffen", true);
		sections.put("Schilde/Parierwaffen", defensiveWeaponsSection);
		addOwnPageOption(settingsPage, defensiveWeaponsSection);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);

		final TitledPane armorSection = settingsPage.addSection("Rüstung", true);
		sections.put("Rüstung", armorSection);
		addOwnPageOption(settingsPage, armorSection);
		settingsPage.addStringChoice("Bild", getZoneImages());
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);

		final TitledPane evasionSection = settingsPage.addSection("Ausweichen", true);
		sections.put("Ausweichen", evasionSection);
		addOwnPageOption(settingsPage, evasionSection);

		final TitledPane energiesSection = settingsPage.addSection("Lebensenergie/Ausdauer", true);
		sections.put("Lebensenergie/Ausdauer", energiesSection);
		addOwnPageOption(settingsPage, energiesSection);

		final TitledPane zonesSection = settingsPage.addSection("Trefferzonen", true);
		sections.put("Trefferzonen", zonesSection);
		addOwnPageOption(settingsPage, zonesSection);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);

		settingsPage.clear();
		sections.clear();
		load();
		super.loadSettings(settings);

		final List<String> weaponSetNames = new ArrayList<>();
		final List<String> armorSetNames = new ArrayList<>();

		final JSONArray armorSets = hero == null ? null : hero.getObj("Kampf").getArrOrDefault("Rüstungskombinationen", new JSONArray(null));
		final JSONObject categories = settings.getObjOrDefault("Kategorien", new JSONObject(null));

		if (hero != null) {
			final JSONArray actualWeaponSets = hero.getObj("Kampf").getArrOrDefault("Waffenkombinationen", new JSONArray(null));
			weaponSets = actualWeaponSets.clone(null);
			for (int i = 0; i < actualWeaponSets.size(); ++i) {
				final JSONObject actualWeaponSet = actualWeaponSets.getObj(i);
				addWeaponSet(actualWeaponSet, categories.getObj(actualWeaponSet.getString("Name")), weaponSetNames, armorSets);
			}

			for (int i = 0; i < armorSets.size(); ++i) {
				final JSONObject armorSet = armorSets.getObj(i);
				final String name = armorSet.getStringOrDefault("Name", "Unbenannte Rüstungskombination");
				armorSetNames.add(name);
				final TitledPane section = settingsPage.addSection(name, true);
				section.setUserData(new Tuple<>(armorSet, true));
				sections.put(name, section);
				addOwnPageOption(settingsPage, section);
				settingsPage.addStringChoice("Bild", getZoneImages());
				settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);
			}
		} else {
			weaponSets = new JSONArray(null);
		}

		final List<String> order = new ArrayList<>(List.of("Nahkampfwaffen", "Fernkampfwaffen", "Geschosstypen", "Waffenloser Kampf", "Schilde/Parierwaffen",
				"Rüstung", "Ausweichen", "Lebensenergie/Ausdauer", "Trefferzonen"));
		order.addAll(5, armorSetNames);
		order.addAll(weaponSetNames);
		orderSections(order);
		orderSections(categories.keySet());

		settingsPage.endSection();
		addWeaponSet = new Button("Waffenkombination hinzufügen");
		addWeaponSet.setOnAction(e -> {
			renameWeaponSet(null, null);
		});
		settingsPage.addNode(addWeaponSet);

		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject category = categories.getObjOrDefault(name, new JSONObject(null));
			final boolean show = switch (name) {
				case "Rüstung" -> "Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart"));
				case "Trefferzonen" -> !"Gesamtrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart"));
				default -> true;
			};
			settingsPage.getBool(section, "").set(category.getBoolOrDefault("Anzeigen", show));
			settingsPage.getBool(section, AS_SEPARATE_SHEET).set(category.getBoolOrDefault(AS_SEPARATE_SHEET, false));
			switch (name) {
				case "Geschosstypen", "Waffenloser Kampf", "Ausweichen", "Lebensenergie/Ausdauer", "Trefferzonen" -> {}
				default -> {
					final int additionalRows = List.of("Nahkampfwaffen", "Fernkampfwaffen", "Schilde/Parierwaffen", "Rüstung").contains(name) ? 5 : 0;
					settingsPage.getInt(section, ADDITIONAL_ROWS).set(category.getIntOrDefault(ADDITIONAL_ROWS, additionalRows));
					if ("Rüstung".equals(name) || section.getUserData() != null && ((Tuple<JSONObject, Boolean>) section.getUserData())._2) {
						final List<String> zoneImages = getZoneImages();
						final String zoneImageDefault = zoneImages.contains("Kriegerin.png") ? "Kriegerin.png"
								: zoneImages.size() > 1 ? zoneImages.get(1) : "Keines";
						final StringProperty zoneImage = settingsPage.getString(section, "Bild");
						zoneImage.set(category.getStringOrDefault("Bild", zoneImageDefault));
						if (!zoneImages.contains(zoneImage.get())) {
							zoneImage.set(zoneImageDefault);
						}
					}
				}
			}
		}
	}

	private void renameWeaponSet(final JSONObject weaponSet, final TitledPane section) {
		new RenameDialog(settingsPage.getControl().getScene().getWindow(), "Waffenkombination", "Waffenkombinationen", weaponSets,
				weaponSet,
				(oldName, newName) -> {
					if (weaponSet == null) {
						settingsPage.removeNode(addWeaponSet);
						final JSONArray armorSets = hero == null ? null : hero.getObj("Kampf").getArrOrDefault("Rüstungskombinationen", new JSONArray(null));
						addWeaponSet(weaponSets.getObj(weaponSets.size() - 1), new JSONObject(null), null, armorSets);
						settingsPage.endSection();
						settingsPage.addNode(addWeaponSet);
					} else {
						settingsPage.getString(section, null).set(newName);
					}
				}, List.of("Rüstung"));
	}

	@SuppressWarnings("unchecked")
	private <A, B, C> void setupWeaponSelection(final String type, final CheckComboBox<A> current, final List<A> items,
			final List<A> selectedItems, final CheckComboBox<B> other1, final CheckComboBox<C> other2, final boolean[] clearing,
			final ComboBox<String> additionalRows) {

		if ("Seitenwaffe".equals(type)) {
			current.setConverter((StringConverter<A>) new StringConverter<Tuple<JSONObject, String>>() {
				@Override
				public Tuple<JSONObject, String> fromString(final String name) {
					return null;
				}

				@Override
				public String toString(final Tuple<JSONObject, String> item) {
					final JSONValue parent = item._1.getParent();
					String name = item._1.getStringOrDefault("Name",
							parent instanceof final JSONObject p ? p.getStringOrDefault("Name", "Unbenannt") : "Unbenannt");
					if (item._2 != null) {
						name += " (" + item._2 + ")";
					} else if ("".equals(name)) {
						name = "Keine";
					}
					return name;
				}
			});
		} else if ("Rüstung".equals(type)) {
			current.setConverter((StringConverter<A>) new StringConverter<JSONObject>() {
				@Override
				public JSONObject fromString(final String name) {
					return null;
				}

				@Override
				public String toString(final JSONObject item) {
					if (item == null) return "Rüstung";
					String name = item.getString("Name");
					if (name == null && item.getParent() != null) {
						final JSONValue parent = item.getParent();
						if (parent instanceof final JSONObject obj) {
							name = obj.getStringOrDefault("Name", "Unbenannt");
						}
					} else if ("".equals(name)) {
						name = "Keine";
					}
					return name;
				}
			});
		} else {
			current.setConverter((StringConverter<A>) DSAUtil.itemNameConverter);
		}
		current.setPrefWidth(150);
		settingsPage.createLine(type, current, current.checkModelProperty());

		final IndexedCheckModel<A> currentSelection = current.getCheckModel();
		final ObservableList<A> selected = currentSelection.getCheckedItems();
		final ObservableList<B> other1Selected = other1.getCheckModel().getCheckedItems();
		final ObservableList<C> other2Selected = other2.getCheckModel().getCheckedItems();

		current.getItems().addAll(items);
		for (final A item : selectedItems) {
			currentSelection.check(item);
		}
		if (selectedItems.size() > 1) {
			additionalRows.setDisable(true);
		}

		currentSelection.getCheckedItems().addListener((ListChangeListener<A>) change -> {
			if (!clearing[0]) {
				if (selected.size() > 1 && (other1Selected.size() > 1 || other2Selected.size() > 1)) {
					clearing[0] = true;
					current.getItems().clear();
					current.getItems().addAll(items);
					currentSelection.clearChecks();
					currentSelection.check(selectedItems.get(0));
					clearing[0] = false;
				} else {
					selectedItems.clear();
					selectedItems.addAll(selected);
				}
				if (selected.size() > 1) {
					additionalRows.setValue(type);
					additionalRows.setDisable(true);
				} else if (other1Selected.size() < 2 && other2Selected.size() < 2) {
					additionalRows.setDisable(false);
				}
			}
		});
	}

	@Override
	public String toString() {
		return "Kampfbrief";
	}
}
