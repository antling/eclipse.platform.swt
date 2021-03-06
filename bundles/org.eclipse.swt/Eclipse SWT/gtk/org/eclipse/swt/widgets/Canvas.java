/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;


import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.gtk.*;

/**
 * Instances of this class provide a surface for drawing
 * arbitrary graphics.
 * <dl>
 * <dt><b>Styles:</b></dt>
 * <dd>(none)</dd>
 * <dt><b>Events:</b></dt>
 * <dd>(none)</dd>
 * </dl>
 * <p>
 * This class may be subclassed by custom control implementors
 * who are building controls that are <em>not</em> constructed
 * from aggregates of other controls. That is, they are either
 * painted using SWT graphics calls or are handled by native
 * methods.
 * </p>
 *
 * @see Composite
 * @see <a href="http://www.eclipse.org/swt/snippets/#canvas">Canvas snippets</a>
 * @see <a href="http://www.eclipse.org/swt/examples.php">SWT Example: ControlExample</a>
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further information</a>
 */
public class Canvas extends Composite {
	Caret caret;
	IME ime;
	long /*int*/ cachedCairo;
	boolean blink, drawFlag;

Canvas () {}

/**
 * Constructs a new instance of this class given its parent
 * and a style value describing its behavior and appearance.
 * <p>
 * The style value is either one of the style constants defined in
 * class <code>SWT</code> which is applicable to instances of this
 * class, or must be built by <em>bitwise OR</em>'ing together
 * (that is, using the <code>int</code> "|" operator) two or more
 * of those <code>SWT</code> style constants. The class description
 * lists the style constants that are applicable to the class.
 * Style bits are also inherited from superclasses.
 * </p>
 *
 * @param parent a composite control which will be the parent of the new instance (cannot be null)
 * @param style the style of control to construct
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the parent is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
 * </ul>
 *
 * @see SWT
 * @see Widget#getStyle
 */
public Canvas (Composite parent, int style) {
	super (parent, checkStyle (style));
}

/**
 * Fills the interior of the rectangle specified by the arguments,
 * with the receiver's background.
 *
 * @param gc the gc where the rectangle is to be filled
 * @param x the x coordinate of the rectangle to be filled
 * @param y the y coordinate of the rectangle to be filled
 * @param width the width of the rectangle to be filled
 * @param height the height of the rectangle to be filled
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the gc is null</li>
 *    <li>ERROR_INVALID_ARGUMENT - if the gc has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.2
 */
public void drawBackground (GC gc, int x, int y, int width, int height) {
	drawBackground (gc, x, y, width, height, 0, 0);
}

/**
 * Returns the caret.
 * <p>
 * The caret for the control is automatically hidden
 * and shown when the control is painted or resized,
 * when focus is gained or lost and when an the control
 * is scrolled.  To avoid drawing on top of the caret,
 * the programmer must hide and show the caret when
 * drawing in the window any other time.
 * </p>
 *
 * @return the caret for the receiver, may be null
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public Caret getCaret () {
	checkWidget();
	return caret;
}

@Override
Point getIMCaretPos () {
	if (caret == null) return super.getIMCaretPos ();
	return new Point (caret.x, caret.y);
}

/**
 * Returns the IME.
 *
 * @return the IME
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.4
 */
public IME getIME () {
	checkWidget ();
	return ime;
}

@Override
long /*int*/ gtk_button_press_event (long /*int*/ widget, long /*int*/ event) {
	if (ime != null) {
		long /*int*/ result = ime.gtk_button_press_event (widget, event);
		if (result != 0) return result;
	}
	return  super.gtk_button_press_event (widget, event);
}

@Override
long /*int*/ gtk_commit (long /*int*/ imcontext, long /*int*/ text) {
	if (ime != null) {
		long /*int*/ result = ime.gtk_commit (imcontext, text);
		if (result != 0) return result;
	}
	return super.gtk_commit (imcontext, text);
}

@Override
long /*int*/ gtk_draw (long /*int*/ widget, long /*int*/ cairo) {
	if ((state & OBSCURED) != 0) return 0;
	long /*int*/ result;
	if ( GTK.GTK_VERSION < OS.VERSION(3, 22, 0)) {
		boolean isFocus = caret != null && caret.isFocusCaret ();
		if (isFocus) caret.killFocus ();
		 result = super.gtk_draw (widget, cairo);
		if (isFocus) caret.setFocus ();
	} else {
		this.cachedCairo = cairo;
		result = super.gtk_draw (widget, cairo);
		/*
		 *  blink is needed to be checked as gtk_draw() signals sent from other parts of the canvas
		 *  can interfere with the blinking state. This will ensure that we are only draw/redrawing the
		 *  caret when it is intended to. See Bug 517487.
		 *
		 *  Additionally, only draw the caret if it has focus. See bug 528819.
		 */
		if (caret != null && blink == true && caret.isFocusCaret()) {
			drawCaret(widget,cairo);
			blink = false;
		}
	}
	return result;
}

private void drawCaret (long /*int*/ widget, long /*int*/ cairo) {
	if(this.isDisposed()) return;
	if (cairo == 0) error(SWT.ERROR_NO_HANDLES);
	if (drawFlag) {
		Cairo.cairo_save(cairo);
		if (caret.image != null && !caret.image.isDisposed() && caret.image.mask == 0) {
			Cairo.cairo_set_source_rgb(cairo, 1, 1, 1);
			Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_DIFFERENCE);
			long /*int*/ surface = Cairo.cairo_get_target(cairo);
			int nWidth = 0;
			switch (Cairo.cairo_surface_get_type(surface)) {
				case Cairo.CAIRO_SURFACE_TYPE_IMAGE:
					nWidth = Cairo.cairo_image_surface_get_width(surface);
					break;
				case Cairo.CAIRO_SURFACE_TYPE_XLIB:
					nWidth = Cairo.cairo_xlib_surface_get_width(surface);
					break;
			}
			int nX = caret.x;
			if ((style & SWT.MIRRORED) != 0) nX = getClientWidth () - nWidth - nX;
			Cairo.cairo_translate(cairo, nX, caret.y);
			Cairo.cairo_set_source_surface(cairo, caret.image.surface, 0, 0);
			Cairo.cairo_paint(cairo);
		} else {
			Cairo.cairo_set_source_rgb(cairo, 1, 1, 1);
			Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_DIFFERENCE);
			int nWidth = caret.width, nHeight = caret.height;
			if (nWidth <= 0) nWidth = Caret.DEFAULT_WIDTH;
			int nX = caret.x;
			if ((style & SWT.MIRRORED) != 0) nX = getClientWidth () - nWidth - nX;
			Cairo.cairo_rectangle(cairo, nX, caret.y, nWidth, nHeight);
			}
		Cairo.cairo_fill(cairo);
		Cairo.cairo_restore(cairo);
		drawFlag = false;
	} else {
		drawFlag = true;
		}
	return;
}

