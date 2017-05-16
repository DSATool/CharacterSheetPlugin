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
import dsatool.resources.ResourceManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SpecialSkillsSheet extends Sheet {

	private final IntegerProperty additionalChoiceRows = new SimpleIntegerProperty(1);
	private final float fontSize = 6;
	private final BooleanProperty ownSkillsOnly = new SimpleBooleanProperty(false);
	private final Map<String, BooleanProperty> skillGroups = new HashMap<>();

	public SpecialSkillsSheet() {
		super(788);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (separatePage.get()) {
			header = SheetUtil.createHeader("Sonderfertigkeiten", true, false, false, hero, fill, fillAll);
		}

		startCreate(document);

		fillSkills(document);

		endCreate(document);
	}

	private void fillSkill(final Table table, String name, final JSONObject skill, final JSONObject actualSkill, final JSONObject cheaperSkill) {
		if (ownSkillsOnly.get() && actualSkill == null && cheaperSkill == null) return;

		if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
			if (fill && actualSkill != null && (actualSkill.containsKey("Auswahl") || actualSkill.containsKey("Freitext"))) {
				name += " (" + actualSkill.getStringOrDefault("Auswahl", "") + actualSkill.getStringOrDefault("Freitext", "") + ')';
			} else {
				name += ": ";
			}
		}

		final String actual = fill && actualSkill != null ? "X" : " ";

		String cheaper = " ";
		if (fill) {
			final int origCost = skill.getIntOrDefault("Kosten", 0);
			final int newCost = new ProOrCon(name, hero, skill, actualSkill != null ? actualSkill : new JSONObject(null)).getCost();
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

		baseTable.addColumn(new Column(110, FontManager.serif, fontSize, HAlign.LEFT));
		baseTable.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
		baseTable.addColumn(new Column(117, FontManager.serif, fontSize, HAlign.LEFT));
		baseTable.addColumn(new Column(0, FontManager.serif, fontSize, HAlign.LEFT));

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
			if (!skillGroups.get(groupName).get()) {
				continue;
			}

			final Table table = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			table.addColumn(new Column(110, 110, FontManager.serif, 2, fontSize, HAlign.LEFT));
			table.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
			table.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			table.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
			table.addColumn(new Column(16, FontManager.serif, fontSize, HAlign.CENTER));
			table.addColumn(new Column(107, 107, FontManager.serif, 2, fontSize, HAlign.LEFT));
			table.addColumn(new Column(0, 0, FontManager.serif, 2, fontSize, HAlign.LEFT));

			final JSONObject group = specialSkills.getObj(groupName);

			table.addRow(new TextCell(groupName, FontManager.serifBold, 0, fontSize).setColSpan(7));

			JSONObject actualSkills = null;
			JSONObject cheaperSkills = null;
			if (hero != null) {
				actualSkills = hero.getObj("Sonderfertigkeiten");
				cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
			}

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
									fillSkill(table, name, skill, actualSkill, null);
								}
							}
							if (cheaperSkills.containsKey(name)) {
								exists = true;
								final JSONArray cheaperChoiceSkills = cheaperSkills.getArr(name);
								for (int i = 0; i < cheaperChoiceSkills.size(); ++i) {
									final JSONObject cheaperSkill = cheaperChoiceSkills.getObj(i);
									fillSkill(table, name, skill, null, cheaperSkill);
								}
							}
						}
						for (int i = !exists && additionalChoiceRows.get() == 0 ? -1 : 0; i < additionalChoiceRows.get(); ++i) {
							fillSkill(table, name, skill, null, null);
						}
					} else {
						if (hero != null && fill) {
							fillSkill(table, name, skill, actualSkills.getObjOrDefault(name, null), cheaperSkills.getObjOrDefault(name, null));
						} else {
							fillSkill(table, name, skill, null, null);
						}
					}
				}
			}

			if (table.getNumRows() > 1) {
				final Cell tableCell = new TableCell(table).setColSpan(7);
				baseTable.addRow(tableCell);
			}
		}

		baseTable.render(document, 571, 12, bottom.bottom, 54, 10);
	}

	@Override
	public void load() {
		super.load();
		settings.addBooleanChoice("Nur erlernte/verbilligte Sonderfertigkeiten", ownSkillsOnly);
		settings.addIntegerChoice("Zusätzliche Zeilen Sonderfertigkeiten mit Auswahl", additionalChoiceRows, 0, 30);
		final JSONObject skillGroupNames = ResourceManager.getResource("data/Sonderfertigkeiten");
		for (final String skillGroupName : skillGroupNames.keySet()) {
			skillGroups.put(skillGroupName, new SimpleBooleanProperty(true));
			settings.addBooleanChoice(skillGroupName, skillGroups.get(skillGroupName));
		}
	}

	@Override
	public String toString() {
		return "Sonderfertigkeiten";
	}
}
