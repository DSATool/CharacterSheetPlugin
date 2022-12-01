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
import java.util.Map;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import boxtable.event.TableEvent;
import charactersheet.util.SheetUtil;
import charactersheet.util.SheetUtil.BottomObserver;
import dsa41basis.ui.hero.HeroController;
import dsatool.settings.SettingsPage;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.TitledPane;
import jsonant.value.JSONObject;

public abstract class Sheet implements HeroController {

	protected static final String AS_SEPARATE_SHEET = "Als eigenständigen Bogen drucken";
	private static final String ADD_EMPTY_PAGE = "Leerseite einfügen";

	protected static BottomObserver bottom;
	protected static Consumer<TableEvent> header;

	static {
		reset();
	}

	public static void reset() {
		bottom = new BottomObserver(842);
		header = null;
	}

	protected boolean fill;

	protected boolean fillAll;
	protected boolean showName;
	protected boolean showDate;
	protected JSONObject hero;
	protected final SettingsPage settingsPage = new SettingsPage();
	protected PDRectangle pageSize = PDRectangle.A4;
	protected int height;

	private final boolean canBeSeparate;

	protected final Map<String, TitledPane> sections = new HashMap<>();

	protected Sheet(final int height) {
		this(height, true);
	}

	protected Sheet(final int height, final boolean canBeSeparate) {
		this.height = height;
		this.canBeSeparate = canBeSeparate;
	}

	protected void addOwnPageOption(final SettingsPage settings, final TitledPane section) {
		final CheckMenuItem ownPageItem = new CheckMenuItem("Auf neuer Seite beginnen");
		section.getContextMenu().getItems().add(ownPageItem);
		settings.addProperty(section, AS_SEPARATE_SHEET, ownPageItem.selectedProperty());
	}

	public boolean check() {
		return true;
	}

	public abstract void create(PDDocument document) throws IOException;

	protected void endCreate(final PDDocument document) {
		if (settingsPage.getBool(ADD_EMPTY_PAGE).get()) {
			document.addPage(new PDPage(document.getPage(document.getNumberOfPages() - 1).getMediaBox()));
		}
	}

	public Node getControl() {
		return settingsPage.getControl();
	}

	public JSONObject getSettings(final JSONObject parent) {
		final JSONObject settings = new JSONObject(parent);
		if (canBeSeparate) {
			settings.put(AS_SEPARATE_SHEET, settingsPage.getBool(AS_SEPARATE_SHEET).get());
		}
		settings.put(ADD_EMPTY_PAGE, settingsPage.getBool(ADD_EMPTY_PAGE).get());
		return settings;
	}

	public void load() {
		if (canBeSeparate) {
			settingsPage.addBooleanChoice(AS_SEPARATE_SHEET);
		}
		settingsPage.addBooleanChoice(ADD_EMPTY_PAGE);
		settingsPage.addSeparator();
	}

	public void loadSettings(final JSONObject settings) {
		if (canBeSeparate) {
			settingsPage.getBool(AS_SEPARATE_SHEET).set(settings.getBoolOrDefault(AS_SEPARATE_SHEET, true));
		}
		settingsPage.getBool(ADD_EMPTY_PAGE).set(settings.getBoolOrDefault(ADD_EMPTY_PAGE, false));
	}

	protected void orderSections(final Collection<String> order) {
		int index = 0;
		for (final String key : order) {
			if (sections.containsKey(key)) {
				settingsPage.moveSection(sections.get(key), index);
				++index;
			}
		}
	}

	protected void separatePage(final PDDocument document, final SettingsPage settings, final TitledPane section) throws IOException {
		if (settings.getBool(section, AS_SEPARATE_SHEET).get() && bottom.bottom != bottom.top) {
			final PDPage page = new PDPage(pageSize);
			document.addPage(page);
			if (header != null) {
				final PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true);
				header.accept(new TableEvent(document, stream, 0, pageSize.getHeight(), pageSize.getWidth(), pageSize.getHeight()));
				stream.close();
			}
			bottom = new BottomObserver(height);
		}
	}

	public void setFill(final boolean fill, final boolean fillAll) {
		this.fill = fill;
		this.fillAll = fillAll;
	}

	@Override
	public void setHero(final JSONObject hero) {
		this.hero = hero;
		loadSettings(hero != null && hero.containsKey("Heldenbogen") ? hero.getObj("Heldenbogen").getObjOrDefault(toString(), new JSONObject(null))
				: new JSONObject(null));
	}

	public void setShowNameAndDate(final boolean showName, final boolean showDate) {
		this.showName = showName;
		this.showDate = showDate;
	}

	protected void startCreate(final PDDocument document) throws IOException {
		float oldBottom = bottom.bottom;

		if (!canBeSeparate || settingsPage.getBool(AS_SEPARATE_SHEET).get() || !SheetUtil.matchesPageSize(document, pageSize)) {
			final PDPage page = new PDPage(pageSize);
			document.addPage(page);
			oldBottom = height;
			if (header != null) {
				final PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true);
				header.accept(new TableEvent(document, stream, 0, pageSize.getHeight(), pageSize.getWidth(), pageSize.getHeight()));
				stream.close();
			}
		}

		bottom = new BottomObserver(height);
		bottom.bottom = oldBottom;

		final PDOutlineItem bookmark = new PDOutlineItem();
		bookmark.setDestination(document.getPage(document.getNumberOfPages() - 1));
		bookmark.setTitle(toString());
		document.getDocumentCatalog().getDocumentOutline().addLast(bookmark);
	}

	@Override
	public String toString() {
		return "Unbenannt";
	}
}
