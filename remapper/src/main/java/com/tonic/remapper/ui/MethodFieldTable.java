package com.tonic.remapper.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MethodFieldTable extends AbstractTableModel {
    private final Kind kind;
    private ClassMapping current;
    private String filter = "";
    private List<Integer> filteredRows = new ArrayList<>();

    MethodFieldTable(Kind k) { this.kind = k; }

    void setClass(ClassMapping cm) {
        current = cm;
        updateFilteredRows();
        fireTableDataChanged();
    }

    void setFilter(String f) {
        if (f == null) f = "";
        filter = f.trim().toLowerCase(Locale.ROOT);
        updateFilteredRows();
        fireTableDataChanged();
    }

    private void updateFilteredRows() {
        filteredRows.clear();
        if (current == null) return;
        if (filter.isEmpty()) {
            int size = kind == Kind.METHOD ? current.methods.size() : current.fields.size();
            for (int i = 0; i < size; i++) filteredRows.add(i);
            return;
        }
        if (kind == Kind.METHOD) {
            for (int i = 0; i < current.methods.size(); i++) {
                MethodRecord mr = current.methods.get(i);
                String obf = (mr.node.name + mr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = mr.newName != null ? mr.newName.toLowerCase(Locale.ROOT) : "";
                if (obf.contains(filter) || mapped.contains(filter)) {
                    filteredRows.add(i);
                }
            }
        } else {
            for (int i = 0; i < current.fields.size(); i++) {
                FieldRecord fr = current.fields.get(i);
                String obf = (fr.node.name + " " + fr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = fr.newName != null ? fr.newName.toLowerCase(Locale.ROOT) : "";
                if (obf.contains(filter) || mapped.contains(filter)) {
                    filteredRows.add(i);
                }
            }
        }
    }

    @Override
    public int getRowCount() { return current == null ? 0 : filteredRows.size(); }

    @Override
    public int getColumnCount() { return 2; }

    @Override
    public String getColumnName(int col) { if (col == 0) return "Obf"; if (col == 1) return "Mapped"; return ""; }

    @Override
    public Object getValueAt(int row, int col) {
        if (current == null) return null;
        int underlying = filteredRows.get(row);
        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(underlying);
            if (col == 0) return mr.node.name + mr.node.desc;
            return mr.newName;
        } else {
            FieldRecord fr = current.fields.get(underlying);
            if (col == 0) return fr.node.name + " " + fr.node.desc;
            return fr.newName;
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) { return col == 1; }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (current == null || col != 1) return;
        int underlying = filteredRows.get(row);
        String v = aValue == null ? null : aValue.toString();
        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(underlying);
            mr.newName = v;
            String key = mr.node.name + mr.node.desc;
            if (v == null || v.isBlank()) current.methodMap.remove(key);
            else current.methodMap.put(key, v);
        } else {
            FieldRecord fr = current.fields.get(underlying);
            fr.newName = v;
            String key = fr.node.name + " " + fr.node.desc;
            if (v == null || v.isBlank()) current.fieldMap.remove(key);
            else current.fieldMap.put(key, v);
        }
        fireTableRowsUpdated(row, row);
    }
}