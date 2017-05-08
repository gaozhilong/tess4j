/**
 * Copyright @ 2012 Quan Nguyen
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.tess4j;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.imageio.IIOImage;

import org.slf4j.LoggerFactory;

import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.PointerByReference;

import net.sourceforge.lept4j.Box;
import net.sourceforge.lept4j.Boxa;
import net.sourceforge.lept4j.ILeptonica;
import net.sourceforge.lept4j.Leptonica;
import net.sourceforge.tess4j.ITessAPI.TessBaseAPI;
import net.sourceforge.tess4j.ITessAPI.TessOcrEngineMode;
import net.sourceforge.tess4j.ITessAPI.TessPageIterator;
import net.sourceforge.tess4j.ITessAPI.TessResultIterator;
import net.sourceforge.tess4j.ITessAPI.TessResultRenderer;
import net.sourceforge.tess4j.util.ImageIOHelper;
import net.sourceforge.tess4j.util.LoggHelper;
import net.sourceforge.tess4j.util.PdfUtilities;

/**
 * An object layer on top of <code>TessAPI</code>, provides character
 * recognition support for common image formats, and multi-page TIFF images
 * beyond the uncompressed, binary TIFF format supported by Tesseract OCR
 * engine. The extended capabilities are provided by the
 * <code>Java Advanced Imaging Image I/O Tools</code>.<br>
 * <br>
 * Support for PDF documents is available through <code>Ghost4J</code>, a
 * <code>JNA</code> wrapper for <code>GPL Ghostscript</code>, which should be
 * installed and included in system path.<br>
 * <br>
 * Any program that uses the library will need to ensure that the required
 * libraries (the <code>.jar</code> files for <code>jna</code>,
 * <code>jai-imageio</code>, and <code>ghost4j</code>) are in its compile and
 * run-time <code>classpath</code>.
 */
public class Tesseract implements ITesseract {

    private static Tesseract instance;
    private String language = "eng";
    private String datapath = "./";
    private RenderedFormat renderedFormat = RenderedFormat.TEXT;
    private int psm = -1;
    private int ocrEngineMode = TessOcrEngineMode.OEM_DEFAULT;
    private final Properties prop = new Properties();
    private final List<String> configList = new ArrayList<String>();

    private TessAPI api;
    private TessBaseAPI handle;

    private static final org.slf4j.Logger logger = LoggerFactory
        .getLogger(new LoggHelper().toString());

    /**
     * Returns TessAPI object.
     *
     * @return api
     */
    protected TessAPI getAPI() {
        return this.api;
    }

    /**
     * Returns API handle.
     *
     * @return handle
     */
    protected TessBaseAPI getHandle() {
        return this.handle;
    }

    /**
     * Gets an instance of the class library.
     *
     * @deprecated As of Release 2.0, use default constructor instead.
     * @return instance
     */
    @Deprecated
    public static synchronized Tesseract getInstance() {
        if (Tesseract.instance == null) {
            Tesseract.instance = new Tesseract();
        }

        return Tesseract.instance;
    }

    /**
     * Sets path to <code>tessdata</code>.
     *
     * @param datapath
     *        the tessdata path to set
     */
    @Override
    public void setDatapath(String datapath) {
        this.datapath = datapath;
    }

    /**
     * Sets language for OCR.
     *
     * @param language
     *        the language code, which follows ISO 639-3 standard.
     */
    @Override
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Sets OCR engine mode.
     *
     * @param ocrEngineMode
     *        the OcrEngineMode to set
     */
    @Override
    public void setOcrEngineMode(int ocrEngineMode) {
        this.ocrEngineMode = ocrEngineMode;
    }

    /**
     * Sets page segmentation mode.
     *
     * @param mode
     *        the page segmentation mode to set
     */
    @Override
    public void setPageSegMode(int mode) {
        this.psm = mode;
    }

    /**
     * Enables hocr output.
     *
     * @param hocr
     *        to enable or disable hocr output
     */
    public void setHocr(boolean hocr) {
        this.renderedFormat = hocr ? RenderedFormat.HOCR : RenderedFormat.TEXT;
        this.prop.setProperty("tessedit_create_hocr", hocr ? "1" : "0");
    }

