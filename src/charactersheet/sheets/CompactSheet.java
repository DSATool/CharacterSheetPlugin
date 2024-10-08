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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import javafx.scene.control.TitledPane;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class CompactSheet extends Sheet {

	private static final String ATTRIBUTES = "Eigenschaften und Basiswerte";
	private static final String ADDITIONAL_ROWS = "Zusätzliche Zeilen";
	private static final String GROUP_BASIC_TALENTS = "Basistalente gruppieren";
	private static final String MARK_BASIC_TALENTS = "Basistalente markieren";
	private static final String VALUES_FOR_ATTRIBUTES = "Eigenschaftswerte statt Eigenschaften anzeigen";

	private static final float fontSize = 10f;

	public CompactSheet() {
		super(842, false);
	}

	private void addAttributesTable(final PDDocument document) throws IOException {
		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		final JSONObject actualAttributes = hero.getObj("Eigenschaften");

		final int numAttributes = attributes.size() + 3;

		final Table table = new Table().setBorder(0, 0, 0, 0);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		for (int i = 0; i < numAttributes; ++i) {
			table.addColumn(new Column(291.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		for (final String attribute : attributes.keySet()) {
			table.addCells(attribute, actualAttributes.getObj(attribute).getIntOrDefault("Wert", 0));
		}

		table.addCells("SO", HeroUtil.getCurrentValue(hero.getObj("Basiswerte").getObj("Sozialstatus"), false));

		table.addCells("GS", HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Geschwindigkeit"), hero,
				hero.getObj("Basiswerte").getObj("Geschwindigkeit"), false));

		final int woundThreshold = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Wundschwelle"), hero,
				hero.getObj("Basiswerte").getObj("Wundschwelle"), false);
		table.addCells("WS", woundThreshold);

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addBiographyTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(453, 453, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(65, 65, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(65, 65, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));

		final JSONObject bio = hero.getObj("Biografie");

		table.addRow("Name: " + bio.getStringOrDefault("Vorname", "") + " " + bio.getStringOrDefault("Nachname", ""),
				new TextCell("Spieler: " + hero.getStringOrDefault("Spieler", "")).setColSpan(2));

		if (settingsPage.getBool("Allgemein").get()) {
			table.addRow("Rasse: " + SheetUtil.getRaceString(bio), new TextCell("AP: " + bio.getIntOrDefault("Abenteuerpunkte", 0)).setColSpan(2));
			table.addRow("Kultur: " + SheetUtil.getCultureString(bio),
					new TextCell("Geschlecht: " + ("weiblich".equals(bio.getString("Geschlecht")) ? "♀" : "♂")).setColSpan(2));
			table.addRow("Profession: " + HeroUtil.getProfessionString(hero, bio, ResourceManager.getResource("data/Professionen"), true),
					"Größe: " + bio.getIntOrDefault("Größe", 0), "Gewicht: " + bio.getIntOrDefault("Gewicht", 0));
		}

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addCloseCombatTable(final PDDocument document, final TitledPane section) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
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

				final JSONArray types = item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen"));
				final String type = item.getStringOrDefault("Waffentyp:Primär",
						baseWeapon.getStringOrDefault("Waffentyp:Primär", types.size() != 0 ? types.getString(0) : ""));
				final JSONObject weaponModifier = item.getObjOrDefault("Waffenmodifikatoren", baseWeapon.getObj("Waffenmodifikatoren"));
				final JSONObject weaponMastery = HeroUtil.getSpecialisation(hero.getObj("Sonderfertigkeiten").getArrOrDefault("Waffenmeister", null), type,
						item.getStringOrDefault("Typ", baseWeapon.getString("Typ")));

				final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

				final Integer atValue = HeroUtil.getAT(hero, item, type, true, false, null, false);
				final String at = atValue != null ? Integer.toString(atValue) : "";
				final Integer paValue = HeroUtil.getPA(hero, item, type, false, false);
				final String pa = paValue != null ? Integer.toString(paValue) : "—";

				final JSONObject TPKKValues = item.getObjOrDefault("Trefferpunkte/Körperkraft",
						baseWeapon.getObjOrDefault("Trefferpunkte/Körperkraft", null));
				final int threshold = TPKKValues != null ? TPKKValues.getIntOrDefault("Schwellenwert", Integer.MIN_VALUE) : Integer.MIN_VALUE;
				final int step = TPKKValues != null ? TPKKValues.getIntOrDefault("Schadensschritte", Integer.MIN_VALUE) : Integer.MIN_VALUE;
				final String tpkkThreshold = threshold == Integer.MIN_VALUE ? "—" : Integer.toString(threshold
						+ (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schwellenwert", 0) : 0));
				final String tpkkStep = step == Integer.MIN_VALUE ? "—" : Integer.toString(
						step + (weaponMastery != null ? weaponMastery.getObj("Trefferpunkte/Körperkraft").getIntOrDefault("Schadensschritte", 0) : 0));
				final Cell tpkk = new TextCell(tpkkThreshold).addText("/").addText(tpkkStep).setEquallySpaced(true);

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
				final String bf = BF != null ? BF.toString() : "—";

				table.addRow(name, tp, at, pa, tpkk, wm, ini, distance, bf);
			}
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS + " für Nahkampfwaffen").get(); ++i) {
			table.addRow(" ", " ", " ", " ", "/", "/");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 321, 6, bottom.bottom - 5, 10, 10);
		}
	}

	private void addDerivedValuesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		for (int i = 0; i < 4; ++i) {
			table.addColumn(new Column(583f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		for (int i = 0; i < 5; ++i) {
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(291.5f / 11, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		final JSONObject derivedValues = ResourceManager.getResource("data/Basiswerte");
		String[] derivedNames = { "Attacke-Basis", "Parade-Basis", "Fernkampf-Basis", "Initiative-Basis" };
		String[] derivedLabels = { "AT-Basis", "PA-Basis", "FK-Basis", "INI-Basis" };
		for (int i = 0; i < 4; ++i) {
			final String derivedName = derivedNames[i];
			final JSONObject derivedValue = derivedValues.getObj(derivedName);
			table.addCells(derivedLabels[i], HeroUtil.deriveValue(derivedValue, hero, hero.getObj("Basiswerte").getObj(derivedName), false));
		}

		derivedNames = new String[] { "Lebensenergie", "Ausdauer", "Magieresistenz", "Astralenergie", "Karmaenergie" };
		derivedLabels = new String[] { "LeP", "AuP", "MR", "AsP", "KaP" };
		for (int i = 0; i < 5; ++i) {
			final String derivedName = derivedNames[i];
			final JSONObject derivedValue = derivedValues.getObj(derivedName);
			final JSONObject actualValue = hero.getObj("Basiswerte").getObjOrDefault(derivedName, null);
			String value;
			if (actualValue != null) {
				value = Integer.toString(HeroUtil.deriveValue(derivedValue, hero, actualValue, false));
			} else {
				value = "—";
			}
			table.addCells(derivedLabels[i], value);
		}

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addInfightTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(34, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(43, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));

		SheetUtil.addTitle(table, "Waffenloser Kampf");

		table.addRow("Talent", "TP", "AT", "PA");

		final int TPKKModifier = (HeroUtil.getCurrentValue(hero.getObj("Eigenschaften").getObj("KK"), false) - 10) / 3;

		final String tp = "1W" + (TPKKModifier == 0 ? "" : Util.getSignedIntegerString(TPKKModifier)) + "(A)";
		final String at1 = Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Raufen", true, false, null, false));
		final String pa1 = Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Raufen", false, false));

		table.addRow("Raufen", tp, at1, pa1);

		final String at2 = Integer.toString(HeroUtil.getAT(hero, HeroUtil.infight, "Ringen", true, false, null, false));
		final String pa2 = Integer.toString(HeroUtil.getPA(hero, HeroUtil.infight, "Ringen", false, false));

		table.addRow("Ringen", tp, at2, pa2);

		bottom.bottom = table.render(document, 117, 332, bottom.bottom - 5, 10, 10);
	}

	private void addMulticolTable(final PDDocument document, final String title, final Object[] tableHeader, final Table table,
			final int numColumns, final List<Object[]> rows, final Set<Integer> dividers, final int additionalRows) throws IOException {

		for (int i = 0; i < additionalRows || rows.size() % numColumns != 0; ++i) {
			rows.add(new Object[] { " " });
		}

		int start = 0;

		Table mainTable = new Table().setFiller(SheetUtil.stripe());
		mainTable.addEventHandler(EventType.BEGIN_PAGE, header);
		final float width = (583 - 5 * (numColumns - 1)) / (float) numColumns;

		mainTable.addColumn(new Column(width, FontManager.serif, fontSize, HAlign.LEFT));
		for (int i = 1; i < numColumns; ++i) {
			mainTable.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
			mainTable.addColumn(new Column(width, FontManager.serif, fontSize, HAlign.CENTER));
		}

		Table columnTable = table;
		if (tableHeader != null) {
			columnTable.addRow(tableHeader);
		}

		while (true) {
			SheetUtil.addTitle(mainTable, title);

			final float headerHeight = mainTable.getHeight(583) + 0.25f;
			final float rowHeight = columnTable.duplicate().addRow(" ").getHeight(583);

			final int maxRows = (rows.size() - start) / numColumns;
			if (maxRows == 0) {
				break;
			}
			int numRows = Math.min((int) ((bottom.bottom - 5 - headerHeight - 10) / rowHeight) - (tableHeader != null ? 1 : 0), maxRows);
			if (numRows < 15) {
				numRows = maxRows;
			}
			int rowIndex = 0;
			for (int column = 0; column < numColumns; ++column) {

				while (rowIndex < numRows) {
					final int index = rowIndex + column * numRows + start;
					final Object[] row = rows.get(index);
					columnTable.addRow(row);

					if (dividers != null && rowIndex != 0 && dividers.contains(index)) {
						columnTable.getRows().get(columnTable.getNumRows() - 1).addEventHandler(EventType.AFTER_ROW, event -> {
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

					++rowIndex;
				}

				mainTable.addCells(new TableCell(columnTable));
				if (column != numColumns - 1) {
					mainTable.addCells(" ");
				}
				columnTable = table.duplicate();
				if (tableHeader != null) {
					columnTable.addRow(tableHeader);
				}

				rowIndex = 0;
			}

			bottom.bottom = mainTable.render(document, 583, 6, bottom.bottom - 5, 10, 10);
			mainTable = mainTable.duplicate();

			start += numRows * numColumns;
		}
	}

	private void addProsAndConsTable(final PDDocument document) throws IOException {
		final Table table = new Table();
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(583, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Vor- und Nachteile");

		for (final String pOrC : new String[] { "Vorteile", "Nachteile" }) {
			final StringBuilder prosAndCons = new StringBuilder();
			final JSONObject prosOrCons = ResourceManager.getResource("data/" + pOrC);
			final JSONObject actual = hero.getObj(pOrC);

			final Map<String, JSONObject> actualProsOrCons = new TreeMap<>(SheetUtil.comparator);
			for (final String proOrConName : actual.keySet()) {
				actualProsOrCons.put(proOrConName, prosOrCons.getObj(proOrConName));
			}

			for (final String proOrConName : actualProsOrCons.keySet()) {
				final JSONObject proOrCon = actualProsOrCons.get(proOrConName);
				if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
					prosAndCons.append(DSAUtil.printProsOrCons(actual.getArr(proOrConName), proOrConName, proOrCon, true));
				} else {
					prosAndCons.append(DSAUtil.printProOrCon(actual.getObj(proOrConName), proOrConName, proOrCon, true));
				}
				prosAndCons.append("; ");
			}
			if (prosAndCons.length() > 2) {
				prosAndCons.delete(prosAndCons.length() - 2, prosAndCons.length());
			}
			table.addRow(new TextCell(prosAndCons.toString()).setDrawRows(true));
		}

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addRangedCombatTable(final PDDocument document, final TitledPane section) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
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

				final String type = item.getStringOrDefault("Waffentyp:Primär", baseWeapon.getStringOrDefault("Waffentyp:Primär",
						item.getArrOrDefault("Waffentypen", baseWeapon.getArr("Waffentypen")).getString(0)));

				final String tp = HeroUtil.getTPString(hero, item, baseWeapon);

				final Integer atValue = HeroUtil.getAT(hero, item, type, false, false, null, false);
				final String at = atValue != null ? Integer.toString(atValue) : "";
				final TextCell atCell = new TextCell(at);
				if (hero.getObj("Vorteile").containsKey("Entfernungssinn")) {
					atCell.addText(new Text("-2").setFontSize(7));
				}

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

				table.addRow(name, tp, atCell, load, distances[0], distances[1], distances[2], distances[3], distances[4], tpdistance[0], tpdistance[1],
						tpdistance[2], tpdistance[3], tpdistance[4], num);
			}
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS + " für Fernkampfwaffen").get(); ++i) {
			table.addRow("");
		}

		if (table.getNumRows() > 1) {
			bottom.bottom = table.render(document, 321, 6, bottom.bottom - 5, 10, 10);
		}
	}

	private void addSpecialSkillsTable(final PDDocument document, final String type) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
		table.addColumn(new Column(583, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, type);

		final StringBuilder skillsString = new StringBuilder();

		final JSONObject regularSkills = ResourceManager.getResource("data/Sonderfertigkeiten");
		final JSONObject rituals = ResourceManager.getResource("data/Rituale");
		final JSONObject shamanRituals = ResourceManager.getResource("data/Schamanenrituale");
		final JSONObject liturgies = ResourceManager.getResource("data/Liturgien");
		final JSONObject actualSkills = hero.getObj("Sonderfertigkeiten");

		final JSONObject[] skillGroups = new JSONObject["Rituale".equals(type) ? rituals.size() + 1 : "Liturgien".equals(type) ? 1 : regularSkills.size()];
		int i = 0;
		switch (type) {
			case "Sonderfertigkeiten" -> {
				for (final String skillGroupName : regularSkills.keySet()) {
					skillGroups[i] = regularSkills.getObj(skillGroupName);
					++i;
				}
			}
			case "Rituale" -> {
				for (final String skillGroupName : rituals.keySet()) {
					skillGroups[i] = rituals.getObj(skillGroupName);
					++i;
				}
				skillGroups[i] = shamanRituals;
			}
			case "Liturgien" -> skillGroups[0] = liturgies;
		}

		final Map<String, JSONObject> skills = new TreeMap<>(SheetUtil.comparator);
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
				skillsString.append(DSAUtil.printProsOrCons(actualSkills.getArr(skillName), skillName, skill, false));
			} else {
				skillsString.append(skillName);
			}
			skillsString.append("; ");
		}

		if (skillsString.length() > 2) {
			skillsString.delete(skillsString.length() - 2, skillsString.length());
		}
		table.addRow(new TextCell(skillsString.toString()).setDrawRows(true));

		bottom.bottom = table.render(document, 583, 6, bottom.bottom - 5, 10, 10);
	}

	private void addSpellTable(final PDDocument document, final TitledPane section) throws IOException {
		final JSONObject spells = ResourceManager.getResource("data/Zauber");
		final JSONObject actualSpells = hero.getObj("Zauber");

		final List<Object[]> rows = new ArrayList<>();

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
			final JSONObject rep = spell.getObj("Repräsentationen").getObj(spellNameRep._2);

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
				String name = spellNameRep._1;
				if (spell.containsKey("Auswahl")) {
					name = name + ": " + actualTalent.getStringOrDefault("Auswahl", "");
				} else if (spell.containsKey("Freitext")) {
					name = name + ": " + actualTalent.getStringOrDefault("Freitext", "");
				}

				final JSONArray challenge = rep.getArrOrDefault("Probe", spell.getArr("Probe"));
				final String challengeString = settingsPage.getBool(section, VALUES_FOR_ATTRIBUTES).get()
						? HeroUtil.getChallengeValuesString(hero, challenge, fill) : DSAUtil.getChallengeString(challenge);

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

				rows.add(new Object[] { name, spellNameRep._2, challengeString, traitString, zfw });
			}
		}

		final Table table = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);
		table.addColumn(new Column(162, 162, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(19, 19, FontManager.serif, 4, 8, HAlign.CENTER));
		table.addColumn(new Column(57, 57, FontManager.serif, 4, 8, HAlign.CENTER));
		table.addColumn(new Column(28, 28, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(23, FontManager.serif, 8, HAlign.CENTER));

		final Cell nameTitle = new TextCell("Zauber", FontManager.serifBold, 0, 8);
		final Cell repTitle = new TextCell("Rep.", FontManager.serifBold, 0, 8);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, 0, 8);
		final Cell traitTitle = new TextCell("Merk.", FontManager.serifBold, 0, 8).setHAlign(HAlign.CENTER);
		final Cell valueTitle = new TextCell("ZfW", FontManager.serifBold, 0, 8);
		final Object[] tableHeader = { nameTitle, repTitle, challengeTitle, traitTitle, valueTitle };

		addMulticolTable(document, "Zauber", tableHeader, table, 2, rows, null, settingsPage.getInt(section, ADDITIONAL_ROWS).get());
	}

	private void addTalentsTable(final PDDocument document, final TitledPane section) throws IOException {
		final JSONObject talents = ResourceManager.getResource("data/Talente");
		final JSONObject actualTalentGroups = hero.getObj("Talente");

		final List<Object[]> rows = new ArrayList<>();
		final Set<Integer> dividers = new HashSet<>();

		final int ATBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Attacke-Basis"), hero,
				hero.getObj("Basiswerte").getObj("Attacke-Basis"), true);
		final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero,
				hero.getObj("Basiswerte").getObj("Parade-Basis"), true);
		final int FKBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Fernkampf-Basis"), hero,
				hero.getObj("Basiswerte").getObj("Fernkampf-Basis"), false);

		int index = 0;

		for (final String talentGroupName : talents.keySet()) {
			final JSONObject talentGroup = talents.getObj(talentGroupName);
			final JSONObject actualTalentGroup = actualTalentGroups.getObjOrDefault(talentGroupName, null);

			if (actualTalentGroup == null || actualTalentGroup.size() == 0) {
				continue;
			}

			dividers.add(index);

			final Cell nameTitle = new TextCell(talentGroupName, FontManager.serifBold, 0, 8);
			final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, 0, 8);

			switch (talentGroupName) {
				case "Nahkampftalente" -> rows
						.add(new Object[] { nameTitle, new TextCell("AT", FontManager.serifBold, 0, 8).addText("/").addText("PA").setEquallySpaced(true),
								new TextCell("BE", FontManager.serifBold, 0, 8), tawTitle });
				case "Fernkampftalente" -> rows.add(
						new Object[] { nameTitle, new TextCell("FK", FontManager.serifBold, 0, 8), new TextCell("BE", FontManager.serifBold, 0, 8), tawTitle });
				case "Körperliche Talente" -> rows.add(new Object[] { nameTitle, new TextCell("Probe", FontManager.serifBold, 0, 8),
						new TextCell("BE", FontManager.serifBold, 0, 8), tawTitle });
				case "Sprachen und Schriften" -> rows.add(new Object[] { nameTitle, new TextCell("Kpl.", FontManager.serifBold, 0, 8),
						new TextCell("S", FontManager.serifBold, 0, 8), tawTitle });
				default -> rows.add(new Object[] { nameTitle, new TextCell("Probe", FontManager.serifBold, 0, 8), " ", tawTitle });
			}

			++index;

			final Map<String, JSONValue> actual = new TreeMap<>((s1, s2) -> {
				final boolean firstIsBasis = talentGroup.getObj(s1).getBoolOrDefault("Basis", false);
				if (settingsPage.getBool(section, GROUP_BASIC_TALENTS).get() && firstIsBasis != talentGroup.getObj(s2).getBoolOrDefault("Basis", false))
					return firstIsBasis ? -1 : 1;
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
					Cell language = null;
					final Cell special = switch (talentGroupName) {
						case "Nahkampftalente" -> {
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
							yield new TextCell(atString).addText("/").addText(paString).setEquallySpaced(true);
						}
						case "Fernkampftalente" -> new TextCell(Integer.toString(FKBase + actualTalent.getIntOrDefault("AT", 0)));
						case "Sprachen und Schriften" -> {
							if (actualTalent.getBoolOrDefault("Muttersprache", false)) {
								language = new TextCell("MS");
							} else if (actualTalent.getBoolOrDefault("Zweitsprache", false)) {
								language = new TextCell("ZS");
							} else if (actualTalent.getBoolOrDefault("Lehrsprache", false)) {
								language = new TextCell("LS");
							} else {
								language = new TextCell(" ");
							}
							yield new TextCell(talent.getInt("Komplexität").toString());
						}
						default -> {
							if (talent.containsKey("Probe")) {
								final JSONArray challenge = talent.getArr("Probe");
								final String[] attributeStrings = new String[3];
								if (settingsPage.getBool(section, VALUES_FOR_ATTRIBUTES).get()) {
									final JSONObject attributes = hero.getObj("Eigenschaften");
									attributeStrings[0] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(0)), false));
									attributeStrings[1] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(1)), false));
									attributeStrings[2] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(2)), false));
								} else {
									attributeStrings[0] = challenge.getString(0);
									attributeStrings[1] = challenge.getString(1);
									attributeStrings[2] = challenge.getString(2);
								}

								yield new TextCell(attributeStrings[0]).addText("/").addText(attributeStrings[1]).addText("/").addText(attributeStrings[2])
										.setEquallySpaced(true).setPadding(0, 1, 1, 0);
							} else {
								yield new TextCell("—");
							}
						}
					};

					final String be = switch (talentGroupName) {
						case "Nahkampftalente", "Fernkampftalente", "Körperliche Talente" -> DSAUtil.getBEString(talent);
						default -> " ";
					};

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
							settingsPage.getBool(section, MARK_BASIC_TALENTS).get() && talent.getBoolOrDefault("Basis", false) ? FontManager.serifItalic
									: FontManager.serif,
							8, 8);
					final Object beOrLanguage = "Sprachen und Schriften".equals(talentGroupName) ? language : be;
					rows.add(new Object[] { nameCell, special, beOrLanguage, taw });

					if (settingsPage.getBool(section, GROUP_BASIC_TALENTS).get() && basicTalent && !talent.getBoolOrDefault("Basis", false)) {
						dividers.add(index);
					}
					basicTalent = talent.getBoolOrDefault("Basis", false);

					++index;
				}
			}
		}

		final Table table = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);
		table.addColumn(new Column(100, 100, FontManager.serif, 4, 8, HAlign.LEFT));
		table.addColumn(new Column(43, FontManager.serif, 8, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, 8, HAlign.CENTER));
		table.addColumn(new Column(23, FontManager.serif, 8, HAlign.CENTER));

		addMulticolTable(document, "Talente", null, table, 3, rows, settingsPage.getBool(section, GROUP_BASIC_TALENTS).get() ? dividers : null,
				settingsPage.getInt(section, ADDITIONAL_ROWS).get());
	}

	private void addTotalArmorTable(final PDDocument document, final TitledPane section) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
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
					beString = DSAUtil.threeDecimalPlaces.format(item.getDoubleOrDefault("Behinderung", baseArmor.getDoubleOrDefault("Behinderung",
							item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).doubleValue())));
				}

				String rsString;
				final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObjOrDefault("Rüstungsschutz", null));
				if ("Gesamtrüstung".equals(armorSetting) || zones == null && !(item.containsKey("Rüstungsschutz") || baseArmor.containsKey("Rüstungsschutz"))) {
					final int pieceRS = item.getIntOrDefault("Gesamtrüstungsschutz", baseArmor.getIntOrDefault("Gesamtrüstungsschutz", 0));
					RS += pieceRS;
					rsString = Integer.toString(pieceRS);
				} else if (zones == null) {
					final double pieceRS = item.getDoubleOrDefault("Rüstungsschutz", baseArmor.getDoubleOrDefault("Rüstungsschutz", 0.0));
					RS += pieceRS;
					rsString = DSAUtil.threeDecimalPlaces.format(pieceRS);
				} else {
					final int[] zoneValues = new int[8];
					int j = 0;
					for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
						zoneValues[j] = zones.getInt(zone);
						++j;
					}
					final double pieceRS = (zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4 + zoneValues[4] + zoneValues[5]
							+ zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0;
					RS += pieceRS;
					rsString = DSAUtil.threeDecimalPlaces.format(pieceRS);
				}
				table.addRow(name, beString, rsString);
			}
		}

		final JSONObject pros = hero.getObj("Vorteile");
		if (pros.containsKey("Natürlicher Rüstungsschutz")) {
			RS += pros.getObj("Natürlicher Rüstungsschutz").getIntOrDefault("Stufe", 0);
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS + " für Rüstung").get(); ++i) {
			table.addRow("");
		}

		table.addRow("Gesamt:", DSAUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero)), DSAUtil.threeDecimalPlaces.format(RS));

		bottom.bottom = table.render(document, 135, 454, bottom.bottom - 5, 10, 10);
	}

	private void addZoneArmorTable(final PDDocument document, final TitledPane section) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);
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
					be = DSAUtil.threeDecimalPlaces.format(item.getDoubleOrDefault("Behinderung", baseArmor.getDoubleOrDefault("Behinderung",
							item.getIntOrDefault("Gesamtbehinderung", baseArmor.getIntOrDefault("Gesamtbehinderung", 0)).doubleValue())));
				}

				final String[] rs = new String[8];
				final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObjOrDefault("Rüstungsschutz", null));
				if ("Gesamtrüstung".equals(armorSetting) || zones == null && !(item.containsKey("Rüstungsschutz") || baseArmor.containsKey("Rüstungsschutz"))) {
					final int RS = item.getIntOrDefault("Gesamtrüstungsschutz", baseArmor.getIntOrDefault("Gesamtrüstungsschutz", 0));
					for (int j = 0; j < 8; ++j) {
						rs[i] = Integer.toString(RS);
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
					for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
						zoneValues[j] = zones.getInt(zone);
						if ("Zonenrüstung".equals(armorSetting)) {
							rs[j] = Integer.toString(zoneValues[j]);
						}
						++j;
					}
					if ("Zonengesamtrüstung".equals(armorSetting)) {
						final String zoneRS = DSAUtil.threeDecimalPlaces.format((zoneValues[0] * 2 + zoneValues[1] * 4 + zoneValues[2] * 4 + zoneValues[3] * 4
								+ zoneValues[4] + zoneValues[5] + zoneValues[6] * 2 + zoneValues[7] * 2) / 20.0);
						for (int k = 0; k < 8; ++k) {
							rs[k] = zoneRS;
						}
					}
				}
				table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7]);
			}
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS + " für Rüstung").get(); ++i) {
			table.addRow("");
		}

		int i = 0;
		final int[] rs = new int[8];
		for (final String zone : new String[] { "Kopf", "Brust", "Rücken", "Bauch", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein" }) {
			rs[i] = HeroUtil.getZoneRS(hero, zone);
			++i;
		}
		table.addRow("Gesamt:", DSAUtil.threeDecimalPlaces.format(HeroUtil.getBERaw(hero)), rs[0], rs[1], rs[2], rs[3], rs[4], rs[5], rs[6], rs[7]);

		bottom.bottom = table.render(document, 257, 332, bottom.bottom - 5, 10, 10);
	}

	@Override
	public boolean check() {
		return hero != null;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (hero != null) {
			header = SheetUtil.createHeader(null, false, false, false, hero, fill, fillAll, showName, showDate);
			startCreate(document);

			try {
				addBiographyTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}

			for (final TitledPane section : settingsPage.getSections()) {
				if (!settingsPage.getBool(section, "").get()) {
					continue;
				}
				try {
					final String name = settingsPage.getString(section, null).get();
					switch (name) {
						case ATTRIBUTES -> {
							try {
								addAttributesTable(document);
							} catch (final Exception e) {
								ErrorLogger.logError(e);
							}
							try {
								addDerivedValuesTable(document);
							} catch (final Exception e) {
								ErrorLogger.logError(e);
							}
						}

						case "Vor-/Nachteile" -> addProsAndConsTable(document);

						case "Sonderfertigkeiten", "Rituale", "Liturgien" -> addSpecialSkillsTable(document, name);

						case "Kampf" -> {
							float smallTop = bottom.bottom;

							try {
								addCloseCombatTable(document, section);
							} catch (final Exception e) {
								ErrorLogger.logError(e);
							}

							try {
								addRangedCombatTable(document, section);
							} catch (final Exception e) {
								ErrorLogger.logError(e);
							}

							final float smallBottom = bottom.bottom;
							smallTop = smallTop >= smallBottom ? smallTop : height - 5;
							bottom.bottom = smallTop;

							try {
								addInfightTable(document);
							} catch (final Exception e) {
								ErrorLogger.logError(e);
							}

							final float secondBottom = bottom.bottom;
							bottom.bottom = smallTop;

							if (settingsPage.getBool(section, "Gesamtrüstung").get()) {
								try {
									addTotalArmorTable(document, section);
								} catch (final Exception e) {
									ErrorLogger.logError(e);
								}
							}
							bottom.bottom = Math.min(secondBottom, bottom.bottom);

							if (settingsPage.getBool(section, "Zonenrüstung").get()) {
								try {
									addZoneArmorTable(document, section);
								} catch (final Exception e) {
									ErrorLogger.logError(e);
								}
							}

							bottom.bottom = Math.min(bottom.bottom, smallBottom);
						}

						case "Talente" -> addTalentsTable(document, section);

						case "Zauber" -> addSpellTable(document, section);
					}
				} catch (final Exception e) {
					ErrorLogger.logError(e);
				}
			}

			endCreate(document);
		}
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);
		settings.put("Allgemein", settingsPage.getBool("Allgemein").get());

		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			settings.put(name, settingsPage.getBool(section, "").get());
			switch (name) {
				case "Kampf" -> {
					settings.put("Gesamtrüstung", settingsPage.getBool(section, "Gesamtrüstung").get());
					settings.put("Zonenrüstung", settingsPage.getBool(section, "Zonenrüstung").get());
					settings.put(ADDITIONAL_ROWS + " für Nahkampfwaffen", settingsPage.getInt(section, ADDITIONAL_ROWS + " für Nahkampfwaffen").get());
					settings.put(ADDITIONAL_ROWS + " für Fernkampfwaffen", settingsPage.getInt(section, ADDITIONAL_ROWS + " für Fernkampfwaffen").get());
					settings.put(ADDITIONAL_ROWS + " für Rüstung", settingsPage.getInt(section, ADDITIONAL_ROWS + " für Rüstung").get());
				}
				case "Talente" -> {
					settings.put("Basistalente gruppieren", settingsPage.getBool(section, GROUP_BASIC_TALENTS).get());
					settings.put("Basistalente markieren", settingsPage.getBool(section, MARK_BASIC_TALENTS).get());
					settings.put(ADDITIONAL_ROWS + " für Talente", settingsPage.getInt(section, ADDITIONAL_ROWS).get());
					settings.put(VALUES_FOR_ATTRIBUTES + " für Talente", settingsPage.getBool(section, VALUES_FOR_ATTRIBUTES).get());
				}
				case "Zauber" -> {
					settings.put(ADDITIONAL_ROWS + " für Zauber", settingsPage.getInt(section, ADDITIONAL_ROWS).get());
					settings.put(VALUES_FOR_ATTRIBUTES + " für Zauber", settingsPage.getBool(section, VALUES_FOR_ATTRIBUTES).get());
				}
			}
		}
		return settings;
	}

	@Override
	public void load() {
		super.load();

		settingsPage.addBooleanChoice("Allgemein");
		sections.put(ATTRIBUTES, settingsPage.addSection(ATTRIBUTES, true));
		sections.put("Vor-/Nachteile", settingsPage.addSection("Vor-/Nachteile", true));
		sections.put("Sonderfertigkeiten", settingsPage.addSection("Sonderfertigkeiten", true));

		sections.put("Kampf", settingsPage.addSection("Kampf", true));
		settingsPage.addBooleanChoice("Gesamtrüstung");
		settingsPage.addBooleanChoice("Zonenrüstung");
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS + " für Nahkampfwaffen", 0, 30);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS + " für Fernkampfwaffen", 0, 30);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS + " für Rüstung", 0, 30);

		sections.put("Talente", settingsPage.addSection("Talente", true));
		settingsPage.addBooleanChoice(GROUP_BASIC_TALENTS);
		settingsPage.addBooleanChoice(MARK_BASIC_TALENTS);
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 60);
		settingsPage.addBooleanChoice(VALUES_FOR_ATTRIBUTES);

		sections.put("Zauber", settingsPage.addSection("Zauber", true));
		settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 60);
		settingsPage.addBooleanChoice(VALUES_FOR_ATTRIBUTES);

		sections.put("Rituale", settingsPage.addSection("Rituale", true));
		sections.put("Liturgien", settingsPage.addSection("Liturgien", true));
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);

		settingsPage.getBool("Allgemein").set(settings.getBoolOrDefault("Allgemein", true));

		orderSections(List.of(ATTRIBUTES, "Vor-/Nachteile", "Sonderfertigkeiten", "Kampf", "Talente", "Zauber", "Rituale", "Liturgien"));
		orderSections(settings.keySet());

		settingsPage.getBool(sections.get(ATTRIBUTES), "").set(settings.getBoolOrDefault(ATTRIBUTES, true));
		settingsPage.getBool(sections.get("Vor-/Nachteile"), "").set(settings.getBoolOrDefault("Vor-/Nachteile", true));
		settingsPage.getBool(sections.get("Sonderfertigkeiten"), "").set(settings.getBoolOrDefault("Sonderfertigkeiten", true));

		final TitledPane fight = sections.get("Kampf");
		settingsPage.getBool(fight, "").set(settings.getBoolOrDefault("Kampf", true));
		settingsPage.getBool(fight, "Gesamtrüstung").set(
				settings.getBoolOrDefault("Gesamtrüstung", !"Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart"))));
		settingsPage.getBool(fight, "Zonenrüstung").set(
				settings.getBoolOrDefault("Zonenrüstung", "Zonenrüstung".equals(Settings.getSettingStringOrDefault("Zonenrüstung", "Kampf", "Rüstungsart"))));
		settingsPage.getInt(fight, "Zusätzliche Zeilen für Nahkampfwaffen").set(settings.getIntOrDefault("Zusätzliche Zeilen für Nahkampfwaffen", 0));
		settingsPage.getInt(fight, "Zusätzliche Zeilen für Fernkampfwaffen").set(settings.getIntOrDefault("Zusätzliche Zeilen für Fernkampfwaffen", 0));
		settingsPage.getInt(fight, "Zusätzliche Zeilen für Rüstung").set(settings.getIntOrDefault("Zusätzliche Zeilen für Rüstung", 0));

		final TitledPane talents = sections.get("Talente");
		settingsPage.getBool(talents, "").set(settings.getBoolOrDefault("Talente", true));
		settingsPage.getBool(talents, GROUP_BASIC_TALENTS).set(settings.getBoolOrDefault("Basistalente gruppieren", true));
		settingsPage.getBool(talents, MARK_BASIC_TALENTS).set(settings.getBoolOrDefault("Basistalente markieren", false));
		settingsPage.getInt(talents, ADDITIONAL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_ROWS + " für Talente", 0));
		settingsPage.getBool(talents, VALUES_FOR_ATTRIBUTES).set(settings.getBoolOrDefault(VALUES_FOR_ATTRIBUTES + " für Talente", false));

		final TitledPane spells = sections.get("Zauber");
		settingsPage.getBool(spells, "").set(settings.getBoolOrDefault("Zauber", hero != null && HeroUtil.isMagical(hero)));
		settingsPage.getInt(spells, ADDITIONAL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_ROWS + " für Zauber", 0));
		settingsPage.getBool(spells, VALUES_FOR_ATTRIBUTES).set(settings.getBoolOrDefault(VALUES_FOR_ATTRIBUTES + "für Zauber", false));

		settingsPage.getBool(sections.get("Rituale"), "").set(settings.getBoolOrDefault("Rituale", hero != null && HeroUtil.isMagical(hero)));
		settingsPage.getBool(sections.get("Liturgien"), "").set(settings.getBoolOrDefault("Liturgien", hero != null && HeroUtil.isClerical(hero, true)));
	}

	@Override
	public String toString() {
		return "Kompaktbogen";
	}

}
