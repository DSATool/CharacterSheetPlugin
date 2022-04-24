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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import boxtable.cell.Cell;
import boxtable.cell.ImageCell;
import boxtable.cell.TextCell;
import boxtable.common.Bordered;
import boxtable.common.HAlign;
import boxtable.common.VAlign;
import boxtable.event.EventType;
import boxtable.table.Column;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import dsatool.util.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class CharacterSheet extends Sheet {
	private final IntegerProperty additionalConnectionRows = new SimpleIntegerProperty(5);
	private final IntegerProperty additionalConRows = new SimpleIntegerProperty(5);
	private final IntegerProperty additionalProRows = new SimpleIntegerProperty(5);
	private final BooleanProperty showAge = new SimpleBooleanProperty(false);
	private final BooleanProperty showAsP = new SimpleBooleanProperty(false);
	private final BooleanProperty showBankMoney = new SimpleBooleanProperty(false);
	private final BooleanProperty showBirthday = new SimpleBooleanProperty(false);
	private final BooleanProperty showConnections = new SimpleBooleanProperty(false);
	private final BooleanProperty showCons = new SimpleBooleanProperty(true);
	private final BooleanProperty showImage = new SimpleBooleanProperty(false);
	private final BooleanProperty showKaP = new SimpleBooleanProperty(false);
	private final BooleanProperty showPros = new SimpleBooleanProperty(true);
	private final ObjectProperty<File> image = new SimpleObjectProperty<>(null);
	private final float fontSize = 10.5f;
	private final float descSize = 9;
	private final float rowFontSize = 7;

	public CharacterSheet() {
		super(770, false);
	}

	private void addAPTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(45, FontManager.serif, descSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(50, 50, FontManager.serif, 4, fontSize, HAlign.CENTER).setBorder(1, 1, 1, 0));

		JSONObject biography = null;
		if (hero != null) {
			biography = hero.getObj("Biografie");
		}

		table.addRow("Stufe", fillAll && hero != null ? Integer.toString(biography.getIntOrDefault("Abenteuerpunkte", 0) / 1000) : " ");
		table.addRow("AP", fillAll && hero != null ? biography.getIntOrDefault("Abenteuerpunkte", 0) : " ");
		table.addRow("Investiert", fillAll && hero != null
				? Integer.toString(biography.getIntOrDefault("Abenteuerpunkte", 0) - biography.getIntOrDefault("Abenteuerpunkte-Guthaben", 0)) : " ");
		table.addRow("Guthaben", fillAll && hero != null ? biography.getIntOrDefault("Abenteuerpunkte-Guthaben", 0) : " ");
		table.addRow("Neu", new TextCell(" ").setBorder(1, 1, 1, 1));

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		bottom.bottom = table.render(document, 95, showImage.get() ? 300 : 488,
				page.getMediaBox().getHeight() - 265 + (showAsP.get() ? 0 : 12) + (showKaP.get() ? 0 : 12), 72, 10) - 5;
	}

	private void addAttributesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(62, FontManager.serif, descSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell("Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered startDesc = new TextCell("Start", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered maxDesc = new TextCell("Max.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, curDesc, modDesc, startDesc, maxDesc);

		JSONObject actualAttributes = null;
		if (hero != null) {
			actualAttributes = hero.getObj("Eigenschaften");
		}

		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		for (final String attribute : attributes.keySet()) {
			JSONObject actualAttribute = null;
			if (hero != null && fill) {
				actualAttribute = actualAttributes.getObj(attribute);
				final String cur = Integer.toString(actualAttribute.getIntOrDefault("Wert", 0));
				final String mod = Util.getSignedIntegerString(actualAttribute.getIntOrDefault("Modifikator", 0));
				final int start = actualAttribute.getIntOrDefault("Start", 0);
				final String max = Integer.toString((int) Math.round(start * 1.5));
				table.addRow(attributes.getObj(attribute).getString("Name"), cur, mod, start, max);
			} else {
				table.addRow(attributes.getObj(attribute).getString("Name"), " ");
			}
		}

		final Bordered so = new TextCell(
				hero != null && fill ? Integer.toString(HeroUtil.getCurrentValue(hero.getObj("Basiswerte").getObj("Sozialstatus"), false)) : " ").setBorder(1,
						1, 1, 1);
		final Bordered empty = new TextCell(" ").setBorder(0, 0, 0, 0);
		table.addRow("Sozialstatus", so, empty, empty, empty);

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, 142, 12, page.getMediaBox().getHeight() - 105, 72, 10);
	}

	private void addBiographyTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(145, 145, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(145, 145, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(145, 145, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));
		table.addColumn(new Column(135, 135, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0.5f));

		if (hero != null && fill) {
			JSONObject bio = null;
			bio = hero.getObj("Biografie");
			final Cell name = new TextCell("Name: " + bio.getStringOrDefault("Vorname", "") + " " + bio.getStringOrDefault("Nachname", "")).setColSpan(3);
			final JSONObject time = ResourceManager.getResource("data/Allgemein").getObj("Zeit");
			final String age = showAge.get() ? "Alter: " + (time != null ? Integer.toString(DSAUtil.getDaysBetween(bio.getIntOrDefault("Geburtstag", 1),
					bio.getIntOrDefault("Geburtsmonat", 1), bio.getIntOrDefault("Geburtsjahr", 1000), time.getIntOrDefault("Tag", 1),
					time.getIntOrDefault("Monat", 1), time.getIntOrDefault("Jahr", 1000)) / 365) : " ") : " ";
			table.addRow(name, age);

			final String eyeColor = "Augenfarbe: " + bio.getStringOrDefault("Augenfarbe", "");
			String hairColor, skinColor;
			if (bio.containsKey("Schuppenfarbe 1")) {
				hairColor = "Schuppenfarbe 1: " + bio.getStringOrDefault("Schuppenfarbe 1", "");
				skinColor = "Schuppenfarbe 2: " + bio.getStringOrDefault("Schuppenfarbe 2", "");
			} else {
				hairColor = "Haarfarbe: " + bio.getStringOrDefault("Haarfarbe", "");
				skinColor = "Hautfarbe: " + bio.getStringOrDefault("Hautfarbe", "");
			}
			final String birthday = showBirthday.get() ? "Geburtstag: " + bio.getIntOrDefault("Geburtstag", 0) + ". "
					+ DSAUtil.months[bio.getIntOrDefault("Geburtsmonat", 1) - 1] + " " + bio.getIntOrDefault("Geburtsjahr", 0) : " ";
			table.addRow(eyeColor, hairColor, skinColor, birthday);

			final Cell race = new TextCell("Rasse: " + SheetUtil.getRaceString(bio)).setColSpan(3);
			final String gender = "Geschlecht: " + ("weiblich".equals(bio.getString("Geschlecht")) ? "♀" : "♂");
			table.addRow(race, gender);

			final Cell culture = new TextCell("Kultur: " + SheetUtil.getCultureString(bio)).setColSpan(3);
			final String size = "Größe: " + bio.getIntOrDefault("Größe", 0);
			table.addRow(culture, size);

			final Cell profession = new TextCell(
					"Profession: " + HeroUtil.getProfessionString(hero, bio, ResourceManager.getResource("data/Professionen"), true)).setColSpan(3);
			final String weight = "Gewicht: " + bio.getIntOrDefault("Gewicht", 0);
			table.addRow(profession, weight);
		} else {
			table.addRow("Name:", " ", " ", showAge.get() ? "Alter: " : " ");
			final boolean scalecolor = hero != null && hero.getObj("Biografie").containsKey("Schuppenfarbe 1");
			table.addRow("Augenfarbe:", scalecolor ? "Schuppenfarbe 1:" : "Haarfarbe:", scalecolor ? "Schuppenfarbe 2" : "Hautfarbe:",
					showBirthday.get() ? "Geburtstag:" : " ");
			table.addRow("Rasse:", " ", " ", "Geschlecht:");
			table.addRow("Kultur:", " ", " ", "Größe:");
			table.addRow("Profession:", " ", " ", "Gewicht:");
		}

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, 571, 12, page.getMediaBox().getHeight() - 36, 72, 10);
	}

	private void addConnectionsTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(126, 126, FontManager.serif, 4, rowFontSize, HAlign.LEFT));
		table.addColumn(new Column(126, 126, FontManager.serif, 4, rowFontSize, HAlign.LEFT));
		table.addColumn(new Column(0, 319, FontManager.serif, 4, rowFontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Verbindungen");

		final Cell connectionTitle = new TextCell("Verbindung", FontManager.serifBold, 8.5f, 8.5f);
		final Cell placeTitle = new TextCell("Ort", FontManager.serifBold, 8.5f, 8.5f);
		final Cell descTitle = new TextCell("Beschreibung", FontManager.serifBold, 8.5f, 8.5f);
		table.addRow(connectionTitle, placeTitle, descTitle);

		if (hero != null) {
			final JSONArray connections = hero.getObj("Vorteile").getArrOrDefault("Verbindungen", null);
			if (connections != null) {
				for (int i = 0; i < connections.size(); ++i) {
					table.addRow(fill ? connections.getObj(i).getStringOrDefault("Freitext", "") : " ");
				}
			}
		}

		for (int i = 0; i < additionalConnectionRows.get(); ++i) {
			table.addRow("");
		}

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	private void addDerivedValuesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(67, FontManager.serif, descSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(75, FontManager.serif, 8, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(20, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, fontSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell("Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered resultDesc = new TextCell("Ergebnis", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, emptyDesc, curDesc, modDesc, resultDesc);

		final JSONObject derivedValues = ResourceManager.getResource("data/Basiswerte");
		for (final String derivedName : new String[] { "Attacke-Basis", "Parade-Basis", "Fernkampf-Basis", "Initiative-Basis", "Artefaktkontrolle" }) {
			final JSONObject derivedValue = derivedValues.getObj(derivedName);
			final StringBuilder derivation = new StringBuilder("(");
			derivation.append(String.join("+", derivedValue.getArr("Eigenschaften").getStrings()));
			if (derivedValue.containsKey("Basiswerte")) {
				final JSONArray basicValues = derivedValue.getArr("Basiswerte");
				for (int i = 0; i < basicValues.size(); ++i) {
					if (derivation.length() > 0) {
						derivation.append('+');
					}
					derivation.append(derivedValues.getObj(basicValues.getString(i)).getStringOrDefault("Abkürzung", basicValues.getString(i)));
				}
			}
			derivation.append(')');
			if (derivedValue.containsKey("Multiplikator")) {
				final double multiplier = derivedValue.getDouble("Multiplikator");
				if (multiplier < 1) {
					derivation.append(':');
					derivation.append((int) (1 / multiplier));
				} else {
					derivation.append('*');
					derivation.append(multiplier);
				}
			}
			if (hero != null && fill) {
				final JSONObject actualValue = hero.getObj("Basiswerte").getObj(derivedName);
				final String cur = Integer.toString(HeroUtil.deriveValue(derivedValue, hero, actualValue, false));
				final String mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));
				final String result = DSAUtil.threeDecimalPlaces.format(HeroUtil.deriveValueRaw(derivedValue, hero));
				table.addRow(derivedName, new TextCell(derivation.toString()).setPadding(0, 0, 2, 0), cur, mod, result);
			} else {
				table.addRow(derivedName, new TextCell(derivation.toString()).setPadding(0, 0, 2, 0), " ");
			}
		}

		double woundThreshold = 0;
		int woundModifier = 0;
		if (hero != null) {
			woundThreshold = HeroUtil.deriveValueRaw(derivedValues.getObj("Wundschwelle"), hero);
			final JSONObject woundObj = hero.getObj("Basiswerte").getObj("Wundschwelle");
			woundModifier = woundObj.getIntOrDefault("Modifikator", 0);
		}
		for (int i = 1; i < 4; ++i) {
			final String name = Integer.toString(i) + ". Wundschwelle";
			final String derivation = (i % 2 == 0 ? "" : i + "/2 ") + "KO";
			if (hero != null && fill) {
				final String cur = Integer.toString((int) Math.round(i * woundThreshold + woundModifier));
				final String mod = Util.getSignedIntegerString(woundModifier);
				final String result = DSAUtil.threeDecimalPlaces.format(i * woundThreshold);
				table.addRow(name, new TextCell(derivation).setPadding(0, 0, 2, 0), cur, mod, result);
			} else {
				table.addRow(name, new TextCell(derivation).setPadding(0, 0, 2, 0));
			}
		}

		if (hero != null && fill) {
			final JSONObject velocityObj = hero.getObj("Basiswerte").getObj("Geschwindigkeit");
			final int velocity = HeroUtil.deriveValue(derivedValues.getObj("Geschwindigkeit"), hero, velocityObj, false);
			final int velocityModifier = velocityObj.getIntOrDefault("Modifikator", 0);
			final Bordered cur = new TextCell(Integer.toString(velocity)).setBorder(1, 1, 1, 1);
			final String mod = Util.getSignedIntegerString(velocityModifier);
			final String result = Integer.toString(velocity - velocityModifier);
			table.addRow("Geschwindigkeit", " ", cur, mod, result);
		} else {
			table.addRow("Geschwindigkeit", " ", new TextCell(" ").setBorder(1, 1, 1, 1));
		}

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, 212, 183, page.getMediaBox().getHeight() - 105, 72, 10);
	}

	private void addEnergiesTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(62, FontManager.serif, descSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(70, FontManager.serif, 8, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(45, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER).setBorder(0, 1, 1, 1));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered buyDesc = new TextCell("Kauf", FontManager.serif, 6, 6).addText("/").addText("Max.").setEquallySpaced(true).setBorder(0, 0, 0, 0);
		final Bordered permDesc = new TextCell("Perm.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell("Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered resultDesc = new TextCell("Start", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, emptyDesc, buyDesc, permDesc, modDesc, resultDesc, curDesc);

		final JSONObject derivedValues = ResourceManager.getResource("data/Basiswerte");
		for (final String derivedName : new String[] { "Lebensenergie", "Ausdauer", "Magieresistenz", "Astralenergie", "Karmaenergie" }) {
			final JSONObject derivedValue = derivedValues.getObjOrDefault(derivedName, new JSONObject(null));
			if (!showAsP.get() && "Astralenergie".equals(derivedName) || !showKaP.get() && "Karmaenergie".equals(derivedName)) {
				continue;
			}

			final JSONArray derivationAttributes = derivedValue.getArrOrDefault("Eigenschaften", null);
			final StringBuilder derivation = new StringBuilder();
			if (derivationAttributes != null) {
				derivation.append('(');
				derivation.append(String.join("+", derivationAttributes.getStrings()));
				if (hero != null && "Astralenergie".equals(derivedName) && hero.getObj("Sonderfertigkeiten").containsKey("Gefäß der Sterne")) {
					derivation.append("+CH");
				}
				derivation.append(')');
				if (derivedValue.containsKey("Multiplikator")) {
					final double multiplier = derivedValue.getDouble("Multiplikator");
					if (multiplier < 1) {
						derivation.append(':');
						derivation.append((int) (1 / multiplier));
					} else {
						derivation.append('*');
						derivation.append(multiplier);
					}
				}
			}

			Cell buyable = new TextCell("—").addText("/").addText("—").setEquallySpaced(true);
			if (hero != null && fill) {
				final JSONObject actualValue = hero.getObj("Basiswerte").getObjOrDefault(derivedName, null);
				if (actualValue != null) {
					if (derivedValue.containsKey("Zukauf:Maximum")) {
						final String buy = actualValue.getIntOrDefault("Kauf", 0).toString();
						final String max = Integer.toString(HeroUtil.deriveValue(derivedValue.getObj("Zukauf:Maximum"), hero, null, false));
						buyable = new TextCell(buy).addText("/").addText(max).setEquallySpaced(true);
					}
					final String perm = actualValue.getIntOrDefault("Permanent", 0).toString();
					final String mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));
					final String result = DSAUtil.threeDecimalPlaces.format(HeroUtil.deriveValueRaw(derivedValue, hero));
					final TextCell cur = new TextCell(Integer.toString(HeroUtil.deriveValue(derivedValue, hero, actualValue, false)));
					if ("Lebensenergie".equals(derivedName)) {
						cur.setBorder(1, 1, 1, 1);
					}
					table.addRow(derivedName, derivation.toString(), buyable, perm, mod, result, cur);
				} else {
					table.addRow(derivedName, derivation.toString(), buyable, "—", "—", "—", "—");
				}
			} else {
				table.addRow(derivedName, derivation.toString(), "/", "", "", "", new Cell().setBorder(1, 1, 1, 1));
			}
		}

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, 277, 12, page.getMediaBox().getHeight() - 258, 72, 10);
	}

	private void addImageTable(final PDDocument document) throws IOException {
		final Table table = new Table();
		table.addColumn(new Column(178, FontManager.serif, 0, HAlign.RIGHT).setVAlign(VAlign.TOP));

		final float height = 215.75f - (showAsP.get() ? 0 : 12) - (showKaP.get() ? 0 : 12);
		final float width = 178;

		final File file = image.get();

		if (file != null) {
			if (file.exists()) {
				table.addRow(new ImageCell(file).setMinHeight(height).setBorder(0, 0, 0, 0));
			} else {
				ErrorLogger.log("Bilddatei nicht gefunden:\n" + file.getAbsolutePath());
				table.addRow(new Cell().setMinHeight(height));
			}
		} else {
			table.addRow(new Cell().setMinHeight(height));
		}

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, width, 583 - width, page.getMediaBox().getHeight() - 112, 10, 10);
	}

	private void addMoneyTable(final PDDocument document) throws IOException {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		final boolean needsSmallTable = showBankMoney.get() && showImage.get() && !showAsP.get() && !showKaP.get();

		table.addColumn(new Column(needsSmallTable ? 21 : 62, FontManager.serif, descSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(40, FontManager.serif, fontSize, HAlign.RIGHT).setBorder(1, 1, 0, 1));
		table.addColumn(new Column(40, FontManager.serif, fontSize, HAlign.RIGHT).setBorder(1, 1, 0, 1));
		table.addColumn(new Column(40, FontManager.serif, fontSize, HAlign.RIGHT).setBorder(1, 1, 0, 1));
		table.addColumn(new Column(40, FontManager.serif, fontSize, HAlign.RIGHT).setBorder(1, 1, 0, 1));
		table.addColumn(new Column(needsSmallTable ? 26 : 91, FontManager.serif, descSize, HAlign.RIGHT).setBorder(0, 1, 0, 0));
		table.addColumn(new Column(70, FontManager.serif, fontSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));

		table.addCells("Geld");

		JSONObject money = null;
		if (hero != null) {
			money = hero.getObj("Besitz").getObj("Geld");
		}

		for (final String value : new String[] { "Dukaten", "Silbertaler", "Heller", "Kreuzer" }) {
			table.addCells(new TextCell((hero != null && fillAll ? money.getIntOrDefault(value, 0) + " " : "") + value.charAt(0)).setPadding(0, 2, 2, 0));
		}

		if (showBankMoney.get()) {
			table.addCells("Bank ", new TextCell(",      D").setPadding(0, 2, 2, 0).setBorder(1, 1, 1, 1));
		} else {
			table.addCells(" ", " ");
		}

		final PDPage page = document.getPage(document.getNumberOfPages() - 1);
		table.render(document, needsSmallTable ? 277 : 383, 12, page.getMediaBox().getHeight() - 237, 72, 10);
	}

	private void addProOrConTable(final PDDocument document, final String title) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe()).setNumHeaderRows(2);
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(175, 175, FontManager.serif, 4, rowFontSize, HAlign.LEFT));
		table.addColumn(new Column(30, FontManager.serif, rowFontSize, HAlign.CENTER));
		table.addColumn(new Column(30, FontManager.serif, rowFontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 336, FontManager.serif, 4, rowFontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, title + 'e');

		final Cell titleCell = new TextCell(title, FontManager.serifBold, 8.5f, 8.5f);
		final Cell valueTitle = new TextCell("Wert", FontManager.serifBold, 8.5f, 8.5f);
		final Cell costTitle = new TextCell("GP", FontManager.serifBold, 8.5f, 8.5f);
		final Cell descTitle = new TextCell("Beschreibung", FontManager.serifBold, 8.5f, 8.5f);
		table.addRow(titleCell, valueTitle, costTitle, descTitle);

		if (hero != null) {
			final JSONObject prosOrCons = ResourceManager.getResource("data/" + title + 'e');
			final JSONObject actual = hero.getObj(title + 'e');

			final Map<String, JSONObject> actualProsOrCons = new TreeMap<>(SheetUtil.comparator);
			for (final String proOrConName : actual.keySet()) {
				actualProsOrCons.put(proOrConName, prosOrCons.getObj(proOrConName));
			}

			for (final String proOrConName : actualProsOrCons.keySet()) {
				final JSONObject proOrCon = actualProsOrCons.get(proOrConName);
				final boolean isQuirk = proOrCon.getBoolOrDefault("Schlechte Eigenschaft", false);
				final String description = proOrCon.getStringOrDefault("Beschreibung:Kurz", "");
				if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
					final JSONArray proOrConList = actual.getArr(proOrConName);
					for (int i = 0; i < proOrConList.size(); ++i) {
						if (fill) {
							final JSONObject actualProOrCon = proOrConList.getObj(i);
							String value = " ";
							String cost;
							if (proOrCon.getBoolOrDefault("Abgestuft", false) || isQuirk) {
								if (fillAll || !isQuirk) {
									value = actualProOrCon.getIntOrDefault("Stufe", 0).toString();
								}
								cost = Integer
										.toString((int) Math.round(proOrCon.getDoubleOrDefault("Kosten", 0.0) * actualProOrCon.getIntOrDefault("Stufe", 0)));
							} else {
								cost = proOrCon.getIntOrDefault("Kosten", 0).toString();
							}
							table.addRow(DSAUtil.printProOrCon(actualProOrCon, proOrConName, proOrCon, false), value, cost, description);
						} else {
							table.addRow("");
						}
					}
				} else {
					if (fill) {
						final JSONObject actualProOrCon = actual.getObj(proOrConName);

						String value = " ";
						String cost;
						if (proOrCon.getBoolOrDefault("Abgestuft", false) || isQuirk) {
							if (fillAll || !isQuirk && !"Schulden".equals(proOrConName)) {
								value = actualProOrCon.getIntOrDefault("Stufe", 0).toString();
							}
							cost = Integer.toString((int) Math.round(proOrCon.getDoubleOrDefault("Kosten", 0.0) * actualProOrCon.getIntOrDefault("Stufe", 0)));
						} else {
							cost = proOrCon.getIntOrDefault("Kosten", 0).toString();
						}

						table.addRow(DSAUtil.printProOrCon(actualProOrCon, proOrConName, proOrCon, false), value, cost, description);
					} else {
						table.addRow("");
					}
				}
			}
		}
		final int additionalRows = "Vorteil".equals(title) ? additionalProRows.get() : additionalConRows.get();
		for (int i = 0; i < additionalRows; ++i) {
			table.addRow("");
		}

		bottom.bottom = table.render(document, 571, 12, bottom.bottom, 72, 10) - 5;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Heldenbrief", false, false, false, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		try {
			addBiographyTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

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

		if (showImage.get()) {
			try {
				addImageTable(document);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		try {
			addMoneyTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		try {
			addEnergiesTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		try {
			addAPTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		if (showPros.get()) {
			try {
				addProOrConTable(document, "Vorteil");
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showCons.get()) {
			try {
				addProOrConTable(document, "Nachteil");
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (showConnections.get()) {
			try {
				addConnectionsTable(document);
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
		settings.put("Zusätzliche Zeilen für Vorteile", additionalProRows.get());
		settings.put("Zusätzliche Zeilen für Nachteile", additionalConRows.get());
		settings.put("Zusätzliche Zeilen für Verbindungen", additionalConnectionRows.get());
		settings.put("Alter", showAge.get());
		settings.put("Geburtstag", showBirthday.get());
		settings.put("Bankguthaben", showBankMoney.get());
		settings.put("Astralenergie", showAsP.get());
		settings.put("Karmaenergie", showKaP.get());
		settings.put("Vorteile", showPros.get());
		settings.put("Nachteile", showCons.get());
		settings.put("Verbindungen", showConnections.get());
		settings.put("Bild anzeigen", showImage.get());
		if (image.get() != null) {
			settings.put("Bild", image.get().getAbsolutePath());
		}
		return settings;
	}

	@Override
	public void load() {
		super.load();
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Vorteile", additionalProRows, 0, 30);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Nachteile", additionalConRows, 0, 30);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Verbindungen", additionalConnectionRows, 0, 30);
		settingsPage.addBooleanChoice("Alter", showAge);
		settingsPage.addBooleanChoice("Geburtstag", showBirthday);
		settingsPage.addBooleanChoice("Bankguthaben", showBankMoney);
		settingsPage.addBooleanChoice("Astralenergie", showAsP);
		settingsPage.addBooleanChoice("Karmaenergie", showKaP);
		settingsPage.addBooleanChoice("Vorteile", showPros);
		settingsPage.addBooleanChoice("Nachteile", showCons);
		settingsPage.addBooleanChoice("Verbindungen", showConnections);
		settingsPage.addBooleanChoice("Bild anzeigen", showImage);
		final Button imageButton = settingsPage.addFileChoice("Bild", image, "*.jpg, *.png, *.gif", Arrays.asList("*.jpg", "*.png", "*.gif"));

		showImage.addListener((o, oldV, newV) -> {
			imageButton.setDisable(!newV);
		});
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		additionalProRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Vorteile", 5));
		additionalConRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Nachteile", 5));
		additionalConnectionRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Verbindungen", 5));
		showAge.set(settings.getBoolOrDefault("Alter", false));
		showBirthday.set(settings.getBoolOrDefault("Geburtstag", false));
		showBankMoney.set(settings.getBoolOrDefault("Bankguthaben", false));
		showAsP.set(settings.getBoolOrDefault("Astralenergie", HeroUtil.isMagical(hero)));
		showKaP.set(settings.getBoolOrDefault("Karmaenergie", HeroUtil.isClerical(hero, false)));
		showPros.set(settings.getBoolOrDefault("Vorteile", true));
		showCons.set(settings.getBoolOrDefault("Nachteile", true));
		showConnections.set(settings.getBoolOrDefault("Verbindungen", false));
		showImage.set(settings.getBoolOrDefault("Bild anzeigen", false));
		final String imagePath = settings.getString("Bild");
		if (imagePath != null) {
			image.set(new File(imagePath));
		} else {
			image.set(null);
		}
	}

	@Override
	public String toString() {
		return "Heldenbrief";
	}

}