    /**
     * Set the value of Tesseract's internal parameter.
     *
     * @param key
     *        variable name, e.g., <code>tessedit_create_hocr</code>,
     *        <code>tessedit_char_whitelist</code>, etc.
     * @param value
     *        value for corresponding variable, e.g., "1", "0",
     *        "0123456789", etc.
     */
    @Override
    public void setTessVariable(String key, String value) {
        this.prop.setProperty(key, value);
    }

    /**
     * Sets configs to be passed to Tesseract's <code>Init</code> method.
     *
     * @param configs
     *        list of config filenames, e.g., "digits", "bazaar",
     *        "quiet"
     */
    @Override
    public void setConfigs(List<String> configs) {
        this.configList.clear();
        if (configs != null) {
            this.configList.addAll(configs);
        }
    }

    /**
     * Performs OCR operation.
     *
     * @param imageFile
     *        an image file
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(File imageFile) throws TesseractException {
        return this.doOCR(imageFile, null);
    }

    /**
     * Performs OCR operation.
     *
     * @param imageFile
     *        an image file
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(File imageFile, Rectangle rect)
            throws TesseractException {
        try {
            return this.doOCR(ImageIOHelper.getIIOImageList(imageFile),
                imageFile.getPath(), rect);
        } catch (Exception e) {
            Tesseract.logger.error(e.getMessage(), e);
            throw new TesseractException(e);
        }
    }

    /**
     * Performs OCR operation.
     *
     * @param bi
     *        a buffered image
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(BufferedImage bi) throws TesseractException {
        return this.doOCR(bi, null);
    }

    /**
     * Performs OCR operation.
     *
     * @param bi
     *        a buffered image
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(BufferedImage bi, Rectangle rect)
            throws TesseractException {
        try {
            return this.doOCR(ImageIOHelper.getIIOImageList(bi), rect);
        } catch (Exception e) {
            Tesseract.logger.error(e.getMessage(), e);
            throw new TesseractException(e);
        }
    }

    /**
     * Performs OCR operation.
     *
     * @param imageList
     *        a list of <code>IIOImage</code> objects
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(List<IIOImage> imageList, Rectangle rect)
            throws TesseractException {
        return this.doOCR(imageList, null, rect);
    }

    /**
     * Performs OCR operation.
     *
     * @param imageList
     *        a list of <code>IIOImage</code> objects
     * @param filename
     *        input file name. Needed only for training and reading a
     *        UNLV zone file.
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(List<IIOImage> imageList, String filename,
            Rectangle rect) throws TesseractException {
        this.init();
        this.setTessVariables();

        try {
            StringBuilder sb = new StringBuilder();
            int pageNum = 0;

            for (IIOImage oimage : imageList) {
                pageNum++;
                try {
                    this.setImage(oimage.getRenderedImage(), rect);
                    sb.append(this.getOCRText(filename, pageNum));
                } catch (IOException ioe) {
                    // skip the problematic image
                    Tesseract.logger.error(ioe.getMessage(), ioe);
                }
            }

            if (this.renderedFormat == RenderedFormat.HOCR) {
                sb.insert(0, ITesseract.htmlBeginTag)
                    .append(ITesseract.htmlEndTag);
            }

            return sb.toString();
        } finally {
            this.dispose();
        }
    }

    /**
     * Performs OCR operation. Use <code>SetImage</code>, (optionally)
     * <code>SetRectangle</code>, and one or more of the <code>Get*Text</code>
     * functions.
     *
     * @param xsize
     *        width of image
     * @param ysize
     *        height of image
     * @param buf
     *        pixel data
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @param bpp
     *        bits per pixel, represents the bit depth of the image, with 1
     *        for binary bitmap, 8 for gray, and 24 for color RGB.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(int xsize, int ysize, ByteBuffer buf, Rectangle rect,
            int bpp) throws TesseractException {
        return this.doOCR(xsize, ysize, buf, null, rect, bpp);
    }

    /**
     * Performs OCR operation. Use <code>SetImage</code>, (optionally)
     * <code>SetRectangle</code>, and one or more of the <code>Get*Text</code>
     * functions.
     *
     * @param xsize
     *        width of image
     * @param ysize
     *        height of image
     * @param buf
     *        pixel data
     * @param filename
     *        input file name. Needed only for training and reading a
     *        UNLV zone file.
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @param bpp
     *        bits per pixel, represents the bit depth of the image, with 1
     *        for binary bitmap, 8 for gray, and 24 for color RGB.
     * @return the recognized text
     * @throws TesseractException
     */
    @Override
    public String doOCR(int xsize, int ysize, ByteBuffer buf, String filename,
            Rectangle rect, int bpp) throws TesseractException {
        this.init();
        this.setTessVariables();

        try {
            this.setImage(xsize, ysize, buf, rect, bpp);
            return this.getOCRText(filename, 1);
        } catch (Exception e) {
            Tesseract.logger.error(e.getMessage(), e);
            throw new TesseractException(e);
        } finally {
            this.dispose();
        }
    }

