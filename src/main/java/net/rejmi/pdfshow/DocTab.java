package net.rejmi.pdfshow;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * A visual rep of one PDF document, for placing within a TabView
 */
@SuppressWarnings("serial")
class DocTab extends JPanel {

	private JScrollBar sbar;
	private JComponent pdfComponent;
	/** zero-origin pageNumber, not from document's page numbering */
	private int pageNumber = 0;
	/** Total size of this document */
	int pageCount = 0;
	/** The current PDF */
	PDDocument doc;
	/** The disk file we got it from */
	File file;
	/** The PdfBox renderer for it */
	PDFRenderer renderer;
	/** Scaling to make the document fit */
	float scaleX, scaleY;
	/** Our user's annotations for this doc, indexed by page# */
	List<List<GObject>> addIns;
	
	/** Construct one Document Tab */
	DocTab(File file) throws IOException {
		super();

		// PDF stuff
		this.file = file;
		this.doc = PDDocument.load(file);

		pageCount = doc.getNumberOfPages();
		renderer = new PDFRenderer(doc);
		addIns = new ArrayList<>(pageCount);
		for (int i = 0; i < pageCount; i++) {
			addIns.add(new ArrayList<>());
		}

		// GUI stuff
		setDoubleBuffered(true);
		setLayout(new BorderLayout());
		pdfComponent = new MainComponent();
		add(pdfComponent, BorderLayout.CENTER);
		sbar = new JScrollBar(JScrollBar.VERTICAL, 0, 1, 0, pageCount);
		sbar.addAdjustmentListener(e -> {
			if (e.getValueIsAdjusting())
				return;
			setPageNumber(e.getValue());
		});
		add(sbar, BorderLayout.EAST);
	}

	/**
	 * Scale PDF to fit - after main window is fully sized.
	 * Scaling is quick & dirty: works for slide decks, not for books(!).
	 * XXX Must either constrain ratio or allow to manually adjust.
	 */
	void computeScaling() {
		final PDRectangle pdfBBox = doc.getPage(0).getBBox();
		// System.out.println("BBox: " + pdfBBox);
		final Dimension compSize = pdfComponent.getSize();
		// System.out.println("Component size = " + compSize);
		scaleX = (float)(compSize.getWidth() / pdfBBox.getWidth());
		scaleY = (float)(compSize.getHeight() / pdfBBox.getHeight());
		// System.out.println("Computed scaling as " + scaleX + "," + scaleY);
	}

	void setPageNumber(int page) {
		pageNumber = page;
		sbar.setValue(pageNumber);
		pdfComponent.repaint();
		PdfShow.instance.pageNumberChanged();
	}

	int getPageNumber() {
		return pageNumber;
	}
	
	List<GObject> getCurrentAddIns() {
		return addIns.get(pageNumber);
	}
	
	void gotoNext() {
		if (pageNumber == pageCount)
			return;
		setPageNumber(pageNumber + 1);
	}

	void gotoPrev() {
		if (pageNumber == 0) {
			return;
		}
		setPageNumber(pageNumber - 1);
	}

	void insertNewPage() {
		final PDPageTree pageTree = doc.getPages();
		PDPage curPage = pageTree.get(pageNumber);
		PDPage newPage = new PDPage();
		pageTree.insertAfter(newPage, curPage);
		pageCount = doc.getNumberOfPages();
		addIns.add(pageNumber + 1, new ArrayList<GObject>());
		gotoNext();
	}

	int addIn(GObject gobj) {
		int ix = addIns.get(pageNumber).size();
		addIns.get(pageNumber).add(gobj);
		return ix;
	}

	/** Replace an object (for rubber-banding) */
	void setIn(int ix, GObject gobj) {
		if (addIns.get(pageNumber) == null)
			throw new IllegalStateException("setIn with no list!");
		addIns.get(pageNumber).set(ix, gobj);
	}

	void removeLastIn() {
		List<GObject> l = addIns.get(pageNumber);
		if (l != null && !l.isEmpty())
			l.remove(l.size() - 1);
	}

	void removeIn(int ix) {
		addIns.get(pageNumber).remove(ix);
	}

	void deleteAll() {
		addIns.get(pageNumber).clear();
		repaint();
	}

	void close() {
		try {
			doc.close();
			doc = null;
		} catch (IOException e) {
			e.printStackTrace();	// Nobody wants to listen to your chuntering.
		}
	}

	class MainComponent extends JComponent {
	/**
	 * Draw the stuff on this page, in the correct 1-2-3 order
	 */
	@Override
	protected void paintComponent(Graphics g) {
		// 1) Super
		super.paintComponent(g);
		try {
			// 2) PdfBox - whole page
			if (scaleX == 0) {
				computeScaling();
			}
			renderer.renderPageToGraphics(pageNumber, (Graphics2D) g, scaleX, scaleY);
			// 3) Our annotations, if any
			if (!addIns.get(pageNumber).isEmpty()) {
				for (GObject obj : addIns.get(pageNumber)) {
					obj.draw(g);
				}
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(PdfShow.frame, "Failure: " + e);
		}
	}
	}
}
