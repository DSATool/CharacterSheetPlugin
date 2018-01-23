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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.pdfbox.pdmodel.PDDocument;

import boxtable.cell.TableCell;
import boxtable.cell.TextCell;
import boxtable.common.HAlign;
import boxtable.event.EventType;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.DSAUtil.Units;
import dsatool.util.ErrorLogger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class InventorySheet extends Sheet {
	private final static Map<String, String> types = new HashMap<>();
	static {
		types.put("Applicatus", "Ap");
		types.put("Arcanovi (einmalig)", "AE");
		types.put("Arcanovi (aufladbar)", "AA");
		types.put("Arcanovi (semipermanent)", "SP");
		types.put("Matrixgeber", "MG");
		types.put("Zaubertalisman", "ZT");
		types.put("Infinitum", "INF");
	}
	private final static Map<String, String> stabilities = new HashMap<>();
	static {
		types.put("labil", "labil");
		types.put("stabil", "stabil");
		types.put("sehr stabil", "s. stabil");
		types.put("unempfindlic", "unempf.");
	}
	private final IntegerProperty additionalClothingRows = new SimpleIntegerProperty(20);
	private final IntegerProperty additionalInventoryRows = new SimpleIntegerProperty(60);
	private final IntegerProperty additionalPotionRows = new SimpleIntegerProperty(0);
	private final IntegerProperty additionalValuablesRows = new SimpleIntegerProperty(0);
	private final IntegerProperty additionalArtifactRows = new SimpleIntegerProperty(5);
	private final int fontSize = 9;
	private final BooleanProperty showAttributes = new SimpleBooleanProperty(false);
	private final BooleanProperty showClothing = new SimpleBooleanProperty(true);
	private final BooleanProperty showInventory = new SimpleBooleanProperty(true);
	private final BooleanProperty showPotions = new SimpleBooleanProperty(false);
	private final BooleanProperty showValuables = new SimpleBooleanProperty(false);
	private final BooleanProperty showArtifacts = new SimpleBooleanProperty(true);

	public InventorySheet() {
		super(788);
	}

	private void addArtifactsTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(100, 100, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, 35, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, 35, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(126, 126, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(126, 126, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(124, 124, FontManager.serif, 4, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Artefakte");

		int cols = additionalArtifactRows.get();
		final Queue<JSONObject> artifacts = new LinkedList<>();

		JSONArray items = null;
		if (hero != null) {
			items = hero.getObj("Besitz").getArr("Ausrüstung");
			DSAUtil.foreach(item -> (item.containsKey("Kategorien") && item.getArr("Kategorien").contains("Artefakt")), item -> {
				artifacts.add(item);
			}, items);
			cols += artifacts.size();
		}
		cols = Math.max(cols, 1);

		final TextCell nameTitle = new TextCell("Artefakt", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell typeTitle = new TextCell("Typ", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell loadsTitle = new TextCell("Lad.", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell aspTitle = new TextCell("AsP", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell triggerTitle = new TextCell("Auslöser", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell spellsTitle = new TextCell("Wirkende Sprüche", FontManager.serifBold, 8.5f, 8.5f);
		final TextCell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 8.5f, 8.5f);

		table.addRow(nameTitle, typeTitle, loadsTitle, aspTitle, triggerTitle, spellsTitle, notesTitle);

		for (int i = 0; i < cols; ++i) {
			if (hero != null && fill && !artifacts.isEmpty()) {
				JSONObject item = artifacts.poll();
				final JSONObject baseItem = item;
				if (item.containsKey("Artefakt")) {
					item = item.getObj("Artefakt");
				}
				final String name = item.getStringOrDefault("Name", baseItem.getStringOrDefault("Name", "Unbenannt"));
				final String actualType = item.getStringOrDefault("Typ", baseItem.getStringOrDefault("Typ", ""));
				final String type = types.getOrDefault(actualType, "");
				String loads = "—";
				switch (actualType) {
				case "Applicatus":
				case "Arcanovi (einmalig)":
				case "Arcanovi (aufladbar)":
				case "Arcanovi (semipermanent)":
					loads = DSAUtil.getModificationString(item.getObjOrDefault("Ladungen", baseItem.getObj("Ladungen")), Units.TIME, false);
					break;
				case "Matrixgeber":
				case "Zaubertalisman":
					loads = stabilities.getOrDefault(item.getStringOrDefault("Stabilität", baseItem.getString("Stabilität")), "");
					break;
				}
				final JSONObject energy = item.getObjOrDefault("Astralenergie", baseItem.getObj("Astralenergie"));
				final String asp = energy.getIntOrDefault("Additiv", 0) == 0 ? "—" : DSAUtil.getModificationString(energy, Units.NONE, false);

				final JSONObject trigger = item.getObjOrDefault("Auslöser", baseItem.getObj("Auslöser"));
				final StringBuilder triggerString = new StringBuilder();
				if (trigger.getBoolOrDefault("Reaktion", false)) {
					triggerString.append("R: ");
				} else if (trigger.containsKey("Aktionen")) {
					triggerString.append(trigger.getInt("Aktionen"));
					triggerString.append("A: ");
				}
				triggerString.append(trigger.getStringOrDefault("Beschreibung", trigger.getString("Typ")));

				final JSONArray spells = item.getArrOrDefault("Wirkende Sprüche", baseItem.getArr("Wirkende Sprüche"));
				boolean first = true;
				final StringBuilder spellsString = new StringBuilder();
				for (int j = 0; j < spells.size(); ++j) {
					final JSONObject spell = spells.getObj(j);
					if (first) {
						first = false;
					} else {
						spellsString.append(", ");
					}
					spellsString.append(spell.getStringOrDefault("Spruch", ""));
					if (spell.containsKey("Variante")) {
						spellsString.append(" (");
						spellsString.append(spell.getString("Variante"));
						spellsString.append(')');
					}
				}

				final String notes = item.getStringOrDefault("Anmerkungen", baseItem.getStringOrDefault("Anmerkungen", ""));
				table.addRow(name, type, loads, asp, triggerString, spellsString, notes);
			} else {
				table.addRow("");
			}
		}

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, showAttributes.get() ? 72 : 54, 10) - 5;
	}

	private void addClothingTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Kleidung");

		int cols = additionalClothingRows.get() + 1;
		final Queue<JSONObject> clothing = new LinkedList<>();

		JSONArray items = null;
		if (hero != null) {
			items = hero.getObj("Besitz").getArr("Ausrüstung");
			DSAUtil.foreach(item -> (item.containsKey("Kategorien") && item.getArr("Kategorien").contains("Kleidung")), item -> {
				clothing.add(item);
			}, items);
			cols += clothing.size();
		}
		cols = Math.max(cols, 2);

		final Table[] tables = new Table[2];

		for (int i = 0; i < 2; ++i) {
			tables[i] = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			tables[i].addColumn(new Column(100, 100, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(158, 158, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

			final TextCell nameTitle = new TextCell("Kleidungsstück", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell ksTitle = new TextCell("KS", FontManager.serifBold, 8.5f, 8.5f);

			tables[i].addRow(nameTitle, notesTitle, ksTitle);

			for (int j = 0; j < cols / 2; ++j) {
				if (hero != null && fill && !clothing.isEmpty()) {
					JSONObject item = clothing.poll();
					final JSONObject baseItem = item;
					if (item.containsKey("Kleidung")) {
						item = item.getObj("Kleidung");
					}
					final String name = item.getStringOrDefault("Name", baseItem.getStringOrDefault("Name", "Unbenannt"));
					final String notes = item.getStringOrDefault("Anmerkungen", baseItem.getStringOrDefault("Anmerkungen", ""));
					final String ks = Integer.toString(item.getIntOrDefault("Kälteschutz", baseItem.getIntOrDefault("Kälteschutz", 0)));
					tables[i].addRow(name, notes, ks);
				} else {
					tables[i].addRow("");
				}
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, showAttributes.get() ? 72 : 54, 10) - 5;
	}

	private void addInventoryTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Inventar");

		int cols = additionalInventoryRows.get() + 1;
		final Queue<JSONObject> equipment = new LinkedList<>();

		JSONArray items = null;
		if (hero != null) {
			items = hero.getObj("Besitz").getArr("Ausrüstung");
			DSAUtil.foreach(item -> (!item.containsKey("Kategorien") || item.getArr("Kategorien").size() == 0), item -> {
				equipment.add(item);
			}, items);
			cols += equipment.size();
		}
		cols = Math.max(cols, 2);

		final Table[] tables = new Table[2];

		for (int i = 0; i < 2; ++i) {
			tables[i] = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			tables[i].addColumn(new Column(100, 100, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(133, 133, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

			final TextCell nameTitle = new TextCell("Gegenstand", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell weightTitle = new TextCell("Gew.", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell valueTitle = new TextCell("Wert", FontManager.serifBold, 8.5f, 8.5f);

			tables[i].addRow(nameTitle, notesTitle, weightTitle, valueTitle);

			for (int j = 0; j < cols / 2; ++j) {
				if (hero != null && fill && !equipment.isEmpty()) {
					final JSONObject item = equipment.poll();
					final String name = item.getStringOrDefault("Name", "Unbenannt");

					String defaultNotes = " ";
					if (item.containsKey("Bannschwert") && item.getObj("Bannschwert").getObj("Rituale").containsKey("Bannschwert")) {
						defaultNotes = "Bannschwert";
						if (item.getObj("Bannschwert").getObj("Rituale").containsKey("Apport")) {
							defaultNotes += ", Apport";
						}
					}

					final String notes = item.getStringOrDefault("Anmerkungen", defaultNotes);
					tables[i].addRow(name, notes);
				} else {
					tables[i].addRow("");
				}
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, showAttributes.get() ? 72 : 54, 10) - 5;
	}

	private void addPotionsTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Tränke");

		int cols = additionalPotionRows.get() + 1;
		cols = Math.max(cols, 2);

		final Table[] tables = new Table[2];

		for (int i = 0; i < 2; ++i) {
			tables[i] = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			tables[i].addColumn(new Column(100, 100, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(133, 133, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

			final TextCell nameTitle = new TextCell("Gegenstand", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell notesTitle = new TextCell("Wirkung", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell countTitle = new TextCell("Anz.", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell valueTitle = new TextCell("Wert", FontManager.serifBold, 8.5f, 8.5f);

			tables[i].addRow(nameTitle, notesTitle, countTitle, valueTitle);

			for (int j = 0; j < cols / 2; ++j) {
				tables[i].addRow("");
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, showAttributes.get() ? 72 : 54, 10) - 5;
	}

	private void addValuablesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Wertgegenstände");

		int cols = additionalValuablesRows.get() + 1;
		cols = Math.max(cols, 2);

		final Table[] tables = new Table[2];

		for (int i = 0; i < 2; ++i) {
			tables[i] = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			tables[i].addColumn(new Column(100, 100, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(158, 158, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

			final TextCell nameTitle = new TextCell("Gegenstand", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell valueTitle = new TextCell("Wert", FontManager.serifBold, 8.5f, 8.5f);

			tables[i].addRow(nameTitle, notesTitle, valueTitle);

			for (int j = 0; j < cols / 2; ++j) {
				tables[i].addRow("");
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, showAttributes.get() ? 72 : 54, 10) - 5;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (showAttributes.get()) {
			height = 771;
		}

		header = SheetUtil.createHeader("Ausrüstungsbrief", true, showAttributes.get(), false, hero, fill, fillAll);

		startCreate(document);

		if (showClothing.get()) {
			try {
				addClothingTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showValuables.get()) {
			try {
				addValuablesTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showPotions.get()) {
			try {
				addPotionsTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showArtifacts.get()) {
			try {
				addArtifactsTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showInventory.get()) {
			try {
				addInventoryTable(document);
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
		settings.put("Eigenschaften anzeigen", showAttributes.get());
		settings.put("Kleidung", showClothing.get());
		settings.put("Zusätzliche Zeilen für Kleidung", additionalClothingRows.get());
		settings.put("Wertgegenstände", showValuables.get());
		settings.put("Zusätzliche Zeilen für Wertgegenstände", additionalValuablesRows.get());
		settings.put("Tränke", showPotions.get());
		settings.put("Zusätzliche Zeilen für Tränke", additionalPotionRows.get());
		settings.put("Artefakte", showArtifacts.get());
		settings.put("Zusätzliche Zeilen für Artefakte", additionalArtifactRows.get());
		settings.put("Inventar", showInventory.get());
		settings.put("Zusätzliche Zeilen für Inventar", additionalInventoryRows.get());
		return settings;
	}

	@Override
	public void load() {
		super.load();
		settingsPage.addBooleanChoice("Eigenschaften anzeigen", showAttributes);
		settingsPage.addBooleanChoice("Kleidung", showClothing);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Kleidung", additionalClothingRows, 0, 200);
		settingsPage.addBooleanChoice("Wertgegenstände", showValuables);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Wertgegenstände", additionalValuablesRows, 0, 200);
		settingsPage.addBooleanChoice("Tränke", showPotions);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Tränke", additionalPotionRows, 0, 200);
		settingsPage.addBooleanChoice("Artefakte", showArtifacts);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Artefakte", additionalArtifactRows, 0, 200);
		settingsPage.addBooleanChoice("Inventar", showInventory);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Inventar", additionalInventoryRows, 0, 200);
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		showAttributes.set(settings.getBoolOrDefault("Eigenschaften anzeigen", false));
		showClothing.set(settings.getBoolOrDefault("Kleidung", true));
		additionalClothingRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Kleidung", 20));
		showValuables.set(settings.getBoolOrDefault("Wertgegenstände", false));
		additionalValuablesRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Wertgegenstände", 0));
		showPotions.set(settings.getBoolOrDefault("Tränke", false));
		additionalPotionRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Tränke", 0));
		showArtifacts.set(settings.getBoolOrDefault("Artefakte", true));
		additionalArtifactRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Artefakte", 5));
		showInventory.set(settings.getBoolOrDefault("Inventar", true));
		additionalInventoryRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Inventar", 60));
	}

	@Override
	public String toString() {
		return "Ausrüstungsbrief";
	}
}
