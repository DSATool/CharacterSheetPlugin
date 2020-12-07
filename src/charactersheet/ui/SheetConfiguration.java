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
package charactersheet.ui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.controlsfx.control.CheckListView;
import org.controlsfx.control.CheckModel;

import charactersheet.sheets.AnimalSheet;
import charactersheet.sheets.CharacterSheet;
import charactersheet.sheets.ClericSheet;
import charactersheet.sheets.CompactSheet;
import charactersheet.sheets.FightSheet;
import charactersheet.sheets.InventorySheet;
import charactersheet.sheets.RitualsSheet;
import charactersheet.sheets.Sheet;
import charactersheet.sheets.SpecialSkillsSheet;
import charactersheet.sheets.SpellsSheet;
import charactersheet.sheets.TalentsSheet;
import charactersheet.util.FontManager;
import dsa41basis.ui.hero.HeroController;
import dsa41basis.ui.hero.HeroSelector;
import dsatool.util.ErrorLogger;
import dsatool.util.Util;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.RadioButton;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import jsonant.value.JSONObject;

public class SheetConfiguration extends HeroSelector {
	public static List<Class<? extends Sheet>> sheetControllers = Arrays.asList(CompactSheet.class, CharacterSheet.class,
			FightSheet.class, SpecialSkillsSheet.class, TalentsSheet.class, InventorySheet.class, SpellsSheet.class, RitualsSheet.class, ClericSheet.class,
			AnimalSheet.class);

	@FXML
	private RadioButton fillAll;
	@FXML
	private RadioButton fill;
	@FXML
	private RadioButton noFill;
	@FXML
	private CheckBox showName;
	@FXML
	private CheckBox showDate;
	@FXML
	private CheckListView<Sheet> sheets;
	@FXML
	private StackPane tabArea;

	private final Map<Sheet, Node> sheetControls = new HashMap<>();
	private final Map<String, Sheet> sheetNames = new HashMap<>();

	private JSONObject hero;

