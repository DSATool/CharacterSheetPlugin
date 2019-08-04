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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.pdfbox.pdmodel.PDDocument;

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
import dsa41basis.util.DSAUtil.Units;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SpellsSheet extends Sheet {
	private final IntegerProperty additionalRows = new SimpleIntegerProperty(5);
	private final Map<String, BooleanProperty> representations = new HashMap<>();
	private final BooleanProperty spoMoTable = new SimpleBooleanProperty(true);
	private final BooleanProperty traitTable = new SimpleBooleanProperty(true);
	private final BooleanProperty targetTable = new SimpleBooleanProperty(true);

	private final float fontSize = 8;

	public SpellsSheet() {
		super(536);
		pageSize = SheetUtil.landscape;
	}

	private void addSpoMo(final Table table, final String name, final JSONObject variant, final JSONObject base) {
		table.addCells(name, base.getStringOrDefault("Abkürzung", ""));

		final JSONObject zfp = variant.getObjOrDefault("Erschwernis", base.getObjOrDefault("Erschwernis", null));
		final String zfpString = DSAUtil.getModificationString(zfp, Units.NONE, true);
		table.addCells(zfpString);

		final JSONObject duration = variant.getObjOrDefault("Zauberdauer", base.getObjOrDefault("Zauberdauer", null));
		String durationString = "";
		if (duration.containsKey("Multiplikativ")) {
			durationString = SheetUtil.threeDecimalPlacesSigned.format((duration.getDouble("Multiplikativ") - 1) * 100) + "%";
		} else {
			durationString = DSAUtil.getModificationString(duration, Units.TIME, true);
			if (durationString.charAt(0) != '+') {
				durationString = "+" + durationString;
			}
		}
		table.addCells(durationString);
	}

	@Override
	public boolean check() {
		return HeroUtil.isMagical(hero);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Zauberbrief", true, true, false, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		try {
			createTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		float left = 12;

		final float currentBottom = bottom.bottom;
		float minBottom = bottom.bottom;

		if (spoMoTable.get()) {
			bottom.bottom = bottom.bottom > currentBottom ? height : currentBottom;
			try {
				left = createSpoMoTable(document, left);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
			minBottom = Math.min(minBottom, bottom.bottom);
		}

		if (traitTable.get()) {
			bottom.bottom = bottom.bottom > currentBottom ? height : currentBottom;
			try {
				left = createTraitTable(document, left);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
			minBottom = Math.min(minBottom, bottom.bottom);
		}

		if (targetTable.get()) {
			bottom.bottom = bottom.bottom > currentBottom ? height : currentBottom;
			try {
				left = createTargetTable(document, left);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
			minBottom = Math.min(minBottom, bottom.bottom);
		}

		bottom.bottom = minBottom;

		endCreate(document);
	}

	private float createSpoMoTable(final PDDocument document, final float left) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(115, 115, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(55, 55, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, 40, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Spontane Modifikation", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);
		final Cell zfpTitle = SheetUtil.createTitleCell("ZfP-Kosten", 1);
		final Cell durationTitle = SheetUtil.createTitleCell("Zauberd.", 1);

		table.addRow(nameTitle, abbrevTitle, zfpTitle, durationTitle);

		final JSONObject spoMos = ResourceManager.getResource("data/Spontane_Modifikationen");

		for (final String spoMoName : spoMos.keySet()) {
			final JSONObject spoMo = spoMos.getObj(spoMoName);
			if (spoMo.containsKey("Varianten")) {
				final JSONObject variants = spoMo.getObj("Varianten");
				for (final String variantName : variants.keySet()) {
					addSpoMo(table, variantName, variants.getObj(variantName), spoMo);
				}
			} else {
				addSpoMo(table, spoMoName, spoMo, spoMo);
			}
		}

		bottom.bottom = table.render(document, 230, left, bottom.bottom, 59, 10) - 5;

		return left + 235;
	}

	private void createTable(final PDDocument document) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(80, 160, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(21, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(13, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(12, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(19, 19, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(57, 57, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(28, 28, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(19, 19, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(63, 63, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(59, 59, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(29, 29, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(21, 21, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(46, 46, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(49, 49, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(52, 52, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, fontSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Zauber", 1);
		final Cell valueTitle = SheetUtil.createTitleCell("ZfW", 1);
		final Cell seTitle = SheetUtil.createTitleCell("SE", 1);
		final Cell complexityTitle = SheetUtil.createTitleCell("K", 1);
		final Cell repTitle = SheetUtil.createTitleCell("Rep", 1);
		final Cell challengeTitle = SheetUtil.createTitleCell("Probe", 1);
		final Cell traitTitle = SheetUtil.createTitleCell("Merk.", 1).setHAlign(HAlign.CENTER);
		final Cell revTitle = SheetUtil.createTitleCell("Rev", 1);
		final Cell specialisationTitle = SheetUtil.createTitleCell("Spez.(+2)", 1).setHAlign(HAlign.CENTER);
		final Cell modTitle = SheetUtil.createTitleCell("Spont. Mod.", 1);
		final Cell rangeTitle = SheetUtil.createTitleCell("RW", 1);
		final Cell targetTitle = SheetUtil.createTitleCell("ZO", 1);
		final Cell costTitle = SheetUtil.createTitleCell("Kosten", 1);
		final Cell castTimeTitle = SheetUtil.createTitleCell("Zauberd.", 1);
		final Cell durationTitle = SheetUtil.createTitleCell("Wirkungsd.", 1);
		final Cell descTitle = SheetUtil.createTitleCell("Beschreibung", 1);

		table.addRow(nameTitle, valueTitle, seTitle, complexityTitle, repTitle, challengeTitle, traitTitle, revTitle, specialisationTitle, modTitle, rangeTitle,
				targetTitle, costTitle, castTimeTitle, durationTitle, descTitle);

		final JSONObject talents = ResourceManager.getResource("data/Zauber");
		final JSONObject actualSpells = hero == null ? null : hero.getObjOrDefault("Zauber", null);

		final Map<String, JSONObject> spells = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
		for (final String spellName : talents.keySet()) {
			spells.put(spellName, talents.getObj(spellName));
		}

		for (final String spellName : spells.keySet()) {
			final JSONObject spell = spells.get(spellName);
			final JSONObject spellRepresentations = spell.getObj("Repräsentationen");
			if (spell.containsKey("Auswahl") || spell.containsKey("Freitext")) {
				if (actualSpells != null && actualSpells.containsKey(spellName)) {
					final JSONObject actualSpell = actualSpells.getObj(spellName);
					final Map<String, JSONArray> orderedRepresentations = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
					for (final String representation : spellRepresentations.keySet()) {
						if (actualSpell.containsKey(representation)) {
							orderedRepresentations.put(representation, actualSpell.getArr(representation));
						}
					}
					for (final String representation : orderedRepresentations.keySet()) {
						final JSONArray choiceSpell = orderedRepresentations.get(representation);
						for (int i = 0; i < choiceSpell.size(); ++i) {
							fillSpell(table, spellName, spell, representation, choiceSpell.getObj(i));
						}
					}
				}
			} else {
				final Map<String, JSONObject> orderedRepresentations = new TreeMap<>((s1, s2) -> SheetUtil.comparator.compare(s1, s2));
				if (actualSpells != null && actualSpells.containsKey(spellName)) {
					final JSONObject actualSpell = actualSpells.getObj(spellName);
					for (final String representation : spellRepresentations.keySet()) {
						if (actualSpell.containsKey(representation)) {
							orderedRepresentations.put(representation, actualSpell.getObj(representation));
						} else {
							for (final String knownRepresentation : spellRepresentations.getObj(representation)
									.getObjOrDefault("Verbreitung", new JSONObject(null)).keySet()) {
								if (representations.get(knownRepresentation).get()) {
									orderedRepresentations.put(representation, null);
									break;
								}
							}
						}
					}
				} else {
					for (final String representation : spellRepresentations.keySet()) {
						for (final String knownRepresentation : spellRepresentations.getObj(representation).getObjOrDefault("Verbreitung", new JSONObject(null))
								.keySet()) {
							if (representations.get(knownRepresentation).get()) {
								orderedRepresentations.put(representation, null);
								break;
							}
						}
					}
				}
				for (final String representation : orderedRepresentations.keySet()) {
					fillSpell(table, spellName, spell, representation, orderedRepresentations.get(representation));
				}
			}
		}

		for (int i = 0; i < additionalRows.get(); ++i) {
			table.addRow("");
		}

		bottom.bottom = table.render(document, 818, 12, bottom.bottom, 59, 10) - 5;
	}

	private float createTargetTable(final PDDocument document, final float left) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(90, 90, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Zielobjekt", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);

		table.addRow(nameTitle, abbrevTitle);

		final JSONObject targets = ResourceManager.getResource("data/Zielobjekte");

		for (final String targetName : targets.keySet()) {
			table.addRow(targetName, targets.getObj(targetName).getStringOrDefault("Abkürzung", ""));
		}

		bottom.bottom = table.render(document, 110, left, bottom.bottom, 59, 10) - 5;

		return left + 115;
	}

	private float createTraitTable(final PDDocument document, final float left) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(90, 90, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER).setBorder(0.25f, 0.25f, 0.5f, 0.25f));
		table.addColumn(new Column(90, 90, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.25f, 0.5f, 0.25f, 0.25f));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER).setBorder(0.25f, 0.25f, 0.5f, 0.25f));
		table.addColumn(new Column(90, 90, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.25f, 0.5f, 0.25f, 0.25f));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Merkmal", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);

		table.addRow(nameTitle, abbrevTitle, nameTitle, abbrevTitle, nameTitle, abbrevTitle);

		final JSONObject traits = ResourceManager.getResource("data/Merkmale");

		final List<Tuple<String, String>> rows = new ArrayList<>();

		for (final String traitName : traits.keySet()) {
			rows.add(new Tuple<>(traitName, traits.getObj(traitName).getStringOrDefault("Abkürzung", "")));
		}
		for (int i = 0; i < rows.size() % 3; ++i) {
			rows.add(new Tuple<>("", ""));
		}

		final int height = rows.size() / 3;
		for (int i = 0; i < height; ++i) {
			final Tuple<String, String> leftTrait = rows.get(i);
			final Tuple<String, String> midTrait = rows.get(i + height);
			final Tuple<String, String> rightTrait = rows.get(i + 2 * height);
			table.addRow(leftTrait._1, leftTrait._2, midTrait._1, midTrait._2, rightTrait._1, rightTrait._2);
		}

		bottom.bottom = table.render(document, 330, left, bottom.bottom, 59, 10) - 5;

		return left + 335;
	}

	private void fillSpell(final Table table, String name, final JSONObject baseSpell, final String actualRepresentation, final JSONObject actualSpell) {
		final JSONObject spell = baseSpell.getObj("Repräsentationen").getObjOrDefault(actualRepresentation, baseSpell);

		String value = " ";
		String se = " ";

		if (actualSpell != null && fillAll) {
			if (actualSpell.getBoolOrDefault("aktiviert", true)) {
				value = actualSpell.getIntOrDefault("ZfW", 0).toString();
			}
			final int ses = actualSpell.getIntOrDefault("SEs", 0);
			if (ses != 0) {
				if (ses == 1) {
					se = "X";
				} else if (ses == 2) {
					se = "XX";
				} else {
					se = Integer.toString(ses);
				}
			}
		}

		final Cell complexity = new TextCell(hero != null && fill
				? DSAUtil.getEnhancementGroupString(HeroUtil.getSpellComplexity(hero, name, actualRepresentation, Integer.MAX_VALUE)) : " ").setPadding(0, 1, 1,
						0);

		final String challenge = DSAUtil.getChallengeString(spell.getArrOrDefault("Probe", baseSpell.getArr("Probe")));

		final TextCell traitString = new TextCell();
		final JSONObject traits = ResourceManager.getResource("data/Merkmale");
		final JSONArray actualTraits = spell.getArrOrDefault("Merkmale", baseSpell.getArrOrDefault("Merkmale", null));
		final JSONArray knownTraits = hero != null && fill ? hero.getObj("Sonderfertigkeiten").getArrOrDefault("Merkmalskenntnis", null) : null;
		final JSONArray conTraits = hero != null && fill ? hero.getObj("Nachteile").getArrOrDefault("Unfähigkeit für Merkmal", null) : null;
		if (actualTraits != null) {
			for (final String traitName : traits.keySet()) {
				for (int i = 0; i < actualTraits.size(); ++i) {
					if (traitName.equals(actualTraits.getString(i))) {
						final Text current = new Text(traits.getObj(traitName).getStringOrDefault("Abkürzung", "X"));
						traitString.addText(current);
						if (knownTraits != null) {
							for (int j = 0; j < knownTraits.size(); ++j) {
								if (traitName.equals(knownTraits.getObj(j).getString("Auswahl"))) {
									current.setUnderlined(true);
									break;
								}
							}
						}
						if (conTraits != null) {
							for (int j = 0; j < conTraits.size(); ++j) {
								if (traitName.equals(conTraits.getObj(j).getString("Auswahl"))) {
									current.setStriked(true);
									break;
								}
							}
						}
					}
				}
			}
		}

		final JSONObject rev = spell.getObjOrDefault("Reversalis", baseSpell.getObj("Reversalis"));
		String revString = "Nein";
		if (rev.getBoolOrDefault("Wirkung", false) && rev.getBoolOrDefault("Bannung", false)) {
			revString = "Ja/Bann";
		} else if (rev.getBoolOrDefault("Wirkung", false)) {
			revString = "Ja";
		} else if (rev.getBoolOrDefault("Bannung", false)) {
			revString = "Bann";
		}

		final JSONObject spoMos = ResourceManager.getResource("data/Spontane_Modifikationen");
		final StringBuilder specString = new StringBuilder();
		final List<String> spoMoSpecs = new ArrayList<>();

		final JSONObject skills = hero == null ? null : hero.getObj("Sonderfertigkeiten");
		if (hero != null && fill && skills.containsKey("Zauberspezialisierung")) {
			boolean first = true;
			final JSONArray specialisations = skills.getArr("Zauberspezialisierung");
			if (specialisations != null) {
				for (int i = 0; i < specialisations.size(); ++i) {
					final JSONObject specialisation = specialisations.getObj(i);
					if (name.equals(specialisation.getString("Auswahl"))) {
						final String spec = specialisation.getStringOrDefault("Freitext", "");
						if (spoMos.containsKey(spec)) {
							spoMoSpecs.add(spec);
							continue;
						}
						if (first) {
							first = false;
						} else {
							specString.append(", ");
						}
						specString.append(spec);
					}
				}
			}
		}

		final TextCell spoMoString = new TextCell();
		final JSONArray actualSpoMos = spell.getArrOrDefault("Spontane Modifikationen", baseSpell.getArrOrDefault("Spontane Modifikationen", null));
		if (actualSpoMos != null) {
			for (final String spoMoName : spoMos.keySet()) {
				for (int j = 0; j < actualSpoMos.size(); ++j) {
					if (spoMoName.equals(actualSpoMos.getString(j))) {
						final Text current = new Text(spoMos.getObj(spoMoName).getStringOrDefault("Abkürzung", ""));
						spoMoString.addText(current);
						if (spoMoSpecs.contains(spoMoName)) {
							current.setUnderlined(true);
						}
					}
				}
			}
		}

		final String range = DSAUtil.getModificationString(spell.getObjOrDefault("Reichweite", baseSpell.getObjOrDefault("Reichweite", null)), Units.RANGE,
				false);
		final String target = SheetUtil.getTargetObjectsString(spell.getArrOrDefault("Zielobjekt", baseSpell.getArrOrDefault("Zielobjekt", null)));
		final String cost = DSAUtil.getModificationString(spell.getObjOrDefault("Kosten", baseSpell.getObjOrDefault("Kosten", null)), Units.NONE, false);
		final String castTime = DSAUtil.getModificationString(spell.getObjOrDefault("Zauberdauer", baseSpell.getObjOrDefault("Zauberdauer", null)), Units.TIME,
				false);
		final String effectTime = DSAUtil.getModificationString(spell.getObjOrDefault("Wirkungsdauer", baseSpell.getObjOrDefault("Wirkungsdauer", null)),
				Units.TIME, false);
		final String description = spell.getStringOrDefault("Beschreibung:Kurz", baseSpell.getStringOrDefault("Beschreibung:Kurz", ""));

		if (baseSpell.containsKey("Auswahl")) {
			name = name + ": " + actualSpell.getStringOrDefault("Auswahl", "");
		} else if (baseSpell.containsKey("Freitext")) {
			name = name + ": " + actualSpell.getStringOrDefault("Freitext", "");
		}

		table.addRow(name, value, se, complexity, actualRepresentation, challenge, traitString, revString, specString, spoMoString, range, target, cost,
				castTime, effectTime, description);
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = new JSONObject(parent);
		settings.put("Als eigenständigen Bogen drucken", separatePage.get());
		final JSONArray reps = new JSONArray(settings);
		for (final String name : representations.keySet()) {
			if (representations.get(name).get()) {
				reps.add(name);
			}
		}
		settings.put("Repräsentationen", reps);
		settings.put("Zusätzliche Zeilen für Zauber", additionalRows.get());
		settings.put("Spontane Modifikationen", spoMoTable.get());
		settings.put("Merkmale", traitTable.get());
		settings.put("Zielobjekte", targetTable.get());
		return settings;
	}

	@Override
	public void load() {
		super.load();
		final JSONObject representationNames = ResourceManager.getResource("data/Repraesentationen");
		for (final String representationName : representationNames.keySet()) {
			representations.put(representationName, new SimpleBooleanProperty(false));
			final JSONObject representation = representationNames.getObj(representationName);
			settingsPage.addBooleanChoice("Repräsentation " + representation.getStringOrDefault("Name", "unbekannt"), representations.get(representationName));
		}
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Zauber", additionalRows, 0, 60);
		settingsPage.addBooleanChoice("Spontane Modifikationen", spoMoTable);
		settingsPage.addBooleanChoice("Merkmale", traitTable);
		settingsPage.addBooleanChoice("Zielobjekte", targetTable);
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		final JSONArray reps = settings.getArrOrDefault("Repräsentationen", new JSONArray(null));
		for (final String name : representations.keySet()) {
			representations.get(name).set(reps.contains(name));
		}
		additionalRows.set(settings.getIntOrDefault("Zusätzliche Zeilen für Zauber", 5));
		spoMoTable.set(settings.getBoolOrDefault("Spontane Modifikationen", true));
		traitTable.set(settings.getBoolOrDefault("Merkmale", true));
		targetTable.set(settings.getBoolOrDefault("Zielobjekte", true));
	}

	@Override
	public String toString() {
		return "Zauberbrief";
	}
}
