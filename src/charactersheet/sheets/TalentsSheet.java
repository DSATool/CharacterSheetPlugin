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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import boxtable.cell.Cell;
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
import dsatool.ui.ReactiveSpinner;
import dsatool.util.ErrorLogger;
import dsatool.util.StringUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class TalentsSheet extends Sheet {

	private static final String ADDITIONAL_TALENT_ROWS = "Zusätzliche Zeilen";
	private static final String BASIC_VALUES_IN_WEAPON_TALENTS = "Basiswerte berücksichtigen";
	private static final String GROUP_BASIC_TALENTS = "Basistalente gruppieren";
	private static final String MARK_BASIC_TALENTS = "Basistalente markieren";
	private static final String OWN_TALENTS_ONLY = "Nur erlernte Talente anzeigen";
	private static final String SHOW_PRIMARY = "Leittalente anzeigen";
	private static final String VALUES_FOR_ATTRIBUTES = "Eigenschaftswerte statt Eigenschaften anzeigen";

	private static final List<String> specialGroups = List.of("Gaben", "Ritualkenntnis", "Liturgiekenntnis");

	private static float fontSize = 8.4f;

	public TalentsSheet() {
		super(771);
	}

	private boolean addMetaTable(final PDDocument document, final TitledPane section, final float top, final float left) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(70, 70, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Meta-Talente", 4));

		final Cell nameTitle = new TextCell("Talent", FontManager.serifBold, fontSize, fontSize);
		final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, fontSize, fontSize).setPadding(0, 0, 0, 0);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, fontSize, fontSize);
		final Cell calculationTitle = new TextCell("Berechnung", FontManager.serifBold, fontSize, fontSize);

		table.addRow(nameTitle, tawTitle, challengeTitle, calculationTitle);

		final JSONObject talents = ResourceManager.getResource("data/Talente").getObj("Meta-Talente");
		final Map<String, JSONObject> metaTalents = new TreeMap<>(SheetUtil.comparator);
		for (final String talentName : talents.keySet()) {
			metaTalents.put(talentName, talents.getObj(talentName));
		}

		for (final String talentName : metaTalents.keySet()) {
			final JSONObject talent = metaTalents.get(talentName);

			final JSONArray calculation = talent.getArr("Berechnung");
			int numTalents = calculation.size();

			if (talent.containsKey("Berechnung:Auswahl")) {
				numTalents += 1;
			}

			double taw = 0;
			int min = Integer.MAX_VALUE;
			int current;
			for (int i = 0; i < calculation.size(); ++i) {
				final JSONObject actualTalent = (JSONObject) HeroUtil.findActualTalent(hero, calculation.getString(i))._1;
				final JSONObject currentTalent = HeroUtil.findTalent(calculation.getString(i))._1;
				if (actualTalent == null && !currentTalent.getBoolOrDefault("Basis", false)
						|| actualTalent != null && actualTalent.getIntOrDefault("TaW", currentTalent.getBoolOrDefault("Basis", false) ? 0 : -1) < 0) {
					taw = Double.NEGATIVE_INFINITY;
					break;
				}
				current = actualTalent != null ? actualTalent.getIntOrDefault("TaW", 0) : 0;
				min = Math.min(min, current);
				taw += current;
			}
			if (talent.containsKey("Berechnung:Auswahl")) {
				final JSONArray choice = talent.getArr("Berechnung:Auswahl");
				int choiceTaw = -1;
				for (int i = 0; i < choice.size(); ++i) {
					final JSONValue actualTalent = HeroUtil.findActualTalent(hero, choice.getString(i))._1;
					final JSONObject currentTalent = HeroUtil.findTalent(choice.getString(i))._1;
					if (currentTalent.containsKey("Auswahl") || currentTalent.containsKey("Freitext")) {
						int max = -1;
						for (int j = 0; j < actualTalent.size(); ++j) {
							max = Math.max(max, ((JSONArray) actualTalent).getObj(j).getIntOrDefault("TaW", -1));
						}
						choiceTaw = Math.max(choiceTaw, Math.max(max, currentTalent.getBoolOrDefault("Basis", false) ? 0 : -1));
					} else {
						if (actualTalent != null) {
							choiceTaw = Math.max(choiceTaw,
									((JSONObject) actualTalent).getIntOrDefault("TaW", currentTalent.getBoolOrDefault("Basis", false) ? 0 : -1));
						} else if (choiceTaw == -1 && currentTalent.getBoolOrDefault("Basis", false)) {
							choiceTaw = 0;
						}
					}
				}
				if (choiceTaw < 0) {
					taw = Double.NEGATIVE_INFINITY;
				} else {
					min = Math.min(min, choiceTaw);
					taw += choiceTaw;
				}
			}

			if (taw == Double.NEGATIVE_INFINITY && settingsPage.getBool(section, OWN_TALENTS_ONLY).get()) {
				continue;
			}

			String tawString;
			if (hero != null && fillAll) {
				tawString = taw != Double.NEGATIVE_INFINITY ? DSAUtil.oneDecimalPlace.format(Math.min(taw / numTalents, 2 * min)) : " ";
			} else {
				tawString = " ";
			}

			final JSONArray challenge = talent.getArrOrDefault("Probe", null);
			final Cell challengeCell = challenge != null ? new TextCell(challenge.getString(0)).addText("/").addText(challenge.getString(1)).addText("/")
					.addText(challenge.getString(2)).setEquallySpaced(true).setPadding(0, 1, 1, 0) : new TextCell("—");

			boolean first = true;
			final StringBuilder calculationString = new StringBuilder("(");
			for (int i = 0; i < calculation.size(); ++i) {
				if (first) {
					first = false;
				} else {
					calculationString.append('+');
				}
				final String currentTalent = calculation.getString(i);
				int j = 1;
				for (; i + 1 < calculation.size() && currentTalent.equals(calculation.getString(i + 1)); ++i) {
					++j;
				}
				if (j > 1) {
					calculationString.append(j);
					calculationString.append('x');
				}
				calculationString.append(currentTalent);
			}
			if (talent.containsKey("Berechnung:Auswahl")) {
				calculationString.append("+Talent");
			}
			calculationString.append(")/");
			calculationString.append(numTalents);
			calculationString.toString();

			table.addRow(talentName, tawString, challengeCell, calculationString.toString());
		}

		if (settingsPage.getBool(section, OWN_TALENTS_ONLY).get()) {
			for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS).get(); ++i) {
				table.addRow("");
			}
		}

		if (table.getNumRows() > 2) {
			bottom.bottom = table.render(document, 379, left, top, 72, 10) - 5;
			return true;
		}

		return false;
	}

	private boolean addSpecialTable(final PDDocument document, final TitledPane section, final String groupName, final float top, final float left)
			throws IOException {
		if (hero == null) return false;

		final JSONObject actualGroup = hero.getObj("Talente").getObjOrDefault(groupName, null);
		if (actualGroup == null) return false;

		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(105, 105, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(15, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));

		final JSONObject talentGroupInfo = ResourceManager.getResource("data/Talentgruppen").getObj(groupName);

		table.addRow(SheetUtil.createTitleCell(
				getGroupTableHeader(groupName, talentGroupInfo, !"Ritualkenntnis".equals(groupName), !"Liturgiekenntnis".equals(groupName)),
				table.getNumColumns()));

		final Cell nameTitle = new TextCell("Talent", FontManager.serifBold, fontSize, fontSize);
		final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, fontSize, fontSize).setPadding(0, 0, 0, 0);
		final Cell seTitle = new TextCell("SE", FontManager.serifBold, fontSize, fontSize);
		final Cell challengeTitle = new TextCell("Ritualkenntnis".equals(groupName) ? "Leiteig." : "Probe", FontManager.serifBold, fontSize, fontSize);
		table.addRow(nameTitle, tawTitle, seTitle, challengeTitle);

		final Map<String, JSONObject> talents = new TreeMap<>(SheetUtil.comparator);
		DSAUtil.foreach(o -> true, (name, talent) -> {
			talents.put(name, talent);
		}, ResourceManager.getResource("data/Talente").getObj(groupName));

		for (final String talentName : talents.keySet()) {
			final JSONObject talent = talents.get(talentName);

			final List<JSONObject> actualTalents = new LinkedList<>();
			if (!actualGroup.containsKey(talentName)) {
				continue;
			}
			if (talent.containsKey("Auswahl") || talent.containsKey("Freitext")) {
				final JSONArray choiceTalent = actualGroup.getArr(talentName);
				for (int i = 0; i < choiceTalent.size(); ++i) {
					actualTalents.add(choiceTalent.getObj(i));
				}
			} else {
				actualTalents.add(actualGroup.getObj(talentName));
			}

			for (final JSONObject actualTalent : actualTalents) {
				String name = talentName;
				if (talent.containsKey("Auswahl")) {
					name = name + ": " + actualTalent.getStringOrDefault("Auswahl", "");
				} else if (talent.containsKey("Freitext")) {
					name = name + ": " + actualTalent.getStringOrDefault("Freitext", "");
				}

				if (fill) {
					final int enhancement = HeroUtil.getTalentComplexity(hero, talentName);
					if (enhancement != talentGroupInfo.getIntOrDefault("Steigerung", 0) || "Ritualkenntnis".equals(groupName)) {
						name += " (" + DSAUtil.getEnhancementGroupString(enhancement) + ")";
					}
				} else {
					if (talent.containsKey("Steigerung") && talent.getIntOrDefault("Steigerung", 0) != 0) {
						name += " ("
								+ DSAUtil.getEnhancementGroupString(talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
								+ ")";
					}
				}

				final Cell nameCell = new TextCell(name);

				String taw;
				if (fillAll && actualTalent.getBoolOrDefault("aktiviert", true)) {
					taw = actualTalent.getIntOrDefault("TaW", 0).toString();
				} else if (hero != null && fillAll && talent.getBoolOrDefault("Basis", false)) {
					taw = "0";
				} else {
					taw = " ";
				}

				String se;
				int ses;
				if (fillAll && (ses = actualTalent.getIntOrDefault("SEs", 0)) != 0) {
					if (ses == 1) {
						se = "X";
					} else if (ses == 2) {
						se = "XX";
					} else {
						se = Integer.toString(ses);
					}
				} else {
					se = " ";
				}

				if ("Ritualkenntnis".equals(groupName)) {
					final String attribute = talent.getStringOrDefault("Leiteigenschaft", null);
					table.addRow(nameCell, taw, se, attribute != null ? attribute : "—");
				} else {
					final JSONArray challenge = talent.getArrOrDefault("Probe",
							"Liturgiekenntnis".equals(groupName) ? talentGroupInfo.getArrOrDefault("Probe", null) : null);
					final Cell challengeCell = challenge != null
							? new TextCell(challenge.getString(0)).addText("/").addText(challenge.getString(1)).addText("/")
									.addText(challenge.getString(2)).setEquallySpaced(true).setPadding(0, 1, 1, 0)
							: new TextCell("—");
					table.addRow(nameCell, taw, se, challengeCell);
				}
			}
		}

		for (int i = 0; i < settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS).get(); ++i) {
			table.addRow("");
		}

		if (table.getNumRows() > 2) {
			bottom.bottom = table.render(document, 187, left, top, 72, 10) - 5;
			return true;
		}

		return false;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Talentbrief", true, true, false, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		final JSONObject talents = ResourceManager.getResource("data/Talente");
		final JSONObject talentGroups = ResourceManager.getResource("data/Talentgruppen");

		float wideBottom = bottom.bottom;
		float minBottom = bottom.bottom;

		// 0: Next table left, 1: Small tables right, 2: Small tables left, 3: Small table middle, 4: Small table right
		int layoutMode = 0;

		final TitledPane[] sections = settingsPage.getSections().toArray(new TitledPane[0]);
		for (int i = 0; i < sections.length; ++i) {
			final TitledPane section = sections[i];
			if (!settingsPage.getBool(section, "").get()) {
				continue;
			}

			separatePage(document, settingsPage, section);

			final String name = settingsPage.getString(section, null).get();
			try {
				switch (name) {
					case "Meta-Talente" -> {
						final boolean addedTable = addMetaTable(document, section, layoutMode == 2 ? wideBottom : minBottom, layoutMode == 2 ? 204 : 12);
						if (addedTable) {
							layoutMode = layoutMode == 2 ? 0 : 1;
							if (layoutMode == 1) {
								minBottom = bottom.bottom;
								bottom.bottom = wideBottom;
							}
						}
					}

					case "Gaben", "Ritualkenntnis", "Liturgiekenntnis" -> {
						final float left = layoutMode == 3 ? 204 : layoutMode == 1 || layoutMode == 4 ? 396 : 12;
						final boolean addedTable = addSpecialTable(document, section, name, bottom.bottom, left);
						if (addedTable) {
							switch (layoutMode) {
								case 0 -> {
									layoutMode = 3;
									if (bottom.bottom < minBottom) {
										minBottom = bottom.bottom;
										bottom.bottom = wideBottom;
									} else {
										minBottom = bottom.bottom;
										bottom.bottom = height;
									}
									for (int j = i + 1; j < i + 4 && j < sections.length; ++j) {
										if ("Meta-Talente".equals(settingsPage.getString(sections[j], null).get())) {
											layoutMode = 2;
											bottom.bottom = minBottom;
										}
									}
								}
								case 3 -> {
									layoutMode = 4;
									if (bottom.bottom < wideBottom) {
										minBottom = Math.min(bottom.bottom, minBottom);
										bottom.bottom = wideBottom;
									} else {
										bottom.bottom = 72;
									}
								}
								default -> minBottom = Math.min(bottom.bottom, minBottom);
							}
						}
					}

					default -> {
						bottom.bottom = minBottom;

						final boolean addedTable;
						if (List.of("Sprachen", "Schriften").contains(name)) {
							addedTable = createGroupTable(document, section, name, talents.getObj("Sprachen und Schriften"),
									talentGroups.getObj("Sprachen und Schriften").getObj(name));
						} else {
							addedTable = createGroupTable(document, section, name, talents.getObj(name), talentGroups.getObj(name));
						}

						if (addedTable) {
							wideBottom = bottom.bottom;
							minBottom = wideBottom;
							layoutMode = 0;
						}
					}
				}
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		bottom.bottom = minBottom;

		endCreate(document);
	}

	private boolean createGroupTable(final PDDocument document, final TitledPane section, final String groupName, final JSONObject talentGroup,
			final JSONObject talentGroupInfo) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(110, 110, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(15, FontManager.serif, fontSize, HAlign.CENTER));

		boolean isCloseCombat = false;
		boolean isFightGroup = false;
		boolean needsBE = false;
		boolean isLanguage = false;
		boolean isWriting = false;
		final boolean basicValuesInWeaponTalent = groupName.endsWith("kampftalente") && settingsPage
				.getBool(section, BASIC_VALUES_IN_WEAPON_TALENTS + (groupName.startsWith("Nah") ? " bei AT/PA-Verteilung" : " bei FK-Wert")).get();

		final Map<String, Integer> languageFamilies = new HashMap<>();

		switch (groupName) {
			case "Nahkampftalente":
				isCloseCombat = true;
			case "Fernkampftalente":
				isFightGroup = true;
			case "Körperliche Talente":
				needsBE = true;
				table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(111, 111, FontManager.serif, 4, fontSize, HAlign.LEFT));
				table.addColumn(new Column(111, 111, FontManager.serif, 4, fontSize, HAlign.LEFT));
				table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));
				break;
			case "Sprachen":
				isLanguage = true;
				table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(111, 111, FontManager.serif, 4, fontSize, HAlign.LEFT));
				table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));
				break;
			case "Schriften":
				isWriting = true;
				table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));
				break;
			default:
				table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));
				table.addColumn(new Column(111, 111, FontManager.serif, 4, fontSize, HAlign.LEFT));
				table.addColumn(new Column(111, 111, FontManager.serif, 4, fontSize, HAlign.LEFT));
				table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));
				break;
		}

		table.addRow(SheetUtil.createTitleCell(getGroupTableHeader(groupName, talentGroupInfo, true, true), table.getNumColumns()));

		String derivation;
		final int groupDerive = talentGroupInfo.getIntOrDefault("Ableiten", 10);
		if (isCloseCombat) {
			derivation = "Ableiten (+" + (int) Math.floor(groupDerive / 2.0) + "/+" + (int) Math.ceil(groupDerive / 2.0) + ")";
		} else {
			derivation = "Ableiten (+" + groupDerive + ")";
		}

		final Cell nameTitle = new TextCell("Talent", FontManager.serifBold, fontSize, fontSize);
		final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, fontSize, fontSize).setPadding(0, 0.5f, 0.5f, 0);
		final Cell seTitle = new TextCell("SE", FontManager.serifBold, fontSize, fontSize);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, fontSize, fontSize);
		final Cell kplTitle = new TextCell("Kpl.", FontManager.serifBold, fontSize, fontSize);
		final Cell specialisationTitle = new TextCell("Spezialisierung (+2)", FontManager.serifBold, fontSize, fontSize);
		final Cell preconditionTitle = new TextCell("Voraussetzungen", FontManager.serifBold, fontSize, fontSize);
		final Cell languageTitle = new TextCell("S", FontManager.serifBold, fontSize, fontSize);

		if (needsBE) {
			Cell fourth;
			if (isCloseCombat) {
				fourth = new TextCell("AT", FontManager.serifBold, fontSize, fontSize).addText("/").addText("PA").setEquallySpaced(true);
			} else if (isFightGroup) {
				fourth = new TextCell("FK", FontManager.serifBold, fontSize, fontSize);
			} else {
				fourth = challengeTitle;
			}

			final Cell specialisations = isCloseCombat ? new TextCell("Spezialisierung (+1/+1)", FontManager.serifBold, fontSize, fontSize)
					: specialisationTitle;
			final Cell preconditions = isFightGroup ? new TextCell("Waffenmeister", FontManager.serifBold, fontSize, fontSize) : preconditionTitle;

			table.addRow(nameTitle, tawTitle, seTitle, fourth, new TextCell("BE", FontManager.serifBold, fontSize, fontSize), specialisations, preconditions,
					new TextCell(derivation, FontManager.serifBold, fontSize, fontSize));
		} else if (isLanguage) {
			table.addRow(nameTitle, tawTitle, seTitle, kplTitle, languageTitle, preconditionTitle,
					new TextCell("Schriften", FontManager.serifBold, fontSize, fontSize));
		} else if (isWriting) {
			table.addRow(nameTitle, tawTitle, seTitle, kplTitle, languageTitle, new TextCell("Sprachen", FontManager.serifBold, fontSize, fontSize));
		} else {
			table.addRow(nameTitle, tawTitle, seTitle, challengeTitle, specialisationTitle, preconditionTitle,
					new TextCell(derivation, FontManager.serifBold, fontSize, fontSize));
		}

		boolean basicTalent = false;

		final Map<String, JSONObject> talents = new TreeMap<>((s1, s2) -> {
			final boolean firstIsBasis = talentGroup.getObj(s1).getBoolOrDefault("Basis", false);
			if (settingsPage.getBool(GROUP_BASIC_TALENTS).get() && firstIsBasis != talentGroup.getObj(s2).getBoolOrDefault("Basis", false))
				return firstIsBasis ? -1 : 1;
			return SheetUtil.comparator.compare(s1, s2);
		});
		for (final String talentName : talentGroup.keySet()) {
			talents.put(talentName, talentGroup.getObj(talentName));
		}

		int leftOut = 0;
		for (final String talentName : talents.keySet()) {
			final JSONObject talent = talents.get(talentName);

			if (isLanguage && talent.getBoolOrDefault("Schrift", false) || isWriting && !talent.getBoolOrDefault("Schrift", false)) {
				continue;
			}

			final List<JSONObject> actualTalents = new LinkedList<>();
			final JSONObject actualGroup = hero != null ? hero.getObj("Talente").getObj(isLanguage || isWriting ? "Sprachen und Schriften" : groupName) : null;
			if (talent.containsKey("Auswahl") || talent.containsKey("Freitext")) {
				if (hero != null) {
					final JSONArray choiceTalent = actualGroup.getArrOrDefault(talentName, null);
					if (choiceTalent != null) {
						for (int i = 0; i < choiceTalent.size(); ++i) {
							actualTalents.add(choiceTalent.getObj(i));
						}
					}
				}
			} else {
				actualTalents.add(actualGroup != null ? actualGroup.getObjOrDefault(talentName, null) : null);
			}

			for (final JSONObject actualTalent : actualTalents) {
				if (actualTalent == null && settingsPage.getBool(section, OWN_TALENTS_ONLY).get()) {
					++leftOut;
					break;
				}

				TextCell nameCell;
				final PDFont font = settingsPage.getBool(MARK_BASIC_TALENTS).get() && talent.getBoolOrDefault("Basis", false) ? FontManager.serifItalic
						: FontManager.serif;
				if (talent.containsKey("Sprachfamilien")) {
					String name = "Geheiligte Glyphen von Unau".equals(talentName) ? "Geh. Glyphen von Unau" : talentName.replace(" (Schrift)", "");
					if (talent.containsKey("Auswahl")) {
						name = name + ": " + actualTalent.getStringOrDefault("Auswahl", "");
					} else if (talent.containsKey("Freitext")) {
						name = name + ": " + actualTalent.getStringOrDefault("Freitext", "");
					}

					nameCell = new TextCell(name);
					nameCell.setFont(font);

					final JSONArray families = talent.getArr("Sprachfamilien");
					final StringBuilder familyString = new StringBuilder("");
					for (int i = 0; i < families.size(); ++i) {
						final String family = families.getString(i);
						if (!languageFamilies.containsKey(family)) {
							languageFamilies.put(family, languageFamilies.size() + 1);
						}
						if (i != 0) {
							familyString.append(", ");
						}
						familyString.append(languageFamilies.get(family));
					}

					final Text family = new Text(familyString.toString()).setFont(FontManager.serif).setFontSize(4).setVerticalOffset(3);
					nameCell.addText(family);

					if (hero != null && fill) {
						int enhancement = HeroUtil.getTalentComplexity(hero, talentName);

						if (talent.getBoolOrDefault("Leittalent", false) || actualTalent != null && actualTalent.getBoolOrDefault("Leittalent", false)) {
							if (settingsPage.getBool(SHOW_PRIMARY).get()) {
								nameCell.addText(new Text("(L)").setFont(FontManager.serif));
							}
						} else if (hero.getObj("Nachteile").containsKey("Elfische Weltsicht")) {
							--enhancement;
						}
						if (enhancement != talentGroupInfo.getIntOrDefault("Steigerung", 0)) {
							nameCell.addText(new Text("(" + DSAUtil.getEnhancementGroupString(enhancement) + ")").setFont(FontManager.serif));
						}
					} else {
						if (talent.containsKey("Steigerung") && talent.getIntOrDefault("Steigerung", 0) != 0) {
							nameCell.addText(new Text("("
									+ DSAUtil.getEnhancementGroupString(
											talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
									+ ")").setFont(FontManager.serif));
						}
					}
				} else {
					final String name = talentName.replace(" (Schrift)", "");
					nameCell = new TextCell(name);
					nameCell.setFont(font);

					if (hero != null && fill) {
						int enhancement = HeroUtil.getTalentComplexity(hero, talentName);

						if (talent.getBoolOrDefault("Leittalent", false) || actualTalent != null && actualTalent.getBoolOrDefault("Leittalent", false)) {
							if (settingsPage.getBool(SHOW_PRIMARY).get()) {
								nameCell.addText(new Text("(L)").setFont(FontManager.serif));
							}
						} else if (hero.getObj("Nachteile").containsKey("Elfische Weltsicht")) {
							--enhancement;
						}

						if (enhancement != talentGroupInfo.getIntOrDefault("Steigerung", 0)) {
							nameCell.addText(new Text("(" + DSAUtil.getEnhancementGroupString(enhancement) + ")").setFont(FontManager.serif));
						}
					} else {
						if (talent.containsKey("Steigerung") && talent.getIntOrDefault("Steigerung", 0) != 0) {
							nameCell.addText(new Text("("
									+ DSAUtil.getEnhancementGroupString(
											talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
									+ ")").setFont(FontManager.serif));
						}
					}
				}

				String taw;
				if (fillAll && actualTalent != null && actualTalent.getBoolOrDefault("aktiviert", true)) {
					taw = actualTalent.getIntOrDefault("TaW", 0).toString();
				} else if (hero != null && fillAll && talent.getBoolOrDefault("Basis", false)) {
					taw = "0";
				} else {
					taw = " ";
				}

				String se;
				int ses;
				if (fillAll && actualTalent != null && (ses = actualTalent.getIntOrDefault("SEs", 0)) != 0) {
					if (ses == 1) {
						se = "X";
					} else if (ses == 2) {
						se = "XX";
					} else {
						se = Integer.toString(ses);
					}
				} else {
					se = " ";
				}

				table.addCells(nameCell, taw, se);

				if (isCloseCombat) {
					String at = " ", pa = " ";
					final boolean ATOnly = talent.getBoolOrDefault("NurAT", false);
					if (ATOnly) {
						pa = "—";
					}
					if (hero != null && fillAll) {
						final int ATBase = basicValuesInWeaponTalent
								? HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Attacke-Basis"), hero,
										hero.getObj("Basiswerte").getObj("Attacke-Basis"), false)
								: 0;
						final int PABase = basicValuesInWeaponTalent
								? HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero,
										hero.getObj("Basiswerte").getObj("Parade-Basis"), false)
								: 0;

						if (actualTalent != null && actualTalent.getBoolOrDefault("aktiviert", true)) {
							at = Integer.toString(ATBase + actualTalent.getIntOrDefault("AT", 0));
							if (!ATOnly) {
								pa = Integer.toString(PABase + actualTalent.getIntOrDefault("PA", 0));
							}
						} else if (talent.getBoolOrDefault("Basis", false)) {
							at = Integer.toString(ATBase);
							if (!ATOnly) {
								pa = Integer.toString(PABase);
							}
						}
					}
					table.addCells(new TextCell(at).addText("/").addText(pa).setEquallySpaced(true));
				} else if (isFightGroup) {
					String fk;
					if (hero != null && fillAll && basicValuesInWeaponTalent) {
						final int FKBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Fernkampf-Basis"), hero,
								hero.getObj("Basiswerte").getObj("Fernkampf-Basis"), false);
						if (actualTalent != null && actualTalent.getBoolOrDefault("aktiviert", true)) {
							fk = Integer.toString(FKBase + actualTalent.getIntOrDefault("AT", 0));
						} else if (talent.getBoolOrDefault("Basis", false)) {
							fk = Integer.toString(FKBase);
						} else {
							fk = " ";
						}
					} else {
						fk = " ";
					}
					table.addCells(fk);
				} else if (!isLanguage && !isWriting) {
					final JSONArray challenge = talent.getArrOrDefault("Probe", null);
					Cell challengeCell;
					if (challenge == null) {
						challengeCell = new TextCell("—");
					} else {
						final String[] attributeStrings = new String[3];
						if (settingsPage.getBool(VALUES_FOR_ATTRIBUTES).get()) {
							if (hero != null && fill) {
								final JSONObject attributes = hero.getObj("Eigenschaften");
								attributeStrings[0] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(0)), false));
								attributeStrings[1] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(1)), false));
								attributeStrings[2] = Integer.toString(HeroUtil.getCurrentValue(attributes.getObj(challenge.getString(2)), false));
							} else {
								attributeStrings[0] = "";
								attributeStrings[1] = "";
								attributeStrings[2] = "";
							}
						} else {
							attributeStrings[0] = challenge.getString(0);
							attributeStrings[1] = challenge.getString(1);
							attributeStrings[2] = challenge.getString(2);
						}
						challengeCell = new TextCell(attributeStrings[0]).addText("/").addText(attributeStrings[1]).addText("/").addText(attributeStrings[2])
								.setEquallySpaced(true).setPadding(0, 1, 1, 0);
					}
					table.addCells(challengeCell);
				}

				if (needsBE) {
					table.addCells(new TextCell(DSAUtil.getBEString(talent)).setPadding(0, 1, 1, 0));
				}

				final JSONObject skills = hero != null ? hero.getObj("Sonderfertigkeiten") : null;

				if (isLanguage || isWriting) {
					table.addCells(talent.getInt("Komplexität").toString());
					String special;
					if (fill && actualTalent != null) {
						if (actualTalent.getBoolOrDefault("Muttersprache", false)) {
							special = "MS";
						} else if (actualTalent.getBoolOrDefault("Zweitsprache", false)) {
							special = "ZS";
						} else if (actualTalent.getBoolOrDefault("Lehrsprache", false)) {
							special = "LS";
						} else {
							special = " ";
						}
					} else {
						special = " ";
					}
					table.addCells(special);
				} else {
					String specialisationString;
					if (hero != null && fill) {
						JSONArray specialisations = null;
						if (isFightGroup && skills.containsKey("Waffenspezialisierung")) {
							specialisations = skills.getArr("Waffenspezialisierung");
						} else if (skills.containsKey("Talentspezialisierung")) {
							specialisations = skills.getArr("Talentspezialisierung");
						}
						if (specialisations != null) {
							specialisationString = StringUtil.mkStringObj(specialisations, ", ",
									specialisation -> talentName.equals(specialisation.getString("Auswahl")) ? specialisation.getStringOrDefault("Freitext", "")
											: "");
						} else {
							specialisationString = " ";
						}
					} else {
						specialisationString = " ";
					}
					table.addCells(specialisationString);
				}

				if (isFightGroup) {
					String weaponmaster;
					if (hero != null && fill) {
						final JSONArray specialisations = skills.getArrOrDefault("Waffenmeister", null);
						if (specialisations != null) {
							weaponmaster = StringUtil.mkStringObj(specialisations, ", ",
									specialisation -> talentName.equals(specialisation.getString("Auswahl")) ? specialisation.getStringOrDefault("Freitext", "")
											: "");
						} else {
							weaponmaster = " ";
						}
					} else {
						weaponmaster = " ";
					}
					table.addCells(weaponmaster);
				} else if (!isWriting) {
					String requirementString;
					if (talent.containsKey("Voraussetzungen")) {
						final JSONArray requirements = talent.getArr("Voraussetzungen");
						requirementString = StringUtil.mkStringObj(requirements, ", ",
								requirement -> (requirement.containsKey("Ab") ? requirement.getInt("Ab") + "+:\u00A0" : "")
										+ SheetUtil.getRequirementString(requirement, talent));
					} else {
						requirementString = " ";
					}
					table.addCells(requirementString);
				}

				if (isLanguage && talent.containsKey("Schriften")) {
					table.addCells(talent.getArr("Schriften").getStrings().stream().map(s -> s.replace(" (Schrift)", "")).collect(Collectors.joining(", ")));
				} else if (isWriting) {
					final JSONObject languages = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
					final String languagesString = StringUtil.mkString(languages.keySet(), ", ", languageName -> {
						final JSONObject language = languages.getObj(languageName);
						return language.containsKey("Schriften")
								? StringUtil.mkStringString(language.getArr("Schriften"), ", ", writing -> talentName.equals(writing) ? languageName : "") : "";
					});
					table.addCells(languagesString);
				} else if (talent.containsKey("Ableiten")) {
					final JSONObject derive = talent.getObj("Ableiten");
					final String derivationString = StringUtil.mkString(derive.keySet(), ", ", derivationTalentName -> {
						final JSONObject derivationTalent = derive.getObj(derivationTalentName);
						if (derivationTalent.containsKey("Spezialisierung")) {
							final JSONObject specializations = derivationTalent.getObj("Spezialisierung");
							return StringUtil.mkString(specializations.keySet(), ", ", specialization -> {
								final int difficulty = specializations.getIntOrDefault(specialization, 0);
								return derivationTalentName + "\u00A0(" + specialization + ')' + (difficulty != groupDerive ? " +" + difficulty : "");
							});
						} else
							return derivationTalentName + (derivationTalent.containsKey("Erschwernis") ? " +" + derivationTalent.getInt("Erschwernis") : "");
					});
					table.addCells(derivationString);
				}

				table.completeRow();
			}

			if (settingsPage.getBool(GROUP_BASIC_TALENTS).get() && basicTalent && !talent.getBoolOrDefault("Basis", false)) {
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
		}

		if (settingsPage.getBool(section, OWN_TALENTS_ONLY).get()) {
			for (int i = 0; i < Math.min(leftOut, settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS).get()); ++i) {
				table.addRow("");
			}
		}

		if (table.getNumRows() > 2) {
			bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
			return true;
		}

		return false;
	}

	private void createSection(final String name, final boolean[] lock, final ReactiveSpinner<Integer> additionalRows, final CheckBox ownTalentsOnly) {
		final TitledPane section = settingsPage.addSection(name, true);
		sections.put(name, section);
		addOwnPageOption(settingsPage, section);

		BooleanProperty own = null;
		if (!specialGroups.contains(name)) {
			own = settingsPage.addBooleanChoice(OWN_TALENTS_ONLY).selectedProperty();
		}

		final ReactiveSpinner<Integer> additionalControl = settingsPage.addIntegerChoice(ADDITIONAL_TALENT_ROWS, 0, 30);
		additionalControl.setDisable(true);
		final IntegerProperty additional = settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS);
		additional.addListener((o, oldV, newV) -> {
			if (!lock[0]) {
				lock[0] = true;
				additionalRows.getValueFactory().setValue(0);
				lock[0] = false;
			}
		});

		if (!specialGroups.contains(name)) {
			own.addListener((o, oldV, newV) -> {
				if (!lock[0]) {
					ownTalentsOnly.setIndeterminate(true);
				}
				additionalControl.setDisable(!newV);
			});
		}

		if (name.endsWith("kampftalente")) {
			settingsPage.addBooleanChoice(BASIC_VALUES_IN_WEAPON_TALENTS + (name.startsWith("Nah") ? " bei AT/PA-Verteilung" : " bei FK-Wert"));
		}
	}

	private String getGroupTableHeader(final String name, final JSONObject talentGroup, final boolean addEnhancement, final boolean addChallenge) {
		final StringBuilder title = new StringBuilder(name);

		if (addEnhancement && talentGroup.containsKey("Steigerung")) {
			title.append(" (");
			title.append(DSAUtil.getEnhancementGroupString(talentGroup.getInt("Steigerung")));
			title.append(')');
		}

		if (addChallenge && talentGroup.containsKey("Probe")) {
			title.append(" (");
			final JSONArray challenge = talentGroup.getArr("Probe");
			if (settingsPage.getBool(VALUES_FOR_ATTRIBUTES).get()) {
				title.append(HeroUtil.getChallengeValuesString(hero, challenge, fill));
			} else {
				title.append(DSAUtil.getChallengeString(challenge));
			}
			title.append(')');
		}

		return title.toString();
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);

		settings.put(GROUP_BASIC_TALENTS, settingsPage.getBool(GROUP_BASIC_TALENTS).get());
		settings.put(MARK_BASIC_TALENTS, settingsPage.getBool(MARK_BASIC_TALENTS).get());
		settings.put(SHOW_PRIMARY, settingsPage.getBool(SHOW_PRIMARY).get());
		settings.put(VALUES_FOR_ATTRIBUTES, settingsPage.getBool(VALUES_FOR_ATTRIBUTES).get());

		final JSONObject groups = new JSONObject(settings);
		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject group = new JSONObject(groups);
			group.put("Anzeigen", settingsPage.getBool(section, "").get());
			group.put(AS_SEPARATE_SHEET, settingsPage.getBool(section, AS_SEPARATE_SHEET).get());
			if (!specialGroups.contains(name)) {
				group.put(OWN_TALENTS_ONLY, settingsPage.getBool(section, OWN_TALENTS_ONLY).get());
			}
			group.put(ADDITIONAL_TALENT_ROWS, settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS).get());
			if (name.endsWith("kampftalente")) {
				group.put(BASIC_VALUES_IN_WEAPON_TALENTS, settingsPage
						.getBool(section, BASIC_VALUES_IN_WEAPON_TALENTS + (name.startsWith("Nah") ? " bei AT/PA-Verteilung" : " bei FK-Wert")).get());
			}
			groups.put(name, group);
		}
		settings.put("Gruppen", groups);

		return settings;
	}

	@Override
	public void load() {
		super.load();

		final boolean[] lock = { false };

		settingsPage.addBooleanChoice(GROUP_BASIC_TALENTS);
		settingsPage.addBooleanChoice(MARK_BASIC_TALENTS);
		settingsPage.addBooleanChoice(SHOW_PRIMARY);
		settingsPage.addBooleanChoice(VALUES_FOR_ATTRIBUTES);

		final CheckBox ownTalentsOnly = settingsPage.addBooleanChoice(OWN_TALENTS_ONLY);

		final ReactiveSpinner<Integer> additionalRows = settingsPage.addIntegerChoice(ADDITIONAL_TALENT_ROWS, 0, 30);
		additionalRows.valueProperty().addListener((o, oldV, newV) -> {
			if (!lock[0]) {
				lock[0] = true;
				for (final TitledPane section : settingsPage.getSections()) {
					final String name = settingsPage.getString(section, null).get();
					if (!specialGroups.contains(name) && !"Meta-Talente".equals(name)) {
						settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS).setValue(newV);
					}
				}
				lock[0] = false;
			}
		});
		additionalRows.setDisable(true);

		ownTalentsOnly.setIndeterminate(true);
		ownTalentsOnly.selectedProperty().addListener((o, oldV, newV) -> {
			lock[0] = true;
			for (final TitledPane section : settingsPage.getSections()) {
				if (!specialGroups.contains(settingsPage.getString(section, null).get())) {
					settingsPage.getBool(section, OWN_TALENTS_ONLY).setValue(newV);
				}
				additionalRows.setDisable(!newV);
			}
			lock[0] = false;
		});

		final JSONObject talentGroupNames = ResourceManager.getResource("data/Talente");
		for (final String name : talentGroupNames.keySet()) {
			if ("Sprachen und Schriften".equals(name)) {
				createSection("Sprachen", lock, additionalRows, ownTalentsOnly);
				createSection("Schriften", lock, additionalRows, ownTalentsOnly);
			} else {
				createSection(name, lock, additionalRows, ownTalentsOnly);
			}
		}
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		settingsPage.getBool(GROUP_BASIC_TALENTS).set(settings.getBoolOrDefault(GROUP_BASIC_TALENTS, true));
		settingsPage.getBool(MARK_BASIC_TALENTS).set(settings.getBoolOrDefault(MARK_BASIC_TALENTS, false));
		settingsPage.getBool(SHOW_PRIMARY)
				.set(settings.getBoolOrDefault(SHOW_PRIMARY, hero != null && hero.getObj("Nachteile").containsKey("Elfische Weltsicht")));
		settingsPage.getBool(VALUES_FOR_ATTRIBUTES).set(settings.getBoolOrDefault(VALUES_FOR_ATTRIBUTES, false));

		orderSections(ResourceManager.getResource("data/Talente").keySet());
		final JSONObject groups = settings.getObjOrDefault("Gruppen", new JSONObject(null));
		orderSections(groups.keySet());

		for (final TitledPane section : settingsPage.getSections()) {
			final String name = settingsPage.getString(section, null).get();
			final JSONObject group = groups.getObjOrDefault(name, new JSONObject(null));
			settingsPage.getBool(section, "").set(group.getBoolOrDefault("Anzeigen", true));
			settingsPage.getBool(section, AS_SEPARATE_SHEET).set(group.getBoolOrDefault(AS_SEPARATE_SHEET, false));
			if (!specialGroups.contains(name)) {
				settingsPage.getBool(section, OWN_TALENTS_ONLY).set(group.getBoolOrDefault(OWN_TALENTS_ONLY, false));
			}
			settingsPage.getInt(section, ADDITIONAL_TALENT_ROWS)
					.set(group.getIntOrDefault(ADDITIONAL_TALENT_ROWS, specialGroups.contains(name) || "Meta-Talente".equals(name) ? 0 : 3));
			if (name.endsWith("kampftalente")) {
				settingsPage.getBool(section, BASIC_VALUES_IN_WEAPON_TALENTS + (name.startsWith("Nah") ? " bei AT/PA-Verteilung" : " bei FK-Wert"))
						.set(group.getBoolOrDefault(BASIC_VALUES_IN_WEAPON_TALENTS, true));
			}
		}
	}

	@Override
	protected void orderSections(final Collection<String> order) {
		int index = 0;
		for (final String key : order) {
			if ("Sprachen und Schriften".equals(key)) {
				if (sections.containsKey("Sprachen")) {
					settingsPage.moveSection(sections.get("Sprachen"), index);
					++index;
				}
				if (sections.containsKey("Schriften")) {
					settingsPage.moveSection(sections.get("Schriften"), index);
					++index;
				}
			} else if (sections.containsKey(key)) {
				settingsPage.moveSection(sections.get(key), index);
				++index;
			}
		}
	}

	@Override
	public String toString() {
		return "Talentbrief";
	}
}
