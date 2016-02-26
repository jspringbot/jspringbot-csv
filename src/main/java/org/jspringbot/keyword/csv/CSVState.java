/*
 * Copyright (c) 2012. JSpringBot. All Rights Reserved.
 *
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The JSpringBot licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jspringbot.keyword.csv;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.jspringbot.keyword.csv.criteria.*;
import org.jspringbot.syntax.HighlightRobotLogger;

import java.io.*;
import java.util.*;

import static org.jspringbot.keyword.expression.utils.ELFileUtils.resource;
import static org.jspringbot.keyword.expression.utils.ELFileUtils.stream;

public class CSVState {

    public static final HighlightRobotLogger LOG = HighlightRobotLogger.getLogger(CSVState.class);
    public static final String OR_DISJUNCTION = "OR";
    public static final String AND_CONJUNCTION = "AND";

    public static int MAX_LOG_LINES = 50;

    private List<String[]> lines;

    private String[] headerRow;

    private Map<String, Integer> headers;

    private String name;

    private CSVLineCriteria currentCriteria;

    private List<String[]> queryResults;

    private Stack<RestrictionAppender> appender;

    private File openedFile;

    public CSVState(String name) {
        this.name = name;
    }

    public CSVState listAsNewState(String name) {
        CSVState newState = new CSVState(name);
        newState.lines = list();
        newState.headers = headers;

        return newState;
    }

    public String getName() {
        return name;
    }

    public void createCriteria() {
        appender = new Stack<RestrictionAppender>();
        currentCriteria = new CSVLineCriteria(lines, headers);
        queryResults = null;

        appender.push(currentCriteria);
    }

    public void startDisjunction() {
        appender.push(Restrictions.disjunction());
    }

    public void startConjunction() {
        appender.push(Restrictions.conjunction());
    }

    public void endDisjunction() {
        RestrictionAppender disjunction = appender.pop();

        if (!DisjunctionRestriction.class.isInstance(disjunction)) {
            throw new IllegalStateException("Last started was not an or disjunction.");
        }

        appender.peek().append((DisjunctionRestriction) disjunction);
    }

    public void endConjunction() {
        RestrictionAppender conjunction = appender.pop();

        if (!ConjunctionRestriction.class.isInstance(conjunction)) {
            throw new IllegalStateException("Last started was not an and conjunction.");
        }

        appender.peek().append((ConjunctionRestriction) conjunction);
    }

    public void addColumnNameEqualsRestriction(String name, String value) {
        appender.peek().append(Restrictions.columnNameEquals(name, value));
        queryResults = null;

        LOG.createAppender()
                .append("Add Column Name Equals Restriction:")
                .appendProperty("Column Name", name)
                .appendProperty("Column Value Equals", value)
                .appendProperty("junction", getJunction())
                .log();
    }

    private String getJunction() {
        if (DisjunctionRestriction.class.isInstance(appender.peek())) {
            return OR_DISJUNCTION;
        }

        return AND_CONJUNCTION;
    }

    public void addColumnIndexEqualsRestriction(int index, String value) {
        appender.peek().append(Restrictions.columnIndexEquals(index, value));

        LOG.createAppender()
                .append("Add Column Index Equals Restriction:")
                .appendProperty("Column Index", index)
                .appendProperty("Column Value Equals", value)
                .appendProperty("junction", getJunction())
                .log();
    }

    public int projectCount() {
        int count = currentCriteria.count();

        LOG.createAppender()
                .append("Projected Count:")
                .appendProperty("Count", count)
                .log();

        return count;
    }

    public List<String[]> list() {
        if (currentCriteria == null) {
            throw new IllegalStateException("No csv criteria was created.");
        }

        if (CollectionUtils.isEmpty(appender)) {
            throw new IllegalStateException("No restriction appenders found.");
        }

        if (appender.size() != 1) {
            throw new IllegalStateException("Junction restriction was not properly ended.");
        }

        if (queryResults == null) {
            queryResults = currentCriteria.list();
        }

        LOG.createAppender()
                .append("CSV List Result:")
                .appendText(toCSV(queryResults))
                .log();

        return queryResults;
    }

    public List<Map<String, String>> map() {
        if (currentCriteria == null) {
            throw new IllegalStateException("No csv criteria was created.");
        }

        if (CollectionUtils.isEmpty(appender)) {
            throw new IllegalStateException("No restriction appenders found.");
        }

        if (appender.size() != 1) {
            throw new IllegalStateException("Junction restriction was not properly ended.");
        }

        if (queryResults == null) {
            queryResults = currentCriteria.list();
        }

        LOG.createAppender()
                .append("CSV Map Result:")
                .appendText(toCSV(queryResults))
                .log();

        List<Map<String, String>> results = new ArrayList<Map<String, String>>(queryResults.size());
        for(String[] item : queryResults) {
            results.add(toMap(item));
        }

        return results;
    }

    public String[] uniqueResult() {
        String[] result = currentCriteria.uniqueResult();

        LOG.createAppender()
                .append("Unique Result:")
                .appendPropertyStringArray("Result", result)
                .log();

        return result;
    }

    public Map<String, String> uniqueMapResult() {
        String[] result = currentCriteria.uniqueResult();

        Map<String, String> row = toMap(result);

        LOG.createAppender()
                .append("Unique Map Result:")
                .appendProperty("Result", row)
                .log();

        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toMap(String[] result) {
        if(headerRow == null || headerRow.length == 0) {
            throw new IllegalStateException("No headers found.");
        }

        Map<String, String> row = new CaseInsensitiveMap(result.length);
        for(int i = 0; i < headerRow.length; i++) {
            row.put(headerRow[i], result[i]);
        }
        return row;
    }


    public String[] firstResult() {
        String[] result = currentCriteria.firstResult();

        LOG.createAppender()
                .append("First Result:")
                .appendPropertyStringArray("Result", result)
                .log();

        return result;
    }

    public Map<String, String> firstMapResult() {
        String[] result = currentCriteria.firstResult();

        Map<String, String> row = toMap(result);

        LOG.createAppender()
                .append("First Map Result:")
                .appendProperty("Result", row)
                .log();

        return row;
    }

    public String firstResultColumnIndex(int index) {
        String[] result = firstResult();

        String resultStr = result[index];

        LOG.createAppender()
                .append("First Result Column Index:")
                .appendProperty("Column Index", index)
                .appendProperty("Column Value", resultStr)
                .log();

        return resultStr;
    }

    public String firstResultColumnName(String name) {
        Validate.isTrue(headers.containsKey(name), String.format("No header with name '%s'", name));

        String result = firstResultColumnIndex(headers.get(name));

        LOG.createAppender()
                .append("First Result Column Name:")
                .appendProperty("Column Name", name)
                .appendProperty("Column Value", result)
                .log();

        return result;
    }

    public String lastResultColumnIndex(int index) {
        String[] result = lastResult();

        String columnValue = result[index];

        LOG.createAppender()
                .append("Last Result Column Index:")
                .appendProperty("Column Index", index)
                .appendProperty("Column Value", columnValue)
                .log();

        return columnValue;
    }

    public String lastResultColumnName(String name) {
        Validate.isTrue(headers.containsKey(name), String.format("No header with name '%s'", name));

        String columnValue = lastResultColumnIndex(headers.get(name));

        LOG.createAppender()
                .append("Last Result Column Name:")
                .appendProperty("Column Name", name)
                .appendProperty("Column Value", columnValue)
                .log();

        return columnValue;
    }

    public String getColumnValue(String[] line, String name) {
        return line[headers.get(name)];
    }

    public List<String> getColumnValues(int index) {
        List<String[]> results = list();
        List<String> columnList = new ArrayList<String>(results.size());

        for (String[] line : results) {
            columnList.add(line[index]);
        }

        return columnList;
    }


    public List<String> listColumnName(String name) {
        List<String> result = listColumnIndex(headers.get(name));

        LOG.createAppender()
                .append("List Column Name:")
                .appendProperty("Column Name", name)
                .log();

        return result;
    }

    public List<String> listColumnIndex(int index) {
        Validate.isTrue(headers.containsKey(name), String.format("No header with name '%s'", name));

        List<String[]> results = list();
        List<String> columnList = new ArrayList<String>(results.size());

        for (String[] line : results) {
            columnList.add(line[index]);
        }

        LOG.createAppender()
                .append("List Column Index:")
                .appendProperty("Column Index", index)
                .appendPropertyStringArray("Result", columnList.toArray(new String[columnList.size()]))
                .log();

        return columnList;
    }

    public String[] lastResult() {
        return currentCriteria.lastResult();
    }

    public void parseCSVString(String csv) throws IOException {
        readAll(new StringReader(csv));

        LOG.createAppender()
                .appendBold("Parse CSV String:")
                .append(" (count=%d)", CollectionUtils.size(lines))
                .appendText(toCSV(lines))
                .log();
    }

    public void parseCSVResource(String resource) throws Exception {
        openedFile = resource(resource);

        HighlightRobotLogger.HtmlAppender appender = LOG.createAppender()
                .appendBold("Parse CSV Resource:");

        if (openedFile.isFile()) {
            readAll(new InputStreamReader(stream(openedFile)));

            appender.append(" (count=%d)", CollectionUtils.size(lines));

            if (CollectionUtils.size(lines) <= MAX_LOG_LINES) {
                appender.appendText(toCSV(lines));
            }
        } else {
            appender.append("file not found");
            lines = new ArrayList<String[]>();
        }

        appender.log();
    }

    public void appendCSVLine(String csv) throws IOException {
        LOG.createAppender()
                .appendBold("Append CSV Line:")
                .appendCode(csv)
                .log();

        CSVWriter writer = new CSVWriter(new FileWriter(openedFile, true));

        if (headerRow != null && CollectionUtils.isEmpty(lines)) {
            writer.writeNext(headerRow);
        }

        String[] line = toLine(csv);
        lines.add(line);

        writer.writeNext(line);
        writer.close();
    }

    private void setHeaders(String[] headers) {
        headerRow = headers;
        this.headers = new HashMap<String, Integer>(headers.length);
        for (int i = 0; i < headers.length; i++) {
            this.headers.put(headers[i], i);
        }

        LOG.createAppender()
                .append("Set Headers:")
                .appendPropertyStringArray("Headers", headers)
                .log();
    }

    public void setHeaders(String headers) throws IOException {
        setHeaders(toLine(headers));
    }

    public void setFirstLineAsHeader() {
        if (CollectionUtils.isNotEmpty(lines)) {
            ListIterator<String[]> itr = lines.listIterator();

            setHeaders(itr.next());
            itr.remove();
        }
    }

    private String toCSV(List<String[]> lines) {
        StringWriter str = new StringWriter();
        CSVWriter writer = new CSVWriter(str);

        writer.writeAll(lines);
        str.flush();

        return str.toString();
    }

    public String[] toLine(String csv) throws IOException {
        StringReader str = new StringReader(csv);

        CSVReader reader = new CSVReader(str);
        return reader.readNext();
    }

    private void readAll(Reader reader) throws IOException {
        CSVReader r = new CSVReader(reader);

        try {
            lines = r.readAll();
        } finally {
            IOUtils.closeQuietly(r);
            IOUtils.closeQuietly(reader);
        }
    }

    public List<String[]> getLines() {
        return lines;
    }
}