    /**
     * Initializes Tesseract engine.
     */
    protected void init() {
        this.api = TessAPI.INSTANCE;
        this.handle = this.api.TessBaseAPICreate();
        StringArray sarray = new StringArray(
            this.configList.toArray(new String[0]));
        PointerByReference configs = new PointerByReference();
        configs.setPointer(sarray);
        this.api.TessBaseAPIInit1(this.handle, this.datapath, this.language,
            this.ocrEngineMode, configs, this.configList.size());
        if (this.psm > -1) {
            this.api.TessBaseAPISetPageSegMode(this.handle, this.psm);
        }
    }

    /**
     * Sets Tesseract's internal parameters.
     */
    protected void setTessVariables() {
        Enumeration<?> em = this.prop.propertyNames();
        while (em.hasMoreElements()) {
            String key = (String) em.nextElement();
            this.api.TessBaseAPISetVariable(this.handle, key,
                this.prop.getProperty(key));
        }
    }

    /**
     * A wrapper for {@link #setImage(int, int, ByteBuffer, Rectangle, int)}.
     *
     * @param image
     *        a rendered image
     * @param rect
     *        region of interest
     * @throws java.io.IOException
     */
    protected void setImage(RenderedImage image, Rectangle rect)
            throws IOException {
        this.setImage(image.getWidth(), image.getHeight(),
            ImageIOHelper.getImageByteBuffer(image), rect,
            image.getColorModel().getPixelSize());
    }

    /**
     * Sets image to be processed.
     *
     * @param xsize
     *        width of image
     * @param ysize
     *        height of image
     * @param buf
     *        pixel data
     * @param rect
     *        the bounding rectangle defines the region of the image to be
     *        recognized. A rectangle of zero dimension or <code>null</code>
     *        indicates
     *        the whole image.
     * @param bpp
     *        bits per pixel, represents the bit depth of the image, with 1
     *        for binary bitmap, 8 for gray, and 24 for color RGB.
     */
    protected void setImage(int xsize, int ysize, ByteBuffer buf,
            Rectangle rect, int bpp) {
        int bytespp = bpp / 8;
        int bytespl = (int) Math.ceil(xsize * bpp / 8.0);
        this.api.TessBaseAPISetImage(this.handle, buf, xsize, ysize, bytespp,
            bytespl);

        if (rect != null && !rect.isEmpty()) {
            this.api.TessBaseAPISetRectangle(this.handle, rect.x, rect.y,
                rect.width, rect.height);
        }
    }

    /**
     * Gets recognized text.
     *
     * @param filename
     *        input file name. Needed only for reading a UNLV zone
     *        file.
     * @param pageNum
     *        page number; needed for hocr paging.
     * @return the recognized text
     */
    protected synchronized String getOCRText(String filename, int pageNum) {
        if (filename != null && !filename.isEmpty()) {
            this.api.TessBaseAPISetInputName(this.handle, filename);
        }

        Pointer utf8Text = this.renderedFormat == RenderedFormat.HOCR
            ? this.api.TessBaseAPIGetHOCRText(this.handle, pageNum - 1)
            : this.api.TessBaseAPIGetUTF8Text(this.handle);
        String str = utf8Text.getString(0);
        this.api.TessDeleteText(utf8Text);
        return str;
    }

