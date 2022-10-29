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
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import boxtable.cell.Cell;
import boxtable.cell.TextCell;
import boxtable.common.HAlign;
import boxtable.event.EventType;
import boxtable.event.TableEvent;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import charactersheet.util.SheetUtil.BottomObserver;
import dsa41basis.hero.ProOrCon;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.DSAUtil.Units;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.ui.ReactiveSpinner;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class RitualsSheet extends Sheet {

	private static final String OWN_RITUALS_ONLY = "Nur erlernte/verbilligte Rituale";
	private static final String ADDITIONAL_ROWS = "Zusätzliche Zeilen";

	private static final float fontSize = 7f;

	private final boolean[] lock = { false };

	private CheckBox ownRitualsOnly;

	public RitualsSheet() {
		super(536);
	}

	private void addSection(final String name, final Tuple<String, JSONObject> data, final boolean selected) {
		final TitledPane section = settingsPage.addSection(name, true);
		sections.put(name, section);
		section.setUserData(data);
		settingsPage.getBool(section, "").set(selected);
		final BooleanProperty own = settingsPage.addBooleanChoice(OWN_RITUALS_ONLY).selectedProperty();
		final ReactiveSpinner<Integer> additionalRows = settingsPage.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);
		additionalRows.setDisable(true);
		own.addListener((o, oldV, newV) -> {
			if (!lock[0]) {
				ownRitualsOnly.setIndeterminate(true);
			}
			additionalRows.setDisable(!newV);
		});
	}

	private void addUnspecificSection(final String name, final JSONObject groups) {
		boolean show = false;
		if (groups.containsKey(name)) {
			show = groups.getBoolOrDefault(name, false);
		} else if (hero != null) {
			show = switch (name) {
				case "Stabzauber" -> false;
				case "Zauberzeichen" -> hero.getObj("Sonderfertigkeiten").containsKey("Zauberzeichen");
				case "Bann- und Schutzkreise" -> {
					yield hero.getObj("Sonderfertigkeiten").containsKey("Zauberzeichen") || hero.getObj("Zauber").containsKey("Invocatio minor")
							|| hero.getObj("Zauber").containsKey("Invocatio maior");
				}
				case "Elfenlieder" -> hero.getObj("Vorteile").containsKey("Zweistimmiger Gesang");
				default -> {
					final List<String> requiredKnowledges = getRequiredKnowledges(name);
					if (requiredKnowledges.isEmpty()) {
						yield true;
					} else {
						final JSONArray ritualKnowledges = hero.getObj("Sonderfertigkeiten").getArrOrDefault("Ritualkenntnis", new JSONArray(null));
						boolean found = false;
						for (int i = 0; !found && i < ritualKnowledges.size(); ++i) {
							if (requiredKnowledges.contains(ritualKnowledges.getObj(i).getString("Auswahl"))) {
								found = true;
							}
						}
						yield found;
					}
				}
			};
		}
		addSection(name, null, show);
	}

	@Override
	public boolean check() {
		return HeroUtil.isMagical(hero);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		final Table[] ritualKnowledgeTable = new Table[] { getRitualKnowledgeTable(pageSize != SheetUtil.landscape) };

		header = SheetUtil.createHeader("Ritualbrief", true, true, false, hero, fill, fillAll, showName, showDate).andThen(event -> {
			try {
				if (pageSize == SheetUtil.landscape) {
					bottom.bottom = ritualKnowledgeTable[0].render(document, 818, 12, 536, 59, 10) - 5;
				} else {
					bottom.bottom = ritualKnowledgeTable[0].render(document, 571, 12, 771, 72, 10) - 5;
				}
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		});

		final JSONObject rituals = ResourceManager.getResource("data/Rituale");
		final JSONObject ritualGroupData = ResourceManager.getResource("data/Ritualgruppen");

		final JSONObject apport = rituals.getObj("Allgemeine Rituale").getObj("Apport");

		final List<Tuple<Table, Boolean>> tables = new ArrayList<>(settingsPage.getSections().size());

		for (final TitledPane section : settingsPage.getSections()) {
			if (!settingsPage.getBool(section, "").get()) {
				continue;
			}

			@SuppressWarnings("unchecked")
			final Tuple<String, JSONObject> data = (Tuple<String, JSONObject>) section.getUserData();
			final String ritualGroupName = data == null ? settingsPage.getString(section, null).get() : data._1;
			final JSONObject ritualGroup = ritualGroupData.getObj(ritualGroupName);
			JSONObject item = data == null ? null : data._2;
			final JSONObject baseItem = item;
			if (item != null) {
				final String ritualObjectName = ritualGroup.getString("Ritualobjekt");
				if (item.containsKey(ritualObjectName)) {
					item = item.getObj(ritualObjectName);
				}
			}

			try {
				final String name = settingsPage.getString(section, null).get();
				final JSONObject ritual = rituals.getObj(ritualGroupName);
				final Tuple<Table, Boolean> table = createTable(document, name, ritualGroupName, ritual, ritualGroup, item, baseItem, apport);
				if (table._1.getNumRows() > 2) {
					tables.add(table);
				}
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		for (int i = 0; i < tables.size(); ++i) {
			final Table table = tables.get(i)._1;
			Boolean portrait = tables.get(i)._2;

			if (portrait == null) {
				if (settingsPage.getBool(AS_SEPARATE_SHEET).get()) {
					if (tables.size() > i + 1) {
						portrait = tables.get(i + 1)._2;
					} else {
						portrait = false;
					}
				} else {
					portrait = pageSize != SheetUtil.landscape;
				}
			}

			if (i == 0) {
				if (portrait) {
					pageSize = PDRectangle.A4;
					height = 766 - Math.round(ritualKnowledgeTable[0].getHeight(571));
				} else {
					pageSize = SheetUtil.landscape;
					height = 531 - Math.round(ritualKnowledgeTable[0].getHeight(818));
				}

				startCreate(document);
			}

			table.addEventHandler(EventType.BEGIN_PAGE, header);
			if (portrait) {
				if (!SheetUtil.matchesPageSize(document, PDRectangle.A4)) {
					bottom = new BottomObserver(771);
					pageSize = PDRectangle.A4;
					ritualKnowledgeTable[0] = getRitualKnowledgeTable(true);
					final PDPage page = new PDPage(PDRectangle.A4);
					document.addPage(page);
					final PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true);
					header.accept(
							new TableEvent(document, stream, 0, PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight()));
					stream.close();
				}
				bottom.bottom = table.render(document, 571, 12, bottom.bottom, 77 + ritualKnowledgeTable[0].getHeight(571), 10) - 5;
			} else {
				if (!SheetUtil.matchesPageSize(document, SheetUtil.landscape)) {
					bottom = new BottomObserver(536);
					pageSize = SheetUtil.landscape;
					ritualKnowledgeTable[0] = getRitualKnowledgeTable(false);
					final PDPage page = new PDPage(SheetUtil.landscape);
					document.addPage(page);
					final PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true);
					header.accept(
							new TableEvent(document, stream, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
					stream.close();
				}
				bottom.bottom = table.render(document, 818, 12, bottom.bottom, 64 + ritualKnowledgeTable[0].getHeight(818), 10) - 5;
			}
		}

		endCreate(document);
	}

	private Tuple<Table, Boolean> createTable(final PDDocument document, final String name, final String groupName, final JSONObject actualGroup,
			final JSONObject group,
			final JSONObject item, final JSONObject baseItem, final JSONObject apport) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		final Cell nameTitle = new TextCell("Ritual", FontManager.serifBold, 8.5f, 8.5f);
		final Column nameColumn = new Column(50, 125, FontManager.serif, 5.5f, fontSize, HAlign.LEFT);
		final Cell actualTitle = new TextCell("E", FontManager.serifBold, 8.5f, 8.5f);
		final Column actualColumn = new Column(10, 10, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell seTitle = new TextCell("K", FontManager.serifBold, 8.5f, 8.5f);
		final Column seColumn = new Column(16, 16, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell spreadTitle = new TextCell("V", FontManager.serifBold, 8.5f, 8.5f);
		final Column spreadColumn = new Column(10, 10, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell multiSpreadTitle = new TextCell("Verbreitung", FontManager.serifBold, 8.5f, 8.5f);
		final Column multiSpreadColumn = new Column(75, 100, FontManager.serif, fontSize / 2, fontSize, HAlign.LEFT);
		final Cell apTitle = new TextCell("AP", FontManager.serifBold, 8.5f, 8.5f);
		final Column apColumn = new Column(16, 16, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell volumeTitle = new TextCell("V", FontManager.serifBold, 8.5f, 8.5f);
		final Column volumeColumn = new Column(10, 10, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell activeTitle = new TextCell("B", FontManager.serifBold, 8.5f, 8.5f);
		final Column activeColumn = new Column(10, 10, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, 8.5f, 8.5f);
		final Column challengeColumn = new Column(0, 60, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell timeTitle = new TextCell("Dauer", FontManager.serifBold, 8.5f, 8.5f);
		final Column timeColumn = new Column(0, 30, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell costTitle = new TextCell("Kosten", FontManager.serifBold, 8.5f, 8.5f);
		final Column costColumn = new Column(0, 35, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell contraChallengeTitle = new TextCell("G.-Probe", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column contraChallengeColumn = new Column(0, 40, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell creationChallengeTitle = new TextCell("E.-Probe", FontManager.serifBold, 8.5f, 8.5f);
		final Column creationChallengeColumn = new Column(0, 60, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell creationCostTitle = new TextCell("E.-Kosten", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column creationCostColumn = new Column(0, 40, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell moonPhaseTitle = new TextCell("Mond.", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column moonPhaseColumn = new Column(0, 40, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell activationChallengeTitle = new TextCell("A.-Probe", FontManager.serifBold, 8.5f, 8.5f);
		final Column activationChallengeColumn = new Column(0, 60, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell activationCostTitle = new TextCell("A.-Kosten", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column activationCostColumn = new Column(0, 40, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell activationTimeTitle = new TextCell("A.-Dauer", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column activationTimeColumn = new Column(0, 352, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell rangeTitle = new TextCell("RW", FontManager.serifBold, 8.5f, 8.5f);
		final Column rangeColumn = new Column(33, 40, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell targetTitle = new TextCell("ZO", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 1, 1, 0);
		final Column targetColumn = new Column(0, 15, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell durationTitle = new TextCell("W.-Dauer", FontManager.serifBold, 8.5f, 8.5f);
		final Column durationColumn = new Column(0, 45, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER);
		final Cell complexityTitle = new TextCell("Kp", FontManager.serifBold, 8.5f, 8.5f).setPadding(0, 0, 0, 0);
		final Column complexityColumn = new Column(0, 20, FontManager.serif, fontSize, fontSize, HAlign.CENTER);
		final Cell preconditionTitle = new TextCell("Voraussetzungen", FontManager.serifBold, 8.5f, 8.5f);
		final Column preconditionColumn = new Column(0, 110, FontManager.serif, fontSize / 2, fontSize, HAlign.LEFT);
		final Cell descTitle = new TextCell("Beschreibung", FontManager.serifBold, 8.5f, 8.5f);
		final Column descColumn = new Column(0, 0, FontManager.serif, fontSize / 2, fontSize, HAlign.LEFT);

		final boolean isObjectRitual = group.getString("Ritualobjekt") != null;

		table.addColumn(nameColumn);
		table.addCells(nameTitle);
		table.addColumn(actualColumn);
		table.addCells(actualTitle);
		table.addColumn(seColumn);
		table.addCells(seTitle);

		final float[] width = { nameColumn.getMaxWidth() + actualColumn.getMaxWidth() + seColumn.getMaxWidth() + apColumn.getMaxWidth() };

		ifHas("Verbreitung", actualGroup, ritual -> {
			final Object spread = ritual.getUnsafe("Verbreitung");
			if (spread instanceof JSONObject) {
				table.addColumn(multiSpreadColumn);
				table.addCells(multiSpreadTitle);
				width[0] += multiSpreadColumn.getMaxWidth();
			} else {
				table.addColumn(spreadColumn);
				table.addCells(spreadTitle);
				width[0] += spreadColumn.getMaxWidth();
			}
		});

		table.addColumn(apColumn);
		table.addCells(apTitle);

		if (isObjectRitual) {
			ifHas("Volumen", actualGroup, o -> {
				table.addColumn(volumeColumn);
				table.addCells(volumeTitle);
				width[0] += volumeColumn.getMaxWidth();
			});
		}
		if (isObjectRitual) {
			table.addColumn(activeColumn);
			table.addCells(activeTitle);
			width[0] += activeColumn.getMaxWidth();
		}

		ifHas("Ritualprobe", actualGroup, o -> {
			table.addColumn(challengeColumn);
			table.addCells(challengeTitle);
			width[0] += challengeColumn.getMaxWidth();
		});
		ifHas("Ritualdauer", actualGroup, o -> {
			table.addColumn(timeColumn);
			table.addCells(timeTitle);
			width[0] += timeColumn.getMaxWidth();
		});
		ifHas("Ritualkosten", actualGroup, o -> {
			table.addColumn(costColumn);
			table.addCells(costTitle);
			width[0] += costColumn.getMaxWidth();
		});

		ifHas("Gegenprobe", actualGroup, o -> {
			table.addColumn(contraChallengeColumn);
			table.addCells(contraChallengeTitle);
			width[0] += contraChallengeColumn.getMaxWidth();
		});

		ifHas("Erschaffungsprobe", actualGroup, o -> {
			table.addColumn(creationChallengeColumn);
			table.addCells(creationChallengeTitle);
			width[0] += creationChallengeColumn.getMaxWidth();
		});
		ifHas("Erschaffungskosten", actualGroup, o -> {
			table.addColumn(creationCostColumn);
			table.addCells(creationCostTitle);
			width[0] += creationCostColumn.getMaxWidth();
		});
		ifHas("Mondphase", actualGroup, o -> {
			table.addColumn(moonPhaseColumn);
			table.addCells(moonPhaseTitle);
			width[0] += moonPhaseColumn.getMaxWidth();
		});
		ifHas("Aktivierungsprobe", actualGroup, o -> {
			table.addColumn(activationChallengeColumn);
			table.addCells(activationChallengeTitle);
			width[0] += activationChallengeColumn.getMaxWidth();
		});
		ifHas("Aktivierungskosten", actualGroup, o -> {
			table.addColumn(activationCostColumn);
			table.addCells(activationCostTitle);
			width[0] += activationCostColumn.getMaxWidth();
		});
		ifHas("Aktivierungsdauer", actualGroup, o -> {
			table.addColumn(activationTimeColumn);
			table.addCells(activationTimeTitle);
			width[0] += activationTimeColumn.getMaxWidth();
		});

		if (isObjectRitual) {
			table.addColumn(rangeColumn);
			table.addCells(rangeTitle);
			width[0] += rangeColumn.getMaxWidth();
		} else {
			ifHas("Reichweite", actualGroup, o -> {
				table.addColumn(rangeColumn);
				table.addCells(rangeTitle);
				width[0] += rangeColumn.getMaxWidth();
			});
		}

		ifHas("Zielobjekt", actualGroup, o -> {
			table.addColumn(targetColumn);
			table.addCells(targetTitle);
			width[0] += targetColumn.getMaxWidth();
		});
		ifHas("Wirkungsdauer", actualGroup, o -> {
			table.addColumn(durationColumn);
			table.addCells(durationTitle);
			width[0] += durationColumn.getMaxWidth();
		});
		ifHas("Komplexität", actualGroup, o -> {
			table.addColumn(complexityColumn);
			table.addCells(complexityTitle);
			width[0] += complexityColumn.getMaxWidth();
		});

		final List<JSONObject> preconditions = new ArrayList<>();
		DSAUtil.foreach(o -> true, (ritualName, ritual) -> {
			if (!"Apport".equals(ritualName)) {
				preconditions.add(ritual.getObjOrDefault("Voraussetzungen", null));
			}
			return true;
		}, actualGroup);

		boolean allNull = true;
		boolean allSame = true;
		final JSONObject firstPrecondition = preconditions.get(0);
		for (final JSONObject precondition : preconditions) {
			if (precondition != null) {
				allNull = false;
				if (!precondition.equals(firstPrecondition)) {
					allSame = false;
				}
			} else if (firstPrecondition != null) {
				allSame = false;
			}
		}
		final boolean needsPrecondition = !allNull && !allSame;

		if (needsPrecondition) {
			table.addColumn(preconditionColumn);
			table.addCells(preconditionTitle);
		}

		table.addColumn(descColumn);
		table.addCells(descTitle);

		final StringBuilder headerString = new StringBuilder();

		if (group.containsKey("Material")) {
			headerString.append("Material: ");
			headerString.append(hero != null && fill && item != null
					? item.getStringOrDefault("Material", baseItem.getStringOrDefault("Material", "____________________")) : "____________________");
		}

		if (group.containsKey("Volumen")) {
			if (headerString.length() > 0) {
				headerString.append(" ");
			}
			headerString.append("Volumen: ");
			int used = 0;
			if (item != null) {
				final JSONObject rituals = item.getObjOrDefault("Rituale", baseItem.getObjOrDefault("Rituale", new JSONObject(null)));
				for (final String ritualName : rituals.keySet()) {
					final JSONObject actualRitual = "Apport".equals(ritualName) ? apport : actualGroup.getObj(ritualName);
					final String multipleTimes = actualRitual.getStringOrDefault("Mehrfach", null);
					if ("Anzahl".equals(multipleTimes)) {
						final JSONObject ritual = rituals.getObj(ritualName);
						used += ritual.getIntOrDefault("Volumen",
								ritual.getIntOrDefault("Anzahl", 1) * ("Stabzauber".equals(groupName) ? actualRitual.getIntOrDefault("Volumen", 1) : 1));
					} else if (multipleTimes != null) {
						final JSONArray choices = rituals.getArr(ritualName);
						for (int i = 0; i < choices.size(); ++i) {
							used += choices.getObj(i).getIntOrDefault("Volumen",
									"Stabzauber".equals(groupName) ? actualRitual.getIntOrDefault("Volumen", 1) : 1);
						}
					} else {
						used += rituals.getObj(ritualName).getIntOrDefault("Volumen",
								"Stabzauber".equals(groupName) ? actualRitual.getIntOrDefault("Volumen", 1) : 1);
					}
				}
			}
			headerString.append(hero != null && fillAll && item != null ? used : "___");
			headerString.append(" / ");
			int volume = item != null ? item.getIntOrDefault("Volumen", baseItem.getIntOrDefault("Volumen", 0)) : 0;
			if (volume == 0) {
				volume = group.getInt("Volumen");
			}
			headerString.append(hero != null && fill && volume != 0 ? volume : "___");
		}

		if (group.getBoolOrDefault("pAsP", false)) {
			if (headerString.length() > 0) {
				headerString.append(" ");
			}
			headerString.append("pAsP: ");
			int pAsP = 0;
			if (item != null) {
				final JSONObject rituals = item.getObjOrDefault("Rituale", baseItem.getObjOrDefault("Rituale", null));
				for (final String ritualName : rituals.keySet()) {
					final String multipleTimes = actualGroup.getObj(ritualName).getStringOrDefault("Mehrfach", null);
					if ("Anzahl".equals(multipleTimes)) {
						final JSONObject ritual = rituals.getObj(ritualName);
						final JSONObject creationCost = actualGroup.getObj(ritualName).getObj("Erschaffungskosten").getObjOrDefault("Permanent", null);
						pAsP += ritual.getIntOrDefault("pAsP",
								ritual.getIntOrDefault("Anzahl", 1) * (creationCost != null ? creationCost.getIntOrDefault("Additiv", 1) : 0));
					} else if (multipleTimes != null) {
						final JSONArray choices = rituals.getArr(ritualName);
						for (int i = 0; i < choices.size(); ++i) {
							final JSONObject creationCost = actualGroup.getObj(ritualName).getObj("Erschaffungskosten").getObjOrDefault("Permanent", null);
							pAsP += choices.getObj(i).getIntOrDefault("pAsP", creationCost != null ? creationCost.getIntOrDefault("Additiv", 1) : 0);
						}
					} else {
						final JSONObject creationCost = actualGroup.getObj(ritualName).getObj("Erschaffungskosten").getObjOrDefault("Permanent", null);
						pAsP += rituals.getObj(ritualName).getIntOrDefault("pAsP", creationCost != null ? creationCost.getIntOrDefault("Additiv", 1) : 0);
					}
				}
			}
			headerString.append(hero != null && fillAll && item != null ? pAsP : "___");
		}

		final String additionalHeader = headerString.toString();

		table.addRowAtIndex(0, SheetUtil.createTitleCell(name, table.getNumColumns() - 1).setBorder(0, 0, 0, 0.25f),
				new TextCell(additionalHeader).setHAlign(HAlign.RIGHT).setColSpan(1).setBorder(0, 0, 0, 0.25f));

		JSONObject actualSkills = null;
		JSONObject cheaperSkills = null;
		if (hero != null) {
			actualSkills = hero.getObj("Sonderfertigkeiten");
			cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
		}

		for (final String ritualName : actualGroup.keySet()) {
			if (hero != null && fill) {
				fillRitual(table, actualGroup, isObjectRitual, ritualName, actualGroup.getObj(ritualName), actualSkills.getObjOrDefault(ritualName, null),
						cheaperSkills.getObjOrDefault(ritualName, null), item, baseItem, needsPrecondition);
			} else {
				fillRitual(table, actualGroup, isObjectRitual, ritualName, actualGroup.getObj(ritualName), null, null, null, null, needsPrecondition);
			}
		}
		if (isObjectRitual && !"Schlangenring-Zauber".equals(groupName)) {
			final String ritualName = "Apport";
			if (hero != null && fill) {
				fillRitual(table, actualGroup, isObjectRitual, ritualName, apport, actualSkills.getObjOrDefault(ritualName, null),
						cheaperSkills.getObjOrDefault(ritualName, null), item, baseItem, needsPrecondition);
			} else {
				fillRitual(table, actualGroup, isObjectRitual, ritualName, apport, null, null, null, null, needsPrecondition);
			}
		}

		return new Tuple<>(table, "Allgemeine Rituale".equals(groupName) ? null : width[0] < 400);
	}

	private void fillRitual(final Table table, final JSONObject actualGroup, final boolean isObjectRitual, final String ritualName, final JSONObject ritual,
			final JSONObject actualSkill, final JSONObject cheaperSkill, final JSONObject item, final JSONObject baseItem, final boolean needsPrecondition) {
		if (settingsPage.getBool(OWN_RITUALS_ONLY).get() && actualSkill == null && cheaperSkill == null) return;

		String actual = " ";
		if (actualSkill != null) {
			actual = "X";
		}

		String se = " ";
		if (fill) {
			final int origCost = ritual.getIntOrDefault("Kosten", 0);
			final int newCost = new ProOrCon(ritualName, hero, ritual, actualSkill != null ? actualSkill : new JSONObject(null)).getCost();
			if (newCost != origCost) {
				if (newCost == (origCost + 1) / 2) {
					se = "X";
				} else if (newCost == (origCost + 2) / 4) {
					se = "XX";
				} else if (newCost != origCost) {
					se = Integer.toString(newCost);
				}
			}
		}

		String cost = "var";
		if (ritual.containsKey("Kosten")) {
			cost = ritual.getInt("Kosten").toString();
		}

		boolean multipleTimes = false;
		boolean multipleCount = false;
		if (ritual.containsKey("Mehrfach")) {
			multipleTimes = true;
			if ("Anzahl".equals(ritual.getString("Mehrfach"))) {
				multipleCount = true;
			}
		}

		String active = " ";
		if (item != null && baseItem != null) {
			final JSONObject rituals = item.getObjOrDefault("Rituale", baseItem.getObjOrDefault("Rituale", new JSONObject(null)));
			if (item != null && rituals.containsKey(ritualName)) {
				int count = 1;
				if (multipleCount) {
					count = rituals.getObj(ritualName).getIntOrDefault("Anzahl", 1);
				} else if (multipleTimes) {
					count = rituals.getArr(ritualName).size();
				}
				if (count == 0) {} else if (count == 1) {
					active = "X";
				} else {
					active = Integer.toString(count);
				}
			}
		}

		table.addCells(ritualName, actual, se);

		ifHas("Verbreitung", actualGroup, o -> {
			final Object spread = ritual.getUnsafe("Verbreitung");
			if (spread instanceof final JSONObject obj) {
				boolean first = true;
				final StringBuilder res = new StringBuilder();
				for (final String rep : obj.keySet()) {
					if (first) {
						first = false;
					} else {
						res.append(", ");
					}
					res.append(rep);
					res.append(' ');
					res.append(obj.getInt(rep));
				}
				table.addCells(res);
			} else if (spread != null) {
				table.addCells(spread.toString());
			} else {
				table.addCells("");
			}
		});
		table.addCells(cost);

		if (isObjectRitual) {
			ifHas("Volumen", actualGroup, o -> {
				table.addCells(ritual.getIntOrDefault("Volumen", 0));
			});
		}
		if (isObjectRitual) {
			table.addCells(active);
		}

		ifHas("Ritualprobe", actualGroup, o -> {
			table.addCells(DSAUtil.getChallengeString(ritual.getArrOrDefault("Ritualprobe", null)));
		});
		ifHas("Ritualdauer", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualdauer", null), Units.TIME, false));
		});
		ifHas("Ritualkosten", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualkosten", null), Units.NONE, false));
		});

		ifHas("Gegenprobe", actualGroup, o -> {
			table.addCells(ritual.getStringOrDefault("Gegenprobe", "—"));
		});

		ifHas("Erschaffungsprobe", actualGroup, o -> {
			final Object challenge = ritual.getUnsafe("Erschaffungsprobe");
			if (challenge instanceof final JSONObject obj) {
				String challengeString = obj.getString("Talent");
				if (obj.containsKey("Erschwernis")) {
					final String mod = obj.getUnsafe("Erschwernis").toString();
					if ('-' != mod.charAt(0)) {
						challengeString += '+';
					}
					challengeString += mod;
				}
				table.addCells(challengeString);
			} else {
				table.addCells(DSAUtil.getChallengeString((JSONArray) challenge));
			}
		});
		ifHas("Erschaffungskosten", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Erschaffungskosten", null), Units.NONE, false));
		});
		ifHas("Mondphase", actualGroup, o -> {
			final JSONArray moonPhase = ritual.getArrOrDefault("Mondphase", null);
			if (moonPhase == null) {
				table.addCells("—");
			} else {
				final StringBuilder moonPhaseString = new StringBuilder();
				for (int i = 0; i < moonPhase.size(); ++i) {
					moonPhaseString.append(" ");
					moonPhaseString.append(moonPhase.getString(i).charAt(0));
				}
				table.addCells(moonPhaseString.substring(1));
			}
		});
		ifHas("Aktivierungsprobe", actualGroup, o -> {
			table.addCells(DSAUtil.getChallengeString(ritual.getArrOrDefault("Aktivierungsprobe", null)));
		});
		ifHas("Aktivierungskosten", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Aktivierungskosten", null), Units.NONE, false));
		});
		ifHas("Aktivierungsdauer", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Aktivierungsdauer", null), Units.TIME, false));
		});

		if (isObjectRitual) {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Reichweite", null), Units.RANGE, false));
		} else {
			ifHas("Reichweite", actualGroup, o -> {
				table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Reichweite", null), Units.RANGE, false));
			});
		}

		ifHas("Zielobjekt", actualGroup, o -> {
			table.addCells(SheetUtil.getTargetObjectsString(ritual.getArrOrDefault("Zielobjekt", null)));
		});
		ifHas("Wirkungsdauer", actualGroup, o -> {
			table.addCells(DSAUtil.getModificationString(ritual.getObjOrDefault("Wirkungsdauer", null), Units.TIME, false));
		});
		ifHas("Komplexität", actualGroup, o -> {
			String complexity = ritual.getIntOrDefault("Komplexität", 1).toString();
			if (ritual.getBoolOrDefault("Zusatzzeichen", false)) {
				complexity = "+" + complexity;
			}
			table.addCells(complexity);
		});
		if (needsPrecondition) {
			table.addCells(SheetUtil.getRequirementString(ritual.getObjOrDefault("Voraussetzungen", null), ritual));
		}

		table.addCells(ritual.getStringOrDefault("Beschreibung:Kurz", " "));

		if (multipleTimes && !multipleCount) {
			if (item != null && baseItem != null) {
				final JSONObject rituals = item.getObjOrDefault("Rituale", baseItem.getObjOrDefault("Rituale", new JSONObject(null)));
				if (rituals.containsKey(ritualName)) {
					final JSONArray choice = rituals.getArr(ritualName);
					if (choice.size() > 0) {
						final StringBuilder choices = new StringBuilder();
						boolean first = true;
						for (int i = 0; i < choice.size(); ++i) {
							if (first) {
								first = false;
							} else {
								choices.append(", ");
							}
							choices.append(choice.getObj(i).getString("Auswahl"));
						}
						table.addRow(new TextCell(choices.toString()).setColSpan(table.getNumColumns()));
					} else {
						table.addRow(new TextCell(" ").setColSpan(table.getNumColumns()));
					}
				} else {
					table.addRow(new TextCell(" ").setColSpan(table.getNumColumns()));
				}
			} else {
				table.addRow(new TextCell(" ").setColSpan(table.getNumColumns()));
			}
		}
	}

	private List<String> getRequiredKnowledges(final String groupName) {
		final List<String> requiredKnowledges = new LinkedList<>();
		final JSONObject ritualGroups = ResourceManager.getResource("data/Rituale");
		final JSONObject group = ritualGroups.getObjOrDefault(groupName, new JSONObject(null));
		for (final String ritual : group.keySet()) {
			final JSONObject preconditions = group.getObj(ritual).getObjOrDefault("Voraussetzungen", null);
			final JSONObject prosCons = preconditions != null ? preconditions.getObjOrDefault("Vorteile/Nachteile/Sonderfertigkeiten", null) : null;
			final JSONObject requiredProsCons = prosCons != null ? prosCons.getObjOrDefault("Muss", null) : null;
			final JSONObject knowledges = requiredProsCons != null ? requiredProsCons.getObjOrDefault("Ritualkenntnis", null) : null;
			final JSONObject required = knowledges != null ? knowledges.getObj("Auswahl") : null;
			if (required != null && required.containsKey("Muss")) {
				requiredKnowledges.add(required.getString("Muss"));
			} else if (required != null && required.containsKey("Wahl")) {
				final JSONArray choice = required.getArr("Wahl");
				for (int i = 0; i < choice.size(); ++i) {
					requiredKnowledges.add(choice.getString(i));
				}
			} else {
				requiredKnowledges.clear();
				break;
			}
		}
		return requiredKnowledges;
	}

	private Table getRitualKnowledgeTable(final boolean portrait) {
		final int numCols = portrait ? 4 : 6;

		final Table table = new Table().setBorder(0, 0, 0, 0).setNumHeaderRows(0);

		for (int i = 0; i < numCols; i++) {
			table.addColumn(new Column((portrait ? 455 : 644) / numCols, FontManager.serif, 10.5f, HAlign.RIGHT).setBorder(0, 0, 0, 0));
			table.addColumn(new Column(29, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
		}

		final JSONObject ritualKnowledges = hero != null ? hero.getObj("Talente").getObjOrDefault("Ritualkenntnis", null) : null;

		int count = 0;
		boolean firstLine = true;

		if (ritualKnowledges != null) {
			for (final String ritualKnowledgeName : ritualKnowledges.keySet()) {
				if (ritualKnowledgeName.startsWith("Geister ")) {
					continue;
				}

				if (firstLine) {
					firstLine = false;
				} else {
					if (count % (portrait ? 4 : 6) == 0) {
						for (int i = 0; i < (portrait ? 4 : 6); ++i) {
							table.addCells(new Cell().setMinHeight(5), new Cell().setBorder(0, 0, 0, 0));
						}
					}
				}

				String value = " ";
				if (hero != null && fillAll) {
					final JSONObject ritualKnowledge = ritualKnowledges.getObjOrDefault(ritualKnowledgeName, null);
					value = Integer.toString(ritualKnowledge != null ? ritualKnowledge.getIntOrDefault("TaW", 0) : 0);
				}

				String enhancementCost = DSAUtil.getEnhancementGroupString(
						ResourceManager.getResource("data/Talentgruppen").getObj("Ritualkenntnis").getIntOrDefault("Steigerung", 7) + ResourceManager
								.getResource("data/Talente").getObj("Ritualkenntnis").getObj(ritualKnowledgeName).getIntOrDefault("Steigerung", 0));
				if (hero != null && fill) {
					enhancementCost = DSAUtil.getEnhancementGroupString(HeroUtil.getTalentComplexity(hero, ritualKnowledgeName));
				}

				table.addCells("RK " + ritualKnowledgeName + " (" + enhancementCost + "): ", value);

				++count;
			}
		}

		if (settingsPage.getBool(sections.get("Elfenlieder"), "").get()) {
			if (firstLine) {
				firstLine = false;
			} else {
				if (count % (portrait ? 4 : 6) == 0) {
					for (int i = 0; i < (portrait ? 4 : 6); ++i) {
						table.addCells(new Cell().setMinHeight(5), new Cell().setBorder(0, 0, 0, 0));
					}
				}
			}

			String singVal = " ";
			if (hero != null && fillAll) {
				final JSONObject singing = hero.getObj("Talente").getObj("Körperliche Talente").getObjOrDefault("Singen", null);
				singVal = Integer.toString(singing != null ? singing.getIntOrDefault("TaW", 0) : 0);
			}

			table.addCells("Singen ("
					+ DSAUtil.getChallengeString(HeroUtil.findTalent("Singen")._1.getArrOrDefault("Probe", new JSONArray(List.of("IN", "CH", "CH"), null)))
					+ "): ", singVal);

			++count;

			if (firstLine) {
				firstLine = false;
			} else {
				if (count % (portrait ? 4 : 6) == 0) {
					for (int i = 0; i < (portrait ? 4 : 6); ++i) {
						table.addCells(new Cell().setMinHeight(5), new Cell().setBorder(0, 0, 0, 0));
					}
				}
			}

			String musicVal = " ";
			if (hero != null && fillAll) {
				final JSONObject music = hero.getObj("Talente").getObj("Handwerkstalente").getObjOrDefault("Musizieren", null);
				musicVal = Integer.toString(music != null ? music.getIntOrDefault("TaW", 0) : 0);
			}

			table.addCells("Musizieren ("
					+ DSAUtil.getChallengeString(HeroUtil.findTalent("Musizieren")._1.getArrOrDefault("Probe", new JSONArray(List.of("IN", "CH", "FF"), null)))
					+ "): ", musicVal);

			++count;
		}

		while (count % (portrait ? 4 : 6) != 0) {
			table.addCells(new Cell(), new Cell().setBorder(0, 0, 0, 0));
			++count;
		}

		return table;
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);

		final JSONObject groups = new JSONObject(settings);
		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject group = new JSONObject(groups);
			group.put("Anzeigen", settingsPage.getBool(section, "").get());
			group.put(OWN_RITUALS_ONLY, settingsPage.getBool(section, OWN_RITUALS_ONLY).get());
			group.put(ADDITIONAL_ROWS, settingsPage.getInt(section, ADDITIONAL_ROWS).get());
			groups.put(name, group);
		}
		settings.put("Gruppen", groups);

		return settings;
	}

	private void ifHas(final String key, final JSONObject actualGroup, final Consumer<JSONObject> function) {
		DSAUtil.foreach(o -> true, (name, ritual) -> {
			if (ritual.containsKey(key) && !"Apport".equals(name)) {
				function.accept(ritual);
				return false;
			}
			return true;
		}, actualGroup);
	}

	@Override
	public void load() {
		super.load();

		ownRitualsOnly = settingsPage.addBooleanChoice(OWN_RITUALS_ONLY);
		ownRitualsOnly.setIndeterminate(true);
		ownRitualsOnly.selectedProperty().addListener((o, oldV, newV) -> {
			lock[0] = true;
			for (final TitledPane section : settingsPage.getSections()) {
				settingsPage.getBool(section, OWN_RITUALS_ONLY).setValue(newV);
			}
			lock[0] = false;
		});
	}

	@Override
	public void loadSettings(final JSONObject settings) {

		settingsPage.clear();
		sections.clear();
		load();
		super.loadSettings(settings);

		final JSONObject ritualGroupData = ResourceManager.getResource("data/Ritualgruppen");
		final JSONArray items = hero != null ? hero.getObj("Besitz").getArrOrDefault("Ausrüstung", null) : null;
		final JSONObject groups = settings.getObjOrDefault("Gruppen", new JSONObject(null));

		for (final String ritualGroupName : ritualGroupData.keySet()) {
			final JSONObject ritualGroup = ritualGroupData.getObj(ritualGroupName);
			if (items != null && ritualGroup.getString("Ritualobjekt") != null) {
				final String ritualObjectName = ritualGroup.getString("Ritualobjekt");
				boolean found = false;
				for (int i = 0; i < items.size(); ++i) {
					final JSONObject item = items.getObj(i);
					final JSONArray categories = item.getArr("Kategorien");
					if (categories.contains(ritualObjectName)) {
						final String name = item.containsKey("Name") ? ritualGroupName + ": " + item.getString("Name") : ritualGroupName;
						addSection(name, new Tuple<>(ritualGroupName, item), true);
						found = true;
					}
				}
				if (!found) {
					addUnspecificSection(ritualGroupName, groups);
				}
			} else {
				addUnspecificSection(ritualGroupName, groups);
			}
		}

		orderSections(groups.keySet());
	}

	@Override
	public String toString() {
		return "Ritualbrief";
	}
}