	public SheetConfiguration() {
		super(false, true);

		final FXMLLoader fxmlLoader = new FXMLLoader();
		fxmlLoader.setController(this);

		BorderPane pane = null;

		try {
			pane = fxmlLoader.load(getClass().getResource("SheetConfiguration.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		setContent(pane);

		sheets.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
			if (newV != null) {
				sheetControls.get(newV).toFront();
			}
		});

		initializeDragDrop();

		for (final Class<? extends Sheet> sheetClass : sheetControllers) {
			try {
				final Sheet sheet = sheetClass.getConstructor().newInstance();
				sheet.load();
				controllers.add(sheet);
				final Node control = sheet.getControl();
				tabArea.getChildren().add(control);
				sheetControls.put(sheet, control);
				sheetNames.put(sheet.toString(), sheet);
				sheets.getItems().add(sheet);
			} catch (final Exception e) {
				ErrorLogger.logError(e);
			}
		}

		load();
	}

	private void checkAndOrderSheets() {
		final JSONObject settings = hero != null ? hero.getObjOrDefault("Heldenbogen", null) : null;

		final ObservableList<Sheet> items = sheets.getItems();
		items.clear();
		if (settings != null) {
			for (final String key : settings.keySet()) {
				if (sheetNames.containsKey(key)) {
					items.add(sheetNames.get(key));
				}
			}
		}

		for (final HeroController controller : controllers) {
			final Sheet sheet = (Sheet) controller;
			if (settings == null || !settings.containsKey(sheet.toString())) {
				items.add(sheet);
			}
		}

		for (final HeroController controller : controllers) {
			final Sheet sheet = (Sheet) controller;
			final boolean checked = settings != null ? settings.containsKey(sheet.toString()) : sheet.check();
			if (checked) {
				sheets.getCheckModel().check(sheet);
			} else {
				sheets.getCheckModel().clearCheck(sheet);
			}
		}
	}

	private void create(final File file) {
		Sheet.reset();
		try (final PDDocument document = new PDDocument()) {
			FontManager.initFonts(document);
			document.getDocumentCatalog().setDocumentOutline(new PDDocumentOutline());
			for (final Sheet sheet : sheets.getCheckModel().getCheckedItems()) {
				sheet.setFill(!noFill.isSelected(), fillAll.isSelected());
				sheet.setShowNameAndDate(showName.isSelected(), showDate.isSelected());
				try {
					sheet.create(document);
				} catch (final Exception e) {
					ErrorLogger.logError(e);
				}
			}
			document.save(file);
		} catch (final IOException e) {
			ErrorLogger.logError(e);
		}
	}

	private void initializeDragDrop() {
		sheets.setCellFactory(list -> {
			final CheckBoxListCell<Sheet> cell = new CheckBoxListCell<>();

			cell.setSelectedStateCallback(sheets::getItemBooleanProperty);

			cell.setOnDragDetected(e -> {
				if (cell.isEmpty()) return;
				final Dragboard dragBoard = sheets.startDragAndDrop(TransferMode.MOVE);
				final ClipboardContent content = new ClipboardContent();
				content.put(DataFormat.PLAIN_TEXT, cell.getIndex());
				dragBoard.setContent(content);
				e.consume();
			});

			cell.setOnDragDropped(e -> {
				final CheckModel<Sheet> checkModel = sheets.getCheckModel();
				final ObservableList<Sheet> checkedSheets = checkModel.getCheckedItems();
				final Sheet[] checkedPreviously = checkedSheets.toArray(new Sheet[checkedSheets.size()]);
				final Sheet item = sheets.getItems().get((Integer) e.getDragboard().getContent(DataFormat.PLAIN_TEXT));
				sheets.getItems().remove(item);
				final int index = sheets.getItems().indexOf(cell.getItem());
				if (index == -1) {
					sheets.getItems().add(item);
				} else {
					sheets.getItems().add(index, item);
				}
				checkModel.clearChecks();
				for (final Sheet sheet : checkedPreviously) {
					checkModel.check(sheet);
				}
				e.setDropCompleted(true);
			});

			cell.setOnDragOver(e -> e.acceptTransferModes(TransferMode.MOVE));

			return cell;
		});
	}

	/**
	 * Reloads the data if it has changed
	 */
	@Override
	protected void reload() {
		final MultipleSelectionModel<String> heroModel = list.getSelectionModel();
		final int selectedHero = heroModel.getSelectedIndex();

		final MultipleSelectionModel<Sheet> sheetControlModel = sheets.getSelectionModel();
		final int selectedSheet = sheetControlModel.getSelectedIndex();

		super.reload();

		// Important: First add dummy to heroes to avoid false selection on adding list entry
		heroes.add(0, null);
		list.getItems().add(0, "Leerer Bogen");

		checkAndOrderSheets();

		heroModel.clearAndSelect(Math.max(0, selectedHero));
		sheetControlModel.clearAndSelect(Math.max(0, selectedSheet));
	}

	@FXML
	private void save() {
		final FileChooser dialog = new FileChooser();

		dialog.setTitle("Datei speichern");
		dialog.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("*.pdf", "*.pdf"));

		String name = (hero != null ? hero.getObj("Biografie").getString("Vorname") : "Heldenbogen") + ".pdf";
		if (hero != null && hero.containsKey("Heldenbogen") && hero.getObj("Heldenbogen").containsKey("Datei")) {
			final File file = new File(hero.getObj("Heldenbogen").getString("Datei"));
			final File directory = file.getParentFile();
			if (directory.isDirectory()) {
				dialog.setInitialDirectory(directory);
			}
			name = file.getName();
		}
		dialog.setInitialFileName(name);

		final File file = dialog.showSaveDialog(null);
		if (file != null) {
			create(file);
			if (hero != null) {
				final JSONObject settings = new JSONObject(hero);
				settings.put("Ausfüllen", noFill.isSelected() ? "Nicht" : fillAll.isSelected() ? "Alles" : "Unveränderliches");
				settings.put("Name", showName.isSelected());
				settings.put("Datum", showDate.isSelected());
				settings.put("Datei", file.getAbsolutePath());
				for (final Sheet sheet : sheets.getItems()) {
					if (sheets.getCheckModel().isChecked(sheet)) {
						settings.put(sheet.toString(), sheet.getSettings(settings));
					}
				}
				hero.put("Heldenbogen", settings);
			}
		}
	}

	@Override
	protected void setHero(final int index) {
		super.setHero(index);
		hero = heroes.get(index);
		if (hero != null && hero.containsKey("Heldenbogen")) {
			final JSONObject settings = hero.getObj("Heldenbogen");
			final String filled = settings.getStringOrDefault("Ausfüllen", "Unveränderliches");
			switch (filled) {
				case "Nicht" -> noFill.setSelected(true);
				case "Alles" -> fillAll.setSelected(true);
				default -> fill.setSelected(true);
			}
			showName.setSelected(settings.getBoolOrDefault("Name", false));
			showDate.setSelected(settings.getBoolOrDefault("Datum", false));
		}
		checkAndOrderSheets();
	}

	@FXML
	private void show() {
		try {
			final File file = File.createTempFile("Heldenbogen_" + (hero != null ? hero.getObj("Biografie").getString("Vorname") : "Leer") + "_", ".pdf");
			create(file);
			Util.openFile(file);
		} catch (final IOException e) {
			ErrorLogger.logError(e);
		}
	}

}
