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
package charactersheet.util;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import dsatool.util.Util;

public class FontManager {

	public static PDFont serif;
	public static PDFont serifBold;
	public static PDFont serifItalic;

	public static void initFonts(final PDDocument document) throws IOException {
		serif = PDType0Font.load(document, new File(Util.getAppDir() + "/resources/fonts/LinLibertine_Rah.ttf"));
		serifBold = PDType0Font.load(document, new File(Util.getAppDir() + "/resources/fonts/LinLibertine_RBah.ttf"));
		serifItalic = PDType0Font.load(document, new File(Util.getAppDir() + "/resources/fonts/LinLibertine_RIah.ttf"));
	}

}
