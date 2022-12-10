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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import dsatool.util.Tuple3;
import dsatool.util.Tuple4;
import javafx.scene.control.TitledPane;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SpellsSheet extends Sheet {

	private static final String ADDITIONAL_SPELL_ROWS = "Zusätzliche Zeilen";

	private static final String OWN_MODS_ONLY = "Nur nötige";

	private static final List<String> additionalTables = List.of("Merkmale", "Zielobjekte", "Spontane Modifikationen");

	private final float fontSize = 8f;

	public SpellsSheet() {
		super(536);
		pageSize = SheetUtil.landscape;
	}

	@Override
	public boolean check() {
		return HeroUtil.isMagical(hero);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		header = SheetUtil.createHeader("Zauberbrief", true, true, false, hero, fill, fillAll, showName, showDate);

		startCreate(document);

		Tuple3<Set<String>, Set<String>, Set<String>> ownMods = null;

		try {
			ownMods = createTable(document);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
			endCreate(document);
			return;
		}

		float left = 12;

		final float currentBottom = bottom.bottom;
		float minBottom = bottom.bottom;

		for (final TitledPane section : settingsPage.getSections()) {
			if (settingsPage.getBool(section, "").get()) {
				bottom.bottom = bottom.bottom > currentBottom ? height : currentBottom;
				final boolean ownOnly = settingsPage.getBool(section, OWN_MODS_ONLY).get();
				try {
					left = switch (settingsPage.getString(section, null).get()) {
						case "Merkmale" -> createTraitTable(document, left, ownOnly, ownMods._1);
						case "Zielobjekte" -> createTargetTable(document, left, ownOnly, ownMods._2);
						case "Spontane Modifikationen" -> createSpoMoTable(document, left, ownOnly, ownMods._3);
						default -> left;
					};
				} catch (final Exception e) {
					ErrorLogger.logError(e);
				}
				minBottom = Math.min(minBottom, bottom.bottom);
			}
		}

		bottom.bottom = minBottom;

		endCreate(document);
	}

	private float createSpoMoTable(final PDDocument document, final float left, final boolean ownOnly, final Set<String> ownSpoMos) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(114, 114, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, 40, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, 40, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(114, 114, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, 40, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(40, 40, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Spontane Modifikation", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);
		final Cell zfpTitle = SheetUtil.createTitleCell("Probe", 1);
		final Cell durationTitle = SheetUtil.createTitleCell("Zauberd.", 1);

		table.addRow(nameTitle, abbrevTitle, zfpTitle, durationTitle, nameTitle, abbrevTitle, zfpTitle, durationTitle);

		final JSONObject spoMos = ResourceManager.getResource("data/Spontane_Modifikationen");

		final List<Tuple4<String, String, String, String>> rows = new ArrayList<>();

		for (final String spoMoName : spoMos.keySet()) {
			final JSONObject spoMo = spoMos.getObj(spoMoName);
			if (!ownOnly || ownSpoMos.contains(spoMoName)) {
				if (spoMo.containsKey("Varianten")) {
					final JSONObject variants = spoMo.getObj("Varianten");
					for (final String variantName : variants.keySet()) {
						rows.add(getSpoMo(table, variantName, variants.getObj(variantName), spoMo));
					}
				} else {
					rows.add(getSpoMo(table, spoMoName, spoMo, spoMo));
				}
			}
		}
		for (int i = 0; i < rows.size() % 2; ++i) {
			rows.add(new Tuple4<>("", "", "", ""));
		}

		final int height = rows.size() / 2;
		for (int i = 0; i < height; ++i) {
			final Tuple4<String, String, String, String> leftSpoMo = rows.get(i);
			final Tuple4<String, String, String, String> rightSpoMo = rows.get(i + height);
			table.addRow(leftSpoMo._1, leftSpoMo._2, leftSpoMo._3, leftSpoMo._4, rightSpoMo._1, rightSpoMo._2, rightSpoMo._3, rightSpoMo._4);
		}

		bottom.bottom = table.render(document, 428, left, bottom.bottom, 59, 10) - 5;

		return left + 433;
	}

	private Tuple3<Set<String>, Set<String>, Set<String>> createTable(final PDDocument document) throws IOException {
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

		final Set<String> ownTraits = new HashSet<>();
		final Set<String> ownTargets = new HashSet<>();
		final Set<String> ownSpoMos = new HashSet<>();

		final JSONObject talents = ResourceManager.getResource("data/Zauber");
		final JSONObject actualSpells = hero == null ? null : hero.getObjOrDefault("Zauber", null);

		final Map<String, JSONObject> spells = new TreeMap<>(SheetUtil.comparator);
		for (final String spellName : talents.keySet()) {
			spells.put(spellName, talents.getObj(spellName));
		}

		final JSONObject representationNames = ResourceManager.getResource("data/Repraesentationen");

		for (final String spellName : spells.keySet()) {
			final JSONObject spell = spells.get(spellName);
			final JSONObject spellRepresentations = spell.getObj("Repräsentationen");
			if (spell.containsKey("Auswahl") || spell.containsKey("Freitext")) {
				if (actualSpells != null && actualSpells.containsKey(spellName)) {
					final JSONObject actualSpell = actualSpells.getObj(spellName);
					final Map<String, JSONArray> orderedRepresentations = new TreeMap<>(SheetUtil.comparator);
					for (final String representation : spellRepresentations.keySet()) {
						if (actualSpell.containsKey(representation)) {
							orderedRepresentations.put(representation, actualSpell.getArr(representation));
						}
					}
					for (final String representation : orderedRepresentations.keySet()) {
						final JSONArray choiceSpell = orderedRepresentations.get(representation);
						for (int i = 0; i < choiceSpell.size(); ++i) {
							final Tuple3<Set<String>, Set<String>, Set<String>> mods = fillSpell(table, spellName, spell, representation,
									choiceSpell.getObj(i));
							ownTraits.addAll(mods._1);
							ownTargets.addAll(mods._2);
							ownSpoMos.addAll(mods._3);
						}
					}
				}
			} else {
				final Map<String, JSONObject> orderedRepresentations = new TreeMap<>(SheetUtil.comparator);
				if (actualSpells != null && actualSpells.containsKey(spellName)) {
					final JSONObject actualSpell = actualSpells.getObj(spellName);
					for (final String representation : spellRepresentations.keySet()) {
						if (actualSpell.containsKey(representation)) {
							orderedRepresentations.put(representation, actualSpell.getObj(representation));
						} else {
							for (final String knownRepresentation : spellRepresentations.getObj(representation)
									.getObjOrDefault("Verbreitung", new JSONObject(null)).keySet()) {
								if (settingsPage.getBool(
										"Repräsentation " + representationNames.getObj(knownRepresentation).getStringOrDefault("Name", knownRepresentation))
										.get()) {
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
							if (settingsPage
									.getBool(
											"Repräsentation " + representationNames.getObj(knownRepresentation).getStringOrDefault("Name", knownRepresentation))
									.get()) {
								orderedRepresentations.put(representation, null);
								break;
							}
						}
					}
				}
				for (final String representation : orderedRepresentations.keySet()) {
					final Tuple3<Set<String>, Set<String>, Set<String>> mods = fillSpell(table, spellName, spell, representation,
							orderedRepresentations.get(representation));
					ownTraits.addAll(mods._1);
					ownTargets.addAll(mods._2);
					ownSpoMos.addAll(mods._3);
				}
			}
		}

		for (int i = 0; i < settingsPage.getInt(ADDITIONAL_SPELL_ROWS).get(); ++i) {
			table.addRow("");
		}

		bottom.bottom = table.render(document, 818, 12, bottom.bottom, 59, 10) - 5;

		return new Tuple3<>(ownTraits, ownTargets, ownSpoMos);
	}

	private float createTargetTable(final PDDocument document, final float left, final boolean ownOnly, final Set<String> ownTargets) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(90, 90, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Zielobjekt", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);

		table.addRow(nameTitle, abbrevTitle);

		final JSONObject targets = ResourceManager.getResource("data/Zielobjekte");

		for (final String targetName : targets.keySet()) {
			if (!ownOnly || ownTargets.contains(targetName)) {
				table.addRow(targetName, targets.getObj(targetName).getStringOrDefault("Abkürzung", ""));
			}
		}

		bottom.bottom = table.render(document, 110, left, bottom.bottom, 59, 10) - 5;

		return left + 115;
	}

	private float createTraitTable(final PDDocument document, final float left, final boolean ownOnly, final Set<String> ownTraits) throws IOException {
		final Table table = new Table().setFiller(SheetUtil.stripe());
		table.addEventHandler(EventType.BEGIN_PAGE, header);

		table.addColumn(new Column(70, 70, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER).setBorder(0.25f, 0.25f, 0.5f, 0.25f));
		table.addColumn(new Column(70, 70, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.25f, 0.5f, 0.25f, 0.25f));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER).setBorder(0.25f, 0.25f, 0.5f, 0.25f));
		table.addColumn(new Column(70, 70, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.25f, 0.5f, 0.25f, 0.25f));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, fontSize, HAlign.CENTER));

		final Cell nameTitle = SheetUtil.createTitleCell("Merkmal", 1);
		final Cell abbrevTitle = ((TextCell) SheetUtil.createTitleCell("Abk.", 1)).setPadding(0, 0, 0, 0);

		table.addRow(nameTitle, abbrevTitle, nameTitle, abbrevTitle, nameTitle, abbrevTitle);

		final JSONObject traits = ResourceManager.getResource("data/Merkmale");

		final List<Tuple<String, String>> rows = new ArrayList<>();

		for (final String traitName : traits.keySet()) {
			if (!ownOnly || ownTraits.contains(traitName)) {
				final String nameString = traitName.replace("Dämonisch", "Däm.");
				rows.add(new Tuple<>(nameString, traits.getObj(traitName).getStringOrDefault("Abkürzung", "")));
			}
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

		bottom.bottom = table.render(document, 270, left, bottom.bottom, 59, 10) - 5;

		return left + 275;
	}

	private Tuple3<Set<String>, Set<String>, Set<String>> fillSpell(final Table table, String name, final JSONObject baseSpell,
			final String actualRepresentation, final JSONObject actualSpell) {
		final JSONObject spell = baseSpell.getObj("Repräsentationen").getObjOrDefault(actualRepresentation, baseSpell);

		final Set<String> ownTraits = new HashSet<>();
		final Set<String> ownTargets = new HashSet<>();
		final Set<String> ownSpoMos = new HashSet<>();

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
						ownTraits.add(traitName);
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
						ownSpoMos.add(spoMoName);
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
		final JSONArray targets = spell.getArrOrDefault("Zielobjekt", baseSpell.getArrOrDefault("Zielobjekt", null));
		final String target = SheetUtil.getTargetObjectsString(targets);
		ownTargets.addAll(targets.getStrings());
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

		return new Tuple3<>(ownTraits, ownTargets, ownSpoMos);
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);
		final JSONArray reps = new JSONArray(settings);
		final JSONObject representationNames = ResourceManager.getResource("data/Repraesentationen");
		for (final String representationName : representationNames.keySet()) {
			if (settingsPage.getBool("Repräsentation " + representationNames.getObj(representationName).getStringOrDefault("Name", representationName)).get()) {
				reps.add(representationName);
			}
		}
		settings.put("Repräsentationen", reps);
		settings.put(ADDITIONAL_SPELL_ROWS, settingsPage.getInt(ADDITIONAL_SPELL_ROWS).get());

		for (final TitledPane section : settingsPage.getSections()) {
			settings.put(settingsPage.getString(section, null).get(), settingsPage.getBool(section, "").get());
			settings.put(settingsPage.getString(section, null).get() + ":Eigene", settingsPage.getBool(section, OWN_MODS_ONLY).get());
		}

		return settings;
	}

	private Tuple4<String, String, String, String> getSpoMo(final Table table, final String name, final JSONObject variant, final JSONObject base) {
		final String abbreviation = base.getStringOrDefault("Abkürzung", "");

		final JSONObject zfp = variant.getObjOrDefault("Erschwernis", base.getObjOrDefault("Erschwernis", null));
		final String zfpString = DSAUtil.getModificationString(zfp, Units.NONE, true);

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

		return new Tuple4<>(name, abbreviation, zfpString, durationString);
	}

	@Override
	public void load() {
		super.load();
		final JSONObject representationNames = ResourceManager.getResource("data/Repraesentationen");
		for (final String representationName : representationNames.keySet()) {
			settingsPage.addBooleanChoice("Repräsentation " + representationNames.getObj(representationName).getStringOrDefault("Name", representationName));
		}
		settingsPage.addIntegerChoice(ADDITIONAL_SPELL_ROWS, 0, 60);

		for (final String sectionName : additionalTables) {
			sections.put(sectionName, settingsPage.addSection(sectionName, true));
			settingsPage.addBooleanChoice(OWN_MODS_ONLY);
		}
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);
		final JSONArray reps = settings.getArrOrDefault("Repräsentationen", new JSONArray(null));
		final JSONObject representationNames = ResourceManager.getResource("data/Repraesentationen");
		for (final String representationName : representationNames.keySet()) {
			settingsPage.getBool("Repräsentation " + representationNames.getObj(representationName).getStringOrDefault("Name", representationName))
					.set(reps.contains(representationName));
		}
		settingsPage.getInt(ADDITIONAL_SPELL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_SPELL_ROWS, 5));

		orderSections(additionalTables);
		orderSections(settings.keySet());

		for (final String sectionName : additionalTables) {
			final TitledPane section = sections.get(sectionName);
			settingsPage.getBool(section, "").set(settings.getBoolOrDefault(sectionName, true));
			settingsPage.getBool(section, OWN_MODS_ONLY).set(settings.getBoolOrDefault(sectionName + ":Eigene", false));
		}
	}

	@Override
	public String toString() {
		return "Zauberbrief";
	}
}
