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
import java.util.TreeMap;

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
import dsatool.util.ErrorLogger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class TalentsSheet extends Sheet {
	private static float fontSize = 8.4f;

	private static String getGroupTableHeader(final String name, final JSONObject talentGroup) {
		final StringBuilder title = new StringBuilder(name);
		title.append(" (");

		if (talentGroup.containsKey("Steigerung")) {
			final int enhancement = talentGroup.getInt("Steigerung");
			title.append(DSAUtil.getEnhancementGroupString(enhancement));
		} else {
			title.append("variabel");
		}

		title.append(')');

		if (talentGroup.containsKey("Probe")) {
			title.append(" (");
			title.append(DSAUtil.getChallengeString(talentGroup.getArr("Probe")));
			title.append(')');
		}

		return title.toString();
	}

	private final BooleanProperty groupBasis = new SimpleBooleanProperty(true);
	private final BooleanProperty markBasis = new SimpleBooleanProperty(false);
	private final BooleanProperty primaryTalents = new SimpleBooleanProperty(false);
	private final BooleanProperty showMetaTalents = new SimpleBooleanProperty(true);

	public TalentsSheet() {
		super(771);
	}

	private void addMetaTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(71, 71, FontManager.serif, 4, fontSize, HAlign.LEFT));
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
		final Map<String, JSONObject> metaTalents = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
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

			String tawString;
			if (hero != null && fillAll) {
				double taw = 0;
				int min = Integer.MAX_VALUE;
				int current;
				for (int i = 0; i < calculation.size(); ++i) {
					final JSONObject actualTalent = HeroUtil.findActualTalent(hero, calculation.getString(i))._1;
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
						final JSONObject actualTalent = HeroUtil.findActualTalent(hero, choice.getString(i))._1;
						final JSONObject currentTalent = HeroUtil.findTalent(choice.getString(i))._1;
						if (actualTalent != null) {
							choiceTaw = Math.max(choiceTaw, actualTalent.getIntOrDefault("TaW", currentTalent.getBoolOrDefault("Basis", false) ? 0 : -1));
						} else if (choiceTaw == -1 && currentTalent.getBoolOrDefault("Basis", false)) {
							choiceTaw = 0;
						}
					}
					if (choiceTaw == -1) {
						taw = Double.NEGATIVE_INFINITY;
					} else {
						min = Math.min(min, choiceTaw);
						taw += choiceTaw;
					}
				}
				tawString = taw != Double.NEGATIVE_INFINITY ? Integer.toString(Math.min((int) Math.round(taw / numTalents), 2 * min)) : " ";
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
				for (; i + 1 < calculation.size() && currentTalent.equals(calculation.getString(i + 1)); ++i, ++j) {

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

		bottom.bottom = table.render(document, 419, 164, bottom.bottom, 72, 10) - 5;
	}

	private void addSpecialTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(70, 70, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(15, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));

		final JSONObject talentGroupInfo = ResourceManager.getResource("data/Talentgruppen").getObj("Gaben");

		table.addRow(SheetUtil.createTitleCell(getGroupTableHeader("Gaben", talentGroupInfo), table.getNumColumns()));

		final Cell nameTitle = new TextCell("Talent", FontManager.serifBold, fontSize, fontSize);
		final Cell tawTitle = new TextCell("TaW", FontManager.serifBold, fontSize, fontSize).setPadding(0, 0, 0, 0);
		final Cell seTitle = new TextCell("SE", FontManager.serifBold, fontSize, fontSize);
		final Cell challengeTitle = new TextCell("Probe", FontManager.serifBold, fontSize, fontSize);

		table.addRow(nameTitle, tawTitle, seTitle, challengeTitle);

		final JSONObject talents = ResourceManager.getResource("data/Talente").getObj("Gaben");

		final Map<String, JSONObject> specialTalents = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
		for (final String talentName : talents.keySet()) {
			specialTalents.put(talentName, talents.getObj(talentName));
		}

		for (final String talentName : specialTalents.keySet()) {
			final JSONObject talent = specialTalents.get(talentName);
			JSONObject actualTalent = null;
			if (hero != null && hero.getObj("Talente").getObjOrDefault("Gaben", null) != null) {
				actualTalent = hero.getObj("Talente").getObj("Gaben").getObjOrDefault(talentName, null);
			}

			String name = talentName;
			if (hero != null && fill) {
				final int enhancement = HeroUtil.getTalentComplexity(hero, talentName);
				if (enhancement != talentGroupInfo.getIntOrDefault("Steigerung", 0)) {
					name += " (" + DSAUtil.getEnhancementGroupString(enhancement) + ")";
				}
			} else {
				if (talent.containsKey("Steigerung") && talent.getIntOrDefault("Steigerung", 0) != 0) {
					name += " (" + DSAUtil.getEnhancementGroupString(talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
							+ ")";
				}
			}

			final Cell nameCell = new TextCell(name);

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

			final JSONArray challenge = talent.getArrOrDefault("Probe", null);
			final Cell challengeCell = challenge != null ? new TextCell(challenge.getString(0)).addText("/").addText(challenge.getString(1)).addText("/")
					.addText(challenge.getString(2)).setEquallySpaced(true).setPadding(0, 1, 1, 0) : new TextCell("—");

			table.addRow(nameCell, taw, se, challengeCell);
		}

		bottom.bottom = table.render(document, 152, 12, bottom.bottom, 72, 10) - 5;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (separatePage.get()) {
			header = SheetUtil.createHeader("Talentbrief", true, true, false, hero, fill, fillAll);
		}

		startCreate(document);

		final JSONObject talents = ResourceManager.getResource("data/Talente");
		final JSONObject talentGroups = ResourceManager.getResource("data/Talentgruppen");

		for (final String talentGroupName : talentGroups.keySet()) {
			if (!"Gaben".equals(talentGroupName) && !"Ritualkenntnis".equals(talentGroupName) && !"Liturgiekenntnis".equals(talentGroupName)) {
				if ("Sprachen und Schriften".equals(talentGroupName)) {
					createGroupTable(document, "Sprachen", talents.getObj(talentGroupName), talentGroups.getObj(talentGroupName));
					createGroupTable(document, "Schriften", talents.getObj(talentGroupName), talentGroups.getObj(talentGroupName));
				} else {
					createGroupTable(document, talentGroupName, talents.getObj(talentGroupName), talentGroups.getObj(talentGroupName));
				}
			}
		}

		final float currentBottom = bottom.bottom;
		addSpecialTable(document);
		bottom.bottom = currentBottom;

		if (showMetaTalents.get()) {
			addMetaTable(document);
		}

		endCreate(document);
	}

	private void createGroupTable(final PDDocument document, final String groupName, final JSONObject talentGroup, final JSONObject talentGroupInfo)
			throws IOException {
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

		table.addRow(SheetUtil.createTitleCell(getGroupTableHeader(groupName, talentGroupInfo), table.getNumColumns()));

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
			if (groupBasis.get() && firstIsBasis != talentGroup.getObj(s2).getBoolOrDefault("Basis", false)) return firstIsBasis ? -1 : 1;
			return SheetUtil.comparator.compare(s1, s2);
		});
		for (final String talentName : talentGroup.keySet()) {
			talents.put(talentName, talentGroup.getObj(talentName));
		}

		for (final String talentName : talents.keySet()) {
			final JSONObject talent = talents.get(talentName);

			if (isLanguage && talent.getBoolOrDefault("Schrift", false)) {
				continue;
			}
			if (isWriting && !talent.getBoolOrDefault("Schrift", false)) {
				continue;
			}

			JSONObject actualTalent = null;
			if (hero != null) {
				actualTalent = hero.getObj("Talente").getObj(isLanguage || isWriting ? "Sprachen und Schriften" : groupName).getObjOrDefault(talentName, null);
			}

			TextCell nameCell;
			final PDFont font = markBasis.get() && talent.getBoolOrDefault("Basis", false) ? FontManager.serifItalic : FontManager.serif;
			if (talent.containsKey("Sprachfamilien")) {
				final String name = "Geheiligte Glyphen von Unau".equals(talentName) ? "Geh. Glyphen von Unau" : talentName.replace(" (Schrift)", "");

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
						if (primaryTalents.get()) {
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
								+ DSAUtil.getEnhancementGroupString(talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
								+ ")").setFont(FontManager.serif));
					}
				}
			} else {
				final String name = talentName;
				nameCell = new TextCell(name);
				nameCell.setFont(font);

				if (hero != null && fill) {
					int enhancement = HeroUtil.getTalentComplexity(hero, talentName);

					if (talent.getBoolOrDefault("Leittalent", false) || actualTalent != null && actualTalent.getBoolOrDefault("Leittalent", false)) {
						if (primaryTalents.get()) {
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
								+ DSAUtil.getEnhancementGroupString(talentGroupInfo.getIntOrDefault("Steigerung", 0) + talent.getIntOrDefault("Steigerung", 0))
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
					final int ATBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Attacke-Basis"),
							hero.getObj("Eigenschaften"), hero.getObj("Basiswerte").getObj("Attacke-Basis"), false);
					final int PABase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Parade-Basis"), hero.getObj("Eigenschaften"),
							hero.getObj("Basiswerte").getObj("Parade-Basis"), false);

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
				if (hero != null && fillAll) {
					final int FKBase = HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Fernkampf-Basis"),
							hero.getObj("Eigenschaften"), hero.getObj("Basiswerte").getObj("Fernkampf-Basis"), false);
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
				final Cell challengeCell = challenge != null ? new TextCell(challenge.getString(0)).addText("/").addText(challenge.getString(1)).addText("/")
						.addText(challenge.getString(2)).setEquallySpaced(true).setPadding(0, 1, 1, 0) : new TextCell("—");
				table.addCells(challengeCell);
			}

			if (needsBE) {
				table.addCells(new TextCell(DSAUtil.getBEString(talent)).setPadding(0, 1, 1, 0));
			}

			JSONObject skills = null;
			if (hero != null) {
				skills = hero.getObj("Sonderfertigkeiten");
			}

			if (isLanguage || isWriting) {
				table.addCells(talent.getInt("Komplexität").toString());
				String special;
				if (hero != null && fill && actualTalent != null) {
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
					boolean first = true;
					JSONArray specialisations = null;
					if (isFightGroup && skills.containsKey("Waffenspezialisierung")) {
						specialisations = skills.getArr("Waffenspezialisierung");
					} else if (skills.containsKey("Talentspezialisierung")) {
						specialisations = skills.getArr("Talentspezialisierung");
					}
					final StringBuilder specString = new StringBuilder();
					if (specialisations != null) {
						for (int i = 0; i < specialisations.size(); ++i) {
							final JSONObject specialisation = specialisations.getObj(i);
							if (talentName.equals(specialisation.getString("Auswahl"))) {
								if (first) {
									first = false;
								} else {
									specString.append(", ");
								}
								specString.append(specialisation.getStringOrDefault("Freitext", ""));
							}
						}
					}
					specialisationString = specString.toString();
				} else {
					specialisationString = " ";
				}
				table.addCells(specialisationString);
			}

			if (isFightGroup) {
				String weaponmaster;
				if (hero != null && fill) {
					boolean first = true;
					final JSONArray specialisations = skills.getArrOrDefault("Waffenmeister", null);
					final StringBuilder specString = new StringBuilder();
					if (specialisations != null) {
						for (int i = 0; i < specialisations.size(); ++i) {
							final JSONObject specialisation = specialisations.getObj(i);
							if (talentName.equals(specialisation.getString("Auswahl"))) {
								if (first) {
									first = false;
								} else {
									specString.append(", ");
								}
								specString.append(specialisation.getStringOrDefault("Freitext", ""));
							}
						}
					}
					weaponmaster = specString.toString();
				} else {
					weaponmaster = " ";
				}
				table.addCells(weaponmaster);
			} else if (!isWriting) {
				String requirementString;
				if (talent.containsKey("Voraussetzungen")) {
					final StringBuilder requirementsString = new StringBuilder();
					final JSONArray requirements = talent.getArr("Voraussetzungen");
					boolean first = true;
					for (int i = 0; i < requirements.size(); ++i) {
						if (first) {
							first = false;
						} else {
							requirementsString.append(", ");
						}
						final JSONObject requirement = requirements.getObj(i);
						if (requirement.containsKey("Ab")) {
							requirementsString.append(requirement.getInt("Ab"));
							requirementsString.append("+:\u00A0");
						}
						requirementsString.append(SheetUtil.getRequirementString(requirement, talent));
					}
					requirementString = requirementsString.toString();
				} else {
					requirementString = " ";
				}
				table.addCells(requirementString);
			}

			if (isLanguage && talent.containsKey("Schriften")) {
				table.addCells(String.join(", ", talent.getArr("Schriften").getStrings()));
			} else if (isWriting) {
				final JSONObject languages = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
				boolean first = true;
				final StringBuilder languagesString = new StringBuilder();
				for (final String languageName : languages.keySet()) {
					final JSONObject language = languages.getObj(languageName);
					if (language.containsKey("Schriften")) {
						final JSONArray writings = language.getArr("Schriften");
						for (int i = 0; i < writings.size(); ++i) {
							if (talentName.equals(writings.getString(i))) {
								if (first) {
									first = false;
								} else {
									languagesString.append(", ");
								}
								languagesString.append(languageName);
							}
						}
					}
				}
				table.addCells(languagesString.toString());
			} else if (talent.containsKey("Ableiten")) {
				boolean first = true;
				final StringBuilder derivationString = new StringBuilder();
				final JSONObject derive = talent.getObj("Ableiten");
				for (final String derivationTalentName : derive.keySet()) {
					final JSONObject derivationTalent = derive.getObj(derivationTalentName);
					if (derivationTalent.containsKey("Spezialisierung")) {
						final JSONObject specializations = derivationTalent.getObj("Spezialisierung");
						for (final String specialization : specializations.keySet()) {
							if (first) {
								first = false;
							} else {
								derivationString.append(", ");
							}
							derivationString.append(derivationTalentName);
							derivationString.append("\u00A0(");
							derivationString.append(specialization);
							derivationString.append(')');
							final int difficulty = specializations.getIntOrDefault(specialization, 0);
							if (difficulty != groupDerive) {
								derivationString.append(" +");
								derivationString.append(difficulty);
							}
						}
					} else {
						if (first) {
							first = false;
						} else {
							derivationString.append(", ");
						}
						derivationString.append(derivationTalentName);
						if (derivationTalent.containsKey("Erschwernis")) {
							derivationString.append(" +");
							derivationString.append(derivationTalent.getInt("Erschwernis"));
						}
					}
				}
				table.addCells(derivationString.toString());
			}

			table.completeRow();

			if (groupBasis.get() && basicTalent && !talent.getBoolOrDefault("Basis", false)) {
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

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	@Override
	public void load() {
		super.load();
		settings.addBooleanChoice("Basistalente gruppieren", groupBasis);
		settings.addBooleanChoice("Basistalente markieren", markBasis);
		settings.addBooleanChoice("Leittalente anzeigen", primaryTalents);
		settings.addBooleanChoice("Metatalente anzeigen", showMetaTalents);
	}

	@Override
	public void setHero(JSONObject hero) {
		super.setHero(hero);
		primaryTalents.set(hero != null && hero.getObj("Nachteile").containsKey("Elfische Weltsicht"));
	}

	@Override
	public String toString() {
		return "Talentbrief";
	}
}
