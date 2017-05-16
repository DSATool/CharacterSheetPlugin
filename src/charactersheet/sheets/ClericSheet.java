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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import boxtable.cell.Cell;
import boxtable.cell.TableCell;
import boxtable.cell.TextCell;
import boxtable.common.Bordered;
import boxtable.common.HAlign;
import boxtable.common.Text;
import boxtable.common.VAlign;
import boxtable.event.EventType;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.hero.ProOrCon;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.DSAUtil.Units;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ClericSheet extends Sheet {

	final StringProperty deity = new SimpleStringProperty();
	private final BooleanProperty modTable = new SimpleBooleanProperty(true);
	private final BooleanProperty categoriesTable = new SimpleBooleanProperty(true);

	public ClericSheet() {
		super(771);
	}

	private float addCategoriesTable(final PDDocument document, final float left) throws IOException {
		final Table table = new Table();
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		final List<Table> categoryTables = new ArrayList<>();

		final JSONObject categories = ResourceManager.getResource("data/Liturgiekategorien");

		final float width = 70;
		int maxRows = 0;

		for (final String categoryName : categories.keySet()) {
			final Table categoryTable = new Table().setBorder(0, 0, 0, 0);
			categoryTables.add(categoryTable);
			categoryTable.addColumn(new Column(0, width, FontManager.serif, 7, 7, HAlign.LEFT));
			boolean first = true;
			boolean hasText = false;
			final JSONObject category = categories.getObj(categoryName);
			for (final String entryName : category.keySet()) {
				final JSONObject entry = category.getObj(entryName);
				if (first) {
					first = false;
					if (entry.size() != 0) {
						hasText = true;
						categoryTable.addColumn(new Column(0, width, FontManager.serif, 4, 7, HAlign.CENTER));
					}
				}
				categoryTable.addCells(entryName);
				if (hasText) {
					categoryTable.addCells(entry.getStringOrDefault("Text", ""));
				}
			}
			categoryTable.addRowAtIndex(0, new TextCell(categoryName, FontManager.serifBold, 5, 7).setColSpan(categoryTable.getNumColumns()));
			if (categoryTable.getNumRows() > maxRows) {
				maxRows = categoryTable.getNumRows();
			}
		}

		int numCols = 1;
		int curRows = 0;
		final Map<Tuple<Integer, Integer>, Cell> positions = new HashMap<>();

		for (final Table categoryTable : categoryTables) {
			final int currentLength = categoryTable.getNumRows();
			curRows += currentLength;
			if (curRows > maxRows) {
				++numCols;
				curRows = categoryTable.getNumRows();
			}
			categoryTable.setFiller(SheetUtil.stripe().invert((curRows - currentLength) % 2 == 0));
			positions.put(new Tuple<>(curRows - currentLength, numCols - 1), new TableCell(categoryTable).setRowSpan(currentLength));
		}

		for (int i = 0; i < numCols; ++i) {
			table.addColumn(new Column(width, FontManager.serif, 7, HAlign.LEFT));
		}
		table.addColumn(new Column(0, FontManager.serif, 0, HAlign.CENTER).setBorder(0, 0, 0, 0));

		SheetUtil.addTitle(table, "Kategorien");

		final Bordered emptyCell = new Cell().setMinHeight(categoryTables.get(0).getRows().get(0).getCell(0).getHeight(width)).setBorder(0, 0, 0, 0);
		for (int i = 0; i < maxRows; ++i) {
			for (int j = 0; j <= numCols; ++j) {
				final Cell currentCell = positions.get(new Tuple<>(i, j));
				if (currentCell != null) {
					table.addCells(currentCell);
				} else {
					table.addCells(emptyCell);
				}
			}
		}

		bottom.bottom = table.render(document, width * numCols, left, bottom.bottom, 72, 10) - 5;

		return left + 5 + width * numCols;
	}

	private void addMiraclesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(50, FontManager.serif, 7, HAlign.LEFT));
		table.addColumn(new Column(235, FontManager.serif, 7, HAlign.LEFT).setVAlign(VAlign.TOP));
		table.addColumn(new Column(50, FontManager.serif, 7, HAlign.LEFT));
		table.addColumn(new Column(235, FontManager.serif, 7, HAlign.LEFT).setVAlign(VAlign.TOP));

		final TextCell titleCell = new TextCell("Mirakel", FontManager.serifBold, 9, 9);
		titleCell.addText(
				new Text("(5 KaP, Probe +6, Eigenschaften +LkP*/2+2, Talente +LkP*/2+5)").setFont(FontManager.serif).setFontSize(7).setVerticalOffset(0.5f));
		titleCell.setBackground(new Color(0.9f, 0.9f, 0.9f)).setColSpan(4);
		table.addRow(titleCell);

		table.addRow(new TextCell("Mirakel+ (±0)", FontManager.serifBold, 7, 7).setColSpan(2),
				new TextCell("Mirakel- (+18)", FontManager.serifBold, 7, 7).setColSpan(2));

		JSONObject miraclePlus = null;
		JSONObject miracleMinus = null;
		if (!"".equals(deity.get())) {
			final JSONObject actualDeity = ResourceManager.getResource("data/Talente").getObj("Liturgiekenntnis").getObj(deity.get());
			miraclePlus = actualDeity.getObj("Mirakel+");
			miracleMinus = actualDeity.getObj("Mirakel-");
		}

		String miraclePAttributes = " ";
		String miracleMAttributes = " ";

		if (miraclePlus != null) {
			final JSONArray miraclePlusAttributes = miraclePlus.getArrOrDefault("Eigenschaften", null);
			if (miraclePlusAttributes != null) {
				miraclePAttributes = String.join(", ", miraclePlusAttributes.getStrings());
			} else {
				miraclePAttributes = "—";
			}
		}

		if (miracleMinus != null) {
			final JSONArray miracleMinusAttributes = miracleMinus.getArrOrDefault("Eigenschaften", null);
			if (miracleMinusAttributes != null) {
				miracleMAttributes = String.join(", ", miracleMinusAttributes.getStrings());
			} else {
				miracleMAttributes = "—";
			}
		}

		final Cell attributes = new TextCell("Eigenschaften:", FontManager.serifBold, 7, 7).setPadding(0, 1, 0, 0);
		table.addRow(attributes, new TextCell(miraclePAttributes).setPadding(2, 1, 1, 0), attributes, new TextCell(miracleMAttributes).setPadding(2, 1, 1, 0));

		String miraclePTalents = " ";
		String miracleMTalents = " ";

		if (miraclePlus != null) {
			final JSONArray miraclePlusTalents = miraclePlus.getArrOrDefault("Talente", null);
			if (miraclePlusTalents != null) {
				miraclePTalents = String.join(", ", miraclePlusTalents.getStrings());
			} else {
				miraclePTalents = "—";
			}
		}

		if (miracleMinus != null) {
			final JSONArray miracleMinusTalents = miracleMinus.getArrOrDefault("Talente", null);
			if (miracleMinusTalents != null) {
				miracleMTalents = String.join(", ", miracleMinusTalents.getStrings());
			} else {
				miracleMTalents = "—";
			}
		}

		final Cell talents = new TextCell("Talente:", FontManager.serifBold, 7, 7);
		table.addRow(talents, new TextCell(miraclePTalents).setPadding(2, 1, 1, 0), talents, new TextCell(miracleMTalents).setPadding(2, 1, 1, 0));

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	private float addModificationTable(final PDDocument document, final float left) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(90, 90, FontManager.serif, 4, 7, HAlign.LEFT));
		table.addColumn(new Column(30, 30, FontManager.serif, 4, 7, HAlign.CENTER));
		table.addColumn(new Column(90, 90, FontManager.serif, 4, 7, HAlign.LEFT));
		table.addColumn(new Column(30, 30, FontManager.serif, 4, 7, HAlign.CENTER));

		SheetUtil.addTitle(table, "Modifikationen");

		final JSONObject modifications = ResourceManager.getResource("data/Mirakelmodifikationen");
		final List<Tuple<String, String>> rows = new ArrayList<>();

		for (final String modification : modifications.keySet()) {
			rows.add(new Tuple<>(modification, DSAUtil.getModificationString(modifications.getObj(modification), Units.NONE, true)));
		}
		if (rows.size() % 2 == 1) {
			rows.add(new Tuple<>("", ""));
		}

		final int height = rows.size() / 2;
		for (int i = 0; i < height; ++i) {
			final Tuple<String, String> leftMod = rows.get(i);
			final Tuple<String, String> rightMod = rows.get(i + height);
			table.addCells(new TextCell(leftMod._1).setPadding(0, 2, 1, 0), new TextCell(leftMod._2).setPadding(0, 1, 1, 0));
			table.addCells(new TextCell(rightMod._1).setPadding(0, 2, 1, 0), new TextCell(rightMod._2).setPadding(0, 1, 1, 0));
		}

		bottom.bottom = table.render(document, 240, left, bottom.bottom, 72, 10) - 5;

		return left + 245;
	}

	private void addStatusTable(final PDDocument document) throws IOException {
		final Table table = new Table().setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(190, FontManager.serif, 10, HAlign.LEFT));
		table.addColumn(new Column(190, FontManager.serif, 10, HAlign.LEFT));
		table.addColumn(new Column(0, FontManager.serif, 10, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Weihe", 3));

		table.addRow(new TextCell("Kirche/Orden", FontManager.serifBold, 7, 7), new TextCell("Rang", FontManager.serifBold, 7, 7),
				new TextCell("Heimattempel", FontManager.serifBold, 7, 7));
		table.addRow("");

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	@Override
	public boolean check() {
		return HeroUtil.isClerical(hero, true);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Geweihtenbrief", true, false, false, hero, fill, fillAll).andThen(event -> {
			final Table table = new Table().setBorder(0, 0, 0, 0);
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
			table.addColumn(new Column(368, FontManager.serif, 10.5f, HAlign.RIGHT).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
			JSONObject actualAttributes = null;
			if (hero != null) {
				actualAttributes = hero.getObj("Eigenschaften");
			}
			final JSONObject liturgyKnowledgeGroup = ResourceManager.getResource("data/Talentgruppen").getObj("Liturgiekenntnis");
			final JSONObject liturgyKnowledge = ResourceManager.getResource("data/Talente").getObj("Liturgiekenntnis").getObj(deity.get());
			final JSONArray challenge = liturgyKnowledge.getArrOrDefault("Probe", liturgyKnowledgeGroup.getArr("Probe"));
			for (int i = 0; i < 3; ++i) {
				String value = " ";
				final String attribute = challenge.getString(i);
				if (hero != null && fill) {
					value = Integer.toString(HeroUtil.getCurrentValue(actualAttributes.getObj(attribute), false));
				}
				table.addCells(attribute, value);
			}
			String enhancementCost = DSAUtil.getEnhancementGroupString(liturgyKnowledgeGroup.getIntOrDefault("Steigerung", 6) + ("".equals(deity.get()) ? 0
					: ResourceManager.getResource("data/Talente").getObj("Liturgiekenntnis").getObj(deity.get()).getIntOrDefault("Steigerung", 0)));
			if (hero != null && fill && !"".equals(deity.get())) {
				enhancementCost = DSAUtil.getEnhancementGroupString(HeroUtil.getTalentComplexity(hero, deity.get()));
			}
			final String name = "Liturgiekenntnis (" + ("".equals(deity.get()) ? "                                   " : deity.get()) + ") (" + enhancementCost
					+ "): ";
			String value = " ";
			if (hero != null && !"".equals(deity.get()) && fillAll) {
				final JSONObject liturgyKnowledges = hero.getObj("Talente").getObjOrDefault("Liturgiekenntnis", null);
				if (liturgyKnowledges != null) {
					final JSONObject actualLiturgyKnowledge = liturgyKnowledges.getObj(deity.get());
					value = Integer.toString(actualLiturgyKnowledge != null ? actualLiturgyKnowledge.getIntOrDefault("TaW", 0) : 0);
				} else {
					value = "0";
				}
			}
			table.addCells(name, value);
			try {
				table.renderRows(event.getDocument(), event.getStream(), 0, -1, 571, 12, event.getHeight() - 54);
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		});

		startCreate(document);

		addStatusTable(document);
		addMiraclesTable(document);

		fillLiturgies(document);

		float left = 12;

		final float currentBottom = bottom.bottom;
		float minBottom = bottom.bottom;

		if (modTable.get()) {
			bottom.bottom = currentBottom;
			left = addModificationTable(document, left);
			minBottom = Math.min(minBottom, bottom.bottom);
		}

		if (categoriesTable.get()) {
			bottom.bottom = currentBottom;
			left = addCategoriesTable(document, left);
			minBottom = Math.min(minBottom, bottom.bottom);
		}

		bottom.bottom = minBottom;

		endCreate(document);
	}

	private void fillLiturgies(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(100, 175, FontManager.serif, 4, 7, HAlign.LEFT));
		table.addColumn(new Column(10, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(14, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(49, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(59, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(21, FontManager.serif, 7, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, 7, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Liturgie", 1);
		final Cell actualTitle = SheetUtil.createTitleCell("E", 1);
		final Cell costTitle = SheetUtil.createTitleCell("K", 1);
		final Cell ritualDurationTitle = SheetUtil.createTitleCell("Rituald.", 1);
		final Cell effectDurationTitle = SheetUtil.createTitleCell("Wirkungsd.", 1);
		final Cell targetTitle = SheetUtil.createTitleCell("Ziel", 1);
		final Cell rangeTitle = ((TextCell) SheetUtil.createTitleCell("Rw.", 1)).setPadding(0, 1, 1, 0);
		final Cell descriptionTitle = SheetUtil.createTitleCell("Beschreibung", 1);

		table.addRow(nameTitle, actualTitle, costTitle, ritualDurationTitle, effectDurationTitle, targetTitle, rangeTitle, descriptionTitle);

		final JSONObject liturgyLevels = ResourceManager.getResource("data/Liturgiegrade");
		final JSONObject liturgies = ResourceManager.getResource("data/Liturgien");

		final int numLevels = liturgyLevels.size();

		@SuppressWarnings("unchecked")
		final Map<String, String>[] liturgiesByLevel = new Map[numLevels];

		for (int i = 0; i < numLevels; ++i) {
			liturgiesByLevel[i] = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
		}

		DSAUtil.foreach(liturgy -> liturgy.getObj("Gottheiten").containsKey(deity.get()), (liturgyName, liturgy) -> {
			final int grade = liturgy.getObj("Gottheiten").getObj(deity.get()).getIntOrDefault("Grad", liturgy.getIntOrDefault("Grad", 1));
			final String name = liturgy.getObj("Gottheiten").getObj(deity.get()).getStringOrDefault("Name", liturgyName);
			if (grade >= numLevels || grade < 0) {
				liturgiesByLevel[numLevels - 1].put(name, liturgyName);
			} else {
				liturgiesByLevel[grade].put(name, liturgyName);
			}
		}, liturgies);

		int i = 0;
		for (final String levelName : liturgyLevels.keySet()) {
			final JSONObject level = liturgyLevels.getObj(levelName);

			final int pKaP = level.getIntOrDefault("pKaP", 0);

			final TextCell titleCell = new TextCell(levelName, FontManager.serifBold, 7, 7);
			titleCell.addText(new Text("(" + level.getIntOrDefault("KaP", 0) + " KaP, " + (pKaP != 0 ? pKaP + " pKaP, " : "") + "Probe "
					+ Util.getSignedIntegerString(level.getIntOrDefault("Probe", 0)) + ", Wirkung: "
					+ DSAUtil.getModificationString(level.getObj("Wirkung"), Units.NONE, false) + ", " + level.getIntOrDefault("Kosten", 0) + " AP)")
							.setFont(FontManager.serif));

			table.addRow(titleCell.setColSpan(8));

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

			JSONObject actualSkills = null;
			JSONObject cheaperSkills = null;
			if (hero != null) {
				actualSkills = hero.getObj("Sonderfertigkeiten");
				cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
			}

			final int cost = level.getIntOrDefault("Kosten", 50 * i);

			for (final String name : liturgiesByLevel[i].keySet()) {
				final String baseName = liturgiesByLevel[i].get(name);
				final JSONObject liturgy = liturgies.getObj(baseName);
				if (hero != null && fill) {
					fillLiturgy(table, baseName, name, liturgy, actualSkills.getObjOrDefault(baseName, null), cheaperSkills.getObjOrDefault(baseName, null),
							cost);
				} else {
					fillLiturgy(table, baseName, name, liturgy, null, null, cost);
				}
			}
			++i;
		}

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	private void fillLiturgy(final Table table, final String baseName, final String name, final JSONObject baseLiturgy, final JSONObject actualLiturgy,
			final JSONObject cheaperLiturgy, final int origCost) {
		final JSONObject liturgy = baseLiturgy.getObj("Gottheiten").getObj(deity.get());

		String actual = " ";
		String cost = " ";

		if (hero != null && fill) {
			if (actualLiturgy != null) {
				actual = "X";
			}

			final int newCost = new ProOrCon(baseName, hero, baseLiturgy, actualLiturgy != null ? actualLiturgy : new JSONObject(null)).getCost();
			if (newCost != origCost) {
				if (newCost == (origCost + 1) / 2) {
					cost = "X";
				} else if (newCost == (origCost + 2) / 4) {
					cost = "XX";
				} else if (newCost != origCost) {
					cost = Integer.toString(newCost);
				}
			}
		}

		final String ritualDuration = DSAUtil.getModificationString(liturgy.getObjOrDefault("Ritualdauer", baseLiturgy.getObjOrDefault("Ritualdauer", null)),
				Units.TIME, false);

		final String effectDuration = DSAUtil
				.getModificationString(liturgy.getObjOrDefault("Wirkungsdauer", baseLiturgy.getObjOrDefault("Wirkungsdauer", null)), Units.TIME, false);

		final String target = liturgy.getStringOrDefault("Ziel", baseLiturgy.getStringOrDefault("Ziel", ""));

		final String range = DSAUtil.getModificationString(liturgy.getObjOrDefault("Reichweite", baseLiturgy.getObjOrDefault("Reichweite", null)), Units.RANGE,
				false);

		final String description = liturgy.getStringOrDefault("Beschreibung:Kurz", baseLiturgy.getStringOrDefault("Beschreibung:Kurz", ""));

		table.addCells(name, actual, cost, ritualDuration, effectDuration, target, range, description);
	}

	@Override
	public void load() {
		super.load();
		final Set<String> deities = ResourceManager.getResource("data/Talente").getObj("Liturgiekenntnis").keySet();
		deity.set(deities.iterator().next());
		settings.addStringChoice("Gottheit", deity, deities);
		settings.addBooleanChoice("Modifikationen", modTable);
		settings.addBooleanChoice("Kategorien", categoriesTable);
	}

	@Override
	public void setHero(final JSONObject hero) {
		super.setHero(hero);
		if (HeroUtil.isClerical(hero, true)) {
			final JSONArray liturgyKnowledge = hero.getObj("Sonderfertigkeiten").getArrOrDefault("Liturgiekenntnis", null);
			if (liturgyKnowledge != null && liturgyKnowledge.size() > 0) {
				deity.set(liturgyKnowledge.getObj(0).getString("Auswahl"));
			}
		}
	}

	@Override
	public String toString() {
		return "Geweihtenbrief";
	}
}
