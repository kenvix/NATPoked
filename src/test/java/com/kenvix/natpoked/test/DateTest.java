//--------------------------------------------------
// Class DateTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class DateTest {
    @Test
    public void dateTest() throws ParseException {
        String test = "Thu Mar 31 21:23:11 CST 2022";
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
        var d = formatter.parse(test);
    }

    @Test
    public void mathTest() throws Exception {

    }
}
