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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.pdfbox.pdmodel.PDDocument;

import boxtable.cell.Cell;
import boxtable.cell.ImageCell;
import boxtable.cell.TableCell;
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
import dsa41basis.util.DSAUtil.Units;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.settings.SettingsPage;
import dsatool.util.ErrorLogger;
import dsatool.util.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class AnimalSheet extends Sheet {
	private static final float valueSize = 10.5f;
	private static final float fontSize = 9;
	private static final List<String> sheetTypes = Arrays.asList("Kein", "Vertrautentier", "Reittier", "Tier");

	@FXML
	private VBox animalsBox;

	private final List<Node> animalControls = new ArrayList<>();
	private final List<StringProperty> types = new ArrayList<>();
	private final List<IntegerProperty> additionalAttacks = new ArrayList<>();
	private final List<IntegerProperty> additionalProConRows = new ArrayList<>();
	private final List<IntegerProperty> additionalArmorRows = new ArrayList<>();
	private final List<IntegerProperty> additionalInventoryRows = new ArrayList<>();
	private final List<BooleanProperty> ownSkillsOnly = new ArrayList<>();
	private final List<BooleanProperty> showProsCons = new ArrayList<>();
	private final List<BooleanProperty> showArmor = new ArrayList<>();
	private final List<BooleanProperty> showSkills = new ArrayList<>();
	private final List<BooleanProperty> showInventory = new ArrayList<>();

	private JSONArray animals = null;

	private JSONObject animal = null;

	private boolean isMagical;
	private boolean isHorse;
	private int current = 0;

	public AnimalSheet() {
		super(788);

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("Animals.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}
	}

	public void addAnimal(final ActionEvent event) {
		final String type = switch (((Button) event.getSource()).getText().charAt(0)) {
			case 'V' -> "Vertrautentier";
			case 'R' -> "Reittier";
			default -> "Tier";
		};
		addAnimal(type, new JSONObject(null));
	}

	public TitledPane addAnimal(final String type, final JSONObject settings) {
		final TitledPane control = new TitledPane();
		animalControls.add(control);
		animalsBox.getChildren().add(animalsBox.getChildren().size() - 1, control);
		final SettingsPage settingsPage = new SettingsPage();
		control.setContent(settingsPage.getControl());
		control.setText("Weiteres Tier");
		final StringProperty typeProperty = new SimpleStringProperty(settings.getStringOrDefault("Typ", type));
		types.add(typeProperty);
		settingsPage.addStringChoice("Typ", typeProperty, sheetTypes);
		final IntegerProperty attacks = new SimpleIntegerProperty(settings.getIntOrDefault("Zusätzliche Zeilen für Angriffe", 2));
		additionalAttacks.add(attacks);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Angriffe", attacks, 0, 10);
		final BooleanProperty proCons = new SimpleBooleanProperty(settings.getBoolOrDefault("Vor-/Nachteile", true));
		showProsCons.add(proCons);
		settingsPage.addBooleanChoice("Vor-/Nachteile", proCons);
		final IntegerProperty numProCons = new SimpleIntegerProperty(
				settings.getIntOrDefault("Zusätzliche Zeilen für Vor-/Nachteile", "Vertrautentier".equals(type) ? 2 : 3));
		additionalProConRows.add(numProCons);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Vor-/Nachteile", numProCons, 0, 20);
		final BooleanProperty armor = new SimpleBooleanProperty(settings.getBoolOrDefault("Rüstung", "Reittier".equals(type)));
		showArmor.add(armor);
		settingsPage.addBooleanChoice("Rüstung", armor);
		final IntegerProperty numArmor = new SimpleIntegerProperty(settings.getIntOrDefault("Zusätzliche Zeilen für Rüstung", 3));
		additionalArmorRows.add(numArmor);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Rüstung", numArmor, 0, 20);
		final BooleanProperty skills = new SimpleBooleanProperty(settings.getBoolOrDefault("Fertigkeiten", true));
		showSkills.add(skills);
		settingsPage.addBooleanChoice("Fertigkeiten", skills);
		final BooleanProperty ownSkills = new SimpleBooleanProperty(settings.getBoolOrDefault("Nur erlernbare Fertigkeiten", true));
		ownSkillsOnly.add(ownSkills);
		settingsPage.addBooleanChoice("Nur erlernbare Fertigkeiten", ownSkills);
		final BooleanProperty inventory = new SimpleBooleanProperty(settings.getBoolOrDefault("Inventar", true));
		showInventory.add(inventory);
		settingsPage.addBooleanChoice("Inventar", inventory);
		final IntegerProperty numInventory = new SimpleIntegerProperty(settings.getIntOrDefault("Zusätzliche Zeilen für Inventar", 40));
		additionalInventoryRows.add(numInventory);
		settingsPage.addIntegerChoice("Zusätzliche Zeilen für Inventar", numInventory, 0, 200);

		final ContextMenu menu = new ContextMenu();
		final MenuItem removeItem = new MenuItem("Entfernen");
		removeItem.setOnAction(e -> {
			final int index = animalControls.indexOf(control);
			animalControls.remove(index);
			animalsBox.getChildren().remove(index);
			types.remove(index);
			additionalAttacks.remove(index);
			showProsCons.remove(index);
			additionalProConRows.remove(index);
			showArmor.remove(index);
			additionalArmorRows.remove(index);
			showSkills.remove(index);
			ownSkillsOnly.remove(index);
			showInventory.remove(index);
			additionalInventoryRows.remove(index);
		});
		menu.getItems().add(removeItem);
		control.setContextMenu(menu);

		return control;
	}

	private void addAnimalTable(final PDDocument document) throws IOException {
		final Table baseTable = new Table();
		baseTable.addEventHandler(EventType.BEGIN_PAGE, header);
		baseTable.addColumn(new Column(267, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
		baseTable.addColumn(new Column(304, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));

		final String type = types.get(current).get();
		SheetUtil.addTitle(baseTable, type);

		animal = animals == null ? null : current < animals.size() ? animals.getObj(current) : null;
		isMagical = "Vertrautentier".equals(type);
		isHorse = "Reittier".equals(type);

		baseTable.addRow(new TableCell(getBiographyTable()).setColSpan(2));

		final Table rightTable = new Table().setBorder(0, 0, 0, 0);
		rightTable.addColumn(new Column(304, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		rightTable.addRow(emptyDesc);
		if (isMagical) {
			rightTable.addRow(new TableCell(getAPRkWTable()));
			rightTable.addRow("");
		}
		if (showProsCons.get(current).get()) {
			rightTable.addRow(new TableCell(getProsConsTable()));
			rightTable.addRow("");
		}
		rightTable.addRow(new TableCell(getAttacksTable()));
		rightTable.addRow("");
		if (showArmor.get(current).get()) {
			rightTable.addRow(new TableCell(getHorseArmorTable()));
			rightTable.addRow("");
		}

		final Table leftTable = new Table().setBorder(0, 0, 0, 0);
		leftTable.addColumn(new Column(102, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
		leftTable.addColumn(new Column(158, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
		leftTable.addColumn(new Column(7, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
		if (isHorse) {
			leftTable.addRow(new TableCell(getHorseStatsTable()).setColSpan(2));
			leftTable.addRow(new ImageCell(new File(Util.getAppDir() + "/resources/images/zones/animals/Pferd.jpg")).setColSpan(2).setHAlign(HAlign.CENTER)
					.setVAlign(VAlign.TOP).setMinHeight(rightTable.getHeight(304) - 86).setPadding(1, 0, 0, 0)).setBorder(0, 0, 0, 0);
			leftTable.addRow(new TextCell(" ", FontManager.serif, 0.5f, 0.5f).setColSpan(3));
		} else {
			leftTable.addRow(new TableCell(getAttributesTable()), new TableCell(getBaseValuesTable()));
			leftTable.addRow("");
		}

		baseTable.addRow(new TableCell(leftTable), new TableCell(rightTable));

		if (isMagical) {
			baseTable.addRow(new TableCell(getRitualsTable()).setColSpan(2));
			baseTable.addRow("");
		}

		if (showSkills.get(current).get()) {
			baseTable.addRow(new TableCell(isHorse ? getHorseSkillsTable() : getSkillsTable()).setColSpan(2));
			baseTable.addRow("");
		}

		if (showInventory.get(current).get()) {
			baseTable.addRow(new TableCell(getInventoryTable()).setColSpan(2));
		}

		bottom.bottom = baseTable.render(document, 571, 12, bottom.bottom, 54, 10) - 5;
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (types.size() > 0) {
			header = SheetUtil.createHeader("Tierbrief", true, false, false, hero, fill, fillAll, showName, showDate);

			startCreate(document);

			for (int i = 0; i < types.size(); ++i) {
				if (!"Kein".equals(types.get(i).get())) {
					current = i;
					try {
						addAnimalTable(document);
					} catch (final Exception e) {
						ErrorLogger.logError(e);
					}
				}
			}

			endCreate(document);
		}
	}

	private Table getAPRkWTable() {
		final Table table = new Table().setBorder(0, 0, 0, 0).setNumHeaderRows(0);

		table.addColumn(new Column(17, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(45, 45, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(47, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(45, 45, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(48, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(45, 45, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(28, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(29, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 0, 1));

		final JSONObject bio = animal != null ? animal.getObj("Biografie") : null;
		final JSONObject baseValues = animal != null ? animal.getObj("Basiswerte") : null;

		table.addCells("AP ", fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte", 0) : "");
		table.addCells("Investiert ",
				fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte", 0) - bio.getIntOrDefault("Abenteuerpunkte-Guthaben", 0) : "");
		table.addCells("Guthaben ", fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte-Guthaben", 0) : "");
		table.addCells("RkW", fillAll && baseValues != null ? baseValues.getObj("Ritualkenntnis (Vertrautenmagie)").getIntOrDefault("TaW", 3) : "");

		return table;
	}

	private Table getAttacksTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(65, 65, FontManager.serif, 4, valueSize, HAlign.LEFT));
		table.addColumn(new Column(35, 35, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, 20, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(33, 33, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(24, 24, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, 4, valueSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Angriff", 1), SheetUtil.createTitleCell("TP", 1), SheetUtil.createTitleCell("AT", 1),
				SheetUtil.createTitleCell("PA", 1), SheetUtil.createTitleCell(isMagical ? "Kauf" : "Mod", 1), SheetUtil.createTitleCell("DK", 1),
				SheetUtil.createTitleCell("Besonderes", 1));

		final JSONObject attacks = animal != null ? animal.getObj("Angriffe") : null;
		if (attacks != null) {
			for (final String attackName : attacks.keySet()) {
				Cell name = null;
				Cell tp = null;
				Cell at = null;
				Cell pa = null;
				Cell mod = null;
				Cell dk = null;
				Cell notes = null;

				if (fill) {
					final JSONObject attack = attacks.getObj(attackName);
					name = new TextCell(attackName);
					tp = new TextCell(HeroUtil.getTPString(null, attack, attack));
					at = new TextCell(Integer.toString(attack.getIntOrDefault("Attackewert", 0)));
					pa = new TextCell(Integer.toString(attack.getIntOrDefault("Paradewert", 0)));
					mod = new TextCell(Util.getSignedIntegerString(attack.getIntOrDefault(isMagical ? "Attackewert:Kauf" : "Attackewert:Modifikator", 0)))
							.addText("/")
							.addText(Util.getSignedIntegerString(attack.getIntOrDefault(isMagical ? "Paradewert:Kauf" : "Paradewert:Modifikator", 0)))
							.setEquallySpaced(true);
					dk = new TextCell(String.join("", attack.getArr("Distanzklassen").getStrings()));
					notes = new TextCell(attack.getStringOrDefault("Anmerkungen", " "));
				} else {
					name = new Cell();
					tp = new Cell();
					at = new Cell();
					pa = new Cell();
					mod = new TextCell("/");
					dk = new Cell();
					notes = new Cell();
				}
				table.addRow(name, tp, at, pa, mod, dk, notes);
			}
		}

		for (int i = 0; i < additionalAttacks.get(current).get(); ++i) {
			table.addRow(" ", "", "", "", "/");
		}

		return table;
	}

	private Table getAttributesTable() {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(62, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell(isMagical ? "Max." : "Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, curDesc, modDesc);

		JSONObject actualAttributes = null;
		if (animal != null) {
			actualAttributes = animal.getObj("Eigenschaften");
		}

		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		TextCell current = null;
		for (final String attribute : attributes.keySet()) {
			if (animal != null && fill) {
				final JSONObject actualAttribute = actualAttributes.getObj(attribute);
				final String cur = Integer.toString(actualAttribute.getIntOrDefault("Wert", 0));
				final String mod = isMagical ? Integer.toString((int) Math.round(actualAttribute.getIntOrDefault("Start", 0) * 1.5))
						: Util.getSignedIntegerString(actualAttribute.getIntOrDefault("Modifikator", 0));
				current = new TextCell(cur);
				table.addRow(attributes.getObj(attribute).getString("Name"), current, mod);
			} else {
				table.addRow(attributes.getObj(attribute).getString("Name"));
			}
		}
		if (current != null) {
			current.setBorder(1, 1, 1, 1);
		}

		return table;
	}

	private Table getBaseValuesTable() {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(10, FontManager.serif, valueSize, HAlign.CENTER).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(70, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(39, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(39, FontManager.serif, valueSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell(isMagical ? "Kauf" : "Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, emptyDesc, curDesc, modDesc, emptyDesc);

		JSONObject actualValues = null;
		if (animal != null) {
			actualValues = animal.getObj("Basiswerte");
		}

		if (animal != null && fill) {
			JSONObject actualValue = actualValues.getObj("Initiative");
			Bordered actual = new TextCell(actualValues.getObj("Initiative-Basis").getIntOrDefault("Wert", 0) + "+"
					+ actualValue.getIntOrDefault("Würfel:Anzahl", 1) + "W" + actualValue.getIntOrDefault("Würfel:Typ", 6));
			TextCell mod = new TextCell(Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));
			table.addRow("", "Initiative", actual, isMagical ? "—" : mod);

			actualValue = actualValues.getObj("Magieresistenz");
			actual = actualValue.containsKey("Geist")
					? new TextCell(Integer.toString(actualValue.getIntOrDefault("Geist", 0))).addText("/")
							.addText(Integer.toString(actualValue.getIntOrDefault("Körper", 0))).setEquallySpaced(true)
					: new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
			mod = actualValue.containsKey("Geist")
					? new TextCell(Integer.toString(actualValue.getIntOrDefault("Geist:Kauf", 0))).addText("/")
							.addText(Integer.toString(actualValue.getIntOrDefault("Körper:Kauf", 0))).setEquallySpaced(true)
					: new TextCell(Integer.toString(actualValue.getIntOrDefault("Kauf", 0)));
			table.addRow("", "Magieresistenz", actual, isMagical ? mod : "—");

			actualValue = actualValues.getObj("Geschwindigkeit");
			actual = actualValue.containsKey("Boden")
					? new TextCell(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Boden", 0.0))).addText("/")
							.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Luft", 0.0))).setEquallySpaced(true)
					: new TextCell(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Wert", 0.0)));
			mod = actualValue.containsKey("Boden")
					? new TextCell(isMagical ? DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault("Boden:Kauf", 0))
							: SheetUtil.threeDecimalPlacesSigned.format(actualValue.getIntOrDefault("Boden:Modifikator", 0))).addText("/")
									.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault(isMagical ? "Luft:Kauf" : "Luft:Modifikator", 0)))
									.setEquallySpaced(true)
					: new TextCell(isMagical ? DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault("Kauf", 0))
							: SheetUtil.threeDecimalPlacesSigned.format(actualValue.getIntOrDefault("Modifikator", 0)));
			table.addRow("", "Geschwindigkeit", actual, mod);

			if (!isMagical) {
				actualValue = actualValues.getObj("Fährtensuchen");
				actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
				mod = new TextCell(Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));
				table.addRow("", "Fährtensuchen", actual, mod);
			}

			actualValue = actualValues.getObj("Loyalität");
			actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
			mod = new TextCell(Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));
			table.addRow("", "Loyalität", actual, isMagical ? "—" : mod);

			actualValue = actualValues.getObj("Rüstungsschutz");
			actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
			table.addRow("", "Rüstungsschutz", actual, "—");

			actualValue = actualValues.getObj("Lebensenergie");
			actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
			mod = new TextCell(isMagical ? Integer.toString(actualValue.getIntOrDefault("Kauf", 0))
					: Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));
			table.addRow("", "Lebensenergie", actual, mod);

			actualValue = actualValues.getObj("Ausdauer");
			actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0)));
			if (!isMagical) {
				actual.setBorder(1, 1, 1, 1);
			}
			mod = new TextCell(isMagical ? "—" : Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));
			table.addRow("", "Ausdauer", actual, mod);

			if (isMagical) {
				actualValue = actualValues.getObj("Astralenergie");
				actual = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0))).setBorder(1, 1, 1, 1);
				mod = new TextCell(Integer.toString(actualValue.getIntOrDefault("Kauf", 0)));
				table.addRow("", "Astralenergie", actual, mod);
			}
		} else {
			table.addRow("", "Initiative", "", isMagical ? "—" : "");
			table.addRow("", "Magieresistenz", "", isMagical ? "" : "—");
			table.addRow("", "Geschwindigkeit");
			if (!isMagical) {
				table.addRow("", "Fährtensuchen");
			}
			table.addRow("", "Loyalität", "", isMagical ? "—" : "");
			table.addRow("", "Rüstungsschutz", "", "—");
			table.addRow("", "Lebensenergie");
			table.addRow("", "Ausdauer", isMagical ? "" : new Cell().setBorder(1, 1, 1, 1), isMagical ? "—" : "");
			if (isMagical) {
				table.addRow("", "Astralenergie", new Cell().setBorder(1, 1, 1, 1));
			}
		}

		return table;
	}

	private Table getBiographyTable() {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(160, 160, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0));
		table.addColumn(new Column(160, 160, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0));
		table.addColumn(new Column(180, 250, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0));
		table.addColumn(new Column(70, 250, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0));

		if (animal != null && fill) {
			final JSONObject bio = animal.getObj("Biografie");
			final String trainingString = String.join(", ", bio.getArr("Ausbildung").getStrings());
			table.addRow("Name: " + bio.getStringOrDefault("Name", ""), "Rasse: " + bio.getString("Rasse"),
					new TextCell("Ausbildung: " + trainingString).setColSpan(2));
			table.addRow(new TextCell("Farbe: " + bio.getStringOrDefault("Farbe", "")).setBorder(0.5f, 0, 0, 0.5f),
					new TextCell("Größe: " + bio.getIntOrDefault("Größe", 0)).setBorder(0.5f, 0, 0, 0.5f),
					new TextCell("Gewicht: " + bio.getIntOrDefault("Gewicht", 0)).setBorder(0.5f, 0, 0, 0.5f),
					new TextCell("Geschlecht: " + ("weiblich".equals(bio.getString("Geschlecht")) ? "♀" : "♂")).setBorder(0.5f, 0, 0, 0.5f));
		} else {
			table.addRow("Name:", "Rasse:", new TextCell("Ausbildung:").setColSpan(2));
			table.addRow(new TextCell("Farbe:").setBorder(0.5f, 0, 0, 0.5f), new TextCell("Größe:").setBorder(0.5f, 0, 0, 0.5f),
					new TextCell("Gewicht:").setBorder(0.5f, 0, 0, 0.5f), new TextCell("Geschlecht:").setBorder(0.5f, 0, 0, 0.5f));
		}

		return table;
	}

	private Table getHorseArmorTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(86, 86, FontManager.serif, 4, valueSize, HAlign.LEFT));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(40, FontManager.serif, valueSize, HAlign.LEFT));

		final Cell nameTitle = SheetUtil.createTitleCell("Rüstung", 1);
		final Cell beTitle = SheetUtil.createTitleCell("BE", 1);
		final Cell koTitle = SheetUtil.createTitleCell("Ko", 1);
		final Cell hTitle = SheetUtil.createTitleCell("H", 1);
		final Cell brTitle = SheetUtil.createTitleCell("Br", 1);
		final Cell krTitle = SheetUtil.createTitleCell("Kr", 1);
		final Cell lTitle = SheetUtil.createTitleCell("L", 1);
		final Cell notesTitle = SheetUtil.createTitleCell("Besonderes", 1);
		table.addRow(nameTitle, beTitle, koTitle, hTitle, brTitle, krTitle, lTitle, notesTitle);

		if (animal != null) {
			final int natural = animal.getObj("Vorteile").getObj("Natürlicher Rüstungsschutz").getIntOrDefault("Stufe", 0);
			if (natural != 0) {
				table.addCells("Natürlicher Rüstungsschutz", "0");
				for (int i = 0; i < 5; ++i) {
					table.addCells(fill ? natural : "");
				}
				table.addCells(" ");
			}
			final JSONArray items = animal.getArr("Ausrüstung");
			for (int i = 0; i < items.size(); ++i) {
				JSONObject item = items.getObj(i);
				final JSONArray categories = item.getArrOrDefault("Kategorien", null);
				if (categories != null && categories.contains("Pferderüstung")) {
					if (fill) {
						final JSONObject baseArmor = item;
						if (item.containsKey("Pferderüstung")) {
							item = item.getObj("Pferderüstung");
						}

						final String name = item.getStringOrDefault("Name", baseArmor.getStringOrDefault("Name", ""));

						final String be = item.getIntOrDefault("Behinderung", baseArmor.getIntOrDefault("Behinderung", 0)).toString();

						final String[] rs = new String[5];
						final JSONObject zones = item.getObjOrDefault("Rüstungsschutz", baseArmor.getObj("Rüstungsschutz"));
						final int[] zoneValues = new int[5];
						int j = 0;
						for (final String zone : new String[] { "Kopf", "Hals", "Brust", "Kruppe", "Läufe" }) {
							zoneValues[j] = zones.getIntOrDefault(zone, 0);
							rs[j] = Integer.toString(zoneValues[j]);
							++j;
						}

						final TextCell notes = new TextCell(item.getStringOrDefault("Anmerkungen", baseArmor.getStringOrDefault("Anmerkungen", " ")));

						table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], notes);
					} else {
						table.addRow(" ", " ", " ", " ", " ", " ", " ", " ");
					}
				}
			}
		}
		for (int i = 0; i < additionalArmorRows.get(current).get(); ++i) {
			table.addRow(" ", " ", " ", " ", " ", " ", " ", new TextCell(" "));
		}

		return table;
	}

	private Table getHorseSkillsTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(95, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(34, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, FontManager.serif, fontSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Fertigkeit", 1), SheetUtil.createTitleCell("E", 1), SheetUtil.createTitleCell("S", 1),
				((TextCell) SheetUtil.createTitleCell("Reit-AT", 1)).setPadding(0, 0, 0, 0), SheetUtil.createTitleCell("Beschreibung", 1));

		final JSONObject skills = ResourceManager.getResource("data/Tierfertigkeiten").getObj("Reittiere");
		final JSONObject actualSkills = animal != null && fill ? animal.getObj("Fertigkeiten") : null;
		for (final String skillName : skills.keySet()) {
			final JSONObject skill = skills.getObj(skillName);
			if (skill.getBoolOrDefault("Speziell", false) && ownSkillsOnly.get(current).get()
					&& (actualSkills == null || !actualSkills.containsKey(skillName))) {
				continue;
			}
			final String actual = actualSkills != null && actualSkills.containsKey(skillName) ? "X" : "";
			final String special = skill.getBoolOrDefault("Speziell", false) ? "X" : "";
			final String at = skill.getBoolOrDefault("Kampf", false) ? Util.getSignedIntegerString(skill.getIntOrDefault("Reit-Attacke", 0)) : "—";
			table.addRow(skillName, actual, special, at, skill.getStringOrDefault("Beschreibung:Kurz", ""));
			if (skill.getBoolOrDefault("Mehrfach", false)) {
				final String text = actualSkills != null && actualSkills.containsKey(skillName) ? String.join(", ", actualSkills.getArr(skillName).getStrings())
						: " ";
				table.addRow(new TextCell(text).setColSpan(table.getNumColumns()));
			}
		}

		return table;
	}

	private Table getHorseStatsTable() {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(57, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));
		table.addColumn(new Column(7, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(66, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(50, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(40, FontManager.serif, valueSize, HAlign.CENTER));

		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell("Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(" ", curDesc, modDesc, " ", " ", curDesc, modDesc);

		JSONObject actualAttributes = null;
		JSONObject actualValues = null;
		if (animal != null) {
			actualAttributes = animal.getObj("Eigenschaften");
			actualValues = animal.getObj("Basiswerte");
		}

		if (animal != null && fill) {
			JSONObject actualValue = actualAttributes.getObj("KO");
			String actual = Integer.toString(actualValue.getIntOrDefault("Wert", 0));
			String mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			actualValue = actualValues.getObj("Initiative");
			Bordered actualRight = new TextCell(actualValues.getObj("Initiative-Basis").getIntOrDefault("Wert", 0) + "+"
					+ actualValue.getIntOrDefault("Würfel:Anzahl", 1) + "W" + actualValue.getIntOrDefault("Würfel:Typ", 6));
			TextCell modRight = new TextCell(Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0)));

			table.addRow("Konstitution", actual, mod, "", "Initiative", actualRight, modRight);

			actualValue = actualAttributes.getObj("KK");
			actual = Integer.toString(actualValue.getIntOrDefault("Wert", 0));
			mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			actualValue = actualValues.getObj("Magieresistenz");
			actualRight = new TextCell(Integer.toString(actualValue.getIntOrDefault("Geist", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Körper", 0))).setEquallySpaced(true);

			table.addRow("Körperkraft", actual, mod, "", "Magieresistenz", actualRight, "—");

			actualValue = actualValues.getObj("Tragkraft");
			actual = Integer.toString(actualValue.getIntOrDefault("Wert", 0));
			mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			actualValue = actualValues.getObj("Futterbedarf");
			actualRight = new TextCell(Integer.toString(actualValue.getIntOrDefault("Erhaltung", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Leicht", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Mittel", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Schwer", 0))).setEquallySpaced(true);

			table.addRow("Tragkraft", "x" + actual, mod, "", "Futterbedarf", actualRight, "—");

			actualValue = actualValues.getObj("Zugkraft");
			actual = Integer.toString(actualValue.getIntOrDefault("Wert", 0));
			mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			actualValue = actualValues.getObj("Geschwindigkeit");
			actualRight = new TextCell(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Schritt", 0.0))).addText("/")
					.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Trab", 0.0))).addText("/")
					.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getDoubleOrDefault("Galopp", 0.0))).setEquallySpaced(true);
			modRight = new TextCell(DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault("Schritt:Modifikator", 0))).addText("/")
					.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault("Trab:Modifikator", 0))).addText("/")
					.addText(DSAUtil.threeDecimalPlaces.format(actualValue.getIntOrDefault("Galopp:Modifikator", 0))).setEquallySpaced(true);

			table.addRow("Zugkraft", "x" + actual, mod, "", "Geschwindigkeit", actualRight, modRight);

			actualValue = actualValues.getObj("Loyalität");
			final int lo = actualValue.getIntOrDefault("Wert", 0);
			actual = Integer.toString(lo);
			mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			final JSONObject riding = (JSONObject) HeroUtil.findActualTalent(hero, "Reiten")._1;
			if (riding == null || riding.getIntOrDefault("TaW", -1) < 0) {
				actualRight = new TextCell("—").addText("/").addText("—").setEquallySpaced(true);
			} else {
				final JSONObject attacks = animal.getObj("Angriffe");
				final JSONObject attack = attacks.size() == 0 ? null : attacks.getObj(attacks.keySet().iterator().next());
				int at = attack == null ? 0 : attack.getIntOrDefault("Attackewert", 0);
				int pa = attack == null ? 0 : attack.getIntOrDefault("Paradewert", 0);
				at = Math.round((lo + riding.getIntOrDefault("TaW", 0) + 2 * at) / 4.0f);
				pa = Math.round((lo + riding.getIntOrDefault("TaW", 0) + 2 * pa) / 4.0f);
				actualRight = new TextCell(Integer.toString(at)).addText("/").addText(Integer.toString(pa)).setEquallySpaced(true);
			}

			table.addRow("Loyalität", actual, mod, "", "Reit-AT/PA", fillAll ? actualRight : "/", "—");

			actualValue = actualValues.getObj("Lebensenergie");
			final Bordered actualLeP = new TextCell(Integer.toString(actualValue.getIntOrDefault("Wert", 0))).setBorder(1, 1, 1, 1);
			mod = Util.getSignedIntegerString(actualValue.getIntOrDefault("Modifikator", 0));

			actualValue = actualValues.getObj("Ausdauer");
			actualRight = new TextCell(Integer.toString(actualValue.getIntOrDefault("Trab", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Galopp", 0))).setEquallySpaced(true).setBorder(1, 1, 1, 1);
			modRight = new TextCell(Util.getSignedIntegerString(actualValue.getIntOrDefault("Trab:Modifikator", 0))).addText("/")
					.addText(Util.getSignedIntegerString(actualValue.getIntOrDefault("Galopp:Modifikator", 0))).setEquallySpaced(true);

			table.addRow("Lebensenergie", actualLeP, mod, "", "Ausdauer", actualRight, modRight);
		} else {
			table.addRow("Konstitution", "", "", "", "Initiative");
			table.addRow("Körperkraft", "", "", "", "Magieresistenz", "/", "—");
			table.addRow("Tragkraft", "", "", "", "Futterbedarf", new TextCell("/").addText("/").addText("/").setEquallySpaced(true), "—");
			table.addRow("Zugkraft", "", "", "", "Geschwindigkeit", new TextCell("/").addText("/"), new TextCell("/").addText("/").setEquallySpaced(true));
			table.addRow("Loyalität", "", "", "", "Reit-AT/PA", "/", "—");
			table.addRow("Lebensenergie", new Cell().setBorder(1, 1, 1, 1), "", "", "Ausdauer", new TextCell("/").setBorder(1, 1, 1, 1), new TextCell("/"));
		}

		return table;
	}

	private Table getInventoryTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, "Inventar");

		int cols = additionalInventoryRows.get(current).get() + 1;
		final Queue<JSONObject> equipment = new LinkedList<>();

		JSONArray items = null;
		if (animal != null) {
			items = animal.getArr("Ausrüstung");
			DSAUtil.foreach(item -> (!item.containsKey("Kategorien") || !item.getArr("Kategorien").contains("Pferderüstung")), item -> {
				equipment.add(item);
			}, items);
			cols += equipment.size();
		}
		cols = Math.max(cols, 2);

		final Table[] tables = new Table[2];

		for (int i = 0; i < 2; ++i) {
			tables[i] = new Table().setFiller(SheetUtil.stripe().invert(true)).setBorder(0, 0, 0, 0);

			tables[i].addColumn(new Column(109, 104, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(124, 114, FontManager.serif, 4, fontSize, HAlign.LEFT));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));
			tables[i].addColumn(new Column(25, FontManager.serif, fontSize, HAlign.CENTER));

			final TextCell nameTitle = new TextCell("Gegenstand", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell notesTitle = new TextCell("Anmerkungen", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell weightTitle = new TextCell("Gew.", FontManager.serifBold, 8.5f, 8.5f);
			final TextCell valueTitle = new TextCell("Wert", FontManager.serifBold, 8.5f, 8.5f);

			tables[i].addRow(nameTitle, notesTitle, weightTitle, valueTitle);

			for (int j = 0; j < cols / 2; ++j) {
				if (animal != null && fill && !equipment.isEmpty()) {
					final JSONObject item = equipment.poll();
					final String name = item.getStringOrDefault("Name", "Unbenannt");
					final String notes = item.getStringOrDefault("Anmerkungen", "");
					tables[i].addRow(name, notes);
				} else {
					tables[i].addRow("");
				}
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		return table;
	}

	private Table getProsConsTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(88, 88, FontManager.serif, 4, valueSize, HAlign.LEFT));
		table.addColumn(new Column(25, 25, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(180, 180, FontManager.serif, 4, valueSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Vor-/Nachteil", 1), SheetUtil.createTitleCell("Wert", 1), SheetUtil.createTitleCell("Beschreibung", 1));

		final JSONObject prosCons = ResourceManager.getResource("data/Tiereigenarten")
				.getObj("Reittier".equals(types.get(current).get()) ? "Reittiere" : "Allgemein");
		final JSONObject actualProsCons = animal != null ? animal.getObj("Eigenarten") : null;
		if (actualProsCons != null) {
			for (final String proConName : actualProsCons.keySet()) {
				final JSONObject proCon = prosCons.getObj(proConName);
				final JSONObject actual = actualProsCons.getObj(proConName);
				final String value = proCon.getBoolOrDefault("Abgestuft", false) ? Integer.toString(actual.getIntOrDefault("Stufe", 0)) : " ";
				if (fill) {
					table.addRow(DSAUtil.printProOrCon(actual, proConName, proCon, false), value, proCon.getStringOrDefault("Beschreibung:Kurz", ""));
				} else {
					table.addRow(" ");
				}
			}
		}

		for (int i = 0; i < additionalProConRows.get(current).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	private Table getRitualsTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(75, 75, FontManager.serif, 4, fontSize, HAlign.LEFT));
		table.addColumn(new Column(10, 10, FontManager.serif, fontSize, fontSize, HAlign.CENTER));
		table.addColumn(new Column(13, 13, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 70, FontManager.serif, 4, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 30, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 30, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER));
		table.addColumn(new Column(35, 35, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 25, FontManager.serif, fontSize, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 40, FontManager.serif, fontSize / 2, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, 0, FontManager.serif, fontSize / 2, fontSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Ritual", 1), SheetUtil.createTitleCell("E", 1), SheetUtil.createTitleCell("K", 1),
				SheetUtil.createTitleCell("Probe", 1), ((TextCell) SheetUtil.createTitleCell("Dauer", 1)).setPadding(0, 0, 0, 0),
				((TextCell) SheetUtil.createTitleCell("Kosten", 1)).setPadding(0, 0, 0, 0), SheetUtil.createTitleCell("RW", 1),
				SheetUtil.createTitleCell("ZO", 1), ((TextCell) SheetUtil.createTitleCell("W.-Dauer", 1)).setPadding(0, 0, 0, 0),
				SheetUtil.createTitleCell("Beschreibung", 1));

		final JSONObject rituals = ResourceManager.getResource("data/Tierfertigkeiten").getObj("Vertrautenmagie");
		for (final String name : rituals.keySet()) {
			final JSONObject ritual = rituals.getObj(name);
			final String actual = animal != null && fill && animal.getObj("Fertigkeiten").containsKey(name) ? "X" : "";
			table.addRow(name, actual, new TextCell(Integer.toString(ritual.getIntOrDefault("Kosten", 0))).setPadding(0, 0, 0, 0),
					DSAUtil.getChallengeString(ritual.getArrOrDefault("Ritualprobe", null)),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualdauer", null), Units.TIME, false),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualkosten", null), Units.NONE, false),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Reichweite", null), Units.RANGE, false),
					SheetUtil.getTargetObjectsString(ritual.getArrOrDefault("Zielobjekt", null)),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Wirkungsdauer", null), Units.TIME, false),
					ritual.getStringOrDefault("Beschreibung:Kurz", ""));
		}

		return table;
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = new JSONObject(parent);
		settings.put("Als eigenständigen Bogen drucken", separatePage.get());
		settings.put("Leerseite einfügen", emptyPage.get());
		final JSONObject namedAnimals = new JSONObject(settings);
		final JSONArray additional = new JSONArray(settings);
		for (int i = 0; i < types.size(); ++i) {
			if (!"Kein".equals(types.get(i).get())) {
				final JSONObject animal = animals == null ? null : i < animals.size() ? animals.getObj(i) : null;
				JSONObject setting;
				if (animal != null) {
					setting = new JSONObject(namedAnimals);
					namedAnimals.put(animal.getObj("Biografie").getStringOrDefault("Name", ""), setting);
				} else {
					setting = new JSONObject(additional);
					additional.add(setting);
				}
				setting.put("Typ", types.get(i).get());
				setting.put("Zusätzliche Zeilen für Angriffe", additionalAttacks.get(i).get());
				setting.put("Vor-/Nachteile", showProsCons.get(i).get());
				setting.put("Zusätzliche Zeilen für Vor-/Nachteile", additionalProConRows.get(i).get());
				setting.put("Rüstung", showArmor.get(i).get());
				setting.put("Zusätzliche Zeilen für Rüstung", additionalArmorRows.get(i).get());
				setting.put("Fertigkeiten", showSkills.get(i).get());
				setting.put("Nur erlernbare Fertigkeiten", ownSkillsOnly.get(i).get());
				setting.put("Inventar", showInventory.get(i).get());
				setting.put("Zusätzliche Zeilen für Inventar", additionalInventoryRows.get(i).get());
			}
		}
		if (namedAnimals.size() != 0) {
			settings.put("Tiere", namedAnimals);
		}
		if (additional.size() != 0) {
			settings.put("Zusätzlich", additional);
		}
		return settings;
	}

	private Table getSkillsTable() {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(70, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(10, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(15, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(0, FontManager.serif, fontSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Fertigkeit", 1), SheetUtil.createTitleCell("E", 1), SheetUtil.createTitleCell("S", 1),
				SheetUtil.createTitleCell("Beschreibung", 1));

		final JSONObject skills = ResourceManager.getResource("data/Tierfertigkeiten").getObj("Allgemein");
		final JSONObject actualSkills = animal != null && fill ? animal.getObj("Fertigkeiten") : new JSONObject(null);
		for (final String skillName : skills.keySet()) {
			final JSONObject skill = skills.getObj(skillName);
			if (skill.getBoolOrDefault("Speziell", false) && ownSkillsOnly.get(current).get()
					&& (actualSkills == null || !actualSkills.containsKey(skillName))) {
				continue;
			}
			final String actual = actualSkills != null && actualSkills.containsKey(skillName) ? "X" : "";
			final String difficulty = skill.getBoolOrDefault("Speziell", false) ? "S" : Util.getSignedIntegerString(skill.getIntOrDefault("Erschwernis", 0));
			table.addRow(skillName, actual, difficulty, skill.getStringOrDefault("Beschreibung:Kurz", ""));
			if (skill.containsKey("Auswahl") || skill.containsKey("Freitext")) {
				final StringBuilder text = new StringBuilder();
				final JSONArray actualSkill = actualSkills.getArr(skillName);
				boolean first = true;
				for (int i = 0; i < actualSkill.size(); ++i) {
					if (first) {
						first = false;
					} else {
						text.append(", ");
					}
					final JSONObject current = actualSkill.getObj(i);
					text.append(current.getStringOrDefault("Auswahl", current.getStringOrDefault("Freitext", "")));
				}
				if (text.length() == 0) {
					text.append(' ');
				}
				table.addRow(new TextCell(text.toString()).setColSpan(table.getNumColumns()));
			}
		}

		return table;
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		super.loadSettings(settings);

		settingsPage.clear();
		super.load();
		settingsPage.addNode(animalsBox);

		animalsBox.getChildren().remove(0, animalsBox.getChildren().size() - 1);
		animalControls.clear();
		types.clear();
		additionalAttacks.clear();
		showProsCons.clear();
		additionalProConRows.clear();
		showArmor.clear();
		additionalArmorRows.clear();
		showSkills.clear();
		ownSkillsOnly.clear();
		showInventory.clear();
		additionalInventoryRows.clear();

		animals = hero != null ? hero.getArrOrDefault("Tiere", null) : null;
		if (animals != null) {
			final JSONObject namedAnimals = settings.getObjOrDefault("Tiere", new JSONObject(null));
			for (int i = 0; i < animals.size(); ++i) {
				final JSONObject animal = animals.getObj(i);
				final String name = animal.getObj("Biografie").getStringOrDefault("Name", "");
				final JSONObject setting = namedAnimals.getObjOrDefault(name, new JSONObject(null));
				final String type = setting.getStringOrDefault("Typ", animal.getObj("Biografie").getStringOrDefault("Typ", "Tier"));
				final TitledPane control = addAnimal(type, setting);
				control.setText(name);
				control.setContextMenu(null);
			}
		}

		final JSONArray additional = settings.getArrOrDefault("Zusätzlich", new JSONArray(null));
		for (int i = 0; i < additional.size(); ++i) {
			final JSONObject setting = additional.getObj(i);
			final String type = setting.getStringOrDefault("Typ", "Tier");
			final TitledPane control = addAnimal(type, setting);
			control.setText(type);
			control.setContextMenu(null);
		}
	}

	@Override
	public String toString() {
		return "Tierbrief";
	}
}
