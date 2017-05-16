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
package charactersheet.util;

import java.awt.Color;
import java.io.IOException;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import boxtable.cell.Cell;
import boxtable.cell.TextCell;
import boxtable.common.CellFiller;
import boxtable.common.CellFiller.RowStripe;
import boxtable.common.HAlign;
import boxtable.event.TableEvent;
import boxtable.table.Column;
import boxtable.table.Table;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SheetUtil {
	public static class BottomObserver {
		public float bottom;
		public final float top;

		public BottomObserver(final float start) {
			bottom = start;
			top = start;
		}
	}

	public static final Collator comparator = Collator.getInstance(Locale.GERMANY);

	public static DecimalFormat threeDecimalPlaces = new DecimalFormat("#.###");

	public static DecimalFormat threeDecimalPlacesSigned = new DecimalFormat("+#.###");

	public static PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

	public static void addTitle(final Table table, final String text) {
		table.addRow(SheetUtil.createTitleCell(text, table.getNumColumns()));
	}

	private static void appendModifiedRequirement(final StringBuilder result, final JSONObject skill, final String requirement) {
		if (skill.containsKey("Auswahl") && "Auswahl".equals(requirement)) {
			result.append(skill.getString("Auswahl").replace(' ', '\u00A0'));
		} else if (skill.containsKey("Freitext") && "Freitext".equals(requirement)) {
			result.append(skill.getString("Freitext").replace(' ', '\u00A0'));
		} else {
			result.append(requirement.replace(' ', '\u00A0'));
		}
	}

	private static void appendRequiredSkill(final StringBuilder result, final String skillName, final JSONObject requiredSkill, final JSONObject base) {
		appendModifiedRequirement(result, base, skillName);
		if (requiredSkill.containsKey("Auswahl")) {
			result.append("\u00A0(");
			final JSONObject skillChoice = requiredSkill.getObj("Auswahl");
			if (skillChoice.containsKey("Muss")) {
				appendModifiedRequirement(result, base, skillChoice.getString("Muss"));
			} else if (skillChoice.containsKey("Wahl")) {
				final JSONArray skillChoiceChoice = skillChoice.getArr("Wahl");
				for (int i = 0; i < skillChoiceChoice.size(); ++i) {
					if (i != 0) {
						result.append(" o. ");
					}
					appendModifiedRequirement(result, base, skillChoiceChoice.getString(i));
				}
			}
			if (requiredSkill.containsKey("Freitext")) {
				result.append(", ");
				final JSONObject skillText = requiredSkill.getObj("Freitext");
				if (skillText.containsKey("Muss")) {
					appendModifiedRequirement(result, base, skillText.getString("Muss"));
				} else if (skillText.containsKey("Wahl")) {
					final JSONArray skillChoiceChoice = skillText.getArr("Wahl");
					for (int i = 0; i < skillChoiceChoice.size(); ++i) {
						if (i != 0) {
							result.append(" o. ");
						}
						appendModifiedRequirement(result, base, skillChoiceChoice.getString(i));
					}
				}
			}
			result.append(")");
		} else if (requiredSkill.containsKey("Freitext")) {
			result.append("\u00A0(");
			final JSONObject skillText = requiredSkill.getObj("Freitext");
			if (skillText.containsKey("Muss")) {
				appendModifiedRequirement(result, base, skillText.getString("Muss"));
			} else if (skillText.containsKey("Wahl")) {
				final JSONArray skillChoiceChoice = skillText.getArr("Wahl");
				for (int i = 0; i < skillChoiceChoice.size(); ++i) {
					if (i != 0) {
						result.append(" o. ");
					}
					appendModifiedRequirement(result, base, skillChoiceChoice.getString(i));
				}
			}
			result.append(")");
		}
	}

	public static void checkChoiceBox(final PDPageContentStream stream, final int left, final float top) throws IOException {
		stream.setLineWidth(1);
		stream.moveTo(left + 1, top - 1);
		stream.lineTo(left + 7, top - 7);
		stream.moveTo(left + 7, top - 1);
		stream.lineTo(left + 1, top - 7);
		stream.stroke();
	}

	public static Consumer<TableEvent> createHeader(final String header, final boolean includeNameLine, final boolean includeAttributesLine,
			final boolean includeBasicValuesLine, final JSONObject hero, final boolean fill, final boolean fillAll) {
		return event -> {
			final PDPageContentStream stream = event.getStream();
			final boolean landscape = event.getWidth() == PDRectangle.A4.getHeight();

			try {
				final PDFont font = FontManager.serif;
				final float fontSize = 40;
				stream.setNonStrokingColor(Color.BLACK);
				stream.setFont(font, fontSize);
				final float xStart = (event.getWidth() - font.getStringWidth(header) / 1000 * fontSize) * 0.5f;
				final float yStart = event.getHeight() - 10 - (font.getFontDescriptor().getAscent() + font.getFontDescriptor().getDescent()) / 1000 * fontSize;
				stream.beginText();
				stream.newLineAtOffset(xStart, yStart);
				stream.showText(header);
				stream.endText();
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}

			if (includeNameLine) {
				final Table table = new Table().setBorder(0, 0, 0, 0);
				final float lineWidth = landscape ? 245 : 571;
				table.addColumn(new Column(lineWidth, lineWidth, FontManager.serif, 4, 10.5f, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
				JSONObject bio = null;
				if (hero != null) {
					bio = hero.getObj("Biografie");
				}
				table.addRow("Name: " + (hero != null && fill ? bio.getStringOrDefault("Vorname", "") + " " + bio.getStringOrDefault("Nachname", "") : ""));
				try {
					table.renderRows(event.getDocument(), stream, 0, -1, lineWidth, 12, event.getHeight() - (landscape ? 41 : 36));
				} catch (final IOException e) {
					ErrorLogger.logError(e);
				}
			}
			JSONObject actualAttributes = null;
			if (hero != null) {
				actualAttributes = hero.getObj("Eigenschaften");
			}
			if (includeAttributesLine) {
				final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
				final int numAttributes = attributes.size() + 1;
				final Table table = new Table().setBorder(0, 0, 0, 0);
				for (int i = 0; i < numAttributes; ++i) {
					table.addColumn(new Column(285.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
					table.addColumn(new Column(285.5f / numAttributes, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
				}
				for (final String attribute : attributes.keySet()) {
					table.addCells(attribute, hero != null && fill ? HeroUtil.getCurrentValue(actualAttributes.getObj(attribute), false) : " ");
				}
				table.addCells("BE", hero != null && fill ? HeroUtil.getBE(hero) : " ");
				try {
					if (landscape) {
						table.renderRows(event.getDocument(), stream, 0, -1, 571, 259, event.getHeight() - 41);
					} else {
						table.renderRows(event.getDocument(), stream, 0, -1, 571, 12, event.getHeight() - (includeNameLine ? 54 : 41));
					}
				} catch (final IOException e) {
					ErrorLogger.logError(e);
				}
			}
			if (includeBasicValuesLine) {
				final Table table = new Table().setBorder(0, 0, 0, 0);
				for (int i = 0; i < 4; ++i) {
					table.addColumn(new Column(421f / 5, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
					table.addColumn(new Column(30, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0.5f, 0.5f, 0.5f, 0.5f));
				}
				table.addColumn(new Column(421f / 5, FontManager.serif, 10.5f, HAlign.CENTER).setBorder(0, 0, 0, 0));
				JSONObject baseValues = null;
				if (hero != null) {
					baseValues = hero.getObj("Basiswerte");
				}
				final JSONObject derivedValues = ResourceManager.getResource("data/Basiswerte");
				for (final String value : new String[] { "Attacke-Basis", "Parade-Basis", "Fernkampf-Basis", "Initiative-Basis" }) {
					table.addCells(value, hero != null && fill ? HeroUtil.deriveValue(derivedValues.getObj(value), actualAttributes, hero.getObj(value), false)
							+ baseValues.getObj(value).getIntOrDefault("Modifikator", 0) : " ");
				}
				table.addCells("\u00A0\u00A0\u00A0+4\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0+2");
				final int left = landscape ? 274 : 27;
				final int top = landscape || !includeNameLine ? 41 : 54;
				try {
					table.renderRows(event.getDocument(), stream, 0, -1, 541, left, event.getHeight() - top);
					drawChoiceBox(stream, left + 470, event.getHeight() - top - 2);
					drawChoiceBox(stream, left + 500, event.getHeight() - top - 2);
					if (hero != null && fill) {
						final JSONObject skills = hero.getObj("Sonderfertigkeiten");
						if (skills.containsKey("Kampfreflexe")) {
							checkChoiceBox(stream, left + 470, event.getHeight() - top - 2);
						}
						if (skills.containsKey("Kampfgespür")) {
							checkChoiceBox(stream, left + 500, event.getHeight() - top - 2);
						}
					}
				} catch (final IOException e) {
					ErrorLogger.logError(e);
				}
			}
		};
	}

	public static Cell createTitleCell(final String text, final int colSpan) {
		return new TextCell(text, FontManager.serifBold, 9, 9).setBackground(new Color(0.9f, 0.9f, 0.9f)).setColSpan(colSpan);
	}

	public static void drawChoiceBox(final PDPageContentStream stream, final float left, final float top) throws IOException {
		stream.setLineWidth(0.25f);
		stream.moveTo(left, top);
		stream.lineTo(left + 8, top);
		stream.lineTo(left + 8, top - 8);
		stream.lineTo(left, top - 8);
		stream.lineTo(left, top);
		stream.stroke();
	}

	public static String getCultureString(final JSONObject bio) {
		final StringBuilder cultureString = new StringBuilder(bio.getStringOrDefault("Kultur", ""));
		if (bio.containsKey("Kultur:Modifikation")) {
			final JSONArray cultureModifiers = bio.getArr("Kultur:Modifikation");
			cultureString.append(" (");
			cultureString.append(String.join(", ", cultureModifiers.getStrings()));
			cultureString.append(")");
		}
		return cultureString.toString();
	}

	public static String getProfessionString(final JSONObject hero, final JSONObject bio) {
		final StringBuilder professionString = new StringBuilder(bio.getStringOrDefault("Profession", ""));
		if (bio.containsKey("Profession:Modifikation")) {
			final JSONArray professionModifiers = bio.getArr("Profession:Modifikation");
			professionString.append(" (");
			professionString.append(String.join(", ", professionModifiers.getStrings()));
			professionString.append(")");
		}
		final JSONObject pros = hero.getObj("Vorteile");
		if (pros != null) {
			if (pros.containsKey("Veteran")) {
				final JSONObject veteran = pros.getObj("Veteran");
				professionString.append(" Veteran");
				if (veteran != null && veteran.containsKey("Profession:Variante")) {
					professionString.append(' ');
					professionString.append(veteran.getString("Profession:Variante"));
				}
			}
			if (pros.containsKey("Breitgefächerte Bildung")) {
				final JSONObject bgb = pros.getObj("Breitgefächerte Bildung");
				professionString.append(" BGB ");
				professionString.append(bgb.getString("Profession"));
				if (bgb.containsKey("Profession:Variante")) {
					professionString.append(" (");
					professionString.append(bgb.getString("Profession:Variante"));
					professionString.append(")");
				}
			}
		}
		return professionString.toString();
	}

	public static String getRaceString(final JSONObject bio) {
		final StringBuilder raceString = new StringBuilder(bio.getStringOrDefault("Rasse", ""));

		if (bio.containsKey("Rasse:Modifikation")) {
			final JSONArray raceModifiers = bio.getArr("Rasse:Modifikation");
			raceString.append(" (");
			raceString.append(String.join(", ", raceModifiers.getStrings()));
			raceString.append(")");
		}
		return raceString.toString();
	}

	public static String getRequirementString(final JSONObject requirements, final JSONObject base) {
		if (requirements == null) return "";
		if (requirements.containsKey("Ersatztext")) return requirements.getString("Ersatztext");
		boolean first = true;
		final StringBuilder result = new StringBuilder();
		if (requirements.containsKey("Wahl")) {
			final JSONArray choices = requirements.getArr("Wahl");
			for (int i = 0; i < choices.size(); ++i) {
				if (first) {
					first = false;
				} else {
					result.append(", ");
				}
				final JSONArray choice = choices.getArr(i);
				boolean firstChoice = true;
				for (int j = 0; j < choice.size(); ++j) {
					if (firstChoice) {
						firstChoice = false;
					} else {
						result.append(" o. ");
					}
					result.append(getRequirementString(choice.getObj(j), base));
				}
			}
		}
		if (requirements.containsKey("Eigenschaften")) {
			final JSONObject attributes = requirements.getObj("Eigenschaften");
			for (final String attribute : attributes.keySet()) {
				if (first) {
					first = false;
				} else {
					result.append(", ");
				}
				result.append(attribute);
				result.append('\u00A0');
				result.append(attributes.getInt(attribute));
			}
		}
		if (requirements.containsKey("Basiswerte")) {
			final JSONObject basicValues = requirements.getObj("Basiswerte");
			for (final String basicValue : basicValues.keySet()) {
				if (first) {
					first = false;
				} else {
					result.append(", ");
				}
				result.append(basicValue);
				result.append('\u00A0');
				result.append(basicValues.getInt(basicValue));
			}
		}
		if (requirements.containsKey("Rassen")) {
			final JSONObject races = requirements.getObj("Rassen");
			if (races.containsKey("Muss")) {
				final JSONObject must = races.getObj("Muss");
				for (final String race : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					appendModifiedRequirement(result, base, race);
				}
			}
			if (races.containsKey("Wahl")) {
				final JSONArray choices = races.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String race : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append(" o. ");
						}
						appendModifiedRequirement(result, base, race);
					}
				}
			}
			if (races.containsKey("Nicht")) {
				final JSONObject not = races.getObj("Nicht");
				for (final String race : not.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					result.append("nicht\u00A0");
					appendModifiedRequirement(result, base, race);
				}
			}
		}
		if (requirements.containsKey("Kulturen")) {
			final JSONObject cultures = requirements.getObj("Kulturen");
			if (cultures.containsKey("Muss")) {
				final JSONObject must = cultures.getObj("Muss");
				for (final String culture : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					appendModifiedRequirement(result, base, culture);
				}
			}
			if (cultures.containsKey("Wahl")) {
				final JSONArray choices = cultures.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String culture : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append(" o. ");
						}
						appendModifiedRequirement(result, base, culture);
					}
				}
			}
			if (cultures.containsKey("Nicht")) {
				final JSONObject not = cultures.getObj("Nicht");
				for (final String culture : not.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					result.append("nicht\u00A0");
					appendModifiedRequirement(result, base, culture);
				}
			}
		}
		if (requirements.containsKey("Professionen")) {
			final JSONObject professions = requirements.getObj("Professionen");
			if (professions.containsKey("Muss")) {
				final JSONObject must = professions.getObj("Muss");
				for (final String profession : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					appendModifiedRequirement(result, base, profession);
				}
			}
			if (professions.containsKey("Wahl")) {
				final JSONArray choices = professions.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String profession : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append(" o. ");
						}
						appendModifiedRequirement(result, base, profession);
					}
				}
			}
			if (professions.containsKey("Nicht")) {
				final JSONObject not = professions.getObj("Nicht");
				for (final String profession : not.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					result.append("nicht\u00A0");
					appendModifiedRequirement(result, base, profession);
				}
			}
		}
		if (requirements.containsKey("Talente")) {
			final JSONObject talents = requirements.getObj("Talente");
			if (talents.containsKey("Muss")) {
				final JSONObject must = talents.getObj("Muss");
				for (final String talent : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					appendModifiedRequirement(result, base, talent);
					final int requiredValue = must.getInt(talent);
					if (requiredValue != 0) {
						result.append('\u00A0');
						result.append(requiredValue);
					}
				}
			}
			if (talents.containsKey("Wahl")) {
				final JSONArray choices = talents.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String talent : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append(" o. ");
						}
						appendModifiedRequirement(result, base, talent);
						final int requiredValue = choice.getInt(talent);
						if (requiredValue != 0) {
							result.append('\u00A0');
							result.append(requiredValue);
						}
					}
				}
			}
		}
		if (requirements.containsKey("Sonderfertigkeiten AP")) {
			final JSONObject apRequirements = requirements.getObj("Talente");
			if (apRequirements.containsKey("Muss")) {
				final JSONObject must = apRequirements.getObj("Muss");
				for (final String apRequirement : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					result.append(must.getInt(apRequirement));
					result.append("AP in\u00A0");
					result.append(apRequirement);
				}
			}
			if (apRequirements.containsKey("Wahl")) {
				final JSONArray choices = apRequirements.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String apRequirement : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append("o. ");
						}
						result.append(choice.getInt(apRequirement));
						result.append("AP in\u00A0");
						result.append(apRequirement);
					}
				}
			}
		}
		if (requirements.containsKey("Vorteile/Nachteile/Sonderfertigkeiten")) {
			final JSONObject skills = requirements.getObj("Vorteile/Nachteile/Sonderfertigkeiten");
			if (skills.containsKey("Muss")) {
				final JSONObject must = skills.getObj("Muss");
				for (final String requiredSkillName : must.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					appendRequiredSkill(result, requiredSkillName, must.getObj(requiredSkillName), base);
				}
			}
			if (skills.containsKey("Wahl")) {
				final JSONArray choices = skills.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					boolean firstChoice = true;
					for (final String requiredSkillName : choice.keySet()) {
						if (firstChoice) {
							firstChoice = false;
						} else {
							result.append(" o. ");
						}
						appendRequiredSkill(result, requiredSkillName, choice.getObj(requiredSkillName), base);
					}
				}
			}
			if (skills.containsKey("Nicht")) {
				final JSONObject not = skills.getObj("Nicht");
				for (final String prohibitedSkillName : not.keySet()) {
					if (first) {
						first = false;
					} else {
						result.append(", ");
					}
					result.append("kein\u00A0");
					appendRequiredSkill(result, prohibitedSkillName, not.getObj(prohibitedSkillName), base);
				}
			}
		}
		if (requirements.containsKey("Text")) {
			if (first) {
				first = false;
			} else {
				result.append(", ");
			}
			result.append(requirements.getString("Text"));
		}
		return result.toString();
	}

	public static String getTargetObjectsString(final JSONArray actualTargets) {
		final JSONObject targets = ResourceManager.getResource("data/Zielobjekte");
		final StringBuilder targetString = new StringBuilder();
		if (actualTargets != null) {
			boolean first = true;
			for (final String targetName : targets.keySet()) {
				for (int i = 0; i < actualTargets.size(); ++i) {
					if (targetName.equals(actualTargets.getString(i))) {
						if (first) {
							first = false;
						} else {
							targetString.append(' ');
						}
						targetString.append(targets.getObj(targetName).getStringOrDefault("Abkürzung", ""));
					}
				}
			}
		}
		return targetString.toString();
	}

	public static boolean matchesPageSize(PDDocument document, PDRectangle pageSize) {
		final PDRectangle current = document.getPage(document.getNumberOfPages() - 1).getMediaBox();
		return current.getWidth() == pageSize.getWidth() && current.getHeight() == pageSize.getHeight();
	}

	public static RowStripe stripe() {
		return new CellFiller.RowStripe(new Color(0.9f, 0.9f, 0.9f));
	}
}