    /**
     * Creates renderers for given formats.
     *
     * @param outputbase
     * @param formats
     * @return
     */
    private TessResultRenderer createRenderers(String outputbase,
            List<RenderedFormat> formats) {
        TessResultRenderer renderer = null;

        for (RenderedFormat format : formats) {
            switch (format) {
                case TEXT:
                    if (renderer == null) {
                        renderer = this.api.TessTextRendererCreate(outputbase);
                    } else {
                        this.api.TessResultRendererInsert(renderer,
                            this.api.TessTextRendererCreate(outputbase));
                    }
                    break;
                case HOCR:
                    if (renderer == null) {
                        renderer = this.api.TessHOcrRendererCreate(outputbase);
                    } else {
                        this.api.TessResultRendererInsert(renderer,
                            this.api.TessHOcrRendererCreate(outputbase));
                    }
                    break;
                case PDF:
                    String dataPath = this.api
                        .TessBaseAPIGetDatapath(this.handle);
                    boolean textonly = String.valueOf(ITessAPI.TRUE)
                        .equals(this.prop.getProperty("textonly_pdf"));
                    if (renderer == null) {
                        renderer = this.api.TessPDFRendererCreate(outputbase,
                            dataPath,
                            textonly ? ITessAPI.TRUE : ITessAPI.FALSE);
                    } else {
                        this.api.TessResultRendererInsert(renderer,
                            this.api.TessPDFRendererCreate(outputbase, dataPath,
                                textonly ? ITessAPI.TRUE : ITessAPI.FALSE));
                    }
                    break;
                case BOX:
                    if (renderer == null) {
                        renderer = this.api
                            .TessBoxTextRendererCreate(outputbase);
                    } else {
                        this.api.TessResultRendererInsert(renderer,
                            this.api.TessBoxTextRendererCreate(outputbase));
                    }
                    break;
                case UNLV:
                    if (renderer == null) {
                        renderer = this.api.TessUnlvRendererCreate(outputbase);
                    } else {
                        this.api.TessResultRendererInsert(renderer,
                            this.api.TessUnlvRendererCreate(outputbase));
                    }
                    break;
            }
        }

        return renderer;
    }

    /**
     * Creates documents for given renderer.
     *
     * @param filename
     *        input image
     * @param outputbase
     *        output filename without extension
     * @param formats
     *        types of renderer
     * @throws TesseractException
     */
    @Override
    public void createDocuments(String filename, String outputbase,
            List<RenderedFormat> formats) throws TesseractException {
        this.createDocuments(new String[] { filename },
            new String[] { outputbase }, formats);
    }

    /**
     * Creates documents.
     *
     * @param filenames
     *        array of input files
     * @param outputbases
     *        array of output filenames without extension
     * @param formats
     *        types of renderer
     * @throws TesseractException
     */
    @Override
    public void createDocuments(String[] filenames, String[] outputbases,
            List<RenderedFormat> formats) throws TesseractException {
        if (filenames.length != outputbases.length) {
            throw new RuntimeException("The two arrays must match in length.");
        }

        this.init();
        this.setTessVariables();

        try {
            for (int i = 0; i < filenames.length; i++) {
                File workingTiffFile = null;
                try {
                    String filename = filenames[i];

                    // if PDF, convert to multi-page TIFF
                    if (filename.toLowerCase().endsWith(".pdf")) {
                        workingTiffFile = PdfUtilities
                            .convertPdf2Tiff(new File(filename));
                        filename = workingTiffFile.getPath();
                    }

                    TessResultRenderer renderer = this
                        .createRenderers(outputbases[i], formats);
                    this.createDocuments(filename, renderer);
                    this.api.TessDeleteResultRenderer(renderer);
                } catch (Exception e) {
                    // skip the problematic image file
                    Tesseract.logger.error(e.getMessage(), e);
                } finally {
                    if (workingTiffFile != null && workingTiffFile.exists()) {
                        workingTiffFile.delete();
                    }
                }
            }
        } finally {
            this.dispose();
        }
    }

