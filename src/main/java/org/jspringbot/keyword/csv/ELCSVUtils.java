package org.jspringbot.keyword.csv;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang.StringUtils;
import org.jspringbot.spring.ApplicationContextHolder;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class ELCSVUtils {

    private static CSVHelper getHelper() {
        return ApplicationContextHolder.get().getBean(CSVHelper.class);
    }

    public static String[] asArray(String csv) throws IOException {
        StringReader str = new StringReader(csv);

        CSVReader reader = new CSVReader(str);
        return reader.readNext();
    }

    public static String asLine(Object... items) throws IOException {
        StringWriter str = new StringWriter();
        CSVWriter writer = new CSVWriter(str);

        String[] line = new String[items.length];
        for(int i = 0; i < items.length; i++) {
            line[i] = String.valueOf(items[i]);
        }

        writer.writeNext(line);
        writer.flush();
        writer.close();

        return StringUtils.trim(str.toString());
    }

    public static String value(String[] line, String columnName) throws IOException {
        return getHelper().getColumnValue(line, columnName);
    }
}
