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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;

import boxtable.cell.Cell;
import boxtable.cell.TableCell;
import boxtable.cell.TextCell;
import boxtable.common.HAlign;
import boxtable.event.EventType;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.hero.ProOrCon;
import dsa41basis.util.RequirementsUtil;
import dsatool.resources.ResourceManager;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SpecialSkillsSheet extends Sheet {

	private static Map<String, Object> emptyChoiceText = new HashMap<>();
	static {
		emptyChoiceText.put("Auswahl", "");
		emptyChoiceText.put("Freitext", "");
	}

	private final IntegerProperty additionalChoiceRows = new SimpleIntegerProperty(1);

	private final float fontSize = 6;
	private final Map<String, StringProperty> skillGroups = new HashMap<>();

	public SpecialSkillsSheet() {
		super(788);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Sonderfertigkeiten", true, false, false, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		fillSkills(document);

		endCreate(document);
	}

	private void fillSkill(final Table table, final boolean ownSkills, final String skillName, final JSONObject skill, final JSONObject actualSkill,
			final JSONObject cheaperSkill) {
		if (ownSkills && actualSkill == null && cheaperSkill == null) return;

		String name = skillName;
		if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
			if (fill && actualSkill != null && (actualSkill.containsKey("Auswahl") || actualSkill.containsKey("Freitext"))) {
				name += " (" + actualSkill.getStringOrDefault("Auswahl", "") + actualSkill.getStringOrDefault("Freitext", "") + ')';
			} else if (fill && cheaperSkill != null && (cheaperSkill.containsKey("Auswahl") || cheaperSkill.containsKey("Freitext"))) {
				name += " (" + cheaperSkill.getStringOrDefault("Auswahl", "") + cheaperSkill.getStringOrDefault("Freitext", "") + ')';
			} else {
				name += ": ";
			}
		}

		String actual = "";
		if (fill && actualSkill != null) {
			final JSONObject requirements = skill.getObjOrDefault("Voraussetzungen", null);
			final String choice = actualSkill.getString("Auswahl");
			final String text = actualSkill.getString("Freitext");
			if (skill.getBoolOrDefault("Abgestuft", false)) {
				actual = actualSkill.getIntOrDefault("Stufe", 1).toString();
			} else {
				actual = "X";
			}
			actual = RequirementsUtil.isRequirementFulfilled(hero, requirements, choice, text, false) ? actual : "O";
		}

		String cheaper = " ";
		if (fill && skill.containsKey("Kosten")) {
			final int origCost = skill.getInt("Kosten");
			final int newCost = new ProOrCon(skillName, hero, skill,
					actualSkill != null ? actualSkill : cheaperSkill != null ? cheaperSkill : new JSONObject(emptyChoiceText, null)).getCost();
			if (newCost != origCost) {
				if (newCost == (origCost + 1) / 2) {
					cheaper = "X";
				} else if (newCost == (origCost + 2) / 4) {
					cheaper = "XX";
				} else if (newCost != origCost) {
					cheaper = Integer.toString(newCost);
				}
			}
		}

		final String prevalence = skill.containsKey("Verbreitung") ? skill.getInt("Verbreitung").toString() : "";
		final String cost = skill.containsKey("Kosten") ? skill.getInt("Kosten").toString() : "var";

		final String preconditions = SheetUtil.getRequirementString(skill.getObjOrDefault("Voraussetzungen", null), skill);
		final String description = skill.getStringOrDefault("Beschreibung:Kurz", "");

		table.addCells(name, actual, cheaper, prevalence, cost, preconditions, description);
	}

	private void fillSkills(final PDDocument document) throws IOException {
		final Table baseTable = new Table();
		baseTable.addEventHandler(EventType.BEGIN_PAGE, header);

		baseTable.addColumn(new Column(110, 110, FontManager.serif, 2, fontSize, HAlign.LEFT));
		baseTable.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(107, 107, FontManager.serif, 2, fontSize, HAlign.LEFT));
		baseTable.addColumn(new Column(0, 0, FontManager.serif, 2, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Fertigkeit", 1);
		final Cell actualTitle = SheetUtil.createTitleCell("E", 1);
		final Cell cheaperTitle = SheetUtil.createTitleCell("K", 1);
		final Cell prevalenceTitle = SheetUtil.createTitleCell("V", 1);
		final Cell costTitle = SheetUtil.createTitleCell("AP", 1);
		final Cell preconditionTitle = SheetUtil.createTitleCell("Voraussetzungen", 1);
		final Cell descriptionTitle = SheetUtil.createTitleCell("Beschreibung", 1);

		baseTable.addRow(nameTitle, actualTitle, cheaperTitle, prevalenceTitle, costTitle, preconditionTitle, descriptionTitle);
		baseTable.setNumHeaderRows(1);

		final JSONObject specialSkills = ResourceManager.getResource("data/Sonderfertigkeiten");

		for (final String groupName : specialSkills.keySet()) {
			if ("Keine".equals(skillGroups.get(groupName).get())) {
				continue;
			}

			final Table table = baseTable.duplicate().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			final JSONObject group = specialSkills.getObj(groupName);

			table.addRow(new TextCell(groupName, FontManager.serifBold, 0, fontSize).setColSpan(7));

			JSONObject actualSkills = null;
			JSONObject cheaperSkills = null;
			if (hero != null) {
				actualSkills = hero.getObj("Sonderfertigkeiten");
				cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
			}

			final boolean ownSkills = "Erlernte".equals(skillGroups.get(groupName).get());

			for (final String name : group.keySet()) {
				final JSONObject skill = group.getObj(name);
				if (!skill.containsKey("Auswahl") || !skill.containsKey("Freitext")) {
					if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
						boolean exists = false;
						if (hero != null) {
							if (actualSkills.containsKey(name)) {
								exists = true;
								final JSONArray actualChoiceSkills = actualSkills.getArr(name);
								for (int i = 0; i < actualChoiceSkills.size(); ++i) {
									final JSONObject actualSkill = actualChoiceSkills.getObj(i);
									fillSkill(table, ownSkills, name, skill, actualSkill, null);
								}
							}
							if (cheaperSkills.containsKey(name)) {
								exists = true;
								final JSONArray cheaperChoiceSkills = cheaperSkills.getArr(name);
								for (int i = 0; i < cheaperChoiceSkills.size(); ++i) {
									final JSONObject cheaperSkill = cheaperChoiceSkills.getObj(i);
									boolean found = false;
									if (actualSkills.containsKey(name)) {
										final JSONArray actualChoiceSkills = actualSkills.getArr(name);
										for (int j = 0; j < actualChoiceSkills.size(); ++j) {
											final JSONObject actualSkill = actualChoiceSkills.getObj(j);
											if (skill.containsKey("Auswahl") && actualSkill.getString("Auswahl").equals(cheaperSkill.getString("Auswahl")) ||
													skill.containsKey("Freitext")
															&& actualSkill.getString("Freitext").equals(cheaperSkill.getString("Freitext"))) {
												found = true;
												break;
											}
										}
									}
									if (!found) {
										fillSkill(table, ownSkills, name, skill, null, cheaperSkill);
									}
								}
							}
						}
						for (int i = !exists && additionalChoiceRows.get() == 0 ? -1 : 0; i < additionalChoiceRows.get(); ++i) {
							fillSkill(table, ownSkills, name, skill, null, null);
						}
					} else {
						if (hero != null && fill) {
							fillSkill(table, ownSkills, name, skill, actualSkills.getObjOrDefault(name, null), cheaperSkills.getObjOrDefault(name, null));
						} else {
							fillSkill(table, ownSkills, name, skill, null, null);
						}
					}
				}
			}

			if (table.getNumRows() > 1) {
				final Cell tableCell = new TableCell(table).setColSpan(7);
				baseTable.addRow(tableCell);
			}
		}

		bottom.bottom = baseTable.render(document, 571, 12, bottom.bottom, 54, 10) - 5;
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = new JSONObject(parent);
		settings.put("Als eigenständigen Bogen drucken", separatePage.get());
		settings.put("Leerseite einfügen", emptyPage.get());
		settings.put("Zusätzliche Zeilen für Sonderfertigkeiten mit Auswahl", additionalChoiceRows.get());
		final JSONObject groups = new JSONObject(settings);
		for (final String name : skillGroups.keySet()) {
			groups.put(name, skillGroups.get(name).get());
		}
		settings.put("Gruppen", groups);
		return settings;
	}

	@Override
	public void load() {
		super.load();
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Sonderfertigkeiten mit Auswahl", additionalChoiceRows, 0, 30);

		final boolean[] lock = new boolean[] { false };

		final StringProperty showGroups = new SimpleStringProperty("");
		settingsPage.addStringChoice("Sonderfertigkeiten anzeigen", showGroups, Arrays.asList("Alle", "Erlernte", "Keine", ""));
		showGroups.addListener((o, oldV, newV) -> {
			if (!"".equals(newV)) {
				lock[0] = true;
				for (final StringProperty group : skillGroups.values()) {
					group.setValue(newV);
				}
				lock[0] = false;
			}
		});

		final JSONObject skillGroupNames = ResourceManager.getResource("data/Sonderfertigkeiten");
		for (final String skillGroupName : skillGroupNames.keySet()) {
			skillGroups.put(skillGroupName, new SimpleStringProperty("Schwarze Gaben".equals(skillGroupName) ? "Erlernte" : "Alle"));
			final StringProperty group = skillGroups.get(skillGroupName);
			settingsPage.addStringChoice(skillGroupName, skillGroups.get(skillGroupName), Arrays.asList("Alle", "Erlernte", "Keine"));
			group.addListener((o, oldV, newV) -> {
				if (!lock[0]) {
					showGroups.setValue("");
				}
			});
		}
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		additionalChoiceRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Sonderfertigkeiten mit Auswahl", 1));
		final JSONObject groups = settings.getObjOrDefault("Gruppen", new JSONObject(null));
		for (final String name : skillGroups.keySet()) {
			skillGroups.get(name).set(groups.getStringOrDefault(name, name.equals("Schwarze Gaben") ? "Erlernte" : "Alle"));
		}
	}

	@Override
	public String toString() {
		return "Sonderfertigkeiten";
	}
}