@Override
long /*int*/ gtk_focus_in_event (long /*int*/ widget, long /*int*/ event) {
	long /*int*/ result = super.gtk_focus_in_event (widget, event);
	if (caret != null) caret.setFocus ();
	return result;
}

@Override
long /*int*/ gtk_focus_out_event (long /*int*/ widget, long /*int*/ event) {
	long /*int*/ result = super.gtk_focus_out_event (widget, event);
	if (caret != null) caret.killFocus ();
	return result;
}

@Override
long /*int*/ gtk_preedit_changed (long /*int*/ imcontext) {
	if (ime != null) {
		long /*int*/ result = ime.gtk_preedit_changed (imcontext);
		if (result != 0) return result;
	}
	return super.gtk_preedit_changed (imcontext);
}

@Override
void redrawWidget (int x, int y, int width, int height, boolean redrawAll, boolean all, boolean trim) {
	boolean isFocus = caret != null && caret.isFocusCaret ();
	if (isFocus) caret.killFocus ();
	super.redrawWidget (x, y, width, height, redrawAll, all, trim);
	if (isFocus) caret.setFocus ();
}

@Override
void releaseChildren (boolean destroy) {
	if (caret != null) {
		caret.release (false);
		caret = null;
	}
	if (ime != null) {
		ime.release (false);
		ime = null;
	}
	super.releaseChildren (destroy);
}

@Override
void reskinChildren (int flags) {
	if (caret != null) caret.reskin (flags);
	if (ime != null)  ime.reskin (flags);
	super.reskinChildren (flags);
}