    /**
     * Creates documents.
     *
     * @param filename
     *        input file
     * @param renderer
     *        renderer
     * @throws TesseractException
     */
    private void createDocuments(String filename, TessResultRenderer renderer)
            throws TesseractException {
        this.api.TessBaseAPISetInputName(this.handle, filename); //for reading a UNLV zone file
        int result = this.api.TessBaseAPIProcessPages(this.handle, filename,
            null, 0, renderer);

        if (result == ITessAPI.FALSE) {
            throw new TesseractException("Error during processing page.");
        }
    }

    /**
     * Gets segmented regions at specified page iterator level.
     *
     * @param bi
     *        input image
     * @param pageIteratorLevel
     *        TessPageIteratorLevel enum
     * @return list of <code>Rectangle</code>
     * @throws TesseractException
     */
    @Override
    public List<Rectangle> getSegmentedRegions(BufferedImage bi,
            int pageIteratorLevel) throws TesseractException {
        this.init();
        this.setTessVariables();

        try {
            List<Rectangle> list = new ArrayList<Rectangle>();
            this.setImage(bi, null);

            Boxa boxes = this.api.TessBaseAPIGetComponentImages(this.handle,
                pageIteratorLevel, ITessAPI.TRUE, null, null);
            Leptonica leptInstance = Leptonica.INSTANCE;
            int boxCount = leptInstance.boxaGetCount(boxes);
            for (int i = 0; i < boxCount; i++) {
                Box box = leptInstance.boxaGetBox(boxes, i, ILeptonica.L_CLONE);
                if (box == null) {
                    continue;
                }
                list.add(new Rectangle(box.x, box.y, box.w, box.h));
                PointerByReference pRef = new PointerByReference();
                pRef.setValue(box.getPointer());
                leptInstance.boxDestroy(pRef);
            }

            PointerByReference pRef = new PointerByReference();
            pRef.setValue(boxes.getPointer());
            leptInstance.boxaDestroy(pRef);

            return list;
        } catch (IOException ioe) {
            // skip the problematic image
            Tesseract.logger.error(ioe.getMessage(), ioe);
            throw new TesseractException(ioe);
        } finally {
            this.dispose();
        }
    }

    /**
     * Gets recognized words at specified page iterator level.
     *
     * @param bi
     *        input image
     * @param pageIteratorLevel
     *        TessPageIteratorLevel enum
     * @return list of <code>Word</code>
     */
    @Override
    public List<Word> getWords(BufferedImage bi, int pageIteratorLevel) {
        this.init();
        this.setTessVariables();

        List<Word> words = new ArrayList<Word>();

        try {
            this.setImage(bi, null);

            this.api.TessBaseAPIRecognize(this.handle, null);
            TessResultIterator ri = this.api
                .TessBaseAPIGetIterator(this.handle);
            TessPageIterator pi = this.api
                .TessResultIteratorGetPageIterator(ri);
            this.api.TessPageIteratorBegin(pi);

            do {
                Pointer ptr = this.api.TessResultIteratorGetUTF8Text(ri,
                    pageIteratorLevel);
                String text = ptr.getString(0);
                this.api.TessDeleteText(ptr);
                float confidence = this.api.TessResultIteratorConfidence(ri,
                    pageIteratorLevel);
                IntBuffer leftB = IntBuffer.allocate(1);
                IntBuffer topB = IntBuffer.allocate(1);
                IntBuffer rightB = IntBuffer.allocate(1);
                IntBuffer bottomB = IntBuffer.allocate(1);
                this.api.TessPageIteratorBoundingBox(pi, pageIteratorLevel,
                    leftB, topB, rightB, bottomB);
                int left = leftB.get();
                int top = topB.get();
                int right = rightB.get();
                int bottom = bottomB.get();
                Word word = new Word(text, confidence,
                    new Rectangle(left, top, right - left, bottom - top));
                words.add(word);
            } while (this.api.TessPageIteratorNext(pi,
                pageIteratorLevel) == ITessAPI.TRUE);

            return words;
        } catch (Exception e) {
            return words;
        } finally {
            this.dispose();
        }
    }

    /**
     * Releases all of the native resources used by this instance.
     */
    protected void dispose() {
        this.api.TessBaseAPIDelete(this.handle);
    }
}
