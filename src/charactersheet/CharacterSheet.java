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
package charactersheet;

import charactersheet.ui.SheetConfiguration;
import dsatool.credits.Credits;
import dsatool.gui.Main;
import dsatool.plugins.Plugin;
import dsatool.util.Util;

/**
 * A plugin for creating PDF character sheets
 *
 * @author Dominik Helm
 */
public class CharacterSheet extends Plugin {

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#getPluginName()
	 */
	@Override
	public String getPluginName() {
		return "CharacterSheet";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#initialize()
	 */
	@Override
	public void initialize() {
		Credits.credits.add(new Credits("Heldenbrief-Design inspiriert von Mario Rauschenberg", null, null, "http://www.dsa-hamburg.de", null));
		Credits.credits.add(new Credits("Trefferzonengrafiken (Achaz, Amazone, Magier) von Alexander Kremel",
				"mit freundlicher Genehmigung durch Mario Rauschenberg", null, null, null));
		Credits.credits.add(new Credits("BoxTable\nCopyright (c) 2017 Dominik Helm", "Apache License 2.0", Util.getAppDir() + "/licenses/ALv2.txt",
				"http://github.com/errt/BoxTable", null));
		Credits.credits.add(new Credits("PDFBox, FontBox, Apache Commons Logging\nCopyright (c) 2002-2017 The Apache Software Foundation", "Apache License 2.0",
				Util.getAppDir() + "/licenses/ALv2.txt", "http://pdfbox.apache.org/", null));
		Credits.credits.add(new Credits("Apache Commons Logging\nCopyright (c) 2003-2014 The Apache Software Foundation", "Apache License 2.0",
				Util.getAppDir() + "/licenses/ALv2.txt", "http://commons.apache.org/proper/commons-logging/", null));
		Credits.credits.add(new Credits("Linux Libertine\nCopyright (c) 2003–2012 Philipp H. Poll", "SIL Open Font License 1.1",
				Util.getAppDir() + "/licenses/LinLibertine-OFL.txt", "http://linuxlibertine.org/", null));
		Main.addDetachableToolComposite("Helden", "Heldenbogen", 900, 800, () -> new SheetConfiguration().getRoot());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#load()
	 */
	@Override
	public void load() {}
}
