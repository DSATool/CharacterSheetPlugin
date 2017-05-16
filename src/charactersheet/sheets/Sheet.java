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
	protected static Consumer<TableEvent> header = null;

	protected boolean fill;
	protected boolean fillAll;
	protected JSONObject hero;
	protected final SettingsPage settings = new SettingsPage();
	protected final BooleanProperty separatePage = new SimpleBooleanProperty(true);
	protected final BooleanProperty emptyPage = new SimpleBooleanProperty(false);
	protected PDRectangle pageSize = PDRectangle.A4;
	protected int height;

	protected Sheet(int height) {
		this.height = height;
	}

	public boolean check() {
		return true;
	}

	public abstract void create(PDDocument document) throws IOException;

	protected void endCreate(PDDocument document) {
		if (emptyPage.get()) {
			document.addPage(new PDPage(document.getPage(document.getNumberOfPages() - 1).getMediaBox()));
		}
	}

	public Node getControl() {
		return settings.getControl();
	}

	public void load() {
		settings.addBooleanChoice("Als eigenständigen Bogen drucken", separatePage);
		settings.addBooleanChoice("Leerseite einfügen", emptyPage);
		settings.addSeparator();
	}

	public void setFill(final boolean fill, final boolean fillAll) {
		this.fill = fill;
		this.fillAll = fillAll;
	}

	@Override
	public void setHero(final JSONObject hero) {
		this.hero = hero;
	}

	protected void startCreate() {
		final float oldBottom = separatePage.get() ? height : bottom.bottom;
		bottom = new BottomObserver(height);
		bottom.bottom = oldBottom;
	}

	protected void startCreate(PDDocument document) throws IOException {
		if (separatePage.get() || !SheetUtil.matchesPageSize(document, pageSize)) {
			final PDPage page = new PDPage(pageSize);
			document.addPage(page);
			if (header != null) {
				final PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true);
				header.accept(new TableEvent(document, stream, 0, pageSize.getHeight(), pageSize.getWidth(), pageSize.getHeight()));
				stream.close();
			}
		}
		startCreate();
	}

	@Override
	public String toString() {
		return "Unbenannt";
	}
}
