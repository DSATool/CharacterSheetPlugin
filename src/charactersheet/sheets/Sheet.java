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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import jsonant.value.JSONObject;

public abstract class Sheet implements HeroController {
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
	protected final BooleanProperty separatePage = new SimpleBooleanProperty(true);
	protected final BooleanProperty emptyPage = new SimpleBooleanProperty(false);
	protected PDRectangle pageSize = PDRectangle.A4;

	protected int height;
	private final boolean canBeSeparate;

	protected Sheet(final int height) {
		this(height, true);
	}

	protected Sheet(final int height, final boolean canBeSeparate) {
		this.height = height;
		this.canBeSeparate = canBeSeparate;
	}

	public boolean check() {
		return true;
	}

	public abstract void create(PDDocument document) throws IOException;

	protected void endCreate(final PDDocument document) {
		if (emptyPage.get()) {
			document.addPage(new PDPage(document.getPage(document.getNumberOfPages() - 1).getMediaBox()));
		}
	}

	public Node getControl() {
		return settingsPage.getControl();
	}

	public abstract JSONObject getSettings(JSONObject parent);

	public void load() {
		if (canBeSeparate) {
			settingsPage.addBooleanChoice("Als eigenständigen Bogen drucken", separatePage);
		}
		settingsPage.addBooleanChoice("Leerseite einfügen", emptyPage);
		settingsPage.addSeparator();
	}

	public void loadSettings(final JSONObject settings) {
		separatePage.set(settings.getBoolOrDefault("Als eigenständigen Bogen drucken", true));
		emptyPage.set(settings.getBoolOrDefault("Leerseite einfügen", false));
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

		if (separatePage.get() || !SheetUtil.matchesPageSize(document, pageSize)) {
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