/**
 * Scrolls a rectangular area of the receiver by first copying
 * the source area to the destination and then causing the area
 * of the source which is not covered by the destination to
 * be repainted. Children that intersect the rectangle are
 * optionally moved during the operation. In addition, all outstanding
 * paint events are flushed before the source area is copied to
 * ensure that the contents of the canvas are drawn correctly.
 *
 * @param destX the x coordinate of the destination
 * @param destY the y coordinate of the destination
 * @param x the x coordinate of the source
 * @param y the y coordinate of the source
 * @param width the width of the area
 * @param height the height of the area
 * @param all <code>true</code>if children should be scrolled, and <code>false</code> otherwise
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void scroll (int destX, int destY, int x, int y, int width, int height, boolean all) {
	checkWidget();
	if (width <= 0 || height <= 0) return;
	Point destination = DPIUtil.autoScaleUp (new Point (destX, destY));
	Rectangle srcRect = DPIUtil.autoScaleUp (new Rectangle (x, y, width, height));
	scrollInPixels(destination.x, destination.y, srcRect.x, srcRect.y, srcRect.width, srcRect.height, all);
}

void scrollInPixels (int destX, int destY, int x, int y, int width, int height, boolean all) {
	long /*int*/ cairo = this.cachedCairo;
	if (cairo == 0) return;
	if ((style & SWT.MIRRORED) != 0) {
		int clientWidth = getClientWidth ();
		x = clientWidth - width - x;
		destX = clientWidth - width - destX;
	}
	int deltaX = destX - x, deltaY = destY - y;
	if (deltaX == 0 && deltaY == 0) return;
	if (!isVisible ()) return;
	boolean isFocus = caret != null && caret.isFocusCaret ();
	if (isFocus) caret.killFocus ();
	GdkRectangle clipRect = new GdkRectangle ();
	GDK.gdk_cairo_get_clip_rectangle (cairo, clipRect);
	cairo_rectangle_int_t srcRect = new cairo_rectangle_int_t ();
	srcRect.convertFromGdkRectangle(clipRect);
	long /*int*/ gdkResource = GTK.GTK4? paintSurface () : paintWindow ();
	long /*int*/ visibleRegion = Cairo.cairo_region_create_rectangle(srcRect);
	srcRect.x = x;
	srcRect.y = y;
	/*
	 * Feature in GTK: for 3.16+ the "visible" region in Canvas includes
	 * the scrollbar dimensions in its calculations. This means the "previous"
	 * location the scrollbars are re-painted when scrolling, causing the
	 * hopping effect. See bug 480458.
	 */
	if (GTK.GTK_VERSION >= OS.VERSION(3, 16, 0)) {
		long /*int*/ hBarHandle = 0;
		long /*int*/ vBarHandle = 0;
		if (GTK.GTK_IS_SCROLLED_WINDOW(scrolledHandle)) {
			hBarHandle = GTK.gtk_scrolled_window_get_hscrollbar (scrolledHandle);
			vBarHandle = GTK.gtk_scrolled_window_get_vscrollbar (scrolledHandle);
		}
		GtkRequisition requisition = new GtkRequisition();
		if (hBarHandle != 0) {
			gtk_widget_get_preferred_size (hBarHandle, requisition);
			if (requisition.height > 0) {
				srcRect.y = y - requisition.height;
			}
		}
		if (vBarHandle != 0) {
			gtk_widget_get_preferred_size (vBarHandle, requisition);
			if (requisition.width > 0) {
				srcRect.x = x - requisition.width;
			}
		}
	}
	srcRect.width = width;
	srcRect.height = height;
	long /*int*/ copyRegion = Cairo.cairo_region_create_rectangle (srcRect);
	Cairo.cairo_region_intersect(copyRegion, visibleRegion);
	long /*int*/ invalidateRegion = Cairo.cairo_region_create_rectangle (srcRect);
	Cairo.cairo_region_subtract (invalidateRegion, visibleRegion);
	Cairo.cairo_region_translate (invalidateRegion, deltaX, deltaY);
	cairo_rectangle_int_t copyRect = new cairo_rectangle_int_t();
	Cairo.cairo_region_get_extents (copyRegion, copyRect);
	if (copyRect.width != 0 && copyRect.height != 0) {
		update ();
	}
	Control control = findBackgroundControl ();
	if (control == null) control = this;
	if (control.backgroundImage != null) {
		redrawWidget (x, y, width, height, false, false, false);
		redrawWidget (destX, destY, width, height, false, false, false);
	} else {
		Cairo.cairo_push_group(cairo);
		Cairo.cairo_paint(cairo);
		Cairo.cairo_pop_group_to_source(cairo);
		double[] matrix = {1, 0, 0, 1, -deltaX, -deltaY};
		Cairo.cairo_pattern_set_matrix(Cairo.cairo_get_source(cairo), matrix);
		Cairo.cairo_rectangle(cairo, copyRect.x + deltaX, copyRect.y + deltaY, copyRect.width, copyRect.height);
		Cairo.cairo_clip(cairo);
		Cairo.cairo_paint(cairo);
		boolean disjoint = (destX + width < x) || (x + width < destX) || (destY + height < y) || (y + height < destY);
		if (disjoint) {
			cairo_rectangle_int_t cairoRect = new cairo_rectangle_int_t();
			cairoRect.x = x;
			cairoRect.y = y;
			cairoRect.width = width;
			cairoRect.height = height;
			Cairo.cairo_region_union_rectangle (invalidateRegion, cairoRect);
		} else {
			cairo_rectangle_int_t cairoRect = new cairo_rectangle_int_t();
			if (deltaX != 0) {
				int newX = destX - deltaX;
				if (deltaX < 0) newX = destX + width;
				cairoRect.x = newX;
				cairoRect.y = y;
				cairoRect.width = Math.abs(deltaX);
				cairoRect.height = height;
				Cairo.cairo_region_union_rectangle (invalidateRegion, cairoRect);
			}
			if (deltaY != 0) {
				int newY = destY - deltaY;
				if (deltaY < 0) newY = destY + height;
				cairoRect.x = x;
				cairoRect.y = newY;
				cairoRect.width = width;
				cairoRect.height = Math.abs(deltaY);
				Cairo.cairo_region_union_rectangle (invalidateRegion, cairoRect);
			}
		}
		if (GTK.GTK4) {
			GDK.gdk_surface_invalidate_region(gdkResource, invalidateRegion);
		} else {
			GDK.gdk_window_invalidate_region(gdkResource, invalidateRegion, all);
		}
	}
	Cairo.cairo_region_destroy (visibleRegion);
	Cairo.cairo_region_destroy (copyRegion);
	Cairo.cairo_region_destroy (invalidateRegion);
	if (all) {
		Control [] children = _getChildren ();
		for (int i=0; i<children.length; i++) {
			Control child = children [i];
			Rectangle childBounds = child.getBoundsInPixels ();
			if (Math.min(x + width, childBounds.x + childBounds.width) >= Math.max (x, childBounds.x) &&
				Math.min(y + height, childBounds.y + childBounds.height) >= Math.max (y, childBounds.y)) {
					child.setLocationInPixels (childBounds.x + deltaX, childBounds.y + deltaY);
			}
		}
	}
	if (isFocus) caret.setFocus ();
	/*
	 * Due to overlay drawing of scrollbars current method of scrolling leaves scrollbar and notifiers for them inside the canvas
	 * after scroll. Fix is to redraw once done.
	 */
	if (GTK.GTK_VERSION >= OS.VERSION(3, 16, 0)) {
		redraw(false);
	}
}

