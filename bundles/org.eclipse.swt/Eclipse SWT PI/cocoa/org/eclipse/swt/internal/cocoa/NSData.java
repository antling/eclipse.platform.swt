/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.cocoa;

public class NSData extends NSObject {

public NSData() {
	super();
}

public NSData(long /*int*/ id) {
	super(id);
}

public NSData(id id) {
	super(id);
}

public long /*int*/ bytes() {
	return OS.objc_msgSend(this.id, OS.sel_bytes);
}

public static NSData dataWithBytes(byte[] bytes, long /*int*/ length) {
	long /*int*/ result = OS.objc_msgSend(OS.class_NSData, OS.sel_dataWithBytes_length_, bytes, length);
	return result != 0 ? new NSData(result) : null;
}

public void getBytes(byte[] buffer) {
	OS.objc_msgSend(this.id, OS.sel_getBytes_, buffer);
}

public void getBytes(long /*int*/ buffer, long /*int*/ length) {
	OS.objc_msgSend(this.id, OS.sel_getBytes_length_, buffer, length);
}

public long /*int*/ length() {
	return OS.objc_msgSend(this.id, OS.sel_length);
}

}
