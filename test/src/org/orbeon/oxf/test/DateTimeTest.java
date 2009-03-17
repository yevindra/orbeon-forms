/**
 *  Copyright (C) 2009 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.test;

import junit.framework.TestCase;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;


public class DateTimeTest extends TestCase {

    protected void setUp() throws Exception {
    }

    public void testTimeParsing() {

        // Test PM time parsing
        final String[] pmSuffixes = new String[] { "p.m.", "pm", "p" };
        for (int i = 0; i < pmSuffixes.length; i++) {
            final String pmSuffix = pmSuffixes[i];

            assertEquals(XFormsInputControl.testParseTime("3:34:56 " + pmSuffix), "15:34:56");
            assertEquals(XFormsInputControl.testParseTime("3:34 " + pmSuffix), "15:34:00");
            assertEquals(XFormsInputControl.testParseTime("3 " + pmSuffix), "15:00:00");
        }

        // Test AM time parsing
        final String[] amSuffixes = new String[] { "a.m.", "am", "a", "" };
        for (int i = 0; i < amSuffixes.length; i++) {
            final String amSuffix = amSuffixes[i];

            assertEquals(XFormsInputControl.testParseTime("3:34:56 " + amSuffix), "03:34:56");
            assertEquals(XFormsInputControl.testParseTime("3:34 " + amSuffix), "03:34:00");
            assertEquals(XFormsInputControl.testParseTime("3 " + amSuffix), "03:00:00");
        }

        // TODO
//        assertEquals(XFormsInputControl.testTimeParse("123456"), "xxx");
    }
}