@Override
int setBounds (int x, int y, int width, int height, boolean move, boolean resize) {
	boolean isFocus = caret != null && caret.isFocusCaret ();
	if (isFocus) caret.killFocus ();
	int result = super.setBounds (x, y, width, height, move, resize);
	if (isFocus) caret.setFocus ();
	return result;
}

/**
 * Sets the receiver's caret.
 * <p>
 * The caret for the control is automatically hidden
 * and shown when the control is painted or resized,
 * when focus is gained or lost and when an the control
 * is scrolled.  To avoid drawing on top of the caret,
 * the programmer must hide and show the caret when
 * drawing in the window any other time.
 * </p>
 * @param caret the new caret for the receiver, may be null
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the caret has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setCaret (Caret caret) {
	checkWidget();
	Caret newCaret = caret;
	Caret oldCaret = this.caret;
	this.caret = newCaret;
	if (hasFocus ()) {
		if (oldCaret != null) oldCaret.killFocus ();
		if (newCaret != null) {
			if (newCaret.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);
			newCaret.setFocus ();
		}
	}
}

@Override
public void setFont (Font font) {
	checkWidget();
	if (caret != null) caret.setFont (font);
	super.setFont (font);
}

/**
 * Sets the receiver's IME.
 *
 * @param ime the new IME for the receiver, may be null
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the IME has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.4
 */
public void setIME (IME ime) {
	checkWidget ();
	if (ime != null && ime.isDisposed()) error(SWT.ERROR_INVALID_ARGUMENT);
	this.ime = ime;
}

void updateCaret () {
	long /*int*/ imHandle = imHandle ();
	if (imHandle == 0) return;
	GdkRectangle rect = new GdkRectangle ();
	rect.x = caret.x;
	rect.y = caret.y;
	rect.width = caret.width;
	rect.height = caret.height;
	GTK.gtk_im_context_set_cursor_location (imHandle, rect);
}

}
