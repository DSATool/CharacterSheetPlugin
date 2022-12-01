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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import boxtable.table.Row;
import boxtable.table.Table;
import charactersheet.util.FontManager;
import charactersheet.util.SheetUtil;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.DSAUtil.Units;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.settings.SettingsPage;
import dsatool.ui.ReactiveSpinner;
import dsatool.ui.RenameDialog;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class AnimalSheet extends Sheet {

	private static final String ATTRIBUTES = "Eigenschaften und Basiswerte";
	private static final String APRKW = "Abenteuerpunkte/RkW";
	private static final String OWN_RITUALS_ONLY = "Nur erlernte/verbilligte Rituale";
	private static final String ZONE_IMAGE = "Bild für Zonenrüstung";
	private static final String ADDITIONAL_ROWS = "Zusätzliche Zeilen";

	private static final float fontSize = 9;
	private static final float valueSize = 10.5f;

	private static final List<String> sheetTypes = List.of("Vertrautentier", "Reittier", "Tier");

	@FXML
	private VBox animalsBox;

	private JSONObject animalSettings;

	private boolean isMagical;
	private boolean isHorse;
	private Table baseTable;
	private Table leftTable;
	private Table rightTable;
	private boolean isSwitched;
	private float leftHeight;
	private ImageCell zoneImageCell;

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
		renameAnimal(type, null, null);
	}

	public TitledPane addAnimal(final JSONObject animal, final String name, final String type, final JSONObject settings) {
		final TitledPane section = settingsPage.addSection(name, true);
		section.getStyleClass().add("boldTitledPane");
		sections.put(name, section);
		addOwnPageOption(settingsPage, section);

		if (animal.size() == 0) {
			section.setOnMouseClicked(e -> {
				if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
					renameAnimal(type, animalSettings, section);
				}
			});

			final ContextMenu menu = section.getContextMenu();

			final MenuItem editItem = new MenuItem("Bearbeiten");
			editItem.setOnAction(event -> renameAnimal(type, animalSettings, section));
			menu.getItems().add(0, editItem);

			final MenuItem removeItem = new MenuItem("Löschen");
			removeItem.setOnAction(e -> {
				animalSettings.remove(settings);
				settingsPage.removeSection(section);
			});
			menu.getItems().add(removeItem);
		}

		final Map<String, TitledPane> animalSections = new HashMap<>();

		final SettingsPage animalSettings = new SettingsPage();
		section.setUserData(new Tuple<>(animal, animalSettings));

		animalSettings.setInsets(4, 16);
		final ScrollPane pane = animalSettings.getControl();
		pane.setVbarPolicy(ScrollBarPolicy.NEVER);
		pane.setPadding(new Insets(5, 1, 5, 1));
		settingsPage.addNode(pane);

		settingsPage.getBool(section, "").set(settings.getBoolOrDefault("Anzeigen", true));
		settingsPage.getBool(section, AS_SEPARATE_SHEET).set(settings.getBoolOrDefault(AS_SEPARATE_SHEET, false));

		animalSettings.addStringChoice("Typ", sheetTypes);
		final StringProperty typeProperty = animalSettings.getString("Typ");
		typeProperty.set(settings.getStringOrDefault("Typ", type));

		animalSections.put(ATTRIBUTES, animalSettings.addSection(ATTRIBUTES, true));

		final TitledPane apRkWSection = animalSettings.addSection(APRKW, true);
		animalSections.put(APRKW, apRkWSection);
		final BooleanBinding isMagical = typeProperty.isEqualTo("Vertrautentier");
		apRkWSection.visibleProperty().bind(isMagical);
		apRkWSection.managedProperty().bind(isMagical);

		animalSections.put("Vor-/Nachteile", animalSettings.addSection("Vor-/Nachteile", true));
		animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);

		animalSections.put("Angriffe", animalSettings.addSection("Angriffe", true));
		animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);

		animalSections.put("Rüstung", animalSettings.addSection("Rüstung", true));
		animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);
		final Parent zoneImage = animalSettings.addBooleanChoice(ZONE_IMAGE).getParent();
		final BooleanBinding isHorse = typeProperty.isEqualTo("Reittier");
		zoneImage.visibleProperty().bind(isHorse);
		zoneImage.managedProperty().bind(isHorse);

		final TitledPane ritualsSection = animalSettings.addSection("Rituale", true);
		animalSections.put("Rituale", ritualsSection);
		final BooleanProperty ownRitualsProperty = animalSettings.addBooleanChoice(OWN_RITUALS_ONLY).selectedProperty();
		final ReactiveSpinner<Integer> additionalRitualsControl = animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);
		additionalRitualsControl.setDisable(true);
		ownRitualsProperty.addListener((o, oldV, newV) -> {
			additionalRitualsControl.setDisable(!newV);
		});
		ritualsSection.visibleProperty().bind(isMagical);
		ritualsSection.managedProperty().bind(isMagical);

		final TitledPane skillsSection = animalSettings.addSection("Fertigkeiten", true);
		animalSections.put("Fertigkeiten", skillsSection);
		animalSettings.addStringChoice("Anzeigen", List.of("Alle", "Erlernbare", "Erlernte"));
		final StringProperty showSkills = animalSettings.getString(skillsSection, "Anzeigen");
		final ReactiveSpinner<Integer> additionalSkillsControl = animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 20);
		additionalSkillsControl.setDisable(true);
		showSkills.addListener((o, oldV, newV) -> {
			additionalSkillsControl.setDisable(!"Erlernte".equals(newV));
		});

		TitledPane inventorySection = animalSettings.addSection("Inventar", true);
		animalSections.put("Inventar", inventorySection);
		animalSettings.getBool(inventorySection, "").set(settings.getBoolOrDefault("Inventar", true));
		animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 200);
		animalSettings.getInt(inventorySection, ADDITIONAL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_ROWS + " für Inventar", 40));

		final JSONArray inventories = animal != null ? animal.getArrOrDefault("Inventare", null) : null;
		if (inventories != null) {
			for (int i = 0; i < inventories.size(); ++i) {
				final JSONObject inventory = inventories.getObj(i);
				final String inventoryName = inventory.getStringOrDefault("Name", "");
				inventorySection = animalSettings.addSection(inventoryName, true);
				animalSections.put(inventoryName, inventorySection);
				inventorySection.setUserData(inventory);
				animalSettings.addIntegerChoice(ADDITIONAL_ROWS, 0, 200);
			}
		}

		int index = 0;
		for (final String key : settings.keySet()) {
			if (animalSections.containsKey(key)) {
				animalSettings.moveSection(animalSections.get(key), index);
				++index;
			}
		}

		final JSONObject categories = settings.getObjOrDefault(name, new JSONObject(null));
		for (final TitledPane subsection : animalSettings.getSections()) {
			final String sectionName = animalSettings.getString(subsection, null).get();
			final JSONObject category = categories.getObjOrDefault(sectionName, new JSONObject(null));
			switch (sectionName) {
				case ATTRIBUTES, APRKW -> animalSettings.getBool(subsection, "").set(category.getBoolOrDefault("Anzeigen", true));
				case "Rituale" -> {
					final boolean ownRitualsOnly = settings.getBoolOrDefault(OWN_RITUALS_ONLY, false);
					animalSettings.getBool(subsection, OWN_RITUALS_ONLY).set(ownRitualsOnly);
					animalSettings.getInt(subsection, ADDITIONAL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_ROWS, ownRitualsOnly ? 2 : 0));
				}
				case "Fertigkeiten" -> {
					final String skillsSetting = settings.getStringOrDefault(sectionName, "Erlernbare");
					animalSettings.getBool(subsection, "").set(!"Keine".equals(skillsSetting));
					animalSettings.getString(subsection, "Anzeigen").set("Keine".equals(skillsSetting) ? "Erlernbare" : skillsSetting);
					animalSettings.getInt(subsection, ADDITIONAL_ROWS).set(settings.getIntOrDefault(ADDITIONAL_ROWS, "Erlernte".equals(skillsSetting) ? 2 : 0));
				}
				default -> {
					if ("Rüstung".equals(sectionName)) {
						animalSettings.getBool(subsection, ZONE_IMAGE).set(category.getBoolOrDefault("Bild", true));
					}
					animalSettings.getBool(subsection, "")
							.set(category.getBoolOrDefault("Anzeigen", !"Rüstung".equals(sectionName) || "Reittier".equals(type)));
					final int additionalRows = switch (sectionName) {
						case "Vor-/Nachteile" -> "Vertrautentier".equals(type) ? 2 : 3;
						case "Angriffe" -> 2;
						case "Rüstung" -> 3;
						case "Inventar" -> 40;
						default -> 10;
					};
					animalSettings.getInt(subsection, ADDITIONAL_ROWS).set(category.getIntOrDefault(ADDITIONAL_ROWS, additionalRows));
				}
			}
		}

		return section;
	}

	private void addAnimalTable(final PDDocument document, final TitledPane animalSection) throws IOException {
		baseTable = new Table();
		baseTable.addEventHandler(EventType.BEGIN_PAGE, header);
		baseTable.addColumn(new Column(571, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));

		@SuppressWarnings("unchecked")
		final Tuple<JSONObject, SettingsPage> data = (Tuple<JSONObject, SettingsPage>) animalSection.getUserData();
		final JSONObject animal = data._1;
		final SettingsPage settings = data._2;

		separatePage(document, settingsPage, animalSection);

		final String type = settings.getString("Typ").get();
		SheetUtil.addTitle(baseTable, settingsPage.getString(animalSection, null).get());

		isMagical = "Vertrautentier".equals(type);
		isHorse = "Reittier".equals(type);

		addLargeTable(getBiographyTable(animal));

		leftTable = null;
		rightTable = null;
		zoneImageCell = null;
		leftHeight = 0;

		for (final TitledPane section : settings.getSections()) {
			if (!settings.getBool(section, "").get()) {
				continue;
			}

			final String categoryName = settings.getString(section, null).get();

			try {
				switch (categoryName) {
					case ATTRIBUTES -> {
						if (isHorse) {
							addSmallTable(false, new TableCell(getHorseStatsTable(animal)).setColSpan(2));
						} else {
							addSmallTable(false, new TableCell(getAttributesTable(animal)), new TableCell(getBaseValuesTable(animal)));
						}
					}

					case APRKW -> {
						if (isMagical) {
							addSmallTable(true, new TableCell(getAPRkWTable(animal)));
						}
					}

					case "Vor-/Nachteile" -> {
						addSmallTable(true, new TableCell(getProsConsTable(animal, settings, section)));
					}

					case "Angriffe" -> {
						addSmallTable(true, new TableCell(getAttacksTable(animal, settings, section)));
					}

					case "Rüstung" -> {
						addSmallTable(true, new TableCell(getHorseArmorTable(animal, settings, section)));

						if (settings.getBool(section, ZONE_IMAGE).get()) {
							zoneImageCell = new ImageCell(new File(Util.getAppDir() + "/resources/images/zones/animals/Pferd.jpg"));
							zoneImageCell.setColSpan(2).setHAlign(HAlign.CENTER).setVAlign(VAlign.TOP).setPadding(1, 0, 0, 0);
							leftTable.addRow("");
							leftTable.addRow(zoneImageCell);
							final List<Row> rows = leftTable.getRows();
							rows.get(rows.size() - 1).setBorder(0, 0, 0, 0);
						}
					}

					case "Rituale" -> {
						if (isMagical) {
							addLargeTable(getRitualsTable(animal, settings, section));
						}
					}

					case "Fertigkeiten" -> {
						addLargeTable(isHorse ? getHorseSkillsTable(animal, settings, section) : getSkillsTable(animal, settings, section));
					}

					case "Inventar" -> {
						addLargeTable(getInventoryTable("Inventar", animal != null ? animal.getArr("Ausrüstung") : null,
								settings.getInt(section, ADDITIONAL_ROWS).get()));
					}

					default -> {
						final JSONObject inventory = (JSONObject) section.getUserData();
						addLargeTable(getInventoryTable(inventory.getStringOrDefault("Name", "Unbekanntes Inventar"), inventory.getArr("Ausrüstung"),
								settings.getInt(section, ADDITIONAL_ROWS).get()));
					}
				}
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		if (leftTable != null) {
			addSmallTables();
		}

		baseTable.getRows().remove(baseTable.getRows().size() - 1);

		bottom.bottom = baseTable.render(document, 571, 12, bottom.bottom, 54, 10) - 5;
	}

	private void addLargeTable(final Table toAdd) {
		if (leftTable != null) {
			addSmallTables();
		}
		baseTable.addRow(new TableCell(toAdd));
		baseTable.addRow("");
	}

	private void addSmallTable(final boolean isRight, final Cell... toAdd) {
		if (leftTable == null) {
			leftTable = new Table().setBorder(0, 0, 0, 0);
			leftTable.addColumn(new Column(102, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
			leftTable.addColumn(new Column(158, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
			leftTable.addColumn(new Column(7, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));

			rightTable = new Table().setBorder(0, 0, 0, 0);
			rightTable.addColumn(new Column(304, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
			final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
			rightTable.addRow(emptyDesc);

			isSwitched = isRight;
		} else if (isSwitched == isRight && (isRight ? leftTable : rightTable).getNumRows() != 0) {
			addSmallTables();
			addSmallTable(isRight, toAdd);
		}

		(isRight ? rightTable : leftTable).addRow((Object[]) toAdd);

		if (isRight) {
			rightTable.addRow("");
		} else {
			final List<Row> rows = leftTable.getRows();
			final float[] colWidths = new float[] { 102, 158, 7 };
			try {
				leftHeight += rows.get(rows.size() - 1).getHeight(colWidths);
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		}
	}

	private void addSmallTables() {
		final Table rowTable = new Table().setBorder(0, 0, 0, 0);
		rowTable.addEventHandler(EventType.BEGIN_PAGE, header);

		rightTable.getRows().remove(rightTable.getRows().size() - 1);

		if (zoneImageCell != null) {
			try {
				zoneImageCell.setMinHeight(rightTable.getHeight(304) - leftHeight);
			} catch (final IOException e) {
				ErrorLogger.logError(e);
			}
		}

		if (isSwitched) {
			rowTable.addColumn(new Column(304, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
			rowTable.addColumn(new Column(267, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));

			rowTable.addRow(new TableCell(rightTable), new TableCell(leftTable));
		} else {
			rowTable.addColumn(new Column(267, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));
			rowTable.addColumn(new Column(304, FontManager.serif, 5, HAlign.LEFT).setBorder(0, 0, 0, 0));

			rowTable.addRow(new TableCell(leftTable), new TableCell(rightTable));
		}

		leftTable = null;
		rightTable = null;
		zoneImageCell = null;
		leftHeight = 0;

		addLargeTable(rowTable);
	}

	@Override
	public void create(final PDDocument document) throws IOException {
		if (settingsPage.getSections().size() > 0) {
			header = SheetUtil.createHeader("Tierbrief", true, false, false, hero, fill, fillAll, showName, showDate);

			startCreate(document);

			for (final TitledPane section : settingsPage.getSections()) {
				if (settingsPage.getBool(section, "").get()) {
					try {
						addAnimalTable(document, section);
					} catch (final Exception e) {
						ErrorLogger.logError(e);
					}
				}
			}

			endCreate(document);
		}
	}

	private Table getAPRkWTable(final JSONObject animal) {
		final Table table = new Table().setBorder(0, 0, 0, 0).setNumHeaderRows(0);

		table.addColumn(new Column(18, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(43, 43, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(49, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(43, 43, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(50, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(43, 43, FontManager.serif, 4, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 1));
		table.addColumn(new Column(30, FontManager.serif, valueSize, HAlign.RIGHT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(28, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 0, 1));

		final JSONObject bio = animal != null ? animal.getObj("Biografie") : null;
		final JSONObject baseValues = animal != null ? animal.getObj("Basiswerte") : null;

		table.addCells("AP ", fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte", 0) : "");
		table.addCells("Investiert ",
				fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte", 0) - bio.getIntOrDefault("Abenteuerpunkte-Guthaben", 0) : "");
		table.addCells("Guthaben ", fillAll && bio != null ? bio.getIntOrDefault("Abenteuerpunkte-Guthaben", 0) : "");
		table.addCells("RkW ", fillAll && baseValues != null ? baseValues.getObj("Ritualkenntnis (Vertrautenmagie)").getIntOrDefault("TaW", 3) : "");

		return table;
	}

	private Table getAttacksTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
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
				final Cell name;
				final Cell tp;
				final Cell at;
				final Cell pa;
				final Cell mod;
				final Cell dk;
				final Cell notes;

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

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ", "", "", "", "/");
		}

		return table;
	}

	private Table getAttributesTable(final JSONObject animal) {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(62, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(20, FontManager.serif, valueSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell(isMagical ? "Max." : "Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, curDesc, modDesc);

		JSONObject actualAttributes = animal != null ? actualAttributes = animal.getObj("Eigenschaften") : null;

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
				current = new TextCell("");
				table.addRow(attributes.getObj(attribute).getString("Name"), current);
			}
		}
		current.setBorder(1, 1, 1, 1);

		return table;
	}

	private Table getBaseValuesTable(final JSONObject animal) {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(10, FontManager.serif, valueSize, HAlign.CENTER).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(70, FontManager.serif, fontSize, HAlign.LEFT).setBorder(0, 0, 0, 0));
		table.addColumn(new Column(39, FontManager.serif, valueSize, HAlign.CENTER).setBorder(1, 1, 1, 0));
		table.addColumn(new Column(39, FontManager.serif, valueSize, HAlign.CENTER));

		final Cell emptyDesc = new TextCell(" ", FontManager.serif, 6, 6);
		final Bordered curDesc = new TextCell("Akt.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		final Bordered modDesc = new TextCell(isMagical ? "Kauf" : "Mod.", FontManager.serif, 6, 6).setBorder(0, 0, 0, 0);
		table.addRow(emptyDesc, emptyDesc, curDesc, modDesc, emptyDesc);

		if (animal != null && fill) {
			final JSONObject actualValues = animal.getObj("Basiswerte");

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

	private Table getBiographyTable(final JSONObject animal) {
		final Table table = new Table().setBorder(0, 0, 0, 0);

		table.addColumn(new Column(120, 120, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));
		table.addColumn(new Column(200, 200, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));
		table.addColumn(new Column(80, 80, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));
		table.addColumn(new Column(50, 50, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));
		table.addColumn(new Column(60, 60, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));
		table.addColumn(new Column(60, 60, FontManager.serif, 4, fontSize, HAlign.LEFT).setBorder(0.5f, 0, 0, 0.5f));

		if (animal != null && fill) {
			final JSONObject bio = animal.getObj("Biografie");
			final String trainingString = String.join(", ", bio.getArr("Ausbildung").getStrings());
			table.addRow("Rasse: " + bio.getStringOrDefault("Rasse", ""), "Ausbildung: " + trainingString, "Farbe: " + bio.getStringOrDefault("Farbe", ""),
					"Größe: " + bio.getIntOrDefault("Größe", 0), "Gewicht: " + bio.getIntOrDefault("Gewicht", 0),
					"Geschlecht: " + ("weiblich".equals(bio.getString("Geschlecht")) ? "♀" : "♂"));
		} else {
			table.addRow("Rasse:", "Ausbildung:", "Farbe:", "Größe:", "Gewicht:", "Geschlecht:");
		}

		return table;
	}

	private Table getHorseArmorTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
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

						final TextCell notes = new TextCell(HeroUtil.getItemNotes(item, baseArmor));

						table.addRow(name, be, rs[0], rs[1], rs[2], rs[3], rs[4], notes);
					} else {
						table.addRow(" ", " ", " ", " ", " ", " ", " ", " ");
					}
				}
			}
		}

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	private Table getHorseSkillsTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
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
			final String skillSetting = settings.getString(section, "Anzeigen").get();
			if ((actualSkills == null || !actualSkills.containsKey(skillName))
					&& ("Erlernte".equals(skillSetting) || "Erlernbare".equals(skillSetting) && skill.getBoolOrDefault("Speziell", false))) {
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

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	private Table getHorseStatsTable(final JSONObject animal) {
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

		if (animal != null && fill) {
			final JSONObject actualAttributes = animal.getObj("Eigenschaften");
			final JSONObject actualValues = animal.getObj("Basiswerte");

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
			actual = DSAUtil.oneDecimalPlace.format(actualValue.getDoubleOrDefault("Wert", 0.0));
			mod = Util.getSignedDoubleString(actualValue.getDoubleOrDefault("Modifikator", 0.0), DSAUtil.oneDecimalPlace);

			actualValue = actualValues.getObj("Futterbedarf");
			actualRight = new TextCell(Integer.toString(actualValue.getIntOrDefault("Erhaltung", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Leicht", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Mittel", 0))).addText("/")
					.addText(Integer.toString(actualValue.getIntOrDefault("Schwer", 0))).setEquallySpaced(true);

			table.addRow("Tragkraft", "x" + actual, mod, "", "Futterbedarf", actualRight, "—");

			actualValue = actualValues.getObj("Zugkraft");
			actual = DSAUtil.oneDecimalPlace.format(actualValue.getDoubleOrDefault("Wert", 0.0));
			mod = Util.getSignedDoubleString(actualValue.getDoubleOrDefault("Modifikator", 0.0), DSAUtil.oneDecimalPlace);

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

	private Table getInventoryTable(final String inventoryName, final JSONArray inventory, final int additionalRows) {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));
		table.addColumn(new Column(5, FontManager.serif, fontSize, HAlign.CENTER));
		table.addColumn(new Column(283, FontManager.serif, fontSize, HAlign.LEFT));

		SheetUtil.addTitle(table, inventoryName);

		int rows = additionalRows + 1;
		final Queue<JSONObject> equipment = new LinkedList<>();

		if (inventory != null) {
			if (((JSONObject) inventory.getParent()).containsKey("Name")) {
				DSAUtil.foreach(item -> true, item -> {
					equipment.add(item);
				}, inventory);
			} else {
				DSAUtil.foreach(item -> (!item.containsKey("Kategorien") || !item.getArr("Kategorien").contains("Pferderüstung")), item -> {
					equipment.add(item);
				}, inventory);
			}
			rows += equipment.size();
		}
		rows = Math.max(rows, 2);

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

			for (int j = 0; j < rows / 2; ++j) {
				if (hero != null && fill && !equipment.isEmpty()) {
					final JSONObject item = equipment.poll();
					final String name = item.getStringOrDefault("Name", "Unbenannt");
					final String notes = HeroUtil.getItemNotes(item, item);
					tables[i].addRow(name, notes);
				} else {
					tables[i].addRow("");
				}
			}
		}

		table.addRow(new TableCell(tables[0]), "", new TableCell(tables[1]));

		return table;
	}

	private Table getProsConsTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
		final Table table = new Table().setFiller(SheetUtil.stripe());

		table.addColumn(new Column(88, 88, FontManager.serif, 4, valueSize, HAlign.LEFT));
		table.addColumn(new Column(25, 25, FontManager.serif, 4, valueSize, HAlign.CENTER));
		table.addColumn(new Column(180, 180, FontManager.serif, 4, valueSize, HAlign.LEFT));

		table.addRow(SheetUtil.createTitleCell("Vor-/Nachteil", 1), SheetUtil.createTitleCell("Wert", 1), SheetUtil.createTitleCell("Beschreibung", 1));

		final JSONObject prosCons = ResourceManager.getResource("data/Tiereigenarten")
				.getObj("Reittier".equals(settings.getString("Typ").get()) ? "Reittiere" : "Allgemein");
		final JSONObject actualProsCons = animal != null ? animal.getObj("Eigenarten") : null;
		if (actualProsCons != null) {
			for (final String proConName : actualProsCons.keySet()) {
				final JSONObject proCon = prosCons.getObj(proConName);
				if (proCon.containsKey("Auswahl") || proCon.containsKey("Freitext")) {
					final JSONArray actualProCons = actualProsCons.getArr(proConName);
					for (int i = 0; i < actualProCons.size(); ++i) {
						final JSONObject actual = actualProCons.getObj(i);
						final String value = proCon.getBoolOrDefault("Abgestuft", false) ? Integer.toString(actual.getIntOrDefault("Stufe", 0)) : " ";
						if (fill) {
							table.addRow(DSAUtil.printProOrCon(actual, proConName, proCon, false), value, proCon.getStringOrDefault("Beschreibung:Kurz", ""));
						} else {
							table.addRow(" ");
						}
					}
				} else {
					final JSONObject actual = actualProsCons.getObj(proConName);
					final String value = proCon.getBoolOrDefault("Abgestuft", false) ? Integer.toString(actual.getIntOrDefault("Stufe", 0)) : " ";
					if (fill) {
						table.addRow(DSAUtil.printProOrCon(actual, proConName, proCon, false), value, proCon.getStringOrDefault("Beschreibung:Kurz", ""));
					} else {
						table.addRow(" ");
					}
				}
			}
		}

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	private Table getRitualsTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
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
		final JSONObject actualRituals = animal != null && fill ? animal.getObj("Fertigkeiten") : new JSONObject(null);

		for (final String name : rituals.keySet()) {
			final JSONObject ritual = rituals.getObj(name);
			if ((actualRituals == null || !actualRituals.containsKey(name)) && settings.getBool(section, OWN_RITUALS_ONLY).get()) {
				continue;
			}
			final String actual = actualRituals != null && actualRituals.containsKey(name) ? "X" : "";
			table.addRow(name, actual, new TextCell(Integer.toString(ritual.getIntOrDefault("Kosten", 0))).setPadding(0, 0, 0, 0),
					DSAUtil.getChallengeString(ritual.getArrOrDefault("Ritualprobe", null)),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualdauer", null), Units.TIME, false),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Ritualkosten", null), Units.NONE, false),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Reichweite", null), Units.RANGE, false),
					SheetUtil.getTargetObjectsString(ritual.getArrOrDefault("Zielobjekt", null)),
					DSAUtil.getModificationString(ritual.getObjOrDefault("Wirkungsdauer", null), Units.TIME, false),
					ritual.getStringOrDefault("Beschreibung:Kurz", ""));
		}

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	@Override
	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = super.getSettings(parent);
		final JSONObject namedAnimals = new JSONObject(settings);

		for (final TitledPane section : settingsPage.getSections()) {
			@SuppressWarnings("unchecked")
			final Tuple<JSONObject, SettingsPage> data = (Tuple<JSONObject, SettingsPage>) section.getUserData();
			final SettingsPage animalSettings = data._2;

			final JSONObject animalSetting = new JSONObject(namedAnimals);
			namedAnimals.put(settingsPage.getString(section, null).get(), animalSetting);

			final String type = animalSettings.getString("Typ").get();

			animalSetting.put("Anzeigen", settingsPage.getBool(section, "").get());
			animalSetting.put(AS_SEPARATE_SHEET, settingsPage.getBool(AS_SEPARATE_SHEET).get());
			animalSetting.put("Typ", type);

			for (final TitledPane subsection : animalSettings.getSections()) {
				final String name = animalSettings.getString(subsection, null).get();
				if (!"Vertrautentier".equals(type) && List.of(APRKW, "Rituale").contains(name)) {
					continue;
				}
				final JSONObject category = new JSONObject(animalSetting);
				animalSetting.put(name, category);
				if ("Fertigkeiten".equals(name)) {
					category.put("Anzeigen",
							animalSettings.getBool(subsection, "").get() ? animalSettings.getString(subsection, "Anzeigen").get() : "Keine");
				} else {
					category.put("Anzeigen", animalSettings.getBool(subsection, "").get());
				}
				if (!List.of(ATTRIBUTES, APRKW).contains(name)) {
					category.put(ADDITIONAL_ROWS, animalSettings.getInt(subsection, ADDITIONAL_ROWS).get());
				}
				if ("Rituale".equals(name)) {
					category.put(OWN_RITUALS_ONLY, animalSettings.getBool(subsection, OWN_RITUALS_ONLY).get());
				}
				if ("Rüstung".equals(name) && "Reittier".equals(type)) {
					category.put("Bild", animalSettings.getBool(subsection, ZONE_IMAGE).get());
				}
			}
		}
		if (namedAnimals.size() != 0) {
			settings.put("Tiere", namedAnimals);
		}

		return settings;
	}

	private Table getSkillsTable(final JSONObject animal, final SettingsPage settings, final TitledPane section) {
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
			final String skillSetting = settings.getString(section, "Anzeigen").get();
			if ((actualSkills == null || !actualSkills.containsKey(skillName))
					&& ("Erlernte".equals(skillSetting) || "Erlernbare".equals(skillSetting) && skill.getBoolOrDefault("Speziell", false))) {
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

		for (int i = 0; i < settings.getInt(section, ADDITIONAL_ROWS).get(); ++i) {
			table.addRow(" ");
		}

		return table;
	}

	@Override
	public void loadSettings(final JSONObject settings) {
		settingsPage.clear();
		sections.clear();
		load();
		super.loadSettings(settings);

		final JSONObject namedAnimals = settings.getObjOrDefault("Tiere", new JSONObject(null));
		animalSettings = namedAnimals.clone(null);
		final Map<String, JSONObject> found = new HashMap<>();

		if (hero != null) {
			final JSONArray animals = hero.getArrOrDefault("Tiere", new JSONArray(null));
			for (int i = 0; i < animals.size(); ++i) {
				final JSONObject animal = animals.getObj(i);
				final String name = animal.getObj("Biografie").getStringOrDefault("Name", "");
				found.put(name, animal);
			}
		}

		for (final String name : namedAnimals.keySet()) {
			final JSONObject setting = namedAnimals.getObj(name);
			if (found.containsKey(name)) {
				final JSONObject animal = found.get(name);
				addAnimal(animal, name, setting.getStringOrDefault("Typ", animal.getObj("Biografie").getStringOrDefault("Typ", "Tier")), setting);
			} else {
				addAnimal(new JSONObject(null), name, setting.getStringOrDefault("Typ", "Tier"), setting);
			}
		}

		if (hero != null) {
			for (final String name : found.keySet()) {
				if (!namedAnimals.keySet().contains(name)) {
					final JSONObject animal = found.get(name);
					addAnimal(animal, name, animal.getObj("Biografie").getStringOrDefault("Typ", "Tier"), new JSONObject(null));
				}
			}
		}

		settingsPage.endSection();
		settingsPage.addNode(animalsBox);
	}

	private void renameAnimal(final String type, final JSONObject animal, final TitledPane section) {
		new RenameDialog(animalsBox.getScene().getWindow(), type, "Tiere", animalSettings, animal,
				(oldName, newName) -> {
					if (animal == null) {
						settingsPage.removeNode(animalsBox);
						addAnimal(new JSONObject(null), newName, type, new JSONObject(null));
						settingsPage.endSection();
						settingsPage.addNode(animalsBox);
					} else {
						settingsPage.getString(section, null).set(newName);
					}
				}, List.of());
	}

	@Override
	public String toString() {
		return "Tierbrief";
	}
}
