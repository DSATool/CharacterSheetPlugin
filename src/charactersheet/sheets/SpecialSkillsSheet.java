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
import dsatool.ui.ReactiveSpinner;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SpecialSkillsSheet extends Sheet {

	private static final String ADDITIONAL_CHOICE_ROWS = "Zusätzliche Zeilen für Sonderfertigkeiten mit Auswahl";
	private static final String OWN_SKILLS_ONLY = "Nur erlernte/verbilligte Sonderfertigkeiten";
	private static final String ADDITIONAL_ROWS = "Zusätzliche Zeilen";

	private static Map<String, Object> emptyChoiceText = new HashMap<>();
	static {
		emptyChoiceText.put("Auswahl", "");
		emptyChoiceText.put("Freitext", "");
	}

	private final float fontSize = 6f;

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

		for (final TitledPane section : settingsPage.getSections()) {
			if (!settingsPage.getBool(section, "").get()) {
				continue;
			}

			final Table table = baseTable.duplicate().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			final String groupName = settingsPage.getString(section, null).get();
			final JSONObject group = specialSkills.getObj(groupName);

			table.addRow(new TextCell(groupName, FontManager.serifBold, 0, fontSize).setColSpan(7));

			JSONObject actualSkills = null;
			JSONObject cheaperSkills = null;
			if (hero != null) {
				actualSkills = hero.getObj("Sonderfertigkeiten");
				cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
			}

			final boolean ownSkills = settingsPage.getBool(section, OWN_SKILLS_ONLY).get();
			final int additionalChoiceRows = settingsPage.getInt(section, ADDITIONAL_CHOICE_ROWS).get();

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
						for (int i = !exists && additionalChoiceRows == 0 ? -1 : 0; i < additionalChoiceRows; ++i) {
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

			for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
				table.addRow("");
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
		final JSONObject settings = super.getSettings(parent);
		settings.put(ADDITIONAL_CHOICE_ROWS, settingsPage.getInt(ADDITIONAL_CHOICE_ROWS).get());

		final JSONObject groups = new JSONObject(settings);
		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject group = new JSONObject(groups);
			group.put("Anzeigen", settingsPage.getBool(section, "").get());
			group.put(ADDITIONAL_CHOICE_ROWS, settingsPage.getInt(section, ADDITIONAL_CHOICE_ROWS).get());
			group.put(OWN_SKILLS_ONLY, settingsPage.getBool(section, OWN_SKILLS_ONLY).get());
			group.put(ADDITIONAL_ROWS, settingsPage.getInt(section, ADDITIONAL_ROWS).get());
			groups.put(name, group);
		}
		settings.put("Gruppen", groups);

		return settings;
	}

	@Override
	public void load() {
		super.load();

		final boolean[] lock = { false };

		final ReactiveSpinner<Integer> choiceRows = settingsPage.addIntegerChoice(ADDITIONAL_CHOICE_ROWS, 0, 30);
		choiceRows.valueProperty().addListener((o, oldV, newV) -> {
			if (!lock[0]) {
				lock[0] = true;
				for (final TitledPane section : settingsPage.getSections()) {
					settingsPage.getInt(section, ADDITIONAL_CHOICE_ROWS).setValue(newV);
				}
				lock[0] = false;
			}
		});

		final CheckBox ownSkillsOnly = settingsPage.addBooleanChoice(OWN_SKILLS_ONLY);
		ownSkillsOnly.setIndeterminate(true);
		ownSkillsOnly.selectedProperty().addListener((o, oldV, newV) -> {
			lock[0] = true;
			for (final TitledPane section : settingsPage.getSections()) {
				settingsPage.getBool(section, OWN_SKILLS_ONLY).setValue(newV);
			}
			lock[0] = false;
		});

		final JSONObject skillGroupNames = ResourceManager.getResource("data/Sonderfertigkeiten");
		for (final String skillGroupName : skillGroupNames.keySet()) {
			final TitledPane section = settingsPage.addSection(skillGroupName, true);
			sections.put(skillGroupName, section);

			settingsPage.addIntegerChoice(ADDITIONAL_CHOICE_ROWS, 0, 30);
			final IntegerProperty additional = settingsPage.getInt(section, ADDITIONAL_CHOICE_ROWS);
			additional.addListener((o, oldV, newV) -> {
				if (!lock[0]) {
					lock[0] = true;
					choiceRows.getValueFactory().setValue(0);
					lock[0] = false;
				}
			});

			final BooleanProperty own = settingsPage.addBooleanChoice(OWN_SKILLS_ONLY).selectedProperty();

			final ReactiveSpinner<Integer> additionalRowsControl = settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 30);
			additionalRowsControl.setDisable(true);

			own.addListener((o, oldV, newV) -> {
				if (!lock[0]) {
					ownSkillsOnly.setIndeterminate(true);
				}
				additionalRowsControl.setDisable(!newV);
			});
		}
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		settingsPage.getInt(ADDITIONAL_CHOICE_ROWS).set(settings.getIntOrDefault(ADDITIONAL_CHOICE_ROWS, 1));

		orderSections(ResourceManager.getResource("data/Sonderfertigkeiten").keySet());
		final JSONObject groups = settings.getObjOrDefault("Gruppen", new JSONObject(null));
		orderSections(groups.keySet());

		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject group = groups.getObjOrDefault(name, new JSONObject(null));
			settingsPage.getBool(section, "").set(group.getBoolOrDefault("Anzeigen", true));
			settingsPage.getInt(section, ADDITIONAL_CHOICE_ROWS).set(group.getIntOrDefault(ADDITIONAL_CHOICE_ROWS, "Schwarze Gaben".equals(name) ? 0 : 1));
			settingsPage.getBool(section, OWN_SKILLS_ONLY).set(group.getBoolOrDefault(OWN_SKILLS_ONLY, "Schwarze Gaben".equals(name) ? true : false));
			settingsPage.getInt(section, ADDITIONAL_ROWS).set(group.getIntOrDefault(ADDITIONAL_ROWS, 0));
		}
	}

	@Override
	public String toString() {
		return "Sonderfertigkeiten";
	}
}